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
        <a-upload-dragger :before-upload="handleUpload" :show-upload-list="false" accept=".pdf">
          <p class="ant-upload-drag-icon">
            <cloud-upload-outlined />
          </p>
          <p class="ant-upload-text">点击或拖拽简历 PDF 到此处上传</p>
          <p class="ant-upload-hint">上传后自动解析简历文本，用于生成候选人画像</p>
        </a-upload-dragger>
        <a-textarea
          v-if="resumeText"
          v-model:value="resumeText"
          :rows="8"
          placeholder="简历文本（可编辑）"
          class="resume-text"
        />
        <div class="action-bar">
          <a-button type="primary" :loading="analyzing" :disabled="!resumeText" @click="handleAnalyze">
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
        <div class="chat-messages">
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <div class="msg-label">{{ msg.role === 'ai' ? '🤖 面试官' : '🧑 我' }}</div>
            <div class="msg-content">{{ msg.content }}</div>
          </div>
        </div>
        <div class="chat-input">
          <a-input
            v-model:value="userAnswer"
            placeholder="输入你的回答，回车发送..."
            @pressEnter="handleSend"
            :disabled="sending || interviewState === 'FINISHED'"
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
        </div>
      </div>

      <!-- 步骤 4：面试报告 -->
      <div v-if="step === 3" class="step-content">
        <template v-if="summary">
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
            <a-button @click="step = 0">再测一次</a-button>
          </div>
        </template>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import {
  uploadResumeUsingPost,
  analyzeResumeUsingPost,
  startInterviewUsingPost,
  chatUsingPost,
  summaryUsingPost,
} from '@/api/interviewController'

const step = ref(0)
const resumeText = ref('')
const analyzing = ref(false)
const profile = ref<any>(null)
const questionCount = ref(3)
const difficulty = ref('中等')
const starting = ref(false)
const interviewId = ref('')
const messages = ref<Array<{ role: string; content: string }>>([])
const userAnswer = ref('')
const sending = ref(false)
const interviewState = ref('')
const summarizing = ref(false)
const summary = ref<any>(null)
const pdfPath = ref('')

// 上传简历
async function handleUpload(file: File) {
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
  }
  return false
}

// 分析简历 → 画像
async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzeResumeUsingPost({ resumeText: resumeText.value })
    if (res.data.code === 0) {
      profile.value = res.data.data
      step.value = 1
      message.success('候选人画像生成成功')
    } else {
      message.error(res.data.message || '分析失败')
    }
  } catch (e: any) {
    message.error(e?.message || '分析失败，请稍后重试')
  } finally {
    analyzing.value = false
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
      totalQuestions.value = data.totalQuestions
      interviewState.value = 'ASKING'
      messages.value = [
        {
          role: 'ai',
          content: '面试开始！共 ' + data.totalQuestions + ' 道题。\n\n第一题（1/' + data.totalQuestions + '）：\n' + data.firstQuestion.question,
        },
      ]
      step.value = 2
    } else {
      message.error(res.data.message || '开始面试失败')
    }
  } catch (e: any) {
    message.error(e?.message || '开始面试失败')
  } finally {
    starting.value = false
  }
}

// 发送回答
async function handleSend() {
  if (!userAnswer.value) return
  const answer = userAnswer.value
  userAnswer.value = ''
  messages.value.push({ role: 'user', content: answer })
  sending.value = true
  try {
    const res = await chatUsingPost({ interviewId: interviewId.value, answer })
    if (res.data.code === 0) {
      const data = res.data.data
      interviewState.value = data.state
      messages.value.push({ role: 'ai', content: data.message })
      if (data.state === 'FINISHED') {
        message.success('面试结束！点击下方按钮生成面试报告')
      }
    } else {
      message.error(res.data.message || '发送失败')
      messages.value.push({ role: 'ai', content: '（发送失败，请重试）' })
    }
  } catch (e: any) {
    message.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

// 生成面试报告
async function handleSummary() {
  summarizing.value = true
  try {
    const res = await summaryUsingPost({ interviewId: interviewId.value })
    if (res.data.code === 0) {
      summary.value = res.data.data.summary
      pdfPath.value = res.data.data.pdfPath || ''
      step.value = 3
    } else {
      message.error(res.data.message || '生成报告失败')
    }
  } catch (e: any) {
    message.error(e?.message || '生成报告失败')
  } finally {
    summarizing.value = false
  }
}

const totalQuestions = ref(0)

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
.chat-input {
  display: flex;
  gap: 8px;
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
