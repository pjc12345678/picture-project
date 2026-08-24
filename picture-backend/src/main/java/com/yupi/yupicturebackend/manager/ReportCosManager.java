package com.yupi.yupicturebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.yupi.yupicturebackend.config.ReportCosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

/**
 * 面试报告专用 COS 管理器（使用独立的存储桶，不影响原有 CosManager）
 */
@Component
public class ReportCosManager {

    @Resource
    private ReportCosClientConfig reportCosClientConfig;

    @Resource
    private COSClient reportCosClient;

    /**
     * 上传面试报告对象
     *
     * @param key  唯一键（建议纯英文路径）
     * @param file 文件
     * @return 公网访问 URL
     */
    public String putReportObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(reportCosClientConfig.getBucket(), key, file);
        PutObjectResult result = reportCosClient.putObject(putObjectRequest);
        return reportCosClientConfig.getHost() + "/" + key;
    }

    /**
     * 删除面试报告对象
     *
     * @param key 唯一键
     */
    public void deleteReportObject(String key) {
        reportCosClient.deleteObject(reportCosClientConfig.getBucket(), key);
    }
}
