import axios from "axios";
import {message} from "ant-design-vue";

// 区分开发和生产环境
// const DEV_BASE_URL = "http://localhost:8123";
const PROD_BASE_URL = "http://47.114.52.210/";
// 创建 Axios 实例
/**
 * ⚠ 这里的全局超时只适合"秒级"接口。
 *
 * 凡是背后有大模型调用的接口（生成画像、开始面试、生成报告），都不要指望调大这个值来救：
 * 请求链路上还有 nginx（默认 proxy_read_timeout 60s）和浏览器，任何一环到点都会断开，
 * 而后端并不知道，会继续把几十秒的调用跑完 —— 用户白等、算力白花。
 *
 * 正确做法是"提交任务 + 轮询取结果"（见 api/interviewController.ts 里的画像分析三件套）。
 */
const myAxios = axios.create({
    baseURL: PROD_BASE_URL,
    timeout: 10000,
    withCredentials: true,
});

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录
    if (data.code === 40100) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    // 超时/断网的原始文案（"timeout of 60000ms exceeded"）用户看不懂，
    // 这里换成能指导下一步动作的提示。只改 message，不弹提示：
    // 弹窗交给发起请求的地方，避免同一件事弹两次。
    if (error?.code === 'ECONNABORTED' || String(error?.message || '').includes('timeout')) {
      error.message = '请求超时：服务端处理时间过长，请稍后重试'
    } else if (error?.code === 'ERR_NETWORK') {
      error.message = '网络异常：无法连接服务端，请检查网络后重试'
    }
    return Promise.reject(error)
  },
)

export default myAxios;
