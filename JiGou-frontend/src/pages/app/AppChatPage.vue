<template>
  <div id="appChatPage">
    <!-- 顶部栏：左侧应用名称（下拉可管理应用）、右侧部署按钮 -->
    <div class="chat-header">
      <div class="chat-header__left">
        <img class="chat-header__logo" :src="siteConfig.logo" alt="logo" />
        <a-dropdown :trigger="['click']">
          <span class="chat-header__name">
            {{ app.appName || '应用加载中…' }}
            <DownOutlined class="chat-header__arrow" />
          </span>
          <template #overlay>
            <a-menu @click="handleAppMenuClick">
              <a-menu-item key="detail">
                <template #icon><EyeOutlined /></template>
                <span>查看详情</span>
              </a-menu-item>
              <a-menu-item v-if="canManage" key="edit">
                <template #icon><EditOutlined /></template>
                <span>编辑应用信息</span>
              </a-menu-item>
              <!-- 删除仅限创建者本人 -->
              <template v-if="isOwner">
                <a-menu-divider />
                <a-menu-item key="delete" danger>
                  <template #icon><DeleteOutlined /></template>
                  <span>删除应用</span>
                </a-menu-item>
              </template>
            </a-menu>
          </template>
        </a-dropdown>
        <a-tag
          v-if="app.codeGenType"
          :color="CODE_GEN_TYPE_TAG_COLOR[app.codeGenType] ?? 'default'"
        >
          {{ CODE_GEN_TYPE_LABEL[app.codeGenType] ?? app.codeGenType }}
        </a-tag>
        <a-tag v-if="hasDeployed" color="green">已部署</a-tag>
      </div>

      <div class="chat-header__right">
        <a-tooltip :title="isOwner ? '' : '仅应用创建者可以部署该应用'">
          <a-button type="primary" :disabled="!isOwner" :loading="deploying" @click="doDeploy">
            <template #icon><CloudUploadOutlined /></template>
            <span>部署</span>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!-- 核心内容区：左对话区域，右网页展示区域 -->
    <div class="chat-body">
      <!-- 对话区域 -->
      <div class="chat-panel">
        <div ref="messageListRef" class="chat-panel__messages">
          <a-empty
            v-if="!appLoading && !messages.length"
            description="输入提示词，开始生成你的网站"
          />
          <AppChatMessage
            v-for="item in messages"
            :key="item.id"
            :role="item.role"
            :content="item.content"
            :streaming="item.streaming"
            :error="item.error"
            :time="item.time"
          />
        </div>

        <!-- 用户消息输入框 -->
        <div class="chat-panel__input">
          <a-textarea
            v-model:value="inputMessage"
            class="chat-panel__textarea"
            placeholder="请描述你想生成的网站，越详细效果越贴合想法"
            :auto-size="{ minRows: 3, maxRows: 6 }"
            :disabled="!isOwner || streaming"
            :maxlength="2000"
            @press-enter="onPressEnter"
          />
          <div class="chat-panel__footer">
            <span class="chat-panel__hint">Enter 发送 / Shift + Enter 换行</span>
            <a-button v-if="streaming" size="middle" danger @click="stopGenerate">
              停止生成
            </a-button>
            <a-button
              v-else
              type="primary"
              shape="circle"
              size="large"
              :disabled="!canSend"
              @click="send()"
            >
              <template #icon><ArrowUpOutlined /></template>
            </a-button>
          </div>
          <div v-if="!isOwner" class="chat-panel__tip">仅应用创建者可以继续对话生成</div>
        </div>
      </div>

      <!-- 网页展示区域：流式接口全部返回后展示 -->
      <div class="preview-panel">
        <div class="preview-panel__header">
          <span class="preview-panel__title">
            <GlobalOutlined />
            <span>生成后的网页展示</span>
          </span>
          <a-space :size="8">
            <a-button size="small" :disabled="!previewVisible" @click="refreshPreview">
              <template #icon><ReloadOutlined /></template>
              <span>刷新</span>
            </a-button>
            <a-button
              size="small"
              type="link"
              :disabled="!previewVisible"
              @click="openPreviewInNewTab"
            >
              <template #icon><LinkOutlined /></template>
              <span>新窗口打开</span>
            </a-button>
          </a-space>
        </div>
        <div class="preview-panel__body">
          <iframe
            v-if="previewVisible"
            :key="previewKey"
            class="preview-panel__iframe"
            :src="previewSrc"
            title="应用预览"
          />
          <a-empty v-else description="网站生成完成后，将在这里展示效果" />
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal v-model:open="detailOpen" :app="app" />

    <!-- 部署成功弹窗 -->
    <a-modal
      v-model:open="deployModalOpen"
      title="应用部署成功"
      :width="560"
      :footer="null"
      centered
    >
      <p class="deploy-tip">应用已部署完成，可通过以下地址访问：</p>
      <div class="deploy-url">
        <a class="deploy-url__link" :href="deployUrl" target="_blank">{{ deployUrl }}</a>
        <a-button size="small" @click="copyDeployUrl">
          <template #icon><CopyOutlined /></template>
          <span>复制</span>
        </a-button>
      </div>
      <p class="deploy-tip deploy-tip--sub">
        部署地址由后端返回（依赖 nginx 映射），重新部署会覆盖原有内容。
      </p>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import type { MenuProps } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  CloudUploadOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  EyeOutlined,
  GlobalOutlined,
  LinkOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { deleteApp, deployApp, getAppVoById } from '@/api/appController.ts'
