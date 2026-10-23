package com.aiops.agent.controller;

import com.aiops.agent.dto.DiagnosisResult;
import com.aiops.agent.dto.MonitorSnapshot;
import com.aiops.agent.dto.ToolCallResult;
import com.aiops.agent.service.DeepSeekAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DiagnosisApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeepSeekAgentService agentService;

    @Test
    void shouldPersistAgentDiagnosisAndExposeHistory() throws Exception {
        when(agentService.diagnose(anyString(), anyString(), any(MonitorSnapshot.class)))
                .thenReturn(sampleResult());

        MvcResult result = mockMvc.perform(post("/api/diagnosis/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "server-001",
                                  "description": "业务接口变慢"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("AI 诊断结论"))
                .andExpect(jsonPath("$.toolCallCount").value(1))
                .andExpect(jsonPath("$.simulated").value(false))
                .andExpect(jsonPath("$.monitorSnapshot.deviceId").isNotEmpty())
                .andExpect(jsonPath("$.monitorSnapshot.metrics.cpuUsage").isNumber())
                .andExpect(jsonPath("$.recordId").isNumber())
                .andReturn();

        long recordId = objectMapper.readTree(result.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .path("recordId").asLong();

        mockMvc.perform(get("/api/diagnosis/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].id").value(recordId))
                .andExpect(jsonPath("$.records[0].toolCalls.length()").value(1))
                .andExpect(jsonPath("$.records[0].toolCalls[0].tool")
                        .value("check_system_metrics"))
                .andExpect(jsonPath("$.records[0].monitorSnapshot").exists());

        mockMvc.perform(get("/api/diagnosis/records/{id}", recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("AI 诊断结论"))
                .andExpect(jsonPath("$.monitorSnapshot").exists())
                .andExpect(jsonPath("$.toolCalls[0].result.cpuUsage").value(82.0));
    }

    @Test
    void shouldRunRuleDiagnosisWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/diagnosis/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "server-001",
                                  "description": "业务接口无法访问"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("诊断结论")))
                .andExpect(jsonPath("$.simulated").value(false))
                .andExpect(jsonPath("$.toolCalls.length()").value(3))
                .andExpect(jsonPath("$.monitorSnapshot.metrics.cpuUsage").isNumber())
                .andExpect(jsonPath("$.recordId").isNumber());

        mockMvc.perform(get("/api/diagnosis/records")
                        .param("deviceId", "server-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].monitorSnapshot").exists());
    }

    @Test
    void shouldSupportLegacyRunAlias() throws Exception {
        mockMvc.perform(post("/api/diagnosis/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "server-002",
                                  "description": "页面打开缓慢"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("处理建议")));
    }

    @Test
    void shouldAnalyzeIncidentTextWithoutLocalSnapshot() throws Exception {
        mockMvc.perform(post("/api/diagnosis/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "server-006",
                                  "description": "CPU 92%，内存 88%，磁盘 95%，服务未运行",
                                  "snapshotMode": "TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("文本诊断结论")))
                .andExpect(jsonPath("$.toolCallCount").value(0))
                .andExpect(jsonPath("$.toolCalls.length()").value(0))
                .andExpect(jsonPath("$.monitorSnapshot").doesNotExist())
                .andExpect(jsonPath("$.findings[0]", containsString("CPU 使用率为 92%")))
                .andExpect(jsonPath("$.recordId").isNumber());
    }

    @Test
    void shouldAnalyzeIncidentTextWithAi() throws Exception {
        when(agentService.diagnoseFromText(anyString(), anyString()))
                .thenReturn(textSampleResult());

        mockMvc.perform(post("/api/diagnosis/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": "server-006",
                                  "description": "CPU 92%，服务未运行",
                                  "snapshotMode": "TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("文本 AI 结论"))
                .andExpect(jsonPath("$.toolCallCount").value(0))
                .andExpect(jsonPath("$.toolCalls.length()").value(0))
                .andExpect(jsonPath("$.monitorSnapshot").doesNotExist())
                .andExpect(jsonPath("$.recordId").isNumber());
    }

    @Test
    void shouldRejectBlankDiagnosisAndMissingRecord() throws Exception {
        mockMvc.perform(post("/api/diagnosis/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "", "description": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/diagnosis/records/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("诊断记录不存在"));
    }

    private DiagnosisResult textSampleResult() {
        return new DiagnosisResult(
                "server-006",
                "CPU 92%，服务未运行",
                "文本 AI 结论",
                List.of(),
                List.of(),
                0,
                List.of(),
                false,
                null,
                null,
                null);
    }

    private DiagnosisResult sampleResult() {
        return new DiagnosisResult(
                "server-001",
                "业务接口变慢",
                "AI 诊断结论",
                List.of(),
                List.of(),
                1,
                List.of(new ToolCallResult(
                        "check_system_metrics",
                        Map.of("deviceId", "local-machine"),
                        Map.of("cpuUsage", 82.0, "simulated", false))),
                false,
                null,
                null,
                null);
    }
}
