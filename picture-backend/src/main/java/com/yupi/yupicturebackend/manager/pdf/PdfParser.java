package com.yupi.yupicturebackend.manager.pdf;

import cn.hutool.core.util.StrUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF 解析器：负责从 PDF 文件中提取纯文本
 */
@Slf4j
@Component
public class PdfParser {

    /**
     * 从 PDF 文件中提取纯文本
     *
     * @param file PDF 文件
     * @return 提取到的文本
     */
    public String parseToText(MultipartFile file) {
        // 参数校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 文件不能为空");
        }
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            // 扫描件 / 图片型 PDF 没有文字层，提取结果为空
            if (StrUtil.isBlank(text)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "无法从 PDF 中提取文字，请上传文字版简历（非扫描件）");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 解析失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 解析失败，请确认文件格式正确");
        }
    }
}
