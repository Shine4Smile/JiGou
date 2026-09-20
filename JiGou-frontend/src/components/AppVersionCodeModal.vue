<template>
  <a-modal
    :open="open"
    :title="`v${version} 代码内容`"
    :width="900"
    :footer="null"
    centered
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <div v-if="fileNames.length" class="version-code">
        <!-- 左侧：文件列表（含行数） -->
        <div class="version-code__files">
          <div
            v-for="fileName in fileNames"
            :key="fileName"
            class="code-file"
            :class="{ 'code-file--active': fileName === activeFileName }"
            @click="activeFileName = fileName"
          >
            <FileOutlined class="code-file__icon" />
            <span class="code-file__name">{{ fileName }}</span>
            <span class="code-file__size">{{ lineCount(fileName) }} 行</span>
          </div>
        </div>

        <!-- 右侧：文件内容 -->
        <div class="version-code__content">
          <div class="version-code__title">
            <span>{{ activeFileName }}</span>
            <span class="version-code__time">提交于 {{ formatDateTime(commitTime) }}</span>
          </div>
          <pre class="version-code__pre">{{ activeContent }}</pre>
        </div>
      </div>
      <a-empty v-else-if="!loading" description="该版本没有代码文件" />
      <div v-else class="version-code__placeholder" />
    </a-spin>
  </a-modal>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { FileOutlined } from '@ant-design/icons-vue'
import { getVersionDetail } from '@/api/appVersionController.ts'
import { asApiId } from '@/utils/apiId'
import { normalizeFileMap, splitLines } from '@/utils/lineDiff'
import { formatDateTime } from '@/utils/time'

const props = withDefaults(
  defineProps<{
    /** 弹窗显隐（配合 v-model:open 使用） */
    open: boolean
    /** 应用 id（按字符串透传，避免雪花 id 精度丢失） */
    appId: string | number
    /** 要查看的版本号 */
    version?: number
  }>(),
  {
    version: 0,
  },
)

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
}>()

const loading = ref(false)
/** 文件名 -> 文件内容 */
const fileMap = ref<Record<string, string>>({})
const commitTime = ref('')
const activeFileName = ref('')

const fileNames = computed(() => Object.keys(fileMap.value).sort())
const activeContent = computed(() => fileMap.value[activeFileName.value] ?? '')
const lineCount = (fileName: string) => splitLines(fileMap.value[fileName]).length

/** 拉取版本内容 */
const loadVersion = async () => {
  if (!props.version) {
    fileMap.value = {}
    return
  }
  loading.value = true
  try {
    const res = await getVersionDetail({ appId: asApiId(props.appId), version: props.version })
    if (res.data.code === 0 && res.data.data) {
      fileMap.value = normalizeFileMap(res.data.data.fileMap)
      commitTime.value = res.data.data.commitTime ?? ''
      activeFileName.value = Object.keys(fileMap.value).sort()[0] ?? ''
      return
    }
    fileMap.value = {}
    message.error(`获取版本 v${props.version} 内容失败：` + (res.data.message ?? '请稍后重试'))
  } catch {
    fileMap.value = {}
    message.error('获取版本内容失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}

// 打开弹窗（或切换版本号）时加载内容
watch([() => props.open, () => props.version], ([open]) => {
  if (open) {
    loadVersion()
  }
})

const close = () => {
  emit('update:open', false)
}
</script>

<style scoped>
/* 左侧文件列表 + 右侧代码内容 */
.version-code {
  display: flex;
  gap: 12px;
  height: 60vh;
  min-height: 320px;
}

.version-code__files {
  flex: none;
  width: 240px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid #eef0f4;
  border-radius: 8px;
}

.code-file {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.code-file:hover {
  background: #f5f7fc;
}

.code-file--active {
  background: #f0f7ff;
}

.code-file__icon {
  flex: none;
  color: var(--brand-color);
}

.code-file__name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: rgba(0, 0, 0, 0.88);
}

.code-file__size {
  flex: none;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.version-code__content {
  display: flex;
  flex-direction: column;
  flex: 1 1 0;
  min-width: 0;
}

.version-code__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.version-code__time {
  font-size: 12px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.45);
}

/* 代码区沿用对话页代码块的深色风格 */
.version-code__pre {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #1f1f1f;
  color: #e6e6e6;
  border-radius: 8px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
}

.version-code__placeholder {
  height: 60vh;
}
</style>
