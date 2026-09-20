<template>
  <a-modal
    :open="open"
    title="应用详情"
    :width="760"
    :footer="null"
    centered
    @cancel="emit('update:open', false)"
  >
    <a-spin :spinning="loading">
      <a-descriptions v-if="app" :column="1" bordered size="small" class="detail-descriptions">
        <a-descriptions-item label="应用名称">{{ app.appName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="应用 id">{{ app.id ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="应用封面">
          <a-image v-if="app.cover" :src="app.cover" :width="160" />
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="初始提示词">
          <div class="detail-prompt">{{ app.initPrompt || '-' }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="代码生成类型">
          {{ CODE_GEN_TYPE_LABEL[app.codeGenType ?? ''] ?? '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="可见范围">
          <a-space :size="8">
            <a-tag :color="APP_VISIBILITY_TAG_COLOR[app.visibility ?? ''] ?? 'default'">
              {{ APP_VISIBILITY_LABEL[app.visibility ?? ''] ?? '私有' }}
            </a-tag>
            <span class="detail-tip">{{ getVisibilityTip(app.visibility) }}</span>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="优先级">
          <a-tag :color="app.priority === GOOD_APP_PRIORITY ? 'gold' : 'default'">
            {{
              app.priority === GOOD_APP_PRIORITY
                ? `精选应用（${app.priority}）`
                : `普通应用（${app.priority ?? DEFAULT_APP_PRIORITY}）`
            }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="部署标识">{{ app.deployKey || '未部署' }}</a-descriptions-item>
        <a-descriptions-item label="部署时间">{{
          formatDateTime(app.deployedTime)
        }}</a-descriptions-item>
        <a-descriptions-item label="创建用户">
          <span class="detail-user">
            <a-avatar
              :size="22"
              class="detail-user__avatar"
              :src="app.userVO?.userAvatar || undefined"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span>{{ app.userVO?.userName || app.userVO?.userAccount || '-' }}</span>
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{
          formatDateTime(app.createTime)
        }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{
          formatDateTime(app.updateTime)
        }}</a-descriptions-item>
      </a-descriptions>
      <a-empty v-else description="暂无应用信息" />
    </a-spin>
  </a-modal>
</template>

<script lang="ts" setup>
import { UserOutlined } from '@ant-design/icons-vue'
import {
  APP_VISIBILITY_LABEL,
  APP_VISIBILITY_TAG_COLOR,
  CODE_GEN_TYPE_LABEL,
  DEFAULT_APP_PRIORITY,
  GOOD_APP_PRIORITY,
  getVisibilityTip,
} from '@/constants/app'
import { formatDateTime } from '@/utils/time'

withDefaults(
  defineProps<{
    /** 弹窗显隐（配合 v-model:open 使用） */
    open: boolean
    /** 应用信息 */
    app?: API.AppVO | null
    /** 详情加载中（管理员端会再请求一次详情接口） */
    loading?: boolean
  }>(),
  {
    app: null,
    loading: false,
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

/* 初始提示词可能很长，限制高度滚动展示 */
.detail-prompt {
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 可见范围说明文案 */
.detail-tip {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
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
