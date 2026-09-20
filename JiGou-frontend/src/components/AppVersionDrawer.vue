<template>
  <a-drawer :open="open" title="版本管理" :width="480" placement="right" @close="close">
    <!-- 顶部：当前版本状态 + 提交入口 -->
    <div class="version-toolbar">
      <div class="version-toolbar__info">
        <span class="version-toolbar__current">
          {{ currentVersion > 0 ? `当前版本 v${currentVersion}` : '尚未提交过版本' }}
        </span>
        <a-tag v-if="uncommitted" color="orange" class="version-toolbar__tag">有未提交修改</a-tag>
        <a-tag v-else-if="currentVersion > 0" class="version-toolbar__tag">与当前版本一致</a-tag>
      </div>
      <a-tooltip :title="commitTip">
        <a-button
          type="primary"
          :loading="committing"
          :disabled="!canCommit || !hasCode"
          @click="doCommit"
        >
          <template #icon><CloudUploadOutlined /></template>
          <span>提交版本</span>
        </a-button>
      </a-tooltip>
    </div>

    <a-spin :spinning="loading">
      <div v-if="versionList.length" class="version-list">
        <div v-for="item in versionList" :key="item.version" class="version-item">
          <div class="version-item__main">
            <div class="version-item__header">
              <span class="version-item__name">v{{ item.version }}</span>
              <a-tag v-if="item.current" color="green" class="version-item__tag">当前版本</a-tag>
            </div>
            <div class="version-item__time">
              <span>{{ formatDateTime(item.commitTime) }}</span>
              <span class="version-item__relative">
                {{ formatRelativeTime(item.commitTime) }}
              </span>
            </div>
          </div>
          <a-space :size="0" class="version-item__actions">
            <a-button type="link" size="small" @click="openCode(item.version)">查看代码</a-button>
            <a-button type="link" size="small" @click="openDiff(item.version)">对比</a-button>
            <a-tooltip :title="canCommit ? '' : '仅应用创建者可以回退版本'">
              <a-button
                type="link"
                size="small"
                danger
                :disabled="!canCommit"
                @click="doRollback(item.version)"
              >
                回退
              </a-button>
            </a-tooltip>
          </a-space>
        </div>
      </div>
      <a-empty
        v-else-if="!loading"
        :description="
          hasCode
            ? '还没有提交过版本，点击上方「提交版本」保存当前代码'
            : '还没有生成代码，先通过对话生成应用后再提交版本'
        "
      />
    </a-spin>

    <div class="version-footer">
      版本快照保存在服务端，最多保留最近 {{ MAX_VERSION_COUNT }} 个版本，超出后自动淘汰最旧的版本。
      回退只会覆盖工作区代码，历史版本不会被删除。
    </div>

    <!-- 查看某个版本的完整代码 -->
    <AppVersionCodeModal v-model:open="codeOpen" :app-id="appId" :version="codeVersion" />

    <!-- 任意两个版本的差异对比 -->
    <AppVersionDiffModal
      v-model:open="diffOpen"
      :app-id="appId"
      :versions="versionNumbers"
      :initial-base-version="diffBaseVersion"
      :initial-target-version="diffTargetVersion"
    />
  </a-drawer>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'
import { commitVersion, listVersions, rollbackVersion } from '@/api/appVersionController.ts'
import AppVersionCodeModal from '@/components/AppVersionCodeModal.vue'
import AppVersionDiffModal from '@/components/AppVersionDiffModal.vue'
import { MAX_VERSION_COUNT } from '@/constants/app'
import { asApiId } from '@/utils/apiId'
import { formatDateTime, formatRelativeTime } from '@/utils/time'

const props = withDefaults(
  defineProps<{
    /** 抽屉显隐（配合 v-model:open 使用） */
    open: boolean
    /** 应用 id（按字符串透传，避免雪花 id 精度丢失） */
    appId: string | number
    /** 是否为应用创建者：提交与回退仅创建者可用（与后端校验保持一致） */
    canCommit?: boolean
  }>(),
  {
    canCommit: false,
  },
)

const emit = defineEmits<{
  (e: 'update:open', open: boolean): void
  /** 回退成功：通知父组件用最新代码刷新预览 */
  (e: 'rolled-back', version: number): void
  /** 提交成功：通知父组件刷新应用信息（顶部版本标签） */
  (e: 'committed', version: number): void
}>()

const loading = ref(false)
const committing = ref(false)
/** 当前版本号（0 表示从未提交版本） */
const currentVersion = ref(0)
/** 工作区是否存在代码 */
const hasCode = ref(false)
/** 工作区是否存在未提交的修改 */
const uncommitted = ref(false)
/** 版本列表（后端按版本号降序返回） */
const versionList = ref<API.AppVersionVO[]>([])

/** 提交按钮的禁用原因提示 */
const commitTip = computed(() => {
  if (!props.canCommit) {
    return '仅应用创建者可以提交版本'
  }
  if (!hasCode.value) {
    return '当前还没有可提交的代码，请先通过对话生成应用'
  }
  return '把当前工作区的代码保存为一个新版本'
})

/** 版本号列表（降序），供对比弹窗的下拉选择使用 */
const versionNumbers = computed(() =>
  versionList.value.map((item) => item.version ?? 0).filter((version) => version > 0),
)

