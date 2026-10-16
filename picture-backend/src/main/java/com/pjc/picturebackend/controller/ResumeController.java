package com.pjc.picturebackend.controller;

import com.pjc.picturebackend.common.BaseResponse;
import com.pjc.picturebackend.common.ResultUtils;
import com.pjc.picturebackend.model.entity.User;
import com.pjc.picturebackend.service.ResumeService;
import com.pjc.picturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 简历接口
 */
@Slf4j
@RestController
@RequestMapping("/resume")
public class ResumeController {

    @Resource
    private ResumeService resumeService;

    @Resource
    private UserService userService;

    /**
     * 上传简历：解析 PDF 提取简历文本
     *
     * @param file    简历 PDF 文件
     * @param request 请求对象（用于获取当前登录用户）
     * @return 简历文本
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadResume(@RequestPart("file") MultipartFile file,
                                             HttpServletRequest request) {
        // 登录校验（未登录会抛出 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);
        String resumeText = resumeService.parseResume(file, loginUser.getId());
        log.info("简历解析完成：userId={}, 文本长度={}", loginUser.getId(), resumeText.length());
        return ResultUtils.success(resumeText);
    }
}
