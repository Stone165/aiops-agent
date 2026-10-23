package com.aiops.agent.service;

import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.dto.ToolCallResult;
import com.aiops.agent.tool.OpsToolRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性规则诊断：不依赖大模型。
 *
 * <p>LOCAL 模式根据本机实时快照生成结论；TEXT 模式只解析工单描述中的指标。</p>
 */
@Service
public class DiagnosisService {

    private static final Pattern CPU_PATTERN = Pattern.compile(
            "(?i)(?:cpu|处理器)[^\\d%]{0,20}(\\d+(?:\\.\\d+)?)\\s*%");

    private static final Pattern MEMORY_PATTERN = Pattern.compile(
            "(?i)(?:内存|memory)[^\\d%]{0,20}(\\d+(?:\\.\\d+)?)\\s*%");

    private static final Pattern DISK_PATTERN = Pattern.compile(
            "(?i)(?:磁盘|硬盘|disk)[^\\d%]{0,20}(\\d+(?:\\.\\d+)?)\\s*%");

    private static final Pattern LATENCY_PATTERN = Pattern.compile(
            "(?i)(?:延迟|latency|ping)[^\\d]{0,20}(\\d+(?:\\.\\d+)?)\\s*ms");

    private static final Pattern SERVICE_DOWN_PATTERN = Pattern.compile(
            "(?i)(?:服务|service)[^\\n。]{0,40}(?:未运行|停止|挂了|down|不可用)");

    public DiagnosisResult diagnose(String deviceId,
                                    String description,
                                    MonitorSnapshot snapshot) {

        if (snapshot == null) {
            throw new IllegalArgumentException("监控快照不能为空");
        }

        Map<String, Object> metrics = snapshot.metrics();
        Map<String, Object> network = snapshot.network();
        Map<String, Object> service = snapshot.service();

        List<String> findings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (Boolean.TRUE.equals(metrics.get("cpuHigh"))) {
            findings.add("CPU 使用率偏高（" + metrics.get("cpuUsage") + "%）");
            suggestions.add("检查高 CPU 进程、线程和近期任务变化");
        }

        if (Boolean.TRUE.equals(metrics.get("memoryHigh"))) {
            findings.add("内存使用率偏高（" + metrics.get("memoryUsage") + "%）");
            suggestions.add("检查内存占用、内存泄漏和系统可用内存");
        }

        if (Boolean.TRUE.equals(metrics.get("diskHigh"))) {
            findings.add("磁盘使用率偏高（" + metrics.get("diskUsage") + "%）");
            suggestions.add("清理磁盘空间，检查日志、缓存和大文件占用");
        }

        if (!Boolean.TRUE.equals(network.get("reachable"))) {
            findings.add("网络不可达（目标 " + network.get("targetHost")
                    + ":" + network.get("targetPort") + "）");
            suggestions.add("检查本机网络、DNS、路由、防火墙和目标服务状态");
        }

        if (!Boolean.TRUE.equals(service.get("running"))) {
            findings.add("本地服务 " + service.get("serviceName") + " 未运行（"
                    + service.get("host") + ":" + service.get("port") + "）");
            suggestions.add("检查服务进程、端口监听状态和启动日志");
        }

        if (findings.isEmpty()) {
            findings.add("本机实时检测未发现明显异常");
            suggestions.add("继续观察 CPU、内存、磁盘和网络趋势，并结合应用日志排查");
        }

        List<ToolCallResult> toolCalls = List.of(
                new ToolCallResult(
                        OpsToolRegistry.TOOL_METRICS,
                        Map.of("deviceId", snapshot.deviceId()),
                        metrics),
                new ToolCallResult(
                        OpsToolRegistry.TOOL_NETWORK,
                        Map.of("deviceId", snapshot.deviceId()),
                        network),
                new ToolCallResult(
                        OpsToolRegistry.TOOL_SERVICE,
                        Map.of("deviceId", snapshot.deviceId()),
                        service));

        return new DiagnosisResult(
                deviceId,
                description,
                buildAnswer(findings, suggestions),
                findings,
                suggestions,
                toolCalls.size(),
                toolCalls,
                false,
                null,
                null,
                snapshot);
    }

    /**
     * 工单文本规则诊断：不读取本机指标，只解析描述中的 CPU/内存/磁盘/延迟/服务状态。
     */
    public DiagnosisResult diagnoseFromText(String deviceId, String description) {
        List<String> findings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        Double cpu = extract(CPU_PATTERN, description);
        if (cpu != null) {
            findings.add("描述中 CPU 使用率为 " + formatNumber(cpu) + "%");
            if (cpu >= 80) {
                suggestions.add("CPU 使用率偏高，检查高负载进程、线程和近期任务变化");
            }
        }

        Double memory = extract(MEMORY_PATTERN, description);
        if (memory != null) {
            findings.add("描述中内存使用率为 " + formatNumber(memory) + "%");
            if (memory >= 85) {
                suggestions.add("内存使用率偏高，检查内存占用、泄漏和系统可用内存");
            }
        }

        Double disk = extract(DISK_PATTERN, description);
        if (disk != null) {
            findings.add("描述中磁盘使用率为 " + formatNumber(disk) + "%");
            if (disk >= 90) {
                suggestions.add("磁盘使用率偏高，清理日志、缓存和大文件");
            }
        }

        Double latency = extract(LATENCY_PATTERN, description);
        if (latency != null) {
            findings.add("描述中网络延迟为 " + formatNumber(latency) + "ms");
            if (latency >= 200) {
                suggestions.add("网络延迟偏高，检查本机网络、DNS、路由和目标服务");
            }
        }

        if (SERVICE_DOWN_PATTERN.matcher(description).find()) {
            findings.add("描述中提到业务服务未运行或不可用");
            suggestions.add("检查服务进程、端口监听和启动日志");
        }

        if (findings.isEmpty()) {
            findings.add("描述中未识别到明确的可量化指标");
        }

        suggestions.add("建议补充故障发生时间、影响范围、错误日志以及 CPU/内存/磁盘/延迟等指标");

        return new DiagnosisResult(
                deviceId,
                description,
                buildTextAnswer(findings, suggestions),
                findings,
                suggestions,
                0,
                List.of(),
                false,
                null,
                null,
                null);
    }

    private Double extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private String formatNumber(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private String buildTextAnswer(List<String> findings, List<String> suggestions) {
        StringBuilder answer = new StringBuilder("文本诊断结论：\n");
        for (String finding : findings) {
            answer.append("- ").append(finding).append('\n');
        }

        answer.append("\n处理建议：\n");
        for (String suggestion : suggestions) {
            answer.append("- ").append(suggestion).append('\n');
        }

        answer.append("\n说明：当前为工单文本分析模式，未读取本机实时指标。");
        return answer.toString();
    }

    private String buildAnswer(List<String> findings, List<String> suggestions) {
        StringBuilder answer = new StringBuilder("诊断结论：\n");
        for (String finding : findings) {
            answer.append("- ").append(finding).append('\n');
        }

        answer.append("\n处理建议：\n");
        for (String suggestion : suggestions) {
            answer.append("- ").append(suggestion).append('\n');
        }

        answer.append("\n说明：以上结果基于本机实时采集的 CPU、内存、磁盘、网络和本地服务指标。");
        return answer.toString();
    }
}
