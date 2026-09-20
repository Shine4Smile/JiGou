<template>
  <div id="appSquarePage">
    <!-- 页面说明：独立导航页，同时展示精选案例与全部公开应用 -->
    <a-card class="app-panel intro-panel" :bordered="false">
      <div class="intro">
        <h1 class="section-title">应用广场</h1>
        <span class="section-subtitle">
          浏览精选案例与其他用户公开分享的应用，点击卡片即可预览效果；自己的应用可在卡片上直接切换私有 /
          公开
        </span>
      </div>
    </a-card>

    <!-- 精选案例：由管理员挑选（后端固定 priority = 99） -->
    <a-card class="app-panel app-section" :bordered="false">
      <div class="app-section__header">
        <div class="app-section__heading">
          <span class="panel-title">精选案例</span>
          <span class="panel-subtitle">由管理员挑选的优秀应用，可作为创作参考</span>
        </div>
        <a-space :size="8">
          <a-input-search
            v-model:value="featuredKeyword"
            class="app-section__search"
            placeholder="按名称搜索精选应用"
            allow-clear
            @search="searchFeaturedApps"
          />
          <a-button :loading="featuredLoading" @click="fetchFeaturedApps">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </a-space>
      </div>

      <a-spin :spinning="featuredLoading">
        <a-empty
          v-if="!featuredApps.length"
          description="暂时还没有精选应用，快去创建属于你的作品吧"
        />
        <a-row v-else :gutter="[16, 16]">
          <a-col v-for="item in featuredApps" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
            <AppCard
              :app="item"
              @open="goChat(item)"
              @detail="openDetail(item)"
              @edit="goEdit(item)"
              @delete="confirmDelete(item)"
              @visibility-change="refreshLists"
            />
          </a-col>
        </a-row>
        <div v-if="featuredTotal > 0" class="app-section__pager">
          <a-pagination
            v-model:current="featuredParams.pageNum"
            v-model:page-size="featuredParams.pageSize"
            :total="featuredTotal"
            :page-size-options="pageSizeOptions"
            show-size-changer
            :show-total="(value: number) => `共 ${value} 条`"
            @change="fetchFeaturedApps"
          />
        </div>
      </a-spin>
    </a-card>

    <!-- 公开应用：后端固定只查询 visibility = public -->
    <a-card class="app-panel app-section" :bordered="false">
      <div class="app-section__header">
        <div class="app-section__heading">
          <span class="panel-title">公开应用</span>
          <span class="panel-subtitle">所有登录用户可见的应用，可在自己的作品里切换公开 / 私有</span>
        </div>
        <a-space :size="8">
          <a-input-search
            v-model:value="publicKeyword"
            class="app-section__search"
            placeholder="按名称搜索公开应用"
            allow-clear
            @search="searchPublicApps"
          />
          <a-button :loading="publicLoading" @click="fetchPublicApps">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </a-space>
      </div>

      <a-spin :spinning="publicLoading">
        <a-empty v-if="!publicApps.length" description="还没有公开的应用，可以把你的作品设为公开" />
        <a-row v-else :gutter="[16, 16]">
          <a-col v-for="item in publicApps" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
            <AppCard
              :app="item"
              @open="goChat(item)"
              @detail="openDetail(item)"
              @edit="goEdit(item)"
              @delete="confirmDelete(item)"
              @visibility-change="refreshLists"
            />
          </a-col>
        </a-row>
        <div v-if="publicTotal > 0" class="app-section__pager">
          <a-pagination
            v-model:current="publicParams.pageNum"
            v-model:page-size="publicParams.pageSize"
            :total="publicPagerTotal"
            :page-size-options="pageSizeOptions"
            show-size-changer
            :show-total="() => `共 ${publicTotal} 条`"
            @change="fetchPublicApps"
          />
        </div>
        <!-- 后端限制公开列表最多翻 50 页，超出部分不再请求，这里给出说明 -->
        <div v-if="publicTotal > publicPagerTotal" class="app-section__limit-tip">
          为便于浏览，应用广场仅展示前 {{ MAX_PAGE_NUM }} 页公开应用
        </div>
      </a-spin>
    </a-card>

    <!-- 应用详情弹窗 -->
    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  deleteApp,
  listFeaturedAppVoByPage,
  listPublicAppVoByPage,
} from '@/api/appController.ts'
import AppCard from '@/components/AppCard.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { DEFAULT_APP_PAGE_SIZE, MAX_PAGE_NUM, MAX_PAGE_SIZE } from '@/constants/app'
import { asApiId } from '@/utils/apiId'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const isLogin = computed(() => !!loginUserStore.loginUser.id)

/** 页面大小选项：后端限制用户端每页最多查询 20 个应用 */
const pageSizeOptions = ['6', String(DEFAULT_APP_PAGE_SIZE), String(MAX_PAGE_SIZE)]
/* ------------------------------ 精选应用 ------------------------------ */

