// @ts-ignore
/* eslint-disable */
import request from '@/request'

/**
 * 面试相关 API（通过 picture 后端代理调用 Agent 服务）
 */

/** 上传简历 PDF */
export async function uploadResumeUsingPost(file: File, options?: { [key: string]: any }) {
  const formData = new FormData()
  formData.append('file', file)
  return request('/api/resume/upload', {
    method: 'POST',
    data: formData,
    timeout: 60000,
    ...(options || {}),
  })
}

/** 分析简历文本，生成候选人画像 */
export async function analyzeResumeUsingPost(
  body: { resumeText: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/analyze', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 60000,
    ...(options || {}),
  })
}

/** 开始面试，生成面试计划 */
export async function startInterviewUsingPost(body: any, options?: { [key: string]: any }) {
  return request('/api/interview/start', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 60000,
    ...(options || {}),
  })
}

/** 面试对话 */
export async function chatUsingPost(
  body: { interviewId: string; answer: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 60000,
    ...(options || {}),
  })
}

/** 生成面试总结报告 */
export async function summaryUsingPost(
  body: { interviewId: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/summary', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 60000,
    ...(options || {}),
  })
}
