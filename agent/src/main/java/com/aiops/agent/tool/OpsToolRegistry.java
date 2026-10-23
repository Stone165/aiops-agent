package com.aiops.agent.tool;

import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.service.LocalMonitorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运维工具注册表：集中维护工具定义、白名单和唯一执行入口。
 *
 * <p>工具数据全部来自本机实时快照；诊断期间使用 pin 住的同一份快照。</p>
 */
@Component
public class OpsToolRegistry {

    public static final String TOOL_METRICS = "check_system_metrics";
    public static final String TOOL_NETWORK = "check_network";
    public static final String TOOL_SERVICE = "check_service_status";

    private static final String TOOL_DEFINITIONS = """
            [
              {
                "type": "function",
                "function": {
                  "name": "check_system_metrics",
                  "description": "检查本机 CPU、内存和磁盘使用情况",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "deviceId": {
                        "type": "string",
                        "description": "设备标识"
                      }
                    },
                    "required": ["deviceId"],
                    "additionalProperties": false
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "check_network",
                  "description": "检查本机到配置目标的网络连通性和延迟",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "deviceId": {
                        "type": "string",
                        "description": "设备标识"
                      }
                    },
                    "required": ["deviceId"],
                    "additionalProperties": false
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "check_service_status",
                  "description": "检查本机配置的本地服务是否运行",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "deviceId": {
                        "type": "string",
                        "description": "设备标识"
                      }
                    },
                    "required": ["deviceId"],
                    "additionalProperties": false
                  }
                }
              }
            ]
            """;

    private final ArrayNode definitions;
    private final LocalMonitorService monitorService;

    public OpsToolRegistry(ObjectMapper objectMapper,
                           LocalMonitorService monitorService) {
        this.definitions = parseDefinitions(objectMapper);
        this.monitorService = monitorService;
    }

    /**
     * 返回工具定义的副本，避免调用方修改注册表内部状态。
     */
    public ArrayNode definitions() {
        return definitions.deepCopy();
    }

    /**
     * 唯一允许的工具执行入口。未知工具直接返回错误，不执行任何逻辑。
     */
    public Map<String, Object> execute(String toolName, String deviceId) {
        if (!isSupported(toolName)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("tool", toolName);
            error.put("error", "不支持的工具：" + toolName);
            error.put("allowed", false);
            return error;
        }

        MonitorSnapshot snapshot = monitorService.capture();
        return switch (toolName) {
            case TOOL_METRICS -> snapshot.metrics();
            case TOOL_NETWORK -> snapshot.network();
            case TOOL_SERVICE -> snapshot.service();
            default -> throw new IllegalStateException("不支持的工具：" + toolName);
        };
    }

    public boolean isSupported(String toolName) {
        return TOOL_METRICS.equals(toolName)
                || TOOL_NETWORK.equals(toolName)
                || TOOL_SERVICE.equals(toolName);
    }

    private ArrayNode parseDefinitions(ObjectMapper objectMapper) {
        try {
            return (ArrayNode) objectMapper.readTree(TOOL_DEFINITIONS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("运维工具定义不是合法 JSON", exception);
        }
    }
}
