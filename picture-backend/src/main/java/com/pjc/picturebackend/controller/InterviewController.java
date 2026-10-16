package com.pjc.picturebackend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjc.picturebackend.common.BaseResponse;
import com.pjc.picturebackend.common.ResultUtils;
import com.pjc.picturebackend.exception.BusinessException;
import com.pjc.picturebackend.exception.ErrorCode;
import com.pjc.picturebackend.manager.ReportCosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
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
     * 连接超时：Agent 是同机/同内网的独立服务，连不上就应该立刻失败，不要拖住请求线程
     */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /**
     * 慢接口读超时。Agent 侧的 chat / summary 内部有多次大模型调用，几十秒是常态。
     *
     * <p>⚠ 这里以前是"不设超时"（RestTemplate 默认无限等待）：Agent 一旦卡住，
     * picture 的请求线程就永久被占，堆到线程池耗尽整个服务不可用。加一个足够大的上限，
     * 是为了让"卡死"最终表现为一次可观测的失败，而不是一次静默的资源泄漏。
     */
    private static final int SLOW_READ_TIMEOUT_MS = 180000;

    /**
     * 快接口读超时。简历画像已改成异步任务后，提交 / 查询 / 取消都是纯内存操作（毫秒级），
     * 超时必须收紧——否则一次上游异常会让前端"轮询也卡死"，比原来的同步接口还糟。
     */
    private static final int FAST_READ_TIMEOUT_MS = 15000;

    /**
     * 使用 Spring 原生 RestTemplate（配置 UTF-8，避免 hutool 在 Java 8 下的中文编码问题）
     */
    private final RestTemplate restTemplate;

    /**
     * 给异步任务接口（提交 / 查询 / 取消）用的短超时实例
     */
    private final RestTemplate fastRestTemplate;

    /**
     * 用 Jackson 解析 Agent 响应（不能用 hutool JSONObject，其 JSONNull 无法被 Jackson 序列化）
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewController() {
        this.restTemplate = buildRestTemplate(SLOW_READ_TIMEOUT_MS);
        this.fastRestTemplate = buildRestTemplate(FAST_READ_TIMEOUT_MS);
    }

    /**
     * 构造一个带超时的 RestTemplate
     *
     * @param readTimeoutMs 读超时（毫秒）
     */
    private static RestTemplate buildRestTemplate(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(readTimeoutMs);
        RestTemplate template = new RestTemplate(factory);
        // 关键：Spring 的 StringHttpMessageConverter 默认用 ISO-8859-1 解码，必须改成 UTF-8，否则中文乱码
        template.getMessageConverters().forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });
        return template;
    }

    /**
     * 提交简历画像分析任务（异步）：立即返回 taskId，画像在 Agent 后台线程生成。
     *
     * <p>返回体：{ taskId, status, stage, elapsedMs }
     *
     * <p>⚠ 为什么不再是"一次请求直接拿画像"：生成画像是一次大模型调用，实测同类调用
     * p50 约 37s、最长 73s，而浏览器与 nginx（默认 proxy_read_timeout 60s）都等不了这么久。
     * 同步接口的结局是"后端还在算，前端已经断开"，用户白等、算力白花。
     * 拆成 submit + status 之后，每个请求都是秒级的，慢的只在 Agent 的后台线程里。
     */
    @PostMapping("/analyze")
    public BaseResponse<Map<String, Object>> analyzeResume(@RequestBody Map<String, Object> body) {
        return forwardFast("/resume/analyze/submit", body);
    }

    /**
     * 查询简历画像分析任务（前端约 2 秒轮询一次）。
     *
     * <p>返回体：{ taskId, status, stage, elapsedMs, result? , error? }
     * status 取值：QUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED。
     */
    @PostMapping("/analyze/status")
    public BaseResponse<Map<String, Object>> analyzeStatus(@RequestBody Map<String, Object> body) {
        return forwardFast("/resume/analyze/status", body);
    }

    /**
     * 取消简历画像分析任务。
     *
     * <p>已经发出的上游大模型调用无法真正中断，取消的语义是"结果不要了"：
     * 让用户能立刻退出等待，而不是被一个几十秒的请求绑住。
     */
    @PostMapping("/analyze/cancel")
    public BaseResponse<Map<String, Object>> analyzeCancel(@RequestBody Map<String, Object> body) {
        return forwardFast("/resume/analyze/cancel", body);
    }

    /**
     * 同步生成简历画像（保留给脚本 / 调试；前端不要用，会被网关 60s 超时掐断）
     */
    @PostMapping("/analyze/sync")
    public BaseResponse<Map<String, Object>> analyzeResumeSync(@RequestBody Map<String, Object> body) {
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
     * 恢复面试现场（转发到 Agent /interview/resume）。
     * 前端刷新页面/断线重连后调用，可拿回"当前第几题 + 题干 + 进度卡 + 待推送通知"。
     */
    @PostMapping("/resume")
    public BaseResponse<Map<String, Object>> resume(@RequestBody Map<String, Object> body) {
        return forward("/interview/resume", body);
    }

    /**
     * 主动中止面试（转发到 Agent /interview/abort）。
     * 流转到终态并标记为"部分完成"，之后可继续调用 /interview/summary 出部分报告。
     */
    @PostMapping("/abort")
    public BaseResponse<Map<String, Object>> abort(@RequestBody Map<String, Object> body) {
        return forward("/interview/abort", body);
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
     * 转发请求到 Agent 服务并透传响应（慢接口：读超时 180s）
     */
    private BaseResponse<Map<String, Object>> forward(String path, Map<String, Object> body) {
        return forward(restTemplate, path, body);
    }

    /**
     * 转发请求到 Agent 服务并透传响应（快接口：读超时 15s，用于异步任务的提交 / 查询 / 取消）
     */
    private BaseResponse<Map<String, Object>> forwardFast(String path, Map<String, Object> body) {
        return forward(fastRestTemplate, path, body);
    }

    /**
     * 转发请求到 Agent 服务并透传响应
     *
     * @param template 使用哪个超时策略的 RestTemplate
     * @param path     Agent 侧路径
     * @param body     请求体
     */
    private BaseResponse<Map<String, Object>> forward(RestTemplate template, String path, Map<String, Object> body) {
        if (agentBaseUrl == null || agentBaseUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 服务未配置（agent.base-url）");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> entity = template.postForEntity(agentBaseUrl + path, request, String.class);
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
        } catch (HttpStatusCodeException e) {
            // Agent 侧的业务错误（400 / 404 / 409）本身带可读原因，透传出去比统一成
            // "Agent 服务调用失败"有用得多——否则用户和运维都得翻日志才知道发生了什么。
            // 例：知识库答不了这个候选人（409 INSUFFICIENT_EVIDENCE）以前到了前端只剩
            // "Agent 服务调用失败，请稍后重试"，看起来像服务挂了，其实是个有效结论。
            String agentMessage = extractAgentMessage(e.getResponseBodyAsString());
            log.warn("Agent 返回错误（{}）：status={}, body={}", path, e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(toErrorCode(e.getRawStatusCode()), agentMessage);
        } catch (Exception e) {
            log.error("转发 Agent 失败：{}", path, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Agent 服务调用失败，请稍后重试");
        }
    }

    /**
     * 把 Agent 的 HTTP 状态码映射成 picture 的错误码，让前端既能展示原因也能按码分支
     */
    private ErrorCode toErrorCode(int rawStatusCode) {
        if (rawStatusCode == 404) {
            return ErrorCode.NOT_FOUND_ERROR;
        }
        if (rawStatusCode == 400) {
            return ErrorCode.PARAMS_ERROR;
        }
        return ErrorCode.OPERATION_ERROR;
    }

    /**
     * 从 Agent 的错误响应体里取出 message（取不到就退回一句通用提示）
     */
    private String extractAgentMessage(String responseBody) {
        if (responseBody == null || !responseBody.trim().startsWith("{")) {
            return "Agent 服务调用失败，请稍后重试";
        }
        try {
            Map<String, Object> errorBody =
                    objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            Object message = errorBody.get("message");
            return message == null ? "Agent 服务调用失败，请稍后重试" : String.valueOf(message);
        } catch (Exception ignored) {
            return "Agent 服务调用失败，请稍后重试";
        }
    }
}