/**
 * 加载版本列表
 *
 * @returns 是否加载成功
 */
const loadVersions = async () => {
  loading.value = true
  try {
    const res = await listVersions({ appId: asApiId(props.appId) })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      currentVersion.value = data.currentVersion ?? 0
      hasCode.value = data.hasCode ?? false
      uncommitted.value = data.uncommitted ?? false
      versionList.value = data.versionList ?? []
      return true
    }
    // 非创建者且非管理员：后端返回无权限
    if (res.data.code === 40101) {
      message.warning('无权限查看该应用的版本历史')
      return false
    }
    message.error('获取版本列表失败：' + (res.data.message ?? '请稍后重试'))
    return false
  } catch {
    message.error('获取版本列表失败，请检查网络后重试')
    return false
  } finally {
    loading.value = false
  }
}

/** 真正执行提交（工作区与当前版本内容一致时，外部会先二次确认） */
const submitCommit = async () => {
  committing.value = true
  try {
    const res = await commitVersion({ appId: asApiId(props.appId) })
    if (res.data.code === 0) {
      const newVersion = res.data.data ?? 0
      message.success(`提交成功，已生成 v${newVersion}`)
      await loadVersions()
      emit('committed', newVersion)
      return
    }
    message.error('提交版本失败：' + (res.data.message ?? '请稍后重试'))
  } catch {
    message.error('提交版本失败，请检查网络后重试')
  } finally {
    committing.value = false
  }
}

/** 提交版本：内容与当前版本一致时二次确认，避免产生完全重复的版本 */
const doCommit = async () => {
  if (!props.canCommit) {
    message.warning('仅应用创建者可以提交版本')
    return
  }
  if (!hasCode.value) {
    message.warning('当前还没有可提交的代码，请先通过对话生成应用')
    return
  }
  if (!uncommitted.value && currentVersion.value > 0) {
    Modal.confirm({
      title: '当前代码与最新版本内容一致，仍要提交新版本吗？',
      content: `提交后会生成 v${currentVersion.value + 1}，两个版本内容完全相同。`,
      okText: '仍然提交',
      cancelText: '取消',
      onOk: submitCommit,
    })
    return
  }
  await submitCommit()
}

/**
 * 回退到指定版本（二次确认）
 *
 * @param version 目标版本号
 */
const doRollback = async (version?: number) => {
  if (!version) {
    return
  }
  if (!props.canCommit) {
    message.warning('仅应用创建者可以回退版本')
    return
  }
  Modal.confirm({
    title: `确定回退到 v${version} 吗？`,
    content: '回退会用该版本的代码覆盖当前工作区，历史版本仍会保留，回退后可继续提交新版本。',
    okText: '回退',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await rollbackVersion({ appId: asApiId(props.appId), version })
        if (res.data.code === 0) {
          message.success(`已回退到 v${version}`)
          await loadVersions()
          // 工作区代码已被替换，通知父组件刷新预览
          emit('rolled-back', version)
          return
        }
        message.error('回退失败：' + (res.data.message ?? '请稍后重试'))
      } catch {
        message.error('回退失败，请检查网络后重试')
      }
    },
  })
}

/* ------------------------------ 查看代码 / 版本对比 ------------------------------ */

const codeOpen = ref(false)
const codeVersion = ref(0)
const diffOpen = ref(false)
/** 对比弹窗默认的基准版本（0 表示空版本） */
const diffBaseVersion = ref(0)
/** 对比弹窗默认的对比版本 */
const diffTargetVersion = ref(0)

/**
 * 查看某个版本的完整代码
 *
 * @param version 版本号
 */
const openCode = (version?: number) => {
  if (!version) {
    return
  }
  codeVersion.value = version
  codeOpen.value = true
}

/**
 * 对比某个版本与它上一个版本的差异
 *
 * 列表按版本号降序排列，当前项的下一个元素即为更早的版本；
 * 首个版本没有上一个版本，基准取「空版本」，即查看该版本新增的全部内容
 *
 * @param version 版本号
 */
const openDiff = (version?: number) => {
  if (!version) {
    return
  }
  const index = versionList.value.findIndex((item) => item.version === version)
  diffBaseVersion.value = versionList.value[index + 1]?.version ?? 0
  diffTargetVersion.value = version
  diffOpen.value = true
}

// 打开抽屉时加载版本列表（每次打开都重新拉取，保证看到最新状态）
watch(
  () => props.open,
  (open) => {
    if (open) {
      loadVersions()
    }
  },
)

const close = () => {
  emit('update:open', false)
}
</script>

<style scoped>
/* 顶部：当前版本状态 + 提交按钮 */
.version-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 16px;
}

.version-toolbar__info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.version-toolbar__current {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.version-toolbar__tag {
  align-self: flex-start;
  margin-inline-end: 0;
}

/* 版本列表 */
.version-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #eef0f4;
  border-radius: 10px;
}

.version-item:hover {
  background: #fafcff;
  border-color: #d6e8ff;
}

.version-item__main {
  min-width: 0;
}

.version-item__header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.version-item__name {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.version-item__tag {
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 16px;
}

.version-item__time {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.version-item__relative {
  margin-left: 6px;
}

.version-item__actions {
  flex: none;
}

.version-footer {
  margin-top: 16px;
  font-size: 12px;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.45);
}
</style>
