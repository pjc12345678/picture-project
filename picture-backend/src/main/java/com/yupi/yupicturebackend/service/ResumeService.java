package com.yupi.yupicturebackend.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务
 */
public interface ResumeService {

    /**
     * 解析简历 PDF，提取文本并转发给面试 Agent
     *
     * @param file   PDF 文件
     * @param userId 当前登录用户 id
     * @return 提取到的简历文本
     */
    String parseResume(MultipartFile file, Long userId);
}
