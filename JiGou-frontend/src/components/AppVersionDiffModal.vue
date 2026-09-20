<template>
  <a-modal
    :open="open"
    title="版本对比"
    :width="1000"
    :footer="null"
    centered
    @cancel="close"
  >
    <div class="version-diff">
      <!-- 工具条：可自由选择「基准版本」与「对比版本」 -->
      <div class="version-diff__toolbar">
        <span class="version-diff__label">基准版本</span>
        <a-select v-model:value="baseVersion" :options="baseOptions" class="version-diff__select" />
        <ArrowRightOutlined class="version-diff__arrow" />
        <span class="version-diff__label">对比版本</span>
        <a-select
          v-model:value="targetVersion"
          :options="targetOptions"
          class="version-diff__select"
        />
        <span class="version-diff__summary">{{ summaryText }}</span>
      </div>

      <a-spin :spinning="loading">
        <div v-if="versionDiff.files.length" class="version-diff__body">
          <!-- 左侧：变更文件列表 -->
          <div class="version-diff__files">
            <div
              v-for="file in versionDiff.files"
              :key="file.fileName"
              class="diff-file"
              :class="{ 'diff-file--active': file.fileName === activeFileName }"
              @click="activeFileName = file.fileName"
            >
              <FileOutlined class="diff-file__icon" />
              <span class="diff-file__name">{{ file.fileName }}</span>
              <a-tag :color="FILE_DIFF_STATUS_COLOR[file.status]" class="diff-file__tag">
                {{ FILE_DIFF_STATUS_LABEL[file.status] }}
              </a-tag>
              <span class="diff-file__stat">
                <span v-if="file.addedCount" class="diff-file__stat-add">+{{ file.addedCount }}</span>
                <span v-if="file.deletedCount" class="diff-file__stat-del">
                  -{{ file.deletedCount }}
                </span>
              </span>
            </div>
          </div>

          <!-- 右侧：选中文件的行级差异 -->
          <div class="version-diff__content">
            <div class="version-diff__file-title">
              <span>{{ activeFileDiff?.fileName }}</span>
              <span class="version-diff__file-stat">
                <span v-if="activeFileDiff?.addedCount" class="diff-file__stat-add">
                  +{{ activeFileDiff?.addedCount }}
                </span>
                <span v-if="activeFileDiff?.deletedCount" class="diff-file__stat-del">
                  -{{ activeFileDiff?.deletedCount }}
                </span>
              </span>
            </div>
            <CodeDiffViewer
              class="version-diff__viewer"
              :lines="activeFileDiff?.lines ?? []"
              empty-text="该文件无差异"
            />
          </div>
        </div>
        <a-empty v-else-if="!loading" description="两个版本内容完全一致，没有差异" />
        <div v-else class="version-diff__placeholder" />
      </a-spin>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowRightOutlined, FileOutlined } from '@ant-design/icons-vue'
import { getVersionDetail } from '@/api/appVersionController.ts'
import CodeDiffViewer from '@/components/CodeDiffViewer.vue'
import { asApiId } from '@/utils/apiId'
import {
  FILE_DIFF_STATUS_COLOR,
  FILE_DIFF_STATUS_LABEL,
  diffFileMap,
  normalizeFileMap,
} from '@/utils/lineDiff'

/** 空版本：表示「还没有任何代码」，用于把首个版本与空内容对比 */
const EMPTY_VERSION = 0

const props = withDefaults(
  defineProps<{
    /** 弹窗显隐（配合 v-model:open 使用） */
    open: boolean
    /** 应用 id（按字符串透传，避免雪花 id 精度丢失） */
    appId: string | number
    /** 可选版本号列表（降序），用于下拉选择 */
    versions?: number[]
    /** 打开时默认的基准版本（0 表示空版本） */
    initialBaseVersion?: number
    /** 打开时默认的对比版本 */
    initialTargetVersion?: number
  }>(),
  {
    versions: () => [],
    initialBaseVersion: EMPTY_VERSION,
    initialTargetVersion: EMPTY_VERSION,
  },
)

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
}>()

const loading = ref(false)
/** 基准版本号（左侧） */
const baseVersion = ref(EMPTY_VERSION)
/** 对比版本号（右侧） */
const targetVersion = ref(EMPTY_VERSION)
const baseFileMap = ref<Record<string, string>>({})
const targetFileMap = ref<Record<string, string>>({})
/** 当前在右侧展示差异的文件 */
const activeFileName = ref('')

