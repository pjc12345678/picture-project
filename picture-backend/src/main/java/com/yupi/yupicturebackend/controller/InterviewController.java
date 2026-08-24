package com.yupi.yupicturebackend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import com.yupi.yupicturebackend.manager.ReportCosManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 面试代理接口：将面试相关请求转发给独立的 Agent 服务（ai-agent）
 */
@Slf4j
@RestController
@RequestMapping("/interview")
public class InterviewController {

    @Value("${agent.base-url:}")
    private String agentBaseUrl;

    @Resource
    private ReportCosManager reportCosManager;

    /**
     * 使用 Spring 原生 RestTemplate（配置 UTF-8，避免 hutool 在 Java 8 下的中文编码问题）
     */
    private final RestTemplate restTemplate;

    /**
     * 用 Jackson 解析 Agent 响应（不能用 hutool JSONObject，其 JSONNull 无法被 Jackson 序列化）
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewController() {
        this.restTemplate = new RestTemplate();
        // 关键：Spring 的 StringHttpMessageConverter 默认用 ISO-8859-1 解码，必须改成 UTF-8，否则中文乱码
        restTemplate.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * 分析简历文本，生成候选人画像（转发到 Agent /resume/receive）
     */
    @PostMapping("/analyze")
    public BaseResponse<Map<String, Object>> analyzeResume(@RequestBody Map<String, Object> body) {
        return forward("/resume/receive", body);
    }

    /**
     * 开始面试，生成面试计划（转发到 Agent /interview/start）
     */
    @PostMapping("/start")
    public BaseResponse<Map<String, Object>> startInterview(@RequestBody Map<String, Object> body) {
        return forward("/interview/start", body);
    }

    /**
     * 面试对话（转发到 Agent /interview/chat）
     */
    @PostMapping("/chat")
    public BaseResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        return forward("/interview/chat", body);
    }

    /**
     * 生成面试总结（转发到 Agent /interview/summary）
     */
    @PostMapping("/summary")
    public BaseResponse<Map<String, Object>> summary(@RequestBody Map<String, Object> body) {
        return forward("/interview/summary", body);
    }

    /**
     * 存储面试报告 PDF 到 COS，返回公网可访问 URL（由 ai-agent 上传调用）
     *
     * @param file PDF 文件
     * @return COS URL
     */
    @PostMapping("/pdf/store")
    public BaseResponse<String> storePdf(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        // key 使用纯英文（避免中文文件名导致 URL 访问编码问题）
        String key = "interview-report/" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
        File tempFile = null;
        try {
            tempFile = File.createTempFile("report_", ".pdf");
            file.transferTo(tempFile);
            // 上传到面试报告专用存储桶，返回公网 URL
            String url = reportCosManager.putReportObject(key, tempFile);
            log.info("面试报告已存储到 COS：{}", url);
            return ResultUtils.success(url);
        } catch (Exception e) {
            log.error("面试报告存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "面试报告存储失败");
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    /**
     * 转发请求到 Agent 服务并透传响应
     */
    private BaseResponse<Map<String, Object>> forward(String path, Map<String, Object> body) {
        if (agentBaseUrl == null || agentBaseUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 服务未配置（agent.base-url）");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> entity = restTemplate.postForEntity(agentBaseUrl + path, request, String.class);
            String response = entity.getBody();
            log.info("Agent 响应（{}）：{}", path, response);
            // 校验响应是否为合法 JSON
            if (response == null || !response.trim().startsWith("{")) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 服务返回异常（非 JSON 响应）");
            }
            // 用 Jackson 解析为普通 Map（null 值序列化无障碍）
            Map<String, Object> data = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            return ResultUtils.success(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("转发 Agent 失败：{}", path, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 服务调用失败，请稍后重试");
        }
    }
}
