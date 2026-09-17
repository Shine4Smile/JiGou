<template>
  <div id="homePage">
    <!-- 网站标题 + 用户提示词输入框 -->
    <section class="hero">
      <h1 class="hero__title">
        <span>一句话</span>
        <img class="hero__logo" :src="siteConfig.logo" alt="logo" />
        <span>呈所想</span>
      </h1>
      <p class="hero__subtitle">与 AI 对话轻松创建应用和网站</p>

      <div class="prompt-card">
        <a-textarea
          v-model:value="prompt"
          class="prompt-card__textarea"
          placeholder="使用 NoCode 创建一个高效的小工具，帮我计算……"
          :auto-size="{ minRows: 4, maxRows: 8 }"
          :maxlength="2000"
          :disabled="creating"
        />
        <div class="prompt-card__footer">
          <div class="prompt-card__tools">
            <a-tooltip title="暂未开放">
              <a-button type="text" disabled>
                <template #icon><PaperClipOutlined /></template>
                <span>上传</span>
              </a-button>
            </a-tooltip>
            <a-tooltip title="暂未开放">
              <a-button type="text" disabled>
                <template #icon><HighlightOutlined /></template>
                <span>优化</span>
              </a-button>
            </a-tooltip>
          </div>
          <a-button
            type="primary"
            shape="circle"
            size="large"
            :loading="creating"
            @click="doCreate"
          >
            <template #icon><ArrowUpOutlined /></template>
          </a-button>
        </div>
      </div>

      <!-- 示例提示词：点击直接回填输入框 -->
      <div class="examples">
        <a-tag
          v-for="example in examplePrompts"
          :key="example"
          class="examples__item"
          @click="prompt = example"
        >
          {{ example }}
        </a-tag>
      </div>
    </section>

    <!-- 我的应用：分页查询自己的应用（支持按名称查询，每页最多 20 个） -->
    <section class="app-section">
      <div class="app-section__header">
        <h2 class="app-section__title">我的作品</h2>
        <a-space v-if="isLogin" :size="8">
          <a-input-search
            v-model:value="myKeyword"
            class="app-section__search"
            placeholder="按名称搜索我的应用"
            allow-clear
            @search="searchMyApps"
          />
          <a-button :loading="myLoading" @click="fetchMyApps">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </a-space>
      </div>

      <a-spin :spinning="myLoading">
        <a-empty v-if="!isLogin" description="登录后即可查看和管理自己的应用">
          <a-button type="primary" @click="goLogin">去登录</a-button>
        </a-empty>
        <template v-else>
          <a-empty v-if="!myApps.length" description="还没有作品，输入提示词创建你的第一个应用吧" />
          <a-row v-else :gutter="[16, 16]">
            <a-col v-for="item in myApps" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
              <AppCard
                :app="item"
                @open="goChat(item)"
                @detail="openDetail(item)"
                @edit="goEdit(item)"
                @delete="confirmDelete(item)"
              />
            </a-col>
          </a-row>
          <div v-if="myTotal > 0" class="app-section__pager">
            <a-pagination
              v-model:current="myParams.pageNum"
              v-model:page-size="myParams.pageSize"
              :total="myTotal"
              :page-size-options="pageSizeOptions"
              show-size-changer
              :show-total="(value: number) => `共 ${value} 条`"
              @change="fetchMyApps"
            />
          </div>
        </template>
      </a-spin>
    </section>

    <!-- 精选应用：分页查询精选应用（后端固定 priority = 99） -->
    <section class="app-section">
      <div class="app-section__header">
        <h2 class="app-section__title">精选案例</h2>
        <a-space v-if="isLogin" :size="8">
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
        <a-empty v-if="!isLogin" description="登录后即可查看精选案例">
          <a-button type="primary" @click="goLogin">去登录</a-button>
        </a-empty>
        <template v-else>
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
        </template>
      </a-spin>
    </section>

    <!-- 应用详情弹窗 -->
    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  ArrowUpOutlined,
  HighlightOutlined,
  PaperClipOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  addApp,
  deleteApp,
  listFeaturedAppVoByPage,
  listMyAppVoByPage,
} from '@/api/appController.ts'
import AppCard from '@/components/AppCard.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { siteConfig } from '@/layouts/config'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { DEFAULT_APP_PAGE_SIZE, MAX_PAGE_SIZE } from '@/constants/app'
import { asApiId } from '@/utils/apiId'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const isLogin = computed(() => !!loginUserStore.loginUser.id)

/** 页面大小选项：后端限制用户端每页最多查询 20 个应用 */
const pageSizeOptions = ['6', String(DEFAULT_APP_PAGE_SIZE), String(MAX_PAGE_SIZE)]