const featuredApps = ref<API.AppVO[]>([])
const featuredTotal = ref(0)
const featuredLoading = ref(false)
const featuredKeyword = ref('')
const featuredParams = reactive({
  pageNum: 1,
  pageSize: DEFAULT_APP_PAGE_SIZE,
  appName: undefined as string | undefined,
})

const fetchFeaturedApps = async () => {
  if (!isLogin.value) {
    return
  }
  featuredLoading.value = true
  try {
    // 精选条件由后端固定为 priority = 99，前端只需传分页与名称
    const res = await listFeaturedAppVoByPage({ ...featuredParams })
    if (res.data.data) {
      featuredApps.value = res.data.data.records ?? []
      featuredTotal.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取精选应用失败：' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('获取精选应用失败，请检查网络后重试')
  } finally {
    featuredLoading.value = false
  }
}

// 按名称查询：重置到第一页
const searchFeaturedApps = () => {
  featuredParams.appName = featuredKeyword.value.trim() || undefined
  featuredParams.pageNum = 1
  fetchFeaturedApps()
}
/* ------------------------------ 公开应用 ------------------------------ */

const publicApps = ref<API.AppVO[]>([])
const publicTotal = ref(0)
const publicLoading = ref(false)
const publicKeyword = ref('')
const publicParams = reactive({
  pageNum: 1,
  pageSize: DEFAULT_APP_PAGE_SIZE,
  appName: undefined as string | undefined,
})

/**
 * 分页组件可翻页的总数
 *
 * 后端限制公开列表最多翻 MAX_PAGE_NUM 页，超出部分不展示分页入口，避免请求被参数校验拦截
 */
const publicPagerTotal = computed(() =>
  Math.min(publicTotal.value, MAX_PAGE_NUM * publicParams.pageSize),
)

const fetchPublicApps = async () => {
  if (!isLogin.value) {
    return
  }
  publicLoading.value = true
  try {
    // 「只查询公开应用」的条件由后端固定，前端只需传分页与名称
    const res = await listPublicAppVoByPage({ ...publicParams })
    if (res.data.data) {
      publicApps.value = res.data.data.records ?? []
      publicTotal.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取应用广场失败：' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('获取应用广场失败，请检查网络后重试')
  } finally {
    publicLoading.value = false
  }
}

// 按名称查询：重置到第一页
const searchPublicApps = () => {
  publicParams.appName = publicKeyword.value.trim() || undefined
  publicParams.pageNum = 1
  fetchPublicApps()
}

/** 刷新两个列表：删除应用与切换可见范围都会影响精选 / 公开列表 */
const refreshLists = () => {
  fetchFeaturedApps()
  fetchPublicApps()
}
/* ------------------------------ 应用操作 ------------------------------ */

// 进入对话生成页查看应用详情与效果
const goChat = (app: API.AppVO) => {
  router.push(`/app/chat/${app.id}`)
}

// 进入应用信息修改页
const goEdit = (app: API.AppVO) => {
  router.push(`/app/edit/${app.id}`)
}

const detailOpen = ref(false)
const detailApp = ref<API.AppVO | null>(null)

const openDetail = (app: API.AppVO) => {
  detailApp.value = app
  detailOpen.value = true
}

// 删除自己的应用（二次确认）
const confirmDelete = (app: API.AppVO) => {
  Modal.confirm({
    title: `确定删除应用「${app.appName || app.id}」吗？`,
    content: '删除后不可恢复，请谨慎操作。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      const res = await deleteApp({ id: asApiId(app.id ?? '') })
      if (res.data.code === 0) {
        message.success('删除成功')
        refreshLists()
      } else {
        message.error('删除失败，' + (res.data.message ?? '请稍后重试'))
      }
    },
  })
}

onMounted(() => {
  // 路由已限制登录访问，这里再兜底一次，避免未登录时请求被登录拦截器重定向
  if (isLogin.value) {
    refreshLists()
  }
})
</script>

<style scoped>
#appSquarePage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro-panel :deep(.ant-card-body) {
  padding: 20px 24px;
}

/* 标题 + 说明同一行展示（覆盖全局 .section-subtitle 的左侧间距） */
.intro {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
}

.intro .section-subtitle {
  margin-left: 0;
}

/* 列表分区：卡片材质来自全局 .app-panel，内部用纵向布局分隔标题区与网格 */
.app-section :deep(.ant-card-body) {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px 24px;
}

.app-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

/* 标题 + 说明文案 */
.app-section__heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.panel-subtitle {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}

.app-section__search {
  width: 240px;
}

.app-section__pager {
  display: flex;
  justify-content: center;
  margin-top: 4px;
}

/* 公开列表翻页上限提示 */
.app-section__limit-tip {
  margin-top: 4px;
  font-size: 13px;
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
}

.app-section :deep(.ant-empty) {
  margin: 32px 0;
}

@media (max-width: 768px) {
  .app-section :deep(.ant-card-body) {
    padding: 16px 14px 20px;
  }

  .app-section__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .app-section__search {
    width: 100%;
  }
}
</style>
