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
          {{ app.userVO?.userName || app.userVO?.userAccount || '-' }}
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
import { CODE_GEN_TYPE_LABEL, DEFAULT_APP_PRIORITY, GOOD_APP_PRIORITY } from '@/constants/app'
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
</style>
