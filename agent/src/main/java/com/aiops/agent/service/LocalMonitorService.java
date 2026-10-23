package com.aiops.agent.service;

import com.aiops.agent.config.MonitorProperties;
import com.aiops.agent.dto.MonitorSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采集本机实时运行指标。
 *
 * <p>诊断期间会把开始时采集的快照 pin 到当前线程，后续所有工具调用复用同一份快照，
 * 保证 Agent 看到的是同一个时间点的数据。</p>
 */
@Slf4j
@Service
public class LocalMonitorService {

    private static final ThreadLocal<MonitorSnapshot> PINNED_SNAPSHOT =
            new ThreadLocal<>();

    private final MonitorProperties properties;
    private final OperatingSystemMXBean osBean;
    private final com.sun.management.OperatingSystemMXBean extendedOsBean;

    public LocalMonitorService(MonitorProperties properties) {
        this.properties = properties;
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.extendedOsBean = osBean instanceof com.sun.management.OperatingSystemMXBean extended
                ? extended
                : null;
    }

    /**
     * 诊断期间优先返回已 pin 的快照，否则采集一次最新快照。
     */
    public MonitorSnapshot capture() {
        MonitorSnapshot pinned = PINNED_SNAPSHOT.get();
        return pinned != null ? pinned : captureFresh();
    }

    /**
     * 强制采集一份新的本机快照。
     */
    public MonitorSnapshot captureFresh() {
        String deviceId = resolveDeviceId();
        Map<String, Object> metrics = collectMetrics(deviceId);
        Map<String, Object> network = collectNetwork(deviceId);
        Map<String, Object> service = collectService(deviceId);

        return new MonitorSnapshot(
                deviceId,
                metrics,
                network,
                service,
                false,
                LocalDateTime.now());
    }

    public void pin(MonitorSnapshot snapshot) {
        PINNED_SNAPSHOT.set(snapshot);
    }

    public void unpin() {
        PINNED_SNAPSHOT.remove();
    }

    private Map<String, Object> collectMetrics(String deviceId) {
        double cpuLoad = -1;
        if (extendedOsBean != null) {
            cpuLoad = extendedOsBean.getCpuLoad();
        }

        if (cpuLoad < 0 || Double.isNaN(cpuLoad)) {
            double systemLoad = osBean.getSystemLoadAverage();
            cpuLoad = systemLoad >= 0
                    ? Math.min(1.0, systemLoad / Math.max(1, osBean.getAvailableProcessors()))
                    : 0;
        }

        double cpuUsage = round(clamp(cpuLoad, 0, 1) * 100);

        long totalMemory = extendedOsBean != null
                ? extendedOsBean.getTotalMemorySize()
                : Runtime.getRuntime().totalMemory();
        long freeMemory = extendedOsBean != null
                ? extendedOsBean.getFreeMemorySize()
                : Runtime.getRuntime().freeMemory();

        if (totalMemory <= 0) {
            totalMemory = Runtime.getRuntime().totalMemory();
            freeMemory = Runtime.getRuntime().freeMemory();
        }

        double memoryUsage = totalMemory > 0
                ? round((totalMemory - freeMemory) * 100.0 / totalMemory)
                : 0;

        long totalDisk = 0;
        long freeDisk = 0;
        try {
            FileStore store = Files.getFileStore(
                    Path.of(System.getProperty("user.dir", ".")));
            totalDisk = store.getTotalSpace();
            freeDisk = store.getUsableSpace();
        } catch (IOException exception) {
            log.debug("读取磁盘使用率失败: {}", exception.getMessage());
        }

        double diskUsage = totalDisk > 0
                ? round((totalDisk - freeDisk) * 100.0 / totalDisk)
                : 0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tool", "system_metrics");
        metrics.put("deviceId", deviceId);
        metrics.put("cpuUsage", cpuUsage);
        metrics.put("memoryUsage", memoryUsage);
        metrics.put("diskUsage", diskUsage);
        metrics.put("totalMemoryBytes", totalMemory);
        metrics.put("freeMemoryBytes", freeMemory);
        metrics.put("totalDiskBytes", totalDisk);
        metrics.put("freeDiskBytes", freeDisk);
        metrics.put("cpuHigh", cpuUsage >= 80);
        metrics.put("memoryHigh", memoryUsage >= 85);
        metrics.put("diskHigh", diskUsage >= 90);
        metrics.put("availableProcessors", osBean.getAvailableProcessors());
        metrics.put("osName", System.getProperty("os.name", "unknown"));
        metrics.put("osArch", System.getProperty("os.arch", "unknown"));
        metrics.put("simulated", false);
        return metrics;
    }

    private Map<String, Object> collectNetwork(String deviceId) {
        String host = defaultIfBlank(properties.getNetworkHost(), "www.baidu.com");
        int port = properties.getNetworkPort() > 0 ? properties.getNetworkPort() : 443;
        int timeout = Math.max(100, properties.getNetworkTimeoutMs());

        boolean reachable = false;
        long latencyMs = -1;

        try (Socket socket = new Socket()) {
            long start = System.nanoTime();
            socket.connect(new InetSocketAddress(host, port), timeout);
            latencyMs = (System.nanoTime() - start) / 1_000_000;
            reachable = true;
        } catch (IOException exception) {
            log.debug("网络探测失败 {}:{} - {}", host, port, exception.getMessage());
        }

        Map<String, Object> network = new LinkedHashMap<>();
        network.put("tool", "network_check");
        network.put("deviceId", deviceId);
        network.put("targetHost", host);
        network.put("targetPort", port);
        network.put("reachable", reachable);
        network.put("latencyMs", latencyMs);
        network.put("simulated", false);
        return network;
    }

    private Map<String, Object> collectService(String deviceId) {
        String host = defaultIfBlank(properties.getServiceHost(), "127.0.0.1");
        int port = properties.getServicePort() > 0 ? properties.getServicePort() : 3306;
        int timeout = Math.max(100, properties.getServiceTimeoutMs());

        boolean running = probeTcp(host, port, timeout);

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("tool", "service_status");
        service.put("deviceId", deviceId);
        service.put("serviceName",
                defaultIfBlank(properties.getServiceName(), "MySQL"));
        service.put("host", host);
        service.put("port", port);
        service.put("running", running);
        service.put("simulated", false);
        return service;
    }

    private boolean probeTcp(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (IOException exception) {
            log.debug("服务探测失败 {}:{} - {}", host, port, exception.getMessage());
            return false;
        }
    }

    private String resolveDeviceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return defaultIfBlank(System.getenv("COMPUTERNAME"), "local-machine");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
