package com.aiops.agent.service;

import com.aiops.agent.common.AiServiceException;
import com.aiops.agent.config.DeepSeekProperties;
import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.tool.OpsToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekAgentServiceTest {

    private static final String ENDPOINT =
            "https://api.deepseek.com/chat/completions";

    private static final String TOOL_CALL_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "check_system_metrics",
                          "arguments": "{\\"deviceId\\":\\"server-001\\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;

    private static final String FINAL_RESPONSE = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "CPU 使用率偏高，请检查高负载进程。"
                  }
                }
              ]
            }
            """;

    private MockRestServiceServer server;
    private DeepSeekAgentService service;
    private DeepSeekProperties properties;
    private MonitorSnapshot snapshot;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        snapshot = new MonitorSnapshot(
                "local-machine",
                Map.of(
                        "tool", "system_metrics",
                        "deviceId", "local-machine",
                        "cpuUsage", 42.0,
                        "memoryUsage", 50.0,
                        "diskUsage", 60.0,
                        "cpuHigh", false,
                        "memoryHigh", false,
                        "diskHigh", false,
                        "simulated", false),
                Map.of(
                        "tool", "network_check",
                        "deviceId", "local-machine",
                        "reachable", true,
                        "latencyMs", 3,
                        "simulated", false),
                Map.of(
                        "tool", "service_status",
                        "deviceId", "local-machine",
                        "serviceName", "test-service",
                        "running", true,
                        "simulated", false),
                false,
                LocalDateTime.now());

        LocalMonitorService monitorService = mock(LocalMonitorService.class);
        when(monitorService.capture()).thenReturn(snapshot);

        OpsToolRegistry registry =
                new OpsToolRegistry(objectMapper, monitorService);

        properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        properties.setModel("deepseek-flash");
        properties.setMaxToolRounds(3);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.deepseek.com");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        service = new DeepSeekAgentService(
                restClient, objectMapper, properties, registry);
    }

    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOOL_CALL_RESPONSE,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(FINAL_RESPONSE,
                        MediaType.APPLICATION_JSON));

        DiagnosisResult result = service.diagnose(
                "server-001", "业务接口变慢", snapshot);

        assertThat(result.answer()).contains("CPU 使用率偏高");
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).tool())
                .isEqualTo("check_system_metrics");
        assertThat(result.toolCalls().get(0).result())
                .containsEntry("cpuUsage", 42.0)
                .containsEntry("simulated", false);
        assertThat(result.simulated()).isFalse();
        assertThat(result.monitorSnapshot()).isSameAs(snapshot);
        assertThat(result.recordId()).isNull();
        server.verify();
    }

    @Test
    void shouldDiagnoseFromTextWithoutTools() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess(FINAL_RESPONSE,
                        MediaType.APPLICATION_JSON));

        DiagnosisResult result = service.diagnoseFromText(
                "server-006", "CPU 92%，内存 88%，服务未运行");

        assertThat(result.answer()).contains("CPU 使用率偏高");
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.monitorSnapshot()).isNull();
        assertThat(result.simulated()).isFalse();
        server.verify();
    }

    @Test
    void shouldContinueWhenToolArgumentsAreNotValidJson() {
        String malformed = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "check_system_metrics",
                              "arguments": "not-json"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(malformed, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(FINAL_RESPONSE,
                        MediaType.APPLICATION_JSON));

        DiagnosisResult result = service.diagnose(
                "server-001", "业务接口变慢", snapshot);

        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).result())
                .containsKey("error");
        assertThat(result.answer()).contains("CPU");
        server.verify();
    }

    @Test
    void shouldRejectUnknownToolWithoutExecutingIt() {
        String unknownTool = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "run_shell",
                              "arguments": "{}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(unknownTool, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(FINAL_RESPONSE,
                        MediaType.APPLICATION_JSON));

        DiagnosisResult result = service.diagnose(
                "server-001", "测试未知工具", snapshot);

        assertThat(result.toolCalls().get(0).result())
                .containsEntry("allowed", false);
        server.verify();
    }

    @Test
    void shouldFailWhenApiKeyIsMissing() {
        properties.setApiKey("  ");

        AiServiceException exception = catchThrowableOfType(
                AiServiceException.class,
                () -> service.diagnose("server-001", "测试", snapshot));

        assertThat(exception.getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).contains("DEEPSEEK_API_KEY");
    }

    @Test
    void shouldMapUnauthorizedToBadGateway() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        AiServiceException exception = catchThrowableOfType(
                AiServiceException.class,
                () -> service.diagnose("server-001", "测试", snapshot));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exception.getMessage()).contains("鉴权失败");
        server.verify();
    }

    @Test
    void shouldStopAfterMaxToolRounds() {
        properties.setMaxToolRounds(2);

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(TOOL_CALL_RESPONSE,
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(TOOL_CALL_RESPONSE,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.diagnose(
                "server-001", "反复调用", snapshot))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("最大工具调用轮次");
        server.verify();
    }
}
