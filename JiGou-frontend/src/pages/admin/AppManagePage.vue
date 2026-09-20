<template>
  <div id="appManagePage">
    <!-- 搜索区：支持根据除时间外的任意字段查询 -->
    <a-card class="app-panel search-panel" :bordered="false">
      <a-form layout="inline" :model="searchParams" class="search-form" @finish="doSearch">
        <a-form-item label="应用名称">
          <a-input v-model:value="searchParams.appName" placeholder="请输入应用名称" allow-clear />
        </a-form-item>
        <!--        <a-form-item label="应用 id">-->
        <!--          <a-input v-model:value="searchId" placeholder="请输入应用 id" allow-clear />-->
        <!--        </a-form-item>-->
        <!--        <a-form-item label="初始提示词">-->
        <!--          <a-input-->
        <!--            v-model:value="searchParams.initPrompt"-->
        <!--            placeholder="请输入初始提示词"-->
        <!--            allow-clear-->
        <!--          />-->
        <!--        </a-form-item>-->
        <a-form-item label="生成类型">
          <a-select
            v-model:value="searchParams.codeGenType"
            :options="CODE_GEN_TYPE_OPTIONS"
            placeholder="全部"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <!--        <a-form-item label="部署标识">-->
        <!--          <a-input-->
        <!--            v-model:value="searchParams.deployKey"-->
        <!--            placeholder="请输入部署标识"-->
        <!--            allow-clear-->
        <!--          />-->
        <!--        </a-form-item>-->
        <a-form-item label="优先级">
          <a-select
            v-model:value="searchParams.priority"
            :options="priorityOptions"
            placeholder="全部"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <a-form-item label="可见范围">
          <a-select
            v-model:value="searchParams.visibility"
            :options="visibilityOptions"
            placeholder="全部"
            allow-clear
            style="width: 150px"
          />
        </a-form-item>
        <!--        <a-form-item label="创建用户 id">-->
        <!--          <a-input v-model:value="searchUserId" placeholder="请输入创建用户 id" allow-clear />-->
        <!--        </a-form-item>-->
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
        <span class="panel-title">应用列表</span>
        <span class="panel-subtitle">管理员可管理全部应用，每页数量不限</span>
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
        :scroll="{ x: 1510 }"
        @change="doTableChange"
      >
        <template #bodyCell="{ column, record }">
          <!-- 封面：固定尺寸小图，无封面时展示占位图标 -->
          <template v-if="column.dataIndex === 'cover'">
            <a-image v-if="record.cover" class="app-cover" :src="record.cover" :width="80" />
            <a-avatar v-else class="app-cover app-cover--empty" shape="square" :size="48">
              <template #icon><PictureOutlined /></template>
            </a-avatar>
          </template>
          <template v-else-if="column.dataIndex === 'appName'">
            <EllipsisText :text="record.appName" />
          </template>
          <template v-else-if="column.dataIndex === 'codeGenType'">
            <a-tag :color="CODE_GEN_TYPE_TAG_COLOR[record.codeGenType] ?? 'default'">
              {{ CODE_GEN_TYPE_LABEL[record.codeGenType] ?? record.codeGenType ?? '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'visibility'">
            <a-tag :color="APP_VISIBILITY_TAG_COLOR[record.visibility ?? ''] ?? 'default'">
              {{ APP_VISIBILITY_LABEL[record.visibility ?? ''] ?? '私有' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag v-if="record.priority === GOOD_APP_PRIORITY" color="gold">
              <StarFilled /> 精选
            </a-tag>
            <a-tag v-else>普通</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'deployKey'">
            <EllipsisText :text="record.deployKey" placeholder="未部署" />
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
              <a-button type="link" size="small" @click="goEdit(record)">
                <template #icon><EditOutlined /></template>
                <span>编辑</span>
              </a-button>
              <a-popconfirm
                v-if="record.priority !== GOOD_APP_PRIORITY"
                :title="`确定将「${record.appName || record.id}」设为精选应用吗？`"
                description="设为精选后（优先级 99）会展示在首页「精选案例」中"
                ok-text="设为精选"
                cancel-text="取消"
                placement="topRight"
                @confirm="doSetFeatured(record)"
              >
                <a-button type="link" size="small" :loading="featuringId === record.id">
                  <template #icon><StarFilled /></template>
                  <span>精选</span>
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                v-else
                :title="`确定取消应用「${record.appName || record.id}」的精选吗？`"
                description="取消后（优先级 0）将不再展示在首页「精选案例」中"
                ok-text="取消精选"
                cancel-text="返回"
                placement="topRight"
                @confirm="doCancelFeatured(record)"
              >
                <a-button type="link" size="small" :loading="featuringId === record.id">
                  <template #icon><StarFilled /></template>
                  <span>取消精选</span>
                </a-button>
              </a-popconfirm>
              <a-popconfirm
                :title="`确定删除应用「${record.appName || record.id}」吗？`"
                description="删除后不可恢复，请谨慎操作"
                ok-text="删除"
                cancel-text="取消"
                ok-type="danger"
                placement="topRight"
                @confirm="doDelete(record)"
              >
                <a-button type="link" size="small" danger :loading="deletingId === record.id">
                  <template #icon><DeleteOutlined /></template>
                  <span>删除</span>
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 应用详情弹窗（管理员通过 /app/admin/get 获取任意应用详情） -->
    <AppDetailModal v-model:open="detailOpen" :app="detailApp" :loading="detailLoading" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PictureOutlined,
  ReloadOutlined,
  SearchOutlined,
  StarFilled,
} from '@ant-design/icons-vue'
import {
  deleteAppByAdmin,
  getAppById,
  listAppVoByPage,
  updateAppByAdmin,
} from '@/api/appController.ts'
import AppDetailModal from '@/components/AppDetailModal.vue'
import EllipsisText from '@/components/EllipsisText.vue'
import {
  APP_VISIBILITY_LABEL,
  APP_VISIBILITY_PRIVATE,
  APP_VISIBILITY_PUBLIC,
  APP_VISIBILITY_TAG_COLOR,
  CODE_GEN_TYPE_LABEL,
  CODE_GEN_TYPE_OPTIONS,
  CODE_GEN_TYPE_TAG_COLOR,
  DEFAULT_APP_PRIORITY,
  GOOD_APP_PRIORITY,
} from '@/constants/app'
import { asApiId } from '@/utils/apiId'
import { formatDateTime } from '@/utils/time'

const router = useRouter()

/* ------------------------------ 表格配置 ------------------------------ */

/** 优先级筛选选项，取值与后端 AppConstant 一致 */
const priorityOptions = [
  { label: '普通（0）', value: DEFAULT_APP_PRIORITY },
  { label: '精选（99）', value: GOOD_APP_PRIORITY },
]

/** 可见范围筛选选项，取值与后端 AppVisibilityEnum 一致 */
const visibilityOptions = [
  { label: '私有', value: APP_VISIBILITY_PRIVATE },
  { label: '公开', value: APP_VISIBILITY_PUBLIC },
]

/** 列宽策略与用户管理页一致：固定列宽 + table-layout=fixed，长文本单行省略 */
const columns: TableColumnsType = [
  { title: '应用名称', dataIndex: 'appName', width: 180 },
  { title: '封面', dataIndex: 'cover', width: 110, align: 'center' },
  { title: '生成类型', dataIndex: 'codeGenType', width: 130, align: 'center' },
  { title: '可见范围', dataIndex: 'visibility', width: 110, align: 'center' },
  { title: '优先级', dataIndex: 'priority', width: 110, align: 'center' },
  { title: '部署标识', dataIndex: 'deployKey', width: 130 },
  { title: '创建用户', dataIndex: 'user', width: 160 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 300, align: 'center', fixed: 'right' },
]

/* ------------------------------ 列表数据 ------------------------------ */

const data = ref<API.AppVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件（管理员接口不限每页数量，这里默认 10 条，可切换更大页长）
const searchParams = reactive<API.AppQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// id / userId 均为雪花 id（后端以字符串返回），输入框按字符串收集，查询时原样透传
const searchId = ref<string>('')
const searchUserId = ref<string>('')

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAppVoByPage({
      ...searchParams,
      id: searchId.value ? asApiId(searchId.value.trim()) : undefined,
      userId: searchUserId.value ? asApiId(searchUserId.value.trim()) : undefined,
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
  searchParams.appName = undefined
  searchParams.initPrompt = undefined
  searchParams.codeGenType = undefined
  searchParams.deployKey = undefined
  searchParams.priority = undefined
  searchParams.visibility = undefined
  searchId.value = ''
  searchUserId.value = ''
  searchParams.pageNum = 1
  fetchData()
}

/* ------------------------------ 详情 / 编辑 / 精选 / 删除 ------------------------------ */

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailApp = ref<API.AppVO | null>(null)

// 详情：先用列表数据展示，同时通过管理员详情接口拉取完整信息
const openDetail = async (record: API.AppVO) => {
  detailApp.value = record
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getAppById({ id: asApiId(record.id ?? '') })
    if (res.data.code === 0 && res.data.data) {
      detailApp.value = res.data.data
    }
  } catch {
    message.error('获取应用详情失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

// 编辑：新开页面跳转到应用信息修改页
const goEdit = (record: API.AppVO) => {
  router.push(`/app/edit/${record.id}`)
}

const featuringId = ref<number>()
const deletingId = ref<number>()

// 精选 / 取消精选：本质是把优先级更新为 99 / 0（只传需要修改的字段，后端按非空字段更新）
const updateFeatured = async (record: API.AppVO, priority: number) => {
  if (!record?.id) {
    return
  }
  const featured = priority === GOOD_APP_PRIORITY
  const failTip = featured ? '设置失败' : '取消失败'
  featuringId.value = record.id
  try {
    const res = await updateAppByAdmin({ id: record.id, priority })
    if (res.data.code === 0) {
      message.success(featured ? '已设为精选应用' : '已取消精选')
      // 在「精选」筛选条件下取消精选会让该行从列表中消失，当前页仅此一条时自动回退一页
      const filteredByFeatured = searchParams.priority === GOOD_APP_PRIORITY
      if (!featured && filteredByFeatured && data.value.length === 1 && (searchParams.pageNum ?? 1) > 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await fetchData()
    } else {
      message.error(failTip + '，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error(failTip + '，请稍后重试')
  } finally {
    featuringId.value = undefined
  }
}

// 设为精选
const doSetFeatured = (record: API.AppVO) => updateFeatured(record, GOOD_APP_PRIORITY)

// 取消精选
const doCancelFeatured = (record: API.AppVO) => updateFeatured(record, DEFAULT_APP_PRIORITY)

const doDelete = async (record: API.AppVO) => {
  if (!record?.id) {
    return
  }
  deletingId.value = record.id
  try {
    const res = await deleteAppByAdmin({ id: record.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      // 删除当前页最后一条数据时，自动回退一页
      if (data.value.length === 1 && (searchParams.pageNum ?? 1) > 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await fetchData()
    } else {
      message.error('删除失败，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deletingId.value = undefined
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#appManagePage {
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

/* 封面：固定尺寸，图片按比例裁切 */
.app-cover {
  display: block;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 6px;
}

.app-cover :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-cover--empty {
  color: rgba(0, 0, 0, 0.35);
  background-color: #f5f5f5;
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
