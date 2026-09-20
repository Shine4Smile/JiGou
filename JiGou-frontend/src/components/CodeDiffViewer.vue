<template>
  <div class="diff-viewer">
    <div v-if="!lines.length" class="diff-viewer__empty">
      <a-empty :description="emptyText" />
    </div>
    <div v-else class="diff-viewer__body">
      <div
        v-for="(line, index) in lines"
        :key="index"
        class="diff-line"
        :class="`diff-line--${line.type}`"
      >
        <span class="diff-line__no">{{ line.oldLineNumber ?? '' }}</span>
        <span class="diff-line__no">{{ line.newLineNumber ?? '' }}</span>
        <span class="diff-line__sign">{{ DIFF_LINE_SIGN[line.type] }}</span>
        <span class="diff-line__content">{{ line.content }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { DiffLine, DiffLineType } from '@/utils/lineDiff'

withDefaults(
  defineProps<{
    /** 行级差异序列（由 utils/lineDiff 计算得出） */
    lines: DiffLine[]
    /** 无差异时的占位文案 */
    emptyText?: string
  }>(),
  {
    emptyText: '暂无差异',
  },
)

/** 差异类型 -> 行首符号（与 Git 统一视图一致） */
const DIFF_LINE_SIGN: Record<DiffLineType, string> = {
  add: '+',
  del: '-',
  context: ' ',
}
</script>

<style scoped>
.diff-viewer {
  min-height: 0;
  overflow: auto;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 8px;
}

.diff-viewer__empty {
  padding: 40px 0;
}

.diff-viewer__body {
  min-width: max-content;
}

/* 单行差异：左侧固定两列行号（旧 / 新），右侧展示符号与代码 */
.diff-line {
  display: flex;
  align-items: flex-start;
  min-height: 22px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.88);
  white-space: pre;
}

.diff-line__no {
  flex: none;
  width: 44px;
  padding-right: 8px;
  text-align: right;
  color: rgba(0, 0, 0, 0.35);
  user-select: none;
}

.diff-line__sign {
  flex: none;
  width: 18px;
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  user-select: none;
}

.diff-line__content {
  flex: 1;
  padding-right: 12px;
}

/* 新增行：绿色底 */
.diff-line--add {
  background: #e6ffec;
}

.diff-line--add .diff-line__sign {
  color: #1a7f37;
}

/* 删除行：红色底 */
.diff-line--del {
  background: #ffebe9;
}

.diff-line--del .diff-line__sign {
  color: #cf222e;
}

.diff-line--context:hover {
  background: #fafafa;
}
</style>
