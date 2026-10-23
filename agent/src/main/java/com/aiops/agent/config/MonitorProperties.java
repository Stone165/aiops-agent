package com.aiops.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本机监控采集配置。
 */
@Data
@ConfigurationProperties(prefix = "app.monitor")
public class MonitorProperties {

    /**
     * 网络连通性探测目标，默认探测公网 HTTPS 端口。
     */
    private String networkHost = "www.baidu.com";

    private int networkPort = 443;

    private int networkTimeoutMs = 1000;

    /**
     * 本地服务探测目标，默认检查本机 MySQL 端口。
     */
    private String serviceName = "MySQL";

    private String serviceHost = "127.0.0.1";

    private int servicePort = 3306;

    private int serviceTimeoutMs = 500;
}
