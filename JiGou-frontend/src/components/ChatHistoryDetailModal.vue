<template>
  <a-modal
    :open="open"
    title="对话历史详情"
    :width="760"
    :footer="null"
    centered
    @cancel="emit('update:open', false)"
  >
    <a-descriptions
      v-if="chatHistory"
      :column="1"
      bordered
      size="small"
      class="detail-descriptions"
    >
      <a-descriptions-item label="消息 id">{{ chatHistory.id ?? '-' }}</a-descriptions-item>
      <a-descriptions-item label="消息类型">
        <a-tag :color="CHAT_MESSAGE_TYPE_TAG_COLOR[chatHistory.messageType ?? ''] ?? 'default'">
          {{
            CHAT_MESSAGE_TYPE_LABEL[chatHistory.messageType ?? ''] ?? chatHistory.messageType ?? '-'
          }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="应用名称">{{ chatHistory.appName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="应用 id">{{ chatHistory.appId ?? '-' }}</a-descriptions-item>
      <a-descriptions-item label="创建用户">
        <span class="detail-user">
          <a-avatar
            :size="22"
            class="detail-user__avatar"
            :src="chatHistory.userVO?.userAvatar || undefined"
          >
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span>{{ chatHistory.userVO?.userName || chatHistory.userVO?.userAccount || '-' }}</span>
        </span>
      </a-descriptions-item>
      <a-descriptions-item label="创建时间">{{
        formatDateTime(chatHistory.createTime)
      }}</a-descriptions-item>
      <a-descriptions-item label="消息内容">
        <div class="detail-message">{{ chatHistory.message || '-' }}</div>
      </a-descriptions-item>
    </a-descriptions>
    <a-empty v-else description="暂无对话历史信息" />
  </a-modal>
</template>

<script lang="ts" setup>
import { UserOutlined } from '@ant-design/icons-vue'
import { CHAT_MESSAGE_TYPE_LABEL, CHAT_MESSAGE_TYPE_TAG_COLOR } from '@/constants/chatHistory'
import { formatDateTime } from '@/utils/time'

withDefaults(
  defineProps<{
    /** 弹窗显隐（配合 v-model:open 使用） */
    open: boolean
    /** 对话历史信息 */
    chatHistory?: API.ChatHistoryVO | null
  }>(),
  {
    chatHistory: null,
  },
)

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
}>()
</script>

<style scoped>
.detail-descriptions :deep(.ant-descriptions-item-label) {
  width: 140px;
  white-space: nowrap;
}

/* AI 回复可能很长（包含生成的代码），限制高度滚动展示 */
.detail-message {
  max-height: 260px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 创建用户：头像 + 昵称 */
.detail-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.detail-user__avatar {
  flex: none;
  background: var(--brand-color);
}
</style>