import AppChatMessage from '@/components/AppChatMessage.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { siteConfig } from '@/layouts/config'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import ACCESS_ENUM from '@/access/accessEnum'
import { CODE_GEN_TYPE_LABEL, CODE_GEN_TYPE_TAG_COLOR } from '@/constants/app'
import { asApiId } from '@/utils/apiId'
import { getPreviewUrl, isPreviewAvailable } from '@/utils/appUrl'
import { AppChatStreamError, streamGenCode } from '@/utils/appChatStream'

/** 对话消息 */
type ChatMessage = {
  id: string
  role: 'user' | 'ai'
  content: string
  /** 是否正在流式输出 */
  streaming?: boolean
  /** 是否生成失败 */
  error?: boolean
  /** 消息时间 */
  time?: string
}

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

/** 应用 id：后端 Long 序列化为字符串，全程按字符串透传（不可转 Number，避免精度丢失） */
const appId = String(route.params.appId ?? '')

const app = ref<API.AppVO>({})
const appLoading = ref(false)
const messages = ref<ChatMessage[]>([])
const inputMessage = ref('')
/** 是否有请求正在流式输出 */
const streaming = ref(false)
const deploying = ref(false)
/** 预览区是否展示（流式接口全部返回后展示） */
const previewVisible = ref(false)
/** 预览刷新标记，用于强制 iframe 重新加载 */
const previewKey = ref(0)
const deployUrl = ref('')
const deployModalOpen = ref(false)
const detailOpen = ref(false)
const messageListRef = ref<HTMLElement>()

/** 流式请求的取消控制器，用于「停止生成」与组件卸载 */
let controller: AbortController | null = null
let messageSeed = 0

/** 是否应用创建者：仅创建者可以对话生成与部署（与后端校验保持一致） */
const isOwner = computed(
  () =>
    !!loginUserStore.loginUser.id &&
    String(app.value.userId) === String(loginUserStore.loginUser.id),
)

/** 是否管理员：管理员可以在对话页进入「编辑应用信息」（生成 / 部署仍仅限创建者） */
const isAdmin = computed(() => loginUserStore.loginUser.userRole === ACCESS_ENUM.ADMIN)

/** 是否展示「编辑应用信息」入口：创建者本人或管理员 */
const canManage = computed(() => isOwner.value || isAdmin.value)
const hasDeployed = computed(() => !!app.value.deployKey)
const previewUrl = computed(() => getPreviewUrl(app.value.codeGenType, appId))
/** 追加时间戳参数，避免浏览器复用缓存的旧页面 */
const previewSrc = computed(() => `${previewUrl.value}?t=${previewKey.value}`)
const canSend = computed(() => isOwner.value && !streaming.value && !!inputMessage.value.trim())

/* ------------------------------ 滚动 ------------------------------ */