/** 版本号列表 -> 下拉选项（额外提供「空版本」用于查看首个版本的完整内容） */
const toOptions = (versions: number[]) => [
  { label: '空版本（无代码）', value: EMPTY_VERSION },
  ...versions.map((version) => ({ label: `v${version}`, value: version })),
]
const baseOptions = computed(() => toOptions(props.versions ?? []))
const targetOptions = computed(() => toOptions(props.versions ?? []))

/** 版本级对比结果：数据变化时自动重算 */
const versionDiff = computed(() => diffFileMap(baseFileMap.value, targetFileMap.value))
const activeFileDiff = computed(() =>
  versionDiff.value.files.find((file) => file.fileName === activeFileName.value),
)
/** 汇总文案：x 个文件变更，+a / -d */
const summaryText = computed(() => {
  const diff = versionDiff.value
  if (!diff.files.length) {
    return '无差异'
  }
  return `${diff.files.length} 个文件变更，+${diff.addedCount} / -${diff.deletedCount}`
})

/**
 * 拉取某个版本的文件内容
 *
 * @param version 版本号，EMPTY_VERSION 表示空内容（该版本之前的「无代码」状态）
 */
const loadVersionFileMap = async (version: number) => {
  if (version === EMPTY_VERSION) {
    return {}
  }
  const res = await getVersionDetail({ appId: asApiId(props.appId), version })
  if (res.data.code === 0) {
    return normalizeFileMap(res.data.data?.fileMap)
  }
  message.error(`获取版本 v${version} 内容失败：` + (res.data.message ?? '请稍后重试'))
  return {}
}

/** 加载两个版本的内容并对比 */
const loadDiff = async () => {
  loading.value = true
  try {
    const [base, target] = await Promise.all([
      loadVersionFileMap(baseVersion.value),
      loadVersionFileMap(targetVersion.value),
    ])
    baseFileMap.value = base
    targetFileMap.value = target
    activeFileName.value = ''
  } catch {
    message.error('版本对比失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}

// 打开弹窗时同步父组件传入的默认版本：
// 版本对发生变化由下方 watcher 触发加载；若版本对未变（重复打开同一组版本）则直接重新加载
watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    const base = props.initialBaseVersion ?? EMPTY_VERSION
    const target = props.initialTargetVersion ?? EMPTY_VERSION
    if (baseVersion.value === base && targetVersion.value === target) {
      await loadDiff()
      return
    }
    baseVersion.value = base
    targetVersion.value = target
  },
)

// 用户切换基准 / 对比版本时重新加载
watch([baseVersion, targetVersion], () => {
  if (props.open) {
    loadDiff()
  }
})

watch(versionDiff, (diff) => {
  // 选中的文件在新一轮对比中不存在（如切换版本后被过滤掉）时，回退到第一个变更文件
  if (!diff.files.some((file) => file.fileName === activeFileName.value)) {
    activeFileName.value = diff.files[0]?.fileName ?? ''
  }
})

const close = () => {
  emit('update:open', false)
}
</script>

<style scoped>
/* 工具条：基准版本 -> 对比版本 选择 */
.version-diff__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.version-diff__label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}

.version-diff__select {
  width: 168px;
}

.version-diff__arrow {
  color: rgba(0, 0, 0, 0.45);
}

/* 汇总统计靠右展示 */
.version-diff__summary {
  margin-left: auto;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

/* 左侧文件列表 + 右侧差异内容 */
.version-diff__body {
  display: flex;
  gap: 12px;
  height: 60vh;
  min-height: 320px;
}

.version-diff__files {
  flex: none;
  width: 260px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid #eef0f4;
  border-radius: 8px;
}

.diff-file {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.diff-file:hover {
  background: #f5f7fc;
}

.diff-file--active {
  background: #f0f7ff;
}

.diff-file__icon {
  flex: none;
  color: var(--brand-color);
}

.diff-file__name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.88);
}

/* 覆盖 antd 默认的标签右外边距，避免挤压文件名 */
.diff-file__tag {
  flex: none;
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 16px;
}

.diff-file__stat {
  flex: none;
  font-size: 12px;
}

.diff-file__stat-add {
  color: #1a7f37;
}

.diff-file__stat-del {
  margin-left: 6px;
  color: #cf222e;
}

.version-diff__content {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-width: 0;
}

.version-diff__file-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.version-diff__file-stat {
  font-size: 12px;
  font-weight: 400;
}

.version-diff__viewer {
  flex: 1;
  min-height: 0;
}

.version-diff__placeholder {
  height: 60vh;
}
</style>
