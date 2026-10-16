package com.pjc.picturebackend.service.impl;

import com.pjc.picturebackend.manager.pdf.PdfParser;
import com.pjc.picturebackend.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * 简历服务实现
 */
@Slf4j
@Service
public class ResumeServiceImpl implements ResumeService {

    @Resource
    private PdfParser pdfParser;

    @Override
    public String parseResume(MultipartFile file, Long userId) {
        // 解析 PDF，提取简历文本（真正的 LLM 画像分析由“分析简历”接口单独触发，此处不再同步转发）
        return pdfParser.parseToText(file);
    }
}