const scrollToBottom = () => {
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

// 流式输出时每个分片都会触发滚动，用 rAF 合并，避免频繁布局计算
let scrollScheduled = false
const scheduleScrollToBottom = () => {
  if (scrollScheduled) {
    return
  }
  scrollScheduled = true
  requestAnimationFrame(() => {
    scrollScheduled = false
    scrollToBottom()
  })
}

/* ------------------------------ 应用信息 ------------------------------ */

/**
 * 加载应用信息
 *
 * @param silent 静默加载（不展示 loading，用于生成完成 / 部署完成后刷新数据）
 * @returns 是否加载成功
 */
const loadApp = async (silent = false) => {
  if (!silent) {
    appLoading.value = true
  }
  try {
    const res = await getAppVoById({ id: asApiId(appId) })
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
      return true
    }
    message.error('获取应用信息失败：' + (res.data.message ?? '请稍后重试'))
    return false
  } catch {
    message.error('获取应用信息失败，请检查网络后重试')
    return false
  } finally {
    appLoading.value = false
  }
}

/* ------------------------------ 流式生成 ------------------------------ */

/**
 * 发送消息并流式接收 AI 回复
 *
 * @param text 指定消息内容（首次进入时自动发送初始提示词），为空则取输入框内容
 */
const send = async (text?: string) => {
  const content = (text ?? inputMessage.value).trim()
  if (!content) {
    message.warning('请输入内容后再发送')
    return
  }
  if (!isOwner.value) {
    message.warning('仅应用创建者可以继续对话生成')
    return
  }
  if (streaming.value) {
    message.warning('正在生成中，请稍候')
    return
  }

  inputMessage.value = ''
  messages.value.push({
    id: `u-${++messageSeed}`,
    role: 'user',
    content,
    time: new Date().toISOString(),
  })
  messages.value.push({
    id: `a-${++messageSeed}`,
    role: 'ai',
    content: '',
    streaming: true,
    time: new Date().toISOString(),
  })
  // 取响应式代理对象：直接改原始对象不会触发视图更新
  const aiMessage = messages.value[messages.value.length - 1]
  if (!aiMessage) {
    return
  }

  streaming.value = true
  scheduleScrollToBottom()
  controller = new AbortController()
  try {
    await streamGenCode({
      appId,
      message: content,
      signal: controller.signal,
      onChunk: (chunk) => {
        aiMessage.content += chunk
        scheduleScrollToBottom()
      },
    })
    // 流式接口全部返回：结束光标、展示（刷新）右侧预览
    aiMessage.streaming = false
    previewVisible.value = true
    previewKey.value += 1
    // 生成过程可能更新了应用信息（如编辑时间），静默刷新一次
    await loadApp(true)
  } catch (error) {
    aiMessage.streaming = false
    const err = error as AppChatStreamError
    if (err?.name === 'AbortError') {
      // 用户主动停止：保留已生成的内容，不标记为失败
      if (!aiMessage.content) {
        aiMessage.content = '（已停止生成）'
      }
      return
    }
    aiMessage.error = true
    if (err?.code === 40100) {
      message.warning('请先登录')
      router.push(`/user/login?redirect=${encodeURIComponent(route.fullPath)}`)
      return
    }
    message.error(err?.message || '生成失败，请稍后重试')
  } finally {
    streaming.value = false
    controller = null
    scheduleScrollToBottom()
  }
}

/** 停止生成 */
const stopGenerate = () => {
  controller?.abort()
  controller = null
  streaming.value = false
  message.info('已停止生成')
}

/** 回车发送（Shift + Enter 换行） */
const onPressEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) {
    return
  }
  e.preventDefault()
  send()
}

/* ------------------------------ 部署 ------------------------------ */

const doDeploy = async () => {
  if (!isOwner.value) {
    message.warning('仅应用创建者可以部署该应用')
    return
  }
  if (streaming.value) {
    message.warning('请等待代码生成完成后再部署')
    return
  }
  deploying.value = true
  try {
    const res = await deployApp({ appId: asApiId(appId) })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalOpen.value = true
      await loadApp(true)
    } else {
      message.error('部署失败：' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('部署失败，请稍后重试')
  } finally {
    deploying.value = false
  }
}

/** 复制部署地址（非安全上下文下 clipboard 不可用，降级为临时输入框复制） */
const copyDeployUrl = async () => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(deployUrl.value)
    } else {
      const input = document.createElement('textarea')
      input.value = deployUrl.value
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    message.success('部署地址已复制')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

/* ------------------------------ 预览 ------------------------------ */

const refreshPreview = () => {
  previewKey.value += 1
}

const openPreviewInNewTab = () => {
  window.open(previewSrc.value, '_blank')
}

/* ------------------------------ 应用管理 ------------------------------ */

// 删除应用（二次确认后调用用户端删除接口）
const confirmDeleteApp = () => {
  Modal.confirm({
    title: `确定删除应用「${app.value.appName || appId}」吗？`,
    content: '删除后不可恢复，请谨慎操作。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deleteApp({ id: asApiId(appId) })
      if (res.data.code === 0) {
        message.success('删除成功')
        router.replace('/')
      } else {
        message.error('删除失败，' + (res.data.message ?? '请稍后重试'))
      }
    },
  })
}

