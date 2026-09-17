<template>
  <div id="appEditPage">
    <!-- 无权限 / 应用不存在 -->
    <a-card v-if="noPermission" class="panel" :bordered="false">
      <a-result status="403" title="无法编辑该应用" :sub-title="noPermissionTip">
        <template #extra>
          <a-button type="primary" @click="goBack">返回</a-button>
          <a-button @click="goHome">返回首页</a-button>
        </template>
      </a-result>
    </a-card>

    <template v-else>
      <!-- 编辑表单：普通用户仅可修改应用名称，管理员可修改名称 / 封面 / 优先级 -->
      <a-card class="panel" :bordered="false">
        <template #title>
          <span class="panel-title">应用信息修改</span>
        </template>
        <template #extra>
          <a-space :size="8">
            <a-button @click="goBack">
              <template #icon><LeftOutlined /></template>
              <span>返回</span>
            </a-button>
            <a-button type="primary" :loading="submitting" @click="handleSubmit">
              <template #icon><SaveOutlined /></template>
              <span>保存</span>
            </a-button>
          </a-space>
        </template>

        <a-spin :spinning="loading">
          <a-form
            ref="formRef"
            class="app-form"
            :model="formState"
            :rules="formRules"
            :label-col="{ span: 6 }"
            :wrapper-col="{ span: 16 }"
            autocomplete="off"
          >
            <a-form-item label="应用名称" name="appName">
              <a-input
                v-model:value="formState.appName"
                placeholder="请输入应用名称"
                :maxlength="50"
                allow-clear
              />
            </a-form-item>

            <template v-if="isAdmin">
              <a-form-item label="应用封面" name="cover">
                <div class="cover-field">
                  <div class="cover-field__preview">
                    <img v-if="formState.cover" :src="formState.cover" alt="cover" />
                    <PictureOutlined v-else class="cover-field__icon" />
                  </div>
                  <div class="cover-field__main">
                    <a-input
                      v-model:value="formState.cover"
                      placeholder="请输入封面图片链接（选填）"
                      allow-clear
                    />
                    <div class="cover-field__tip">
                      填写图片链接后左侧会实时预览，留空则展示默认占位
                    </div>
                  </div>
                </div>
              </a-form-item>

              <a-form-item label="优先级" name="priority">
                <a-space :size="8">
                  <a-input-number v-model:value="formState.priority" :min="0" :max="99" />
                  <a-button size="small" @click="formState.priority = GOOD_APP_PRIORITY">
                    <template #icon><StarFilled /></template>
                    <span>设为精选</span>
                  </a-button>
                  <a-button size="small" @click="formState.priority = DEFAULT_APP_PRIORITY">
                    <span>取消精选</span>
                  </a-button>
                </a-space>
                <template #extra>优先级 99 表示精选应用，会展示在首页「精选案例」中</template>
              </a-form-item>
            </template>

            <a-form-item v-else label="应用封面">
              <span class="readonly-text">{{ formState.cover || '-' }}</span>
              <template #extra>普通用户暂不支持修改应用封面与优先级</template>
            </a-form-item>
          </a-form>
        </a-spin>
      </a-card>

      <!-- 只读信息：便于用户确认当前应用状态 -->
      <a-card class="panel" :bordered="false">
        <template #title>
          <span class="panel-title">应用信息</span>
        </template>
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="应用 id">{{ app.id ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="代码生成类型">
            {{ CODE_GEN_TYPE_LABEL[app.codeGenType ?? ''] ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="部署标识">{{
            app.deployKey || '未部署'
          }}</a-descriptions-item>
          <a-descriptions-item label="部署时间">
            {{ formatDateTime(app.deployedTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="创建用户">
            {{ app.userVO?.userName || app.userVO?.userAccount || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="优先级">{{
            app.priority ?? DEFAULT_APP_PRIORITY
          }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{
            formatDateTime(app.createTime)
          }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{
            formatDateTime(app.updateTime)
          }}</a-descriptions-item>
          <a-descriptions-item label="初始提示词" :span="2">
            <div class="readonly-prompt">{{ app.initPrompt || '-' }}</div>
          </a-descriptions-item>
        </a-descriptions>
      </a-card>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { LeftOutlined, PictureOutlined, SaveOutlined, StarFilled } from '@ant-design/icons-vue'
import { editApp, getAppById, getAppVoById, updateAppByAdmin } from '@/api/appController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import ACCESS_ENUM from '@/access/accessEnum'
import { CODE_GEN_TYPE_LABEL, DEFAULT_APP_PRIORITY, GOOD_APP_PRIORITY } from '@/constants/app'
import { asApiId } from '@/utils/apiId'
import { formatDateTime } from '@/utils/time'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

/** 应用 id：后端 Long 序列化为字符串，按字符串原样透传 */
const appId = String(route.params.appId ?? '')
/** 是否管理员：管理员可修改任意应用的应用名称 / 封面 / 优先级 */
const isAdmin = computed(() => loginUserStore.loginUser.userRole === ACCESS_ENUM.ADMIN)

const app = ref<API.AppVO>({})
const loading = ref(false)
const submitting = ref(false)
const noPermission = ref(false)
const noPermissionTip = ref('抱歉，你没有权限修改该应用。')

const formRef = ref<FormInstance>()
const formState = reactive<{
  appName: string
  cover: string
  priority: number
}>({
  appName: '',
  cover: '',
  priority: DEFAULT_APP_PRIORITY,
})

// 封面链接校验：兼容 http(s) 地址、data 图片与以 / 开头的相对路径（与用户管理页保持一致）
const coverValidator = async (_rule: Rule, value?: string) => {
  if (!value) {
    return Promise.resolve()
  }
  const isValid =
    /^https?:\/\/\S+$/i.test(value) || /^data:image\/\S+$/i.test(value) || value.startsWith('/')
  return isValid ? Promise.resolve() : Promise.reject('请输入合法的图片链接')
}

const formRules: Record<string, Rule[]> = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { max: 50, message: '应用名称不能超过 50 个字符', trigger: 'blur' },
  ],
  cover: [
    { max: 512, message: '封面链接不能超过 512 个字符', trigger: 'blur' },
    { validator: coverValidator, trigger: 'blur' },
  ],
}

/**
 * 加载应用信息
 *
 * 管理员走管理员详情接口，普通用户走应用详情接口；
 * 普通用户只能编辑自己创建的应用（后端同样会校验，这里提前拦截给出友好提示）
 */
const loadApp = async () => {
  loading.value = true
  try {
    const res = isAdmin.value
      ? await getAppById({ id: asApiId(appId) })
      : await getAppVoById({ id: asApiId(appId) })
    if (res.data.code !== 0 || !res.data.data) {
      noPermission.value = true
      noPermissionTip.value = res.data.message ?? '应用不存在或已被删除'
      return
    }
    const data = res.data.data
    if (!isAdmin.value && String(data.userId) !== String(loginUserStore.loginUser.id)) {
      noPermission.value = true
      noPermissionTip.value = '抱歉，你只能修改自己创建的应用。'
      return
    }
    app.value = data
    formState.appName = data.appName ?? ''
    formState.cover = data.cover ?? ''
    formState.priority = data.priority ?? DEFAULT_APP_PRIORITY
  } catch {
    noPermission.value = true
    noPermissionTip.value = '获取应用信息失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

/** 保存：管理员调用管理员更新接口（支持封面 / 优先级），普通用户调用用户端编辑接口（仅应用名称） */
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    // 校验不通过：错误提示由表单项自行展示
    return
  }
  submitting.value = true
  try {
    const appName = formState.appName.trim()
    const res = isAdmin.value
      ? await updateAppByAdmin({
          id: asApiId(appId),
          appName,
          cover: formState.cover?.trim(),
          priority: formState.priority,
        })
      : await editApp({ id: asApiId(appId), appName })
    if (res.data.code === 0) {
      message.success('保存成功')
      goBack()
    } else {
      message.error('保存失败，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/** 返回上一页（直接访问该页面时没有上一页，回首页兜底） */
const goBack = () => {
  if (window.history.state?.back) {
    router.back()
  } else {
    goHome()
  }
}

const goHome = () => {
  router.push('/')
}

onMounted(() => {
  loadApp()
})
</script>

<style scoped>
#appEditPage {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
}

/* 卡片容器：白底轻投影，与用户管理页保持一致 */
.panel {
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 1px 6px -1px rgba(0, 0, 0, 0.03);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

/* 表单整体左对齐，标签不换行 */
.app-form {
  max-width: 680px;
}

.app-form :deep(.ant-form-item-label) {
  white-space: nowrap;
}

/* 封面预览 + 输入 */
.cover-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.cover-field__preview {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 96px;
  height: 60px;
  overflow: hidden;
  background: #f5f5f5;
  border-radius: 6px;
  color: rgba(0, 0, 0, 0.25);
}

.cover-field__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-field__icon {
  font-size: 20px;
}

.cover-field__main {
  flex: 1;
  min-width: 0;
}

.cover-field__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.45);
}

/* 只读信息 */
.readonly-text {
  color: rgba(0, 0, 0, 0.65);
  word-break: break-all;
}

.readonly-prompt {
  max-height: 120px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
