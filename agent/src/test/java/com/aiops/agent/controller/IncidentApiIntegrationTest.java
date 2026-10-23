package com.aiops.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndReadIncident() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "业务接口超时",
                                  "description": "接口平均响应时间超过 5 秒",
                                  "deviceId": "server-001",
                                  "severity": "high"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("业务接口超时"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        long id = readId(result);

        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.deviceId").value("server-001"));
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "description": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "标题",
                                  "description": "描述",
                                  "severity": "URGENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("严重程度")));
    }

    @Test
    void shouldSearchFilterAndPaginate() throws Exception {
        createIncident("接口超时", "server-001", "HIGH");
        createIncident("接口 500", "server-002", "MEDIUM");
        createIncident("磁盘告警", "server-003", "CRITICAL");

        mockMvc.perform(get("/api/incidents/search")
                        .param("pageNum", "1")
                        .param("pageSize", "1")
                        .param("status", "OPEN")
                        .param("keyword", "接口"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].title").value(
                        org.hamcrest.Matchers.containsString("接口")));

        mockMvc.perform(get("/api/incidents/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.open").value(3))
                .andExpect(jsonPath("$.critical").value(1))
                .andExpect(jsonPath("$.high").value(1));
    }

    @Test
    void shouldUpdateStatusAndResolution() throws Exception {
        long id = createIncident("业务接口不可用", "server-001", "HIGH");

        mockMvc.perform(put("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("工单状态更新成功"))
                .andExpect(jsonPath("$.incident.status").value("IN_PROGRESS"));

        mockMvc.perform(put("/api/incidents/{id}/resolution", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootCause": "数据库连接池耗尽",
                                  "solution": "扩容连接池并增加监控",
                                  "status": "RESOLVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.status").value("RESOLVED"))
                .andExpect(jsonPath("$.incident.rootCause").value("数据库连接池耗尽"))
                .andExpect(jsonPath("$.incident.solution").value("扩容连接池并增加监控"));

        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("数据库连接池耗尽"));
    }

    @Test
    void shouldReturnNotFoundAndDeleteIncident() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("工单不存在"));

        long id = createIncident("待删除工单", "server-009", "LOW");

        mockMvc.perform(delete("/api/incidents/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/incidents/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidPageParametersAndFilters() throws Exception {
        mockMvc.perform(get("/api/incidents/page").param("pageNum", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/incidents/page").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/incidents/search").param("status", "BAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidStatusAndEmptyResolution() throws Exception {
        long id = createIncident("状态校验工单", "server-010", "LOW");

        mockMvc.perform(put("/api/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(put("/api/incidents/{id}/resolution", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("至少提交一个要更新的字段"));
    }

    private long createIncident(String title, String deviceId, String severity)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "description", "故障描述：" + title,
                                "deviceId", deviceId,
                                "severity", severity))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long readId(MvcResult result) throws Exception {
        String body = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("id").asLong();
    }
}
