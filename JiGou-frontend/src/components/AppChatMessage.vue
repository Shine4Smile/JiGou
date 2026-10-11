<template>
  <div class="message" :class="`message--${role}`">
    <!-- AI 消息：左侧展示头像 -->
    <img v-if="isAi" class="message__avatar" :src="siteConfig.logo" alt="AI" />

    <div class="message__bubble" :class="{ 'message__bubble--error': error }">
      <!-- 用户消息：纯文本展示（用户输入不参与 markdown 渲染） -->
      <template v-if="!isAi">{{ content }}</template>

      <template v-else>
        <!-- AI 生成失败：直接展示后端返回的错误原文，不做 markdown 渲染 -->
        <span v-if="error" class="message__plain">{{ content }}</span>

        <!-- AI 消息：按出现顺序渲染文本段（markdown）与代码段（文件卡片） -->
        <template v-else>
          <div v-if="streaming && !content" class="message__thinking">正在生成代码…</div>

          <template v-for="segment in segments" :key="segment.key">
            <!-- 文本段：markdown 渲染（renderMarkdown 内部已做 XSS 净化） -->
            <div
              v-if="segment.type === 'text'"
              class="message__markdown"
              v-html="renderText(segment.text)"
            ></div>

            <!-- 工具段：展示工具链进度（选择工具 / 工具调用） -->
            <div
              v-else-if="segment.type === 'tool'"
              class="tool-step"
              :class="`tool-step--${segment.stage}`"
            >
              <ToolOutlined v-if="segment.stage === 'select'" class="tool-step__icon" />
              <FileAddOutlined v-else class="tool-step__icon" />
              <span class="tool-step__label">
                {{ segment.stage === 'select' ? '选择工具' : '工具调用' }}
              </span>
              <span class="tool-step__text">{{
                segment.target ? `${segment.action} ${segment.target}` : segment.action
              }}</span>
            </div>

            <!-- 代码段：文件卡片，可折叠，代码按语言高亮 -->
            <div v-else class="file-item">
              <div class="file-item__header" @click="toggleFile(segment)">
                <FileOutlined class="file-item__icon" />
                <span class="file-item__name">{{ segment.fileName || '代码片段' }}</span>
                <span class="file-item__size">
                  {{ segment.closed ? `${segment.lines} 行` : '生成中…' }}
                </span>
                <UpOutlined v-if="isFileExpanded(segment)" class="file-item__toggle" />
                <DownOutlined v-else class="file-item__toggle" />
              </div>
              <pre v-if="isFileExpanded(segment)" class="file-item__code"><code
                  class="hljs"
                  v-html="renderCode(segment)"
                ></code></pre>
            </div>
          </template>

          <!-- 流式输出光标 -->
          <span v-if="streaming" class="message__cursor" />
        </template>

        <!-- 生成完成后的落款（成功 / 失败共用，对应原型中「v1 已保存」的位置） -->
        <div v-if="!streaming" class="message__footer">
          <template v-if="error">
            <CloseCircleOutlined />
            <span>生成失败，请重试</span>
          </template>
          <template v-else-if="fileCount">
            <CheckCircleOutlined />
            <span>已生成 {{ fileCount }} 个文件并保存到应用目录</span>
          </template>
          <template v-else>
            <CheckCircleOutlined />
            <span>已完成</span>
          </template>
          <span v-if="time" class="message__footer-time">{{ formatRelativeTime(time) }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownOutlined,
  FileAddOutlined,
  FileOutlined,
  ToolOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import { siteConfig } from '@/layouts/config'
import { highlightCode, renderMarkdown } from '@/utils/markdown'
import {
  countCodeSegments,
  splitMessageContent,
  type CodeMessageSegment,
  type MessageSegment,
} from '@/utils/messageContent'
import { formatRelativeTime } from '@/utils/time'

const props = withDefaults(
  defineProps<{
    /** 消息角色：user 用户消息（靠右）、ai AI 消息（靠左） */
    role: 'user' | 'ai'
    /** 消息内容 */
    content: string
    /** 是否正在流式输出 */
    streaming?: boolean
    /** 是否生成失败 */
    error?: boolean
    /** 消息时间（前端记录的本地时间） */
    time?: string
  }>(),
  {
    streaming: false,
    error: false,
    time: '',
  },
)

const isAi = computed(() => props.role === 'ai')

/** 按出现顺序切分的消息片段：文本段（markdown）+ 工具段（工具链进度）+ 代码段（文件卡片） */
const segments = computed<MessageSegment[]>(() =>
  isAi.value ? splitMessageContent(props.content) : [],
)

/** 已生成的文件数（代码段个数） */
const fileCount = computed(() => countCodeSegments(segments.value))

/**
 * 渲染结果缓存
 *
 * 流式输出时内容每帧都在变，切片结果与已完成片段会反复重算：
 * 以「片段内容」为 key 缓存渲染结果，避免重复做 markdown 解析与代码高亮
 */
const RENDER_CACHE_LIMIT = 50
const markdownCache = new Map<string, string>()
const codeCache = new Map<string, string>()

/** 文本段 -> HTML（markdown，内部已做 XSS 净化） */
const renderText = (text: string): string => {
  const cached = markdownCache.get(text)
  if (cached !== undefined) {
    return cached
  }
  const html = renderMarkdown(text)
  if (markdownCache.size >= RENDER_CACHE_LIMIT) {
    markdownCache.clear()
  }
  markdownCache.set(text, html)
  return html
}

/** 代码段 -> HTML（按语言高亮） */
const renderCode = (segment: CodeMessageSegment): string => {
  const cacheKey = `${segment.language}\n${segment.code}`
  const cached = codeCache.get(cacheKey)
  if (cached !== undefined) {
    return cached
  }
  const html = highlightCode(segment.code, segment.language)
  if (codeCache.size >= RENDER_CACHE_LIMIT) {
    codeCache.clear()
  }
  codeCache.set(cacheKey, html)
  return html
}

/**
 * 代码块展开状态：key -> 是否展开
 *
 * 用户手动点击过的代码块按点击结果展示；没点击过的按「流式输出中的默认展开、已完成的默认折叠」处理
 */
const toggledFiles = ref<Record<string, boolean>>({})

const isFileExpanded = (segment: CodeMessageSegment): boolean => {
  const state = toggledFiles.value[segment.key]
  return state === undefined ? !segment.closed : state
}

const toggleFile = (segment: CodeMessageSegment) => {
  toggledFiles.value = { ...toggledFiles.value, [segment.key]: !isFileExpanded(segment) }
}
</script>

<style scoped>
.message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;
}

