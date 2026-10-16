<template>
  <div class="edit-profile-page">
    <a-card title="编辑个人资料" :bordered="false">
      <a-form :model="form" layout="vertical">
        <a-form-item label="昵称">
          <a-input v-model:value="form.userName" placeholder="请输入昵称" allow-clear />
        </a-form-item>
        <a-form-item label="头像 URL">
          <a-input v-model:value="form.userAvatar" placeholder="请输入头像图片地址" allow-clear />
          <div v-if="form.userAvatar" class="avatar-preview">
            <img :src="form.userAvatar" alt="头像预览" />
          </div>
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea v-model:value="form.userProfile" placeholder="介绍一下自己" :rows="4" allow-clear />
        </a-form-item>
        <a-space>
          <a-button type="primary" :loading="submitting" @click="handleUpdate">保存</a-button>
          <a-button @click="router.back()">返回</a-button>
        </a-space>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { updateUserUsingPost } from '@/api/userController'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)

// 表单初始值来自当前登录用户
const form = reactive({
  userName: loginUserStore.loginUser.userName ?? '',
  userAvatar: loginUserStore.loginUser.userAvatar ?? '',
  userProfile: loginUserStore.loginUser.userProfile ?? '',
})

// 保存资料
async function handleUpdate() {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  submitting.value = true
  try {
    const res = await updateUserUsingPost({
      id: loginUserStore.loginUser.id,
      userName: form.userName,
      userAvatar: form.userAvatar,
      userProfile: form.userProfile,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      // 刷新登录用户信息，让导航栏昵称立即更新
      await loginUserStore.fetchLoginUser()
      router.back()
    } else {
      message.error('保存失败：' + res.data.message)
    }
  } catch (e: any) {
    message.error('保存失败：' + (e?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.edit-profile-page {
  max-width: 600px;
  margin: 24px auto;
}
.avatar-preview {
  margin-top: 8px;
}
.avatar-preview img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #f0f0f0;
}
</style>
