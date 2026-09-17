<template>
  <div class="message" :class="`message--${role}`">
    <!-- AI 消息：左侧展示头像 -->
    <img v-if="isAi" class="message__avatar" :src="siteConfig.logo" alt="AI" />

    <div class="message__bubble" :class="{ 'message__bubble--error': error }">
      <!-- 用户消息：纯文本展示 -->
      <template v-if="!isAi">{{ content }}</template>

      <!-- AI 消息：流式过程中展示分片文本，流结束后把代码块解析成文件卡片 -->
      <template v-else>
        <div v-if="parsed.text" class="message__text">{{ parsed.text }}</div>

        <div v-if="streaming && !content" class="message__thinking">正在生成代码…</div>

        <div v-for="file in parsed.files" :key="file.name" class="file-item">
          <div class="file-item__header" @click="toggleFile(file.name)">
            <FileOutlined class="file-item__icon" />
            <span class="file-item__name">{{ file.name }}</span>
            <span class="file-item__size">{{ file.size }}</span>
            <UpOutlined v-if="isFileExpanded(file.name)" class="file-item__toggle" />
            <DownOutlined v-else class="file-item__toggle" />
          </div>
          <pre v-if="isFileExpanded(file.name)" class="file-item__code">{{ file.code }}</pre>
        </div>

        <!-- 流式输出光标 -->
        <span v-if="streaming" class="message__cursor" />

        <!-- 生成完成后的落款（对应原型中「v1 已保存」的位置） -->
        <div v-if="!streaming" class="message__footer">
          <template v-if="error">
            <CloseCircleOutlined />
            <span>生成失败，请重试</span>
          </template>
          <template v-else-if="parsed.files.length">
            <CheckCircleOutlined />
            <span>已生成 {{ parsed.files.length }} 个文件并保存到应用目录</span>
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
  FileOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import { siteConfig } from '@/layouts/config'
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

/** 代码语言 -> 文件名，与后端 MultiFileCodeFileSaverTemplate 落盘的文件名保持一致 */
const CODE_FILE_NAME: Record<string, string> = {
  html: 'index.html',
  css: 'style.css',
  js: 'script.js',
  javascript: 'script.js',
}

type ParsedFile = {
  name: string
  code: string
  size: string
}

/**
 * 解析 AI 回复内容
 *
 * 1. 流式过程中只展示原始文本（AI 的文字说明 + 代码块）；
 * 2. 流结束后把 ```html / ```css / ```javascript 代码块提取为文件卡片，
 *    剩余文字作为说明展示（与后端 MultiFileCodeParser 的提取规则一致）
 */
const parsed = computed<{ text: string; files: ParsedFile[] }>(() => {
  const content = props.content ?? ''
  if (!isAi.value || props.streaming || !content) {
    return { text: content, files: [] }
  }
  const pattern = /```(\w*)[ \t]*\r?\n([\s\S]*?)```/g
  const matches = [...content.matchAll(pattern)]
  if (matches.length === 0) {
    return { text: content.trim(), files: [] }
  }
  const files = matches.map((match) => {
    const lang = (match[1] || '').toLowerCase()
    const code = (match[2] ?? '').trim()
    return {
      name: CODE_FILE_NAME[lang] ?? `code.${lang || 'txt'}`,
      code,
      size: `${code.split('\n').length} 行`,
    }
  })
  // 去掉代码块后剩余的说明文字
  const text = content.replace(pattern, '').trim()
  return { text, files }
})

// 代码块展开状态
const expandedFiles = ref<string[]>([])

const isFileExpanded = (name: string) => expandedFiles.value.includes(name)

const toggleFile = (name: string) => {
  expandedFiles.value = isFileExpanded(name)
    ? expandedFiles.value.filter((item) => item !== name)
    : [...expandedFiles.value, name]
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
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* 用户消息：浅蓝底突出，与 AI 消息区分 */
.message--user .message__bubble {
  background: #f0f7ff;
  border-color: #d6e8ff;
}

.message__bubble--error {
  border-color: #ffccc7;
  background: #fff2f0;
}

.message__thinking {
  color: rgba(0, 0, 0, 0.45);
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
  max-height: 260px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #1f1f1f;
  color: #e6e6e6;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
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