/* 用户消息靠右 */
.message--user {
  flex-direction: row-reverse;
}

.message__avatar {
  flex: none;
  width: 32px;
  height: 32px;
  margin-top: 2px;
  border-radius: 50%;
  background: #f5f5f5;
  object-fit: contain;
}

.message__bubble {
  max-width: 86%;
  padding: 10px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fff;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.88);
  /* AI 消息是 markdown 渲染出的块级结构，换行由块级元素自己控制 */
  white-space: normal;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* 用户消息：浅蓝底突出，与 AI 消息区分；并保留输入内容里的换行 */
.message--user .message__bubble {
  background: #f0f7ff;
  border-color: #d6e8ff;
  white-space: pre-wrap;
}

/* 纯文本展示（AI 失败原因），保留换行 */
.message__plain {
  white-space: pre-wrap;
}

.message__bubble--error {
  border-color: #ffccc7;
  background: #fff2f0;
}

.message__thinking {
  color: rgba(0, 0, 0, 0.45);
}

/* markdown 文本段：收敛块级元素间距，与代码卡片保持统一的视觉节奏 */
.message__markdown {
  overflow-x: auto;
}

.message__markdown :deep(p) {
  margin: 0 0 8px;
}

.message__markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.message__markdown :deep(h1),
.message__markdown :deep(h2),
.message__markdown :deep(h3),
.message__markdown :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.message__markdown :deep(h1:first-child),
.message__markdown :deep(h2:first-child),
.message__markdown :deep(h3:first-child) {
  margin-top: 0;
}

.message__markdown :deep(ul),
.message__markdown :deep(ol) {
  margin: 0 0 8px;
  padding-left: 22px;
}

.message__markdown :deep(li) {
  margin: 2px 0;
}

/* 行内代码 */
.message__markdown :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
}

/* AI 直接在文本里输出的围栏代码块（背景由 highlight.js 主题提供） */
.message__markdown :deep(pre) {
  margin: 8px 0;
  border-radius: 8px;
  overflow: auto;
}

.message__markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

.message__markdown :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  border-left: 3px solid #f0f0f0;
  color: rgba(0, 0, 0, 0.65);
}

.message__markdown :deep(table) {
  margin: 8px 0;
  border-collapse: collapse;
}

.message__markdown :deep(th),
.message__markdown :deep(td) {
  padding: 4px 8px;
  border: 1px solid #f0f0f0;
  font-size: 13px;
}

.message__markdown :deep(a) {
  color: #1677ff;
}

.message__markdown :deep(img) {
  max-width: 100%;
}

.message__markdown :deep(hr) {
  margin: 12px 0;
  border: none;
  border-top: 1px solid #f0f0f0;
}

/* 流式输出光标 */
.message__cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #1677ff;
  animation: cursor-blink 1s steps(2, start) infinite;
}

@keyframes cursor-blink {
  to {
    visibility: hidden;
  }
}

/* 工具链进度（对应后端 [选择工具] / [工具调用] 协议行） */
.tool-step {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
}

.tool-step__icon {
  flex: none;
  color: #1677ff;
}

.tool-step--call .tool-step__icon {
  color: #52c41a;
}

.tool-step__label {
  flex: none;
  padding: 0 6px;
  border-radius: 4px;
  background: rgba(22, 119, 255, 0.1);
  color: #1677ff;
  font-size: 11px;
  line-height: 18px;
}

.tool-step--call .tool-step__label {
  background: rgba(82, 196, 26, 0.12);
  color: #389e0d;
}

.tool-step__text {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  color: rgba(0, 0, 0, 0.88);
  overflow-wrap: anywhere;
}

/* 生成的文件卡片 */
.file-item {
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.file-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  cursor: pointer;
}

.file-item__header:hover {
  background: #f5f5f5;
}

.file-item__icon {
  color: #1677ff;
}

.file-item__name {
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.file-item__size {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.file-item__toggle {
  margin-left: auto;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.file-item__code {
  max-height: 280px;
  margin: 0;
  padding: 0;
  overflow: auto;
  /* 背景与配色交给 highlight.js 主题（atom-one-dark），这里只控制尺寸与滚动 */
  background: #282c34;
  color: #abb2bf;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
}

/* 高亮后的代码：统一内边距，避免与外层 pre 叠加 */
.file-item__code :deep(code) {
  display: block;
  padding: 12px;
}

/* 消息落款（对应原型的「v1 已保存 | 1 天前」） */
.message__footer {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.message__footer-time {
  margin-left: auto;
}
</style>