const handleAppMenuClick: MenuProps['onClick'] = ({ key }) => {
  if (key === 'detail') {
    detailOpen.value = true
  } else if (key === 'edit') {
    router.push(`/app/edit/${appId}`)
  } else if (key === 'delete') {
    confirmDeleteApp()
  }
}

/* ------------------------------ 生命周期 ------------------------------ */

onMounted(async () => {
  const loaded = await loadApp()
  if (!loaded) {
    router.replace('/')
    return
  }
  // 历史应用可能已经生成过代码，先探测一次预览资源，避免 iframe 请求不存在的目录
  previewVisible.value = await isPreviewAvailable(previewUrl.value)
  await nextTick()
  scrollToBottom()

  // 从首页创建应用跳转过来：自动把初始提示词作为第一条消息发送给 AI
  if (route.query.autoStart === '1' && app.value.initPrompt) {
    await router.replace({ path: route.path })
    await send(app.value.initPrompt)
  }
})

onBeforeUnmount(() => {
  // 离开页面时中断流式请求，避免请求悬挂
  controller?.abort()
  controller = null
})
</script>

<style scoped>
/*
 * 高度计算：视口高度 - 顶部导航栏(64) - 页脚高度(16 + 22 + 16 = 54)
 * 页面已通过路由 meta.flush 去掉内容区内边距，左右两栏因此能撑满一屏
 */
#appChatPage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--header-height) - var(--footer-height));
  min-height: 480px;
  background: #fff;
}

/* 顶部栏：应用名称 + 部署按钮 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: none;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #eef0f4;
}

.chat-header__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.chat-header__logo {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f5f5f5;
  object-fit: contain;
}

.chat-header__name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 320px;
  overflow: hidden;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
}

.chat-header__arrow {
  flex: none;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

/* 核心内容区：左侧对话区与右侧预览区按 2 : 3 分配宽度 */
.chat-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  flex: 2 1 0;
  min-width: 320px;
  min-height: 0;
  border-right: 1px solid #eef0f4;
}

.preview-panel {
  display: flex;
  flex-direction: column;
  flex: 3 1 0;
  min-width: 0;
  min-height: 0;
}

.chat-panel__messages {
  flex: 1;
  padding: 20px 20px 8px;
  overflow-y: auto;
}

.chat-panel__input {
  flex: none;
  padding: 12px 16px 14px;
  border-top: 1px solid #eef0f4;
}

.chat-panel__textarea {
  resize: none;
  border-radius: 10px;
}

.chat-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.chat-panel__hint {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.chat-panel__tip {
  margin-top: 8px;
  font-size: 12px;
  color: #faad14;
}

.preview-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex: none;
  padding: 10px 16px;
  border-bottom: 1px solid #eef0f4;
}

.preview-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.preview-panel__body {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fafafa;
}

.preview-panel__iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

/* 部署弹窗 */
.deploy-tip {
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.65);
}

.deploy-tip--sub {
  margin-top: 12px;
  margin-bottom: 0;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.deploy-url {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: #f5f5f5;
  border-radius: 6px;
}

.deploy-url__link {
  word-break: break-all;
}

/* 窄屏下改为上下堆叠，避免左右区域过窄 */
@media (max-width: 992px) {
  #appChatPage {
    height: auto;
    min-height: 0;
  }

  .chat-body {
    flex-direction: column;
  }

  .chat-panel {
    flex: none;
    min-width: 0;
    height: 60vh;
    border-right: none;
    border-bottom: 1px solid #eef0f4;
  }

  .preview-panel {
    flex: none;
    height: 70vh;
  }
}
</style>
