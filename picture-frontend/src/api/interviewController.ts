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

/**
 * 提交简历画像分析任务（异步，推荐）。
 *
 * 为什么不是"一次请求直接拿画像"：生成画像是一次大模型调用，实测同类调用
 * p50 约 37s、最长 73s，而浏览器与 nginx（默认 proxy_read_timeout 60s）都等不了这么久。
 * 同步接口的结局是"后端还在算、前端已经断开"，用户白等、算力白花。
 * 拆成提交 + 轮询之后，每个请求都是秒级的，慢的只在服务端后台线程里。
 *
 * @returns { taskId, status, stage, elapsedMs }
 */
export async function submitResumeAnalysisUsingPost(
  body: { resumeText: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/analyze', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    // 提交只做"建任务 + 丢线程池"，正常是毫秒级；给 20s 只是为了容忍网络抖动
    timeout: 20000,
    ...(options || {}),
  })
}

/**
 * 查询简历画像分析任务（轮询用，建议 2s 一次）。
 *
 * @returns { taskId, status: 'QUEUED'|'RUNNING'|'SUCCEEDED'|'FAILED'|'CANCELLED',
 *            stage, elapsedMs, result?, error? }
 */
export async function fetchResumeAnalysisUsingPost(
  body: { taskId: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/analyze/status', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    // 纯内存查任务状态，毫秒级
    timeout: 10000,
    ...(options || {}),
  })
}

/** 取消简历画像分析任务（已发出的上游调用无法中断，只是不再等它的结果） */
export async function cancelResumeAnalysisUsingPost(
  body: { taskId: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/analyze/cancel', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 10000,
    ...(options || {}),
  })
}

/**
 * 同步生成候选人画像（仅调试/脚本使用）。
 *
 * ⚠ 生产前端不要调用：它会被 nginx 的 proxy_read_timeout（默认 60s）掐断，
 * 而服务端还会继续把这次调用跑完。页面请用上面的 submit + fetch 组合。
 */
export async function analyzeResumeUsingPost(
  body: { resumeText: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/analyze/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 180000,
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

/**
 * 恢复面试现场 / 轮询面试状态
 *
 * 返回：{ interviewId, state, questionIndex, totalQuestions, currentQuestion, anchorQuestion,
 *        progressCard, answeredQuestions, notifications, partial, idleSeconds, resumeMessage }
 *
 * 注意：该接口不会刷新服务端的"活动时间"，所以用它做轮询不会破坏超时判定。
 */
export async function resumeInterviewUsingPost(
  body: { interviewId: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/resume', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 10000,
    ...(options || {}),
  })
}

/** 主动中止面试（服务端标记为"部分完成"，之后仍可生成部分报告） */
export async function abortInterviewUsingPost(
  body: { interviewId: string },
  options?: { [key: string]: any }
) {
  return request('/api/interview/abort', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    timeout: 10000,
    ...(options || {}),
  })
}
