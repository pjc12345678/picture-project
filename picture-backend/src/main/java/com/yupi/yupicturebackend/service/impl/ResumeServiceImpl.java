package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.pdf.PdfParser;
import com.yupi.yupicturebackend.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 简历服务实现
 */
@Slf4j
@Service
public class ResumeServiceImpl implements ResumeService {

    @Resource
    private PdfParser pdfParser;

    /**
     * 面试 Agent 服务地址（未配置则不转发）
     */
    @Value("${agent.base-url:}")
    private String agentBaseUrl;

    @Override
    public String parseResume(MultipartFile file, Long userId) {
        // 1. 解析 PDF，提取简历文本
        String resumeText = pdfParser.parseToText(file);
        // 2. 转发给面试 Agent（尽力而为，失败不影响主流程）
        forwardToAgent(userId, resumeText);
        return resumeText;
    }

    /**
     * 将简历文本转发给面试 Agent 服务
     *
     * @param userId     用户 id
     * @param resumeText 简历文本
     */
    private void forwardToAgent(Long userId, String resumeText) {
        if (StrUtil.isBlank(agentBaseUrl)) {
            log.warn("未配置 agent.base-url，跳过简历转发");
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("resumeText", resumeText);
            String response = HttpUtil.createPost(agentBaseUrl + "/resume/receive")
                    .body(JSONUtil.toJsonStr(body))
                    .execute()
                    .body();
            log.info("简历已转发给面试 Agent，响应：{}", response);
        } catch (Exception e) {
            // 转发失败不影响简历解析主流程，记录日志便于排查
            log.error("简历转发面试 Agent 失败", e);
        }
    }
}
