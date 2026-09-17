<template>
  <div id="userCenterPage">
    <!-- 个人信息卡片 -->
    <a-card class="panel profile-panel" :bordered="false">
      <div class="profile">
        <a-avatar
          class="profile__avatar"
          :size="80"
          :src="loginUserStore.loginUser.userAvatar || undefined"
        >
          <template #icon><UserOutlined /></template>
        </a-avatar>

        <div class="profile__body">
          <div class="profile__name-row">
            <span class="profile__name">
              {{
                loginUserStore.loginUser.userName ||
                loginUserStore.loginUser.userAccount ||
                '未命名用户'
              }}
            </span>
            <a-tag :color="roleTagColor">
              <CrownOutlined v-if="loginUserStore.loginUser.userRole === 'admin'" />
              <UserOutlined v-else-if="loginUserStore.loginUser.userRole === 'user'" />
              {{ roleLabel }}
            </a-tag>
          </div>
          <div class="profile__account">
            账号：{{ loginUserStore.loginUser.userAccount ?? '-' }}
          </div>
          <div class="profile__bio">
            {{
              loginUserStore.loginUser.userProfile ||
              '还没有填写个人简介，点击右上角「编辑资料」介绍一下自己吧~'
            }}
          </div>
        </div>

        <div class="profile__action">
          <a-button type="primary" @click="openEditModal">
            <template #icon><EditOutlined /></template>
            <span>编辑资料</span>
          </a-button>
        </div>
      </div>
    </a-card>

    <!-- 编辑资料弹窗：仅允许编辑用户名 / 头像 / 简介，账号与角色只读 -->
    <a-modal
      v-model:open="modalOpen"
      title="编辑资料"
      :width="560"
      :confirm-loading="submitting"
      :mask-closable="false"
      destroy-on-close
      centered
      ok-text="保存"
      cancel-text="取消"
      @ok="handleModalOk"
    >
      <a-form
        ref="formRef"
        class="user-form"
        :model="formState"
        :rules="formRules"
        :label-col="{ span: 5 }"
        :wrapper-col="{ span: 19 }"
        autocomplete="off"
      >
        <a-form-item label="账号">
          <a-input :value="loginUserStore.loginUser.userAccount" disabled />
          <template #extra>账号是唯一登录标识，不支持修改</template>
        </a-form-item>

        <a-form-item label="用户名" name="userName">
          <a-input
            v-model:value="formState.userName"
            placeholder="请输入用户名（选填）"
            :maxlength="32"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="头像" name="userAvatar">
          <div class="avatar-field">
            <a-avatar
              class="avatar-field__preview"
              :size="56"
              :src="formState.userAvatar || undefined"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <div class="avatar-field__main">
              <a-input
                v-model:value="formState.userAvatar"
                placeholder="请输入头像图片链接（选填）"
                allow-clear
              />
              <div class="avatar-field__tip">填写图片链接后左侧会实时预览，留空则展示默认头像</div>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="简介" name="userProfile">
          <a-textarea
            v-model:value="formState.userProfile"
            placeholder="请输入个人简介（选填，最长 200 字）"
            :rows="3"
            :maxlength="200"
            show-count
            allow-clear
          />
        </a-form-item>

        <a-form-item label="用户角色">
          <a-input :value="roleLabel" disabled />
          <template #extra>角色由管理员分配，个人无法自行修改</template>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { CrownOutlined, EditOutlined, UserOutlined } from '@ant-design/icons-vue'
import { editUser } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const loginUserStore = useLoginUserStore()

/* ------------------------------ 角色展示（只读） ------------------------------ */

const roleLabel = computed(() => {
  const role = loginUserStore.loginUser.userRole
  if (role === 'admin') return '管理员'
  if (role === 'user') return '普通用户'
  return role ?? '-'
})

const roleTagColor = computed(() => {
  const role = loginUserStore.loginUser.userRole
  if (role === 'admin') return 'green'
  if (role === 'user') return 'blue'
  return 'default'
})

/* ------------------------------ 编辑资料 ------------------------------ */

type ProfileFormState = {
  userName?: string
  userAvatar?: string
  userProfile?: string
}

const modalOpen = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const formState = reactive<ProfileFormState>({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

// 头像链接校验：兼容 http(s) 地址、data 图片与以 / 开头的相对路径
const avatarValidator = async (_rule: Rule, value?: string) => {
  if (!value) {
    return Promise.resolve()
  }
  const isValid =
    /^https?:\/\/\S+$/i.test(value) || /^data:image\/\S+$/i.test(value) || value.startsWith('/')
  return isValid ? Promise.resolve() : Promise.reject('请输入合法的图片链接')
}

const formRules: Record<string, Rule[]> = {
  userName: [{ max: 32, message: '用户名不能超过 32 个字符', trigger: 'blur' }],
  userAvatar: [
    { max: 512, message: '头像链接不能超过 512 个字符', trigger: 'blur' },
    { validator: avatarValidator, trigger: 'blur' },
  ],
  userProfile: [{ max: 200, message: '简介不能超过 200 个字符', trigger: 'blur' }],
}

// 打开弹窗：用当前登录用户信息回填表单，等待渲染后清空上次校验提示
const openEditModal = async () => {
  const user = loginUserStore.loginUser
  formState.userName = user.userName ?? ''
  formState.userAvatar = user.userAvatar ?? ''
  formState.userProfile = user.userProfile ?? ''
  modalOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

// 提交弹窗表单
const handleModalOk = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    // 校验不通过：错误提示由表单项自行展示
    return
  }
  submitting.value = true
  try {
    // 走 /user/edit 自编辑接口：请求体不含 userRole，天然无法改角色
    const res = await editUser({
      id: loginUserStore.loginUser.id,
      userName: formState.userName?.trim(),
      userAvatar: formState.userAvatar?.trim(),
      userProfile: formState.userProfile?.trim(),
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      modalOpen.value = false
      // 重新拉取登录用户信息，让页面与顶部导航栏的头像 / 昵称同步更新
      await loginUserStore.fetchLoginUser()
    } else {
      message.error(res.data.message ?? '保存失败')
    }
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

// 进入页面时刷新一次，保证展示的是最新信息
onMounted(() => {
  loginUserStore.fetchLoginUser()
})
</script>

<style scoped>
#userCenterPage {
  display: flex;
  justify-content: center;
}

.panel {
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 1px 6px -1px rgba(0, 0, 0, 0.03);
}

.profile-panel {
  width: 100%;
  max-width: 760px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 24px;
}

.profile__avatar {
  flex: none;
  font-size: 32px;
  background-color: #f5f5f5;
  color: rgba(0, 0, 0, 0.35);
}

.profile__body {
  flex: 1;
  min-width: 0;
}

.profile__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile__name {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile__account {
  margin-top: 4px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}

.profile__bio {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(0, 0, 0, 0.65);
  word-break: break-word;
}

.profile__action {
  flex: none;
}

/* 弹窗表单 */
.user-form :deep(.ant-form-item-label) {
  white-space: nowrap;
}

.avatar-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.avatar-field__preview {
  flex: none;
  background-color: #f5f5f5;
  color: rgba(0, 0, 0, 0.35);
}

.avatar-field__main {
  flex: 1;
  min-width: 0;
}

.avatar-field__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.45);
}

@media (max-width: 768px) {
  .profile {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .profile__action {
    width: 100%;
  }

  .profile__action :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
