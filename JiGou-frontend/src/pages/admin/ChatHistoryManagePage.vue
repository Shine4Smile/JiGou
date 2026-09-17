<template>
  <div id="chatHistoryManagePage">
    <!-- 搜索区：支持按应用、消息内容、消息类型、创建用户与创建时间范围查询 -->
    <a-card class="app-panel search-panel" :bordered="false">
      <a-form layout="inline" :model="searchParams" class="search-form" @finish="doSearch">
        <a-form-item label="应用 id">
          <a-input v-model:value="searchAppId" placeholder="请输入应用 id" allow-clear />
        </a-form-item>
        <a-form-item label="消息内容">
          <a-input v-model:value="searchParams.message" placeholder="请输入消息内容" allow-clear />
        </a-form-item>
        <a-form-item label="消息类型">
          <a-select
            v-model:value="searchParams.messageType"
            :options="CHAT_MESSAGE_TYPE_OPTIONS"
            placeholder="全部"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <!--        <a-form-item label="创建用户 id">-->
        <!--          <a-input v-model:value="searchUserId" placeholder="请输入创建用户 id" allow-clear />-->
        <!--        </a-form-item>-->
        <a-form-item label="创建时间">
          <a-range-picker
            v-model:value="searchTimeRange"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            :placeholder="['开始时间', '结束时间']"
          />
        </a-form-item>
        <a-form-item>
          <a-space :size="8">
            <a-button type="primary" html-type="submit" :loading="loading">
              <template #icon><SearchOutlined /></template>
              <span>搜索</span>
            </a-button>
            <a-button @click="doReset">
              <template #icon><ReloadOutlined /></template>
              <span>重置</span>
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 列表区 -->
    <a-card class="app-panel table-panel" :bordered="false">
      <template #title>
        <span class="panel-title">对话历史列表</span>
        <span class="panel-subtitle">管理员可查看全部应用的对话历史，按创建时间降序排列</span>
      </template>
      <template #extra>
        <a-button :loading="loading" @click="fetchData">
          <template #icon><ReloadOutlined /></template>
          <span>刷新</span>
        </a-button>
      </template>

      <a-table
        row-key="id"
        table-layout="fixed"
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        :loading="loading"
        :scroll="{ x: 1400 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 消息内容：单行省略，hover 展示完整内容，完整内容见「详情」 -->
          <template v-if="column.dataIndex === 'message'">
            <EllipsisText :text="record.message" />
          </template>
          <template v-else-if="column.dataIndex === 'messageType'">
            <a-tag :color="CHAT_MESSAGE_TYPE_TAG_COLOR[record.messageType ?? ''] ?? 'default'">
              {{ CHAT_MESSAGE_TYPE_LABEL[record.messageType ?? ''] ?? record.messageType ?? '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'appName'">
            <EllipsisText :text="record.appName || `应用 ${record.appId}`" />
          </template>
          <template v-else-if="column.dataIndex === 'user'">
            <EllipsisText :text="record.userVO?.userName || record.userVO?.userAccount" />
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" size="small" @click="openDetail(record)">
                <template #icon><EyeOutlined /></template>
                <span>详情</span>
              </a-button>
              <a-button
                type="link"
                size="small"
                :disabled="!record.appId"
                @click="goAppChat(record)"
              >
                <template #icon><MessageOutlined /></template>
                <span>查看对话</span>
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 对话历史详情弹窗 -->
    <ChatHistoryDetailModal v-model:open="detailOpen" :chat-history="detailChatHistory" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Dayjs } from 'dayjs'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { EyeOutlined, MessageOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { listChatHistoryVoByPage } from '@/api/chatHistoryController.ts'
import ChatHistoryDetailModal from '@/components/ChatHistoryDetailModal.vue'
import EllipsisText from '@/components/EllipsisText.vue'
import {
  CHAT_MESSAGE_TYPE_LABEL,
  CHAT_MESSAGE_TYPE_OPTIONS,
  CHAT_MESSAGE_TYPE_TAG_COLOR,
} from '@/constants/chatHistory'
import { asApiId } from '@/utils/apiId'
import { formatDateTime } from '@/utils/time'

const router = useRouter()

/* ------------------------------ 表格配置 ------------------------------ */

/** 列宽策略与应用管理页一致：固定列宽 + table-layout=fixed，长文本单行省略 */
const columns: TableColumnsType = [
  { title: '消息内容', dataIndex: 'message', width: 360 },
  { title: '消息类型', dataIndex: 'messageType', width: 110, align: 'center' },
  { title: '应用名称', dataIndex: 'appName', width: 200 },
  { title: '创建用户', dataIndex: 'user', width: 160 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 220, align: 'center', fixed: 'right' },
]

/* ------------------------------ 列表数据 ------------------------------ */

const data = ref<API.ChatHistoryVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件（管理员接口不限每页数量，这里默认 10 条，可切换更大页长）
const searchParams = reactive<API.ChatHistoryQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// id / userId 均为雪花 id（后端以字符串返回），输入框按字符串收集，查询时原样透传
const searchAppId = ref<string>('')
const searchUserId = ref<string>('')
// 创建时间范围（对应后端 createTimeStart / createTimeEnd）
const searchTimeRange = ref<[Dayjs, Dayjs]>()

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listChatHistoryVoByPage({
      ...searchParams,
      appId: searchAppId.value ? asApiId(searchAppId.value.trim()) : undefined,
      userId: searchUserId.value ? asApiId(searchUserId.value.trim()) : undefined,
      createTimeStart: searchTimeRange.value?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
      createTimeEnd: searchTimeRange.value?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
    })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('获取数据失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化处理
const doTableChange = (page: TablePaginationConfig) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索：重置页码后重新查询
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

// 重置搜索条件
const doReset = () => {
  searchParams.message = undefined
  searchParams.messageType = undefined
  searchAppId.value = ''
  searchUserId.value = ''
  searchTimeRange.value = undefined
  searchParams.pageNum = 1
  fetchData()
}

/* ------------------------------ 详情 / 查看对话 ------------------------------ */

const detailOpen = ref(false)
const detailChatHistory = ref<API.ChatHistoryVO | null>(null)

// 详情：列表字段已满足展示需要，直接复用当前行数据
const openDetail = (record: API.ChatHistoryVO) => {
  detailChatHistory.value = record
  detailOpen.value = true
}

// 查看对话：跳转到该应用的对话页（管理员同样可以查看应用的对话历史）
const goAppChat = (record: API.ChatHistoryVO) => {
  if (!record.appId) {
    return
  }
  router.push(`/app/chat/${record.appId}`)
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#chatHistoryManagePage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-panel :deep(.ant-card-body) {
  padding: 20px 24px;
}

.search-form :deep(.ant-form-item) {
  margin-bottom: 8px;
  margin-inline-end: 16px;
}

.search-form :deep(.ant-form-item:last-child) {
  margin-inline-end: 0;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.panel-subtitle {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.45);
}

/* 行高固定，内容垂直居中，长文本只做省略（见 EllipsisText） */
.table-panel :deep(.ant-table-tbody > tr > td) {
  height: 72px;
  padding-top: 8px;
  padding-bottom: 8px;
  vertical-align: middle;
  white-space: nowrap;
  overflow: hidden;
}

@media (max-width: 768px) {
  .search-panel :deep(.ant-card-body) {
    padding: 16px;
  }

  .search-form :deep(.ant-form-item) {
    margin-inline-end: 8px;
  }
}
</style>
