package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.ResumeService;
import com.yupi.yupicturebackend.service.UserService;
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
     * 上传简历：解析 PDF 提取文本，并转发给面试 Agent
     *
     * @param file    简历 PDF 文件
     * @param request 请求对象（用于获取当前登录用户）
     * @return 简历文本
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadResume(@RequestPart("file") MultipartFile file,
                                             HttpServletRequest request) {
        // 登录校验（未登录会抛出 NOT_LOGIN_ERROR）
//        User loginUser = userService.getLoginUser(request);
        String resumeText = resumeService.parseResume(file, 1L*23123132);
        System.out.println(resumeText);
        return ResultUtils.success(resumeText);
    }
}
