<template>
  <!-- 文本较长：单行省略，hover 通过 Tooltip 展示完整内容 -->
  <a-tooltip
    v-if="showTooltip"
    :title="content"
    placement="topLeft"
    :overlay-style="{ maxWidth: '420px', wordBreak: 'break-word' }"
  >
    <span class="ellipsis-text">{{ content }}</span>
  </a-tooltip>
  <!-- 文本较短或为空：直接展示，空值统一用占位符 -->
  <span v-else class="ellipsis-text" :class="{ 'ellipsis-text--empty': isEmpty }">
    {{ isEmpty ? placeholder : content }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 原始文本 */
    text?: string | null
    /** 文本为空时的占位内容 */
    placeholder?: string
    /** 超过该字符数时启用 Tooltip（中英文混排下的经验阈值） */
    tooltipThreshold?: number
  }>(),
  {
    text: '',
    placeholder: '-',
    tooltipThreshold: 16,
  },
)

const content = computed(() => props.text ?? '')
const isEmpty = computed(() => content.value.trim() === '')
const showTooltip = computed(() => !isEmpty.value && content.value.length > props.tooltipThreshold)
</script>

<style scoped>
/* 单行省略：配合表格 table-layout: fixed，保证长文本不会撑高行、撑宽列 */
.ellipsis-text {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ellipsis-text--empty {
  color: rgba(0, 0, 0, 0.25);
}
</style>