/** 示例提示词 */
const examplePrompts = ['波普风电商页面', '企业网站', '电商运营后台', '暗黑话题社区']

/* ------------------------------ 创建应用 ------------------------------ */

const prompt = ref('')
const creating = ref(false)

const goLogin = () => {
  router.push(`/user/login?redirect=${encodeURIComponent(route.fullPath)}`)
}

// 输入提示词创建应用：创建成功后跳转到对话页并自动生成
const doCreate = async () => {
  const initPrompt = prompt.value.trim()
  if (!initPrompt) {
    message.warning('请输入提示词，描述你想要创建的网站')
    return
  }
  if (!isLogin.value) {
    message.warning('请先登录后再创建应用')
    goLogin()
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt })
    const appId = res.data.data
    if (res.data.code === 0 && appId) {
      prompt.value = ''
      // 后端 Long 以字符串返回，直接透传（转 Number 会造成雪花 id 精度丢失）
      // autoStart 用于告知对话页：进入后自动把初始提示词发送给 AI
      router.push({ path: `/app/chat/${appId}`, query: { autoStart: '1' } })
    } else {
      message.error('创建应用失败：' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('创建应用失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

/* ------------------------------ 我的应用 ------------------------------ */

const myApps = ref<API.AppVO[]>([])
const myTotal = ref(0)
const myLoading = ref(false)
const myKeyword = ref('')
const myParams = reactive({
  pageNum: 1,
  pageSize: DEFAULT_APP_PAGE_SIZE,
  appName: undefined as string | undefined,
})

const fetchMyApps = async () => {
  if (!isLogin.value) {
    return
  }
  myLoading.value = true
  try {
    const res = await listMyAppVoByPage({ ...myParams })
    if (res.data.data) {
      myApps.value = res.data.data.records ?? []
      myTotal.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取我的应用失败：' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('获取我的应用失败，请检查网络后重试')
  } finally {
    myLoading.value = false
  }
}

// 按名称查询：重置到第一页
const searchMyApps = () => {
  myParams.appName = myKeyword.value.trim() || undefined
  myParams.pageNum = 1
  fetchMyApps()
}

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

const searchFeaturedApps = () => {
  featuredParams.appName = featuredKeyword.value.trim() || undefined
  featuredParams.pageNum = 1
  fetchFeaturedApps()
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
        // 删除后两个列表都可能受影响（如该应用原本是精选应用）
        await Promise.all([fetchMyApps(), fetchFeaturedApps()])
      } else {
        message.error('删除失败，' + (res.data.message ?? '请稍后重试'))
      }
    },
  })
}

onMounted(() => {
  // 未登录时不请求列表，避免被登录拦截器重定向
  if (isLogin.value) {
    fetchMyApps()
    fetchFeaturedApps()
  }
})
</script>

<style scoped>
#homePage {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

/* 顶部标题与提示词输入区：浅色渐变背景，呼应原型 */
.hero {
  padding: 40px 24px 28px;
  border-radius: 16px;
  background: linear-gradient(135deg, #e9fbf4 0%, #e7f5fb 50%, #e3f6fd 100%);
  text-align: center;
}

.hero__title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 0;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #1f1f1f;
}

.hero__logo {
  width: 44px;
  height: 44px;
}

.hero__subtitle {
  margin: 12px 0 24px;
  font-size: 16px;
  color: rgba(0, 0, 0, 0.45);
}

/* 提示词输入卡片 */
.prompt-card {
  max-width: 860px;
  margin: 0 auto;
  padding: 16px 18px 12px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  text-align: left;
}

/* 兼容两种渲染结构：类名直接落在 textarea 上（无 allowClear）或外层包裹元素上 */
.prompt-card__textarea,
.prompt-card__textarea :deep(.ant-input) {
  padding: 0;
  border: none;
  box-shadow: none;
  font-size: 15px;
  line-height: 1.7;
  resize: none;
}

.prompt-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.prompt-card__tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 示例提示词 */
.examples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.examples__item {
  margin-inline-end: 0;
  padding: 6px 16px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  background: #fff;
  border: none;
  border-radius: 18px;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}

.examples__item:hover {
  color: #1677ff;
  transform: translateY(-1px);
}

/* 应用列表分区 */
.app-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.app-section__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f1f1f;
}

.app-section__search {
  width: 240px;
}

.app-section__pager {
  display: flex;
  justify-content: center;
  margin-top: 4px;
}

.app-section :deep(.ant-empty) {
  margin: 32px 0;
}

@media (max-width: 768px) {
  .hero {
    padding: 24px 16px 20px;
  }

  .hero__title {
    font-size: 28px;
    letter-spacing: 2px;
  }

  .hero__logo {
    width: 32px;
    height: 32px;
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
