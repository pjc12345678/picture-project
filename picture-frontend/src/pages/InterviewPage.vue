<template>
  <div class="interview-page">
    <a-card title="AI 模拟面试" :bordered="false">
      <a-steps :current="step" class="steps">
        <a-step title="上传简历" />
        <a-step title="候选人画像" />
        <a-step title="面试对话" />
        <a-step title="面试报告" />
      </a-steps>

      <!-- 步骤 1：上传简历 -->
      <div v-if="step === 0" class="step-content">
        <a-spin :spinning="uploading" tip="正在解析简历，请稍候…">
          <a-upload-dragger
            :before-upload="handleUpload"
            :show-upload-list="false"
            accept=".pdf"
            :disabled="uploading"
          >
            <p class="ant-upload-drag-icon">
              <cloud-upload-outlined />
            </p>
            <p class="ant-upload-text">点击或拖拽简历 PDF 到此处上传</p>
            <p class="ant-upload-hint">上传后自动解析简历文本，用于生成候选人画像</p>
          </a-upload-dragger>
        </a-spin>
        <a-textarea
          v-if="resumeText"
          v-model:value="resumeText"
          :rows="8"
          placeholder="简历文本（可编辑）"
          class="resume-text"
        />
        <!-- 画像分析是异步任务：展示"真实阶段 + 已等待时长"，而不是一个转圈让用户猜是不是卡死了 -->
        <a-alert v-if="analyzing" type="info" show-icon class="analyze-panel">
          <template #message>正在生成候选人画像…</template>
          <template #description>
            <div class="analyze-line">
              <span>{{ analyzeStage }}</span>
              <span class="analyze-elapsed">已等待 {{ analyzeElapsed }} 秒</span>
            </div>
            <div v-if="analyzeElapsed >= ANALYZE_SLOW_TIP_SECONDS" class="analyze-hint">
              大模型生成画像通常需要 1～3 分钟。分析在服务端进行，刷新或关闭页面都不会中断，
              重新进入会自动接着等。
            </div>
            <a-button
              size="small"
              danger
              :loading="cancelling"
              class="analyze-action"
              @click="handleCancelAnalyze"
            >
              取消分析
            </a-button>
          </template>
        </a-alert>

        <a-alert
          v-else-if="analyzeError"
          type="error"
          show-icon
          class="analyze-panel"
          :message="analyzeError"
        >
          <template #description>
            <a-button size="small" type="primary" @click="handleAnalyze">重新分析</a-button>
          </template>
        </a-alert>

        <div class="action-bar">
          <a-button
            type="primary"
            :disabled="!resumeText || uploading || analyzing"
            @click="handleAnalyze"
          >
            分析简历，生成画像
          </a-button>
        </div>
      </div>

      <!-- 步骤 2：候选人画像 -->
      <div v-if="step === 1" class="step-content">
        <a-descriptions v-if="profile" bordered :column="1" size="small">
          <a-descriptions-item label="姓名">{{ profile.name }}</a-descriptions-item>
          <a-descriptions-item label="教育背景">{{ profile.education }}</a-descriptions-item>
          <a-descriptions-item label="专业">{{ profile.major }}</a-descriptions-item>
          <a-descriptions-item label="技术栈">
            <a-tag v-for="s in profile.skills || []" :key="s" color="blue">{{ s }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="项目经历">
            <div v-for="(p, i) in profile.projects || []" :key="i" class="project-item">
              <b>{{ p.name }}</b>
              <div>{{ p.highlight }}</div>
            </div>
          </a-descriptions-item>
          <a-descriptions-item label="可考察知识点">
            <a-tag v-for="t in profile.potentialTopics || []" :key="t" color="green">{{ t }}</a-tag>
          </a-descriptions-item>
        </a-descriptions>
        <div class="action-bar">
          <a-button @click="step = 0">重新上传</a-button>
          <span class="plan-config">
            题目数量
            <a-input-number v-model:value="questionCount" :min="1" :max="10" />
            难度
            <a-select v-model:value="difficulty" style="width: 100px">
              <a-select-option value="简单">简单</a-select-option>
              <a-select-option value="中等">中等</a-select-option>
              <a-select-option value="困难">困难</a-select-option>
            </a-select>
          </span>
          <a-button type="primary" :loading="starting" @click="handleStart">开始面试</a-button>
        </div>
      </div>

      <!-- 步骤 3：面试对话 -->
      <div v-if="step === 2" class="step-content chat-area">
        <div class="chat-meta">
          <a-tag :color="stateColor">{{ stateText }}</a-tag>
          <span v-if="totalQuestions" class="chat-progress">
            第 {{ questionIndex }} / {{ totalQuestions }} 题
          </span>
          <span v-if="partial" class="chat-partial-tip">部分完成</span>
          <a-button
            v-if="interviewState !== 'FINISHED'"
            type="link"
            size="small"
            danger
            :loading="aborting"
            @click="handleAbort"
          >
            结束面试
          </a-button>
        </div>
        <div ref="messageBox" class="chat-messages">
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <div class="msg-label">{{ roleLabel(msg.role) }}</div>
            <div class="msg-content">{{ msg.content }}</div>
          </div>
          <div v-if="pollingTip" class="polling-tip">{{ pollingTip }}</div>
        </div>
        <div class="chat-input">
          <a-textarea
            v-model:value="userAnswer"
            placeholder="输入你的回答，Enter 发送，Shift+Enter 换行"
            :rows="4"
            :disabled="sending || interviewState === 'FINISHED'"
            @keydown="handleKeydown"
          />
          <a-button
            type="primary"
            :loading="sending"
            :disabled="!userAnswer || interviewState === 'FINISHED'"
            @click="handleSend"
          >
            发送
          </a-button>
        </div>
        <div class="action-bar" v-if="interviewState === 'FINISHED'">
          <a-button type="primary" :loading="summarizing" @click="handleSummary">生成面试报告</a-button>
          <span v-if="partial" class="partial-tip">
            面试提前结束，报告仅基于已作答的题目
          </span>
        </div>
      </div>

      <!-- 步骤 4：面试报告 -->
      <div v-if="step === 3" class="step-content">
        <template v-if="summary">
          <a-alert
            v-if="partial"
            type="warning"
            show-icon
            class="partial-alert"
            message="本场面试为部分完成"
            :description="
              skippedTopics.length
                ? '以下题目因超时未作答被跳过（记 0 分）：' + skippedTopics.join('、')
                : '面试在全部题目完成前结束，评分仅基于候选人已作答的题目。'
            "
          />
          <div class="total-score">总分：{{ summary.totalScore }} / 100</div>
          <div v-if="pdfPath" class="pdf-tip">
            📄 面试报告已生成：
            <a :href="pdfPath" target="_blank">点击下载 PDF 报告</a>
          </div>
          <a-table
            :data-source="scoreRows"
            :columns="scoreColumns"
            size="small"
            :pagination="false"
            class="score-table"
          />
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="优势">
              <ul><li v-for="s in summary.strengths || []" :key="s">{{ s }}</li></ul>
            </a-descriptions-item>
            <a-descriptions-item label="薄弱点">
              <ul><li v-for="w in summary.weaknesses || []" :key="w">{{ w }}</li></ul>
            </a-descriptions-item>
            <a-descriptions-item label="学习建议">
              <ul><li v-for="s in summary.suggestions || []" :key="s">{{ s }}</li></ul>
            </a-descriptions-item>
          </a-descriptions>
          <div class="action-bar">
            <a-button @click="handleRestart">再测一次</a-button>
          </div>
        </template>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import {
  uploadResumeUsingPost,
  submitResumeAnalysisUsingPost,
  fetchResumeAnalysisUsingPost,
  cancelResumeAnalysisUsingPost,
  startInterviewUsingPost,
  chatUsingPost,
  summaryUsingPost,
  resumeInterviewUsingPost,
  abortInterviewUsingPost,
} from '@/api/interviewController'

/** 对话消息：ai=面试官，user=我，system=服务端通知（超时提醒 / 跳题说明） */
type ChatMessage = { role: 'ai' | 'user' | 'system'; content: string }

/** 候选人画像（渲染用；字段与 Agent 侧 CandidateProfile 对齐） */
type CandidateProfileView = {
  name?: string
  education?: string
  major?: string
  skills?: string[]
  projects?: { name?: string; highlight?: string }[]
  potentialTopics?: string[]
}

/** 服务端返回的画像分析任务快照（字段名即前后端契约） */
type AnalyzeTaskSnapshot = {
  taskId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
  /** 面向用户的中文阶段描述 */
  stage?: string
  elapsedMs?: number
  /** 仅 SUCCEEDED 时存在 */
  result?: CandidateProfileView
  /** 仅 FAILED 时存在 */
  error?: string
}

/** 会话 id 落盘用（刷新页面后能回到面试现场） */
const INTERVIEW_ID_KEY = 'interview:currentId'
/** 轮询间隔：及时发现超时提醒 / 跳题通知 */
const POLL_INTERVAL_MS = 20000

/** 画像分析任务 id 落盘用（刷新页面后接着等结果，不用重新分析、不重复花一次大模型调用） */
const ANALYZE_TASK_KEY = 'interview:analyzeTaskId'
/** 简历文本落盘用（刷新后还能直接重试，不必重新上传 PDF；只存在本机 sessionStorage） */
const ANALYZE_TEXT_KEY = 'interview:resumeText'
/** 画像分析轮询间隔：提交与查询都是毫秒级接口，2s 足够及时又不至于刷爆服务端 */
const ANALYZE_POLL_MS = 2000
/** 超过这个秒数才提示"大模型确实慢"，避免刚提交就吓唬用户 */
const ANALYZE_SLOW_TIP_SECONDS = 60

const step = ref(0)
const resumeText = ref('')
const uploading = ref(false)
const analyzing = ref(false)
/** 当前画像分析任务 id（空 = 没有在跑的任务） */
const analyzeTaskId = ref('')
/** 服务端给的中文阶段描述，直接展示，前端不猜 */
const analyzeStage = ref('')
/** 已等待秒数：由服务端 elapsedMs 校正 + 本地秒表平滑 */
const analyzeElapsed = ref(0)
const analyzeError = ref('')
const cancelling = ref(false)
const profile = ref<CandidateProfileView | null>(null)
const questionCount = ref(3)
const difficulty = ref('中等')
const starting = ref(false)
const interviewId = ref('')
const messages = ref<ChatMessage[]>([])
const userAnswer = ref('')
const sending = ref(false)
const interviewState = ref('')
const summarizing = ref(false)
const summary = ref<any>(null)
const pdfPath = ref('')
const totalQuestions = ref(0)
const questionIndex = ref(0)
const partial = ref(false)
const skippedTopics = ref<string[]>([])
const aborting = ref(false)
const pollingTip = ref('')
const messageBox = ref<HTMLElement | null>(null)

/** 轮询定时器（组件卸载 / 面试结束时必须清掉） */
let pollTimer: number | null = null
/** 防止上一次轮询还没回来又发一次 */
let pollInFlight = false

/** 画像分析轮询定时器 + 秒表（卸载时必须清掉，否则会重复轮询、泄漏定时器） */
let analyzePollTimer: number | null = null
let analyzeTickTimer: number | null = null
/** 防止上一次状态查询还没回来又发一次 */
let analyzeInFlight = false

const stateText = computed(() => {
  switch (interviewState.value) {
    case 'FINISHED':
      return '面试已结束'
    case 'FOLLOW_UP':
      return '面试官追问中'
    case 'ASKING':
      return '面试进行中'
    default:
      return '未知状态'
  }
})

const stateColor = computed(() => {
  switch (interviewState.value) {
    case 'FINISHED':
      return 'default'
    case 'FOLLOW_UP':
      return 'orange'
    default:
      return 'green'
  }
})

function roleLabel(role: string) {
  if (role === 'ai') return '🤖 面试官'
  if (role === 'user') return '🧑 我'
  return '🔔 系统通知'
}

/** 追加消息并滚动到底部 */
async function appendMessage(role: ChatMessage['role'], content: string) {
  if (!content) return
  messages.value.push({ role, content })
  await nextTick()
  if (messageBox.value) {
    messageBox.value.scrollTop = messageBox.value.scrollHeight
  }
}

/** 把服务端通知渲染成系统消息（结构化的 notifications 优先，message 里的【系统提示】段不重复展示） */
function appendNotifications(notifications?: string[] | null) {
  if (!notifications || notifications.length === 0) return
  for (const notice of notifications) {
    messages.value.push({ role: 'system', content: notice })
  }
  void nextTick().then(() => {
    if (messageBox.value) {
      messageBox.value.scrollTop = messageBox.value.scrollHeight
    }
  })
}

/** 剥掉 message 开头的【系统提示】段，避免与 notifications 重复显示 */
function stripInlineNotices(text: string) {
  if (!text) return ''
  const marker = '【系统提示】'
  if (!text.startsWith(marker)) return text
  const splitAt = text.indexOf('\n\n')
  return splitAt >= 0 ? text.slice(splitAt + 2).trim() : text.replace(marker, '').trim()
}

/**
 * 统一处理会话快照（/resume 与 /abort 的返回）
 * 负责推进状态、渲染通知、维护轮询。
 */
function applySnapshot(data: any) {
  if (!data || !data.state) {
    throw new Error('会话不存在或已过期')
  }
  interviewState.value = data.state
  totalQuestions.value = data.totalQuestions || 0
  questionIndex.value = data.questionIndex || 0
  partial.value = !!data.partial
  appendNotifications(data.notifications)

  if (data.state === 'FINISHED') {
    stopPolling()
  } else {
    startPolling()
  }
}

/** 恢复到面试现场：进度卡作为系统消息保留，锚点题干作为当前题目展示 */
async function restoreSession(id: string) {
  pollingTip.value = '正在恢复面试现场…'
  try {
    const res = await resumeInterviewUsingPost({ interviewId: id })
    const data = res.data?.code === 0 ? res.data.data : null
    if (!data || !data.state) {
      // 会话确实不在了（服务端重启会话在内存里，重启即丢失）：清掉本地残留
      clearStoredSession()
      message.warning('上一次面试已过期，请重新开始')
      return
    }
    interviewId.value = id
    messages.value = []
    if (data.progressCard?.length) {
      await appendMessage('system', '已完成的题目：\n' + data.progressCard.join('\n'))
    }
    if (data.resumeMessage) {
      await appendMessage('system', data.resumeMessage)
    }
    applySnapshot(data)
    step.value = 2
    message.info('已恢复到上次的面试现场')
  } catch (e: any) {
    if (e?.response) {
      // 服务端明确返回错误：会话不可用
      clearStoredSession()
      message.warning(e?.message || '恢复面试失败，请重新开始')
    } else {
      // 网络异常：保留会话 id，刷新页面可以再试一次
      message.warning('恢复面试现场失败（网络异常），可刷新页面重试')
    }
  } finally {
    pollingTip.value = ''
  }
}

/** 轮询：拿服务端的超时提醒 / 跳题通知 / 结束状态 */
async function pollOnce() {
  if (pollInFlight || sending.value || !interviewId.value || step.value !== 2) return
  if (interviewState.value === 'FINISHED') {
    stopPolling()
    return
  }
  pollInFlight = true
  try {
    const res = await resumeInterviewUsingPost({ interviewId: interviewId.value })
    const data = res.data?.code === 0 ? res.data.data : null
    if (!data || !data.state) {
      // 拿不到状态就先停轮询（不删本地会话 id，避免把还能用的会话误清掉）
      stopPolling()
      message.warning('暂时无法获取面试状态，可刷新页面重试')
      return
    }
    applySnapshot(data)
    if (data.state === 'FINISHED') {
      message.info('面试已结束，可生成面试报告')
    }
  } catch {
    // 轮询失败不打扰用户，等下一轮
  } finally {
    pollInFlight = false
  }
}

function startPolling() {
  if (pollTimer !== null) return
  if (typeof window === 'undefined') return
  pollTimer = window.setInterval(pollOnce, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function storeSession(id: string) {
  try {
    sessionStorage.setItem(INTERVIEW_ID_KEY, id)
  } catch {
    // 隐私模式下 storage 可能不可用，忽略
  }
}

function clearStoredSession() {
  try {
    sessionStorage.removeItem(INTERVIEW_ID_KEY)
  } catch {
    // ignore
  }
}

// 上传简历
async function handleUpload(file: File) {
  uploading.value = true
  try {
    const res = await uploadResumeUsingPost(file)
    if (res.data.code === 0) {
      resumeText.value = res.data.data
      message.success('简历解析成功，可编辑后点击"分析简历"')
    } else {
      message.error(res.data.message || '上传失败')
    }
  } catch (e: any) {
    message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

// ==================== 简历画像分析（异步任务：提交 + 轮询取结果） ====================
//
// 之前这里是一次同步请求：前端等 60s，超时就断开，而后端还在把这次大模型调用跑完
// （实测同类调用 p50 约 37s、最长 73s，还要过 nginx 默认 60s 的读超时），
// 结果是"用户白等一场，画像也拿不到"。
// 现在前端只做两件秒级的事：提交任务、每 2 秒查一次进度；慢的部分留在服务端后台线程里。

/** 提交分析任务（拿到 taskId 后开始轮询） */
async function handleAnalyze() {
  if (!resumeText.value.trim()) {
    message.warning('简历内容为空，请先上传简历')
    return
  }
  stopAnalyzePolling()
  analyzeError.value = ''
  analyzeElapsed.value = 0
  analyzeStage.value = '正在提交分析任务…'
  analyzing.value = true
  try {
    const res = await submitResumeAnalysisUsingPost({ resumeText: resumeText.value })
    const data = res.data?.code === 0 ? res.data.data : null
    // 灰度兼容：若前端先上线、后端还是旧的同步接口，这里拿到的就是画像本身（没有 taskId / status）。
    // 直接用掉它，比让用户看到一句莫名其妙的 "ok" 或"提交失败"强。
    if (data && !data.taskId && !data.status) {
      profile.value = data
      analyzing.value = false
      step.value = 1
      message.success('候选人画像生成成功')
      return
    }
    if (!data?.taskId) {
      // 提交阶段就失败（未登录 / 参数错误 / Agent 不可用）：后端已经给出了可读原因
      analyzing.value = false
      analyzeError.value = res.data?.message || '提交分析任务失败，请稍后重试'
      return
    }
    analyzeTaskId.value = data.taskId
    storeAnalyzeTask(data.taskId, resumeText.value)
    // 提交响应里带回了初始状态：像"队列已满"这种立即失败在这里就能识别，不用等第一次轮询
    applyAnalyzeTask(data)
    if (analyzing.value) {
      startAnalyzePolling()
    }
  } catch (e: any) {
    analyzing.value = false
    analyzeError.value = e?.message || '提交分析任务失败，请稍后重试'
  }
}

/** 把服务端返回的任务快照映射成界面状态 */
function applyAnalyzeTask(task: AnalyzeTaskSnapshot) {
  if (!task) return
  if (task.stage) {
    analyzeStage.value = task.stage
  }
  if (typeof task.elapsedMs === 'number') {
    // 用服务端时间为准（本地秒表只负责两次轮询之间的平滑），取大值避免回跳
    analyzeElapsed.value = Math.max(analyzeElapsed.value, Math.round(task.elapsedMs / 1000))
  }
  switch (task.status) {
    case 'SUCCEEDED':
      profile.value = task.result ?? null
      finishAnalyze()
      step.value = 1
      message.success('候选人画像生成成功')
      break
    case 'FAILED':
      finishAnalyze()
      analyzeError.value = task.error || '画像分析失败，请重试'
      message.error(analyzeError.value)
      break
    case 'CANCELLED':
      finishAnalyze()
      analyzeError.value = '分析已取消'
      break
    default:
      // QUEUED / RUNNING：继续等
      analyzing.value = true
      break
  }
}

/** 收尾：停轮询、清任务 id、清掉落盘的 taskId（否则刷新后会去轮询一个已结束的任务） */
function finishAnalyze() {
  stopAnalyzePolling()
  analyzing.value = false
  analyzeTaskId.value = ''
  clearStoredAnalyzeTask()
}

/** 查一次任务状态 */
async function pollAnalyzeOnce() {
  if (analyzeInFlight || !analyzeTaskId.value) return
  analyzeInFlight = true
  try {
    const res = await fetchResumeAnalysisUsingPost({ taskId: analyzeTaskId.value })
    const data = res.data?.code === 0 ? res.data.data : null
    if (!data || !data.status) {
      // 后端明确报错（任务不存在 / 已过期）：别再空转轮询
      finishAnalyze()
      analyzeError.value = res.data?.message || '分析任务已失效，请重新分析'
      return
    }
    applyAnalyzeTask(data)
  } catch {
    // 单次轮询失败不当作分析失败：网络抖动很常见，保持等待，下一轮继续
    analyzeStage.value = '网络波动，正在重试获取分析进度…'
  } finally {
    analyzeInFlight = false
  }
}

function startAnalyzePolling() {
  if (typeof window === 'undefined') return
  if (analyzePollTimer === null) {
    analyzePollTimer = window.setInterval(pollAnalyzeOnce, ANALYZE_POLL_MS)
  }
  if (analyzeTickTimer === null) {
    // 秒表：让用户看到时间确实在走。每 2s 会被服务端的 elapsedMs 校正一次，不会漂
    analyzeTickTimer = window.setInterval(() => {
      if (analyzing.value) {
        analyzeElapsed.value += 1
      }
    }, 1000)
  }
}

function stopAnalyzePolling() {
  if (analyzePollTimer !== null) {
    window.clearInterval(analyzePollTimer)
    analyzePollTimer = null
  }
  if (analyzeTickTimer !== null) {
    window.clearInterval(analyzeTickTimer)
    analyzeTickTimer = null
  }
}

/**
 * 取消分析。
 *
 * 服务端已经发出的上游调用无法真正中断（取消的语义是"结果不要了"），
 * 所以这里不等服务端返回也立刻退出等待 —— 否则"取消"按钮本身又变成一次等待。
 */
async function handleCancelAnalyze() {
  const taskId = analyzeTaskId.value
  cancelling.value = true
  try {
    if (taskId) {
      await cancelResumeAnalysisUsingPost({ taskId })
    }
  } catch {
    // 取消失败不影响本地退出等待：任务结果最终会被服务端的定期清理回收
  } finally {
    cancelling.value = false
    finishAnalyze()
    analyzeStage.value = ''
    analyzeError.value = ''
    message.info('已取消分析，可重新点击"分析简历，生成画像"')
  }
}

/** 刷新页面后接着等：任务还在服务端跑，taskId 存在 sessionStorage 里 */
function resumeAnalyzeTask(taskId: string) {
  analyzeTaskId.value = taskId
  analyzing.value = true
  analyzeError.value = ''
  analyzeStage.value = '正在恢复分析进度…'
  startAnalyzePolling()
  void pollAnalyzeOnce()
}

function storeAnalyzeTask(taskId: string, text: string) {
  try {
    sessionStorage.setItem(ANALYZE_TASK_KEY, taskId)
    sessionStorage.setItem(ANALYZE_TEXT_KEY, text)
  } catch {
    // 隐私模式下 storage 可能不可用，忽略
  }
}

function clearStoredAnalyzeTask() {
  try {
    sessionStorage.removeItem(ANALYZE_TASK_KEY)
  } catch {
    // ignore
  }
}

function readStoredAnalyzeTask() {
  try {
    return sessionStorage.getItem(ANALYZE_TASK_KEY) || ''
  } catch {
    return ''
  }
}

function clearStoredResumeText() {
  try {
    sessionStorage.removeItem(ANALYZE_TEXT_KEY)
  } catch {
    // ignore
  }
}

// 开始面试
async function handleStart() {
  starting.value = true
  try {
    const res = await startInterviewUsingPost({
      profile: profile.value,
      questionCount: questionCount.value,
      difficulty: difficulty.value,
    })
    if (res.data.code === 0) {
      const data = res.data.data
      interviewId.value = data.interviewId
      storeSession(data.interviewId)
      totalQuestions.value = data.totalQuestions
      questionIndex.value = 1
      partial.value = false
      skippedTopics.value = []
      summary.value = null
      pdfPath.value = ''
      interviewState.value = 'ASKING'
      messages.value = [
        {
          role: 'ai',
          content:
            '面试开始！共 ' +
            data.totalQuestions +
            ' 道题。\n\n第一题（1/' +
            data.totalQuestions +
            '）：\n' +
            data.firstQuestion.question,
        },
      ]
      step.value = 2
      startPolling()
    } else {
      message.error(res.data.message || '开始面试失败')
    }
  } catch (e: any) {
    message.error(e?.message || '开始面试失败')
  } finally {
    starting.value = false
  }
}

// 回车发送，Shift+回车换行
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// 发送回答
async function handleSend() {
  if (!userAnswer.value || sending.value) return
  if (interviewState.value === 'FINISHED') {
    message.warning('面试已结束，请生成面试报告')
    return
  }
  const answer = userAnswer.value
  userAnswer.value = ''
  await appendMessage('user', answer)
  sending.value = true
  try {
    const res = await chatUsingPost({ interviewId: interviewId.value, answer })
    const data = res.data?.code === 0 ? res.data.data : null
    if (!data || !data.state) {
      // 服务端会话丢失（例如进程重启）：本地状态同步失效
      stopPolling()
      clearStoredSession()
      message.error(res.data?.message || '面试会话已失效，请重新开始')
      await appendMessage('system', '会话已失效，请点击"再测一次"重新开始面试。')
      return
    }
    partial.value = !!data.partial
    interviewState.value = data.state
    if (typeof data.questionIndex === 'number') {
      questionIndex.value = data.questionIndex
    }
    if (typeof data.totalQuestions === 'number' && data.totalQuestions > 0) {
      totalQuestions.value = data.totalQuestions
    }
    // 先渲染结构化通知，再渲染面试官回复（并把 message 里内联的【系统提示】段剥掉，避免重复）
    appendNotifications(data.notifications)
    await appendMessage('ai', stripInlineNotices(data.message))
    if (data.state === 'FINISHED') {
      stopPolling()
      message.success('面试结束！点击下方按钮生成面试报告')
    } else {
      startPolling()
    }
  } catch (e: any) {
    message.error(e?.message || '发送失败')
    await appendMessage('system', '发送失败，请重试')
  } finally {
    sending.value = false
  }
}

// 主动结束面试（服务端会标记为"部分完成"，仍可生成报告）
function handleAbort() {
  Modal.confirm({
    title: '确认结束本次面试？',
    content: '结束后将以已作答的题目生成部分完成的报告，剩余题目按未作答处理。',
    okText: '结束面试',
    cancelText: '继续作答',
    okButtonProps: { danger: true },
    onOk: async () => {
      aborting.value = true
      try {
        const res = await abortInterviewUsingPost({ interviewId: interviewId.value })
        const data = res.data?.code === 0 ? res.data.data : null
        if (!data || !data.state) {
          message.error(res.data?.message || '结束面试失败')
          return
        }
        applySnapshot(data)
        await appendMessage('ai', '面试已结束，感谢参与！')
        message.success('面试已结束，可生成面试报告')
      } catch (e: any) {
        message.error(e?.message || '结束面试失败')
      } finally {
        aborting.value = false
      }
    },
  })
}

// 生成面试报告
async function handleSummary() {
  summarizing.value = true
  try {
    const res = await summaryUsingPost({ interviewId: interviewId.value })
    if (res.data.code === 0) {
      const data = res.data.data || {}
      summary.value = data.summary
      pdfPath.value = data.pdfPath || ''
      partial.value = !!data.partial
      skippedTopics.value = data.timeoutSkippedTopics || []
      step.value = 3
      stopPolling()
    } else {
      message.error(res.data.message || '生成报告失败')
    }
  } catch (e: any) {
    message.error(e?.message || '生成报告失败')
  } finally {
    summarizing.value = false
  }
}

// 再测一次：清掉本地会话与落盘的简历文本，回到第一步
function handleRestart() {
  stopPolling()
  stopAnalyzePolling()
  clearStoredSession()
  clearStoredAnalyzeTask()
  clearStoredResumeText()
  analyzeError.value = ''
  analyzeElapsed.value = 0
  analyzeStage.value = ''
  analyzing.value = false
  interviewId.value = ''
  messages.value = []
  interviewState.value = ''
  questionIndex.value = 0
  totalQuestions.value = 0
  partial.value = false
  skippedTopics.value = []
  summary.value = null
  pdfPath.value = ''
  step.value = 0
}

// 进入页面时：优先恢复未结束的面试；没有面试时，看有没有正在跑的画像分析任务
onMounted(async () => {
  // 简历文本先恢复：上次的输入还在，重试时不用重新上传 PDF
  try {
    const savedText = sessionStorage.getItem(ANALYZE_TEXT_KEY)
    if (savedText) {
      resumeText.value = savedText
    }
  } catch {
    // 隐私模式下 storage 可能不可用，忽略
  }

  let storedId = ''
  try {
    storedId = sessionStorage.getItem(INTERVIEW_ID_KEY) || ''
  } catch {
    storedId = ''
  }
  if (storedId) {
    await restoreSession(storedId)
    return
  }

  // 没有进行中的面试，但可能有一个还在服务端跑的画像分析：接着等，而不是假装什么都没发生
  const pendingTaskId = readStoredAnalyzeTask()
  if (pendingTaskId) {
    resumeAnalyzeTask(pendingTaskId)
  }
})

// 卸载时停掉两个轮询；sessionStorage 里的 taskId 保留，刷新后能接着等结果
onUnmounted(() => {
  stopPolling()
  stopAnalyzePolling()
})

const scoreColumns = [
  { title: '考察主题', dataIndex: 'topic', key: 'topic' },
  { title: '题目', dataIndex: 'question', key: 'question' },
  { title: '得分', dataIndex: 'score', key: 'score', width: 70 },
  { title: '点评', dataIndex: 'feedback', key: 'feedback' },
]

const scoreRows = computed(() =>
  (summary.value?.scores || []).map((s: any, i: number) => ({ key: i, ...s }))
)
</script>

<style scoped>
.steps {
  margin-bottom: 24px;
}
.step-content {
  min-height: 300px;
}
.resume-text {
  margin-top: 16px;
}
/* 画像分析进度面板：让"正在等"这件事有明确的时间感，而不是一个转圈 */
.analyze-panel {
  margin-top: 16px;
}
.analyze-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.analyze-elapsed {
  color: #1677ff;
  font-variant-numeric: tabular-nums;
}
.analyze-hint {
  margin-top: 4px;
  color: #874d00;
  font-size: 12px;
  line-height: 1.6;
}
.analyze-action {
  margin-top: 8px;
}
.action-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.plan-config {
  margin-left: 8px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.project-item {
  margin-bottom: 8px;
}
.chat-area {
  display: flex;
  flex-direction: column;
  height: 480px;
}
.chat-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}
.chat-progress {
  font-weight: 500;
}
.chat-partial-tip {
  color: #d46b08;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
}
.msg {
  margin-bottom: 12px;
}
.msg-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.msg-content {
  padding: 8px 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  background: #fff;
  border: 1px solid #f0f0f0;
  max-width: 90%;
}
.msg.user .msg-content {
  background: #e6f4ff;
  border-color: #91caff;
  margin-left: auto;
}
/* 系统通知（超时提醒 / 跳题说明）：居中、弱化，与对话区分开 */
.msg.system .msg-label {
  text-align: center;
}
.msg.system .msg-content {
  margin: 0 auto;
  max-width: 100%;
  background: #fffbe6;
  border-color: #ffe58f;
  color: #874d00;
  font-size: 13px;
  text-align: center;
}
.polling-tip {
  text-align: center;
  font-size: 12px;
  color: #999;
}
.chat-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.chat-input .ant-btn {
  flex-shrink: 0;
}
.partial-tip {
  font-size: 13px;
  color: #d46b08;
}
.partial-alert {
  margin-bottom: 16px;
}
.total-score {
  font-size: 24px;
  font-weight: bold;
  text-align: center;
  margin-bottom: 16px;
  color: #1677ff;
}
.score-table {
  margin-bottom: 16px;
}
.pdf-tip {
  text-align: center;
  margin-bottom: 16px;
  color: #52c41a;
  word-break: break-all;
}
</style>
