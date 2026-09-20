<template>
  <div id="homePage">
    <!-- 顶部通栏：科技感渐变背景（100% 宽）+ 标语 + 提示词输入框 -->
    <section class="hero">
      <div class="hero__inner">
        <div class="hero__brand">
          <img class="hero__logo" :src="siteConfig.logo" alt="logo" />
        </div>
        <h1 class="hero__title">{{ siteConfig.title }}</h1>
        <p class="hero__subtitle">{{ siteConfig.slogan }}</p>

        <!-- 提示词输入卡片：去掉多余边框与上传 / 优化按钮，只保留输入与生成 -->
        <div class="prompt-card">
          <a-textarea
            v-model:value="prompt"
            class="prompt-card__textarea"
            placeholder="请描述你想生成的网站，越详细效果越贴合想法"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="2000"
            :disabled="creating"
          />
          <div class="prompt-card__footer">
            <span class="prompt-card__hint">按 Enter 直接生成，Shift + Enter 换行</span>
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

        <!-- 示例提示词：点击把完整提示词回填到输入框，悬浮可查看全文 -->
        <div class="examples">
          <a-tooltip
            v-for="example in examplePrompts"
            :key="example.label"
            :title="example.prompt"
            placement="bottom"
            overlay-class-name="example-tip"
          >
            <button type="button" class="examples__item" @click="prompt = example.prompt">
              {{ example.label }}
            </button>
          </a-tooltip>
        </div>
      </div>
    </section>

    <!-- 下方内容区：与 hero 使用同一套卡片材质，背景由渐变自然过渡过来 -->
    <div class="home-body">
      <section class="app-section app-panel">
        <div class="app-section__header">
          <div class="app-section__heading">
            <h2 class="app-section__title">我的作品</h2>
            <span class="app-section__desc">
              设为公开后会展示在应用广场，点击卡片上的可见范围标签即可快速切换
            </span>
          </div>
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
            <a-empty
              v-if="!myApps.length"
              description="还没有作品，输入提示词创建你的第一个应用吧"
            />
            <a-row v-else :gutter="[16, 16]">
              <a-col v-for="item in myApps" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
                <AppCard
                  :app="item"
                  @open="goChat(item)"
                  @detail="openDetail(item)"
                  @edit="goEdit(item)"
                  @delete="confirmDelete(item)"
                  @visibility-change="refreshAfterVisibilityChange"
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

    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal v-model:open="detailOpen" :app="detailApp" />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { ArrowUpOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  addApp,
  deleteApp,
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

/**
 * 示例提示词
 *
 * label 为输入框下方展示的短标签，prompt 为点击后回填的完整提示词（约 100 字）
 */
const examplePrompts = [
  {
    label: '个人博客网站',
    prompt:
      '创建一个极简风格的个人博客网站：首页展示博主头像、一句简介和文章卡片列表，点击卡片进入文章详情页，支持按标签筛选，正文用 Markdown 渲染，配色以白底灰字为主，顶部是深色导航栏，移动端自适应。',
  },
  {
    label: '在线商城首页',
    prompt:
      '设计一个在线商城的首页：顶部搜索栏与分类导航，中部是自动轮播的主 Banner，下面用网格展示商品卡片，含商品图、名称、价格和加入购物车按钮，底部是页脚信息，整体用暖色系配色，要求页面在不同屏幕下都能整齐排列。',
  },
  {
    label: '数据看板后台',
    prompt:
      '做一个数据看板后台页面：左侧是折叠菜单侧边栏，右侧顶部放四个关键指标卡片，下方并排展示折线图、柱状图和饼图，底部是一张可排序的明细表格，支持切换统计时间范围，深色科技风配色，数字要有滚动增长的动画。',
  },
  {
    label: '企业官网',
    prompt:
      '生成一个科技公司的企业官网：顶部是固定导航栏，首屏放品牌标语和两个行动按钮，接着是产品服务介绍卡片、客户案例和团队介绍三个区块，末尾是联系我们表单与备案信息页脚，整体用蓝紫渐变科技风，滚动时元素有淡入效果。',
  },
]

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
      // 对话页会在「自己的应用且没有对话历史」时自动发送初始提示词
      router.push(`/app/chat/${appId}`)
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

/* ------------------------------ 可见范围切换 ------------------------------ */

/**
 * 卡片上切换可见范围成功后刷新「我的作品」
 *
 * 公开 / 私有的展示状态、以及应用广场的数据都会随之变化，
 * 广场列表在应用广场页刷新，这里只需同步自己的列表
 */
const refreshAfterVisibilityChange = () => {
  fetchMyApps()
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
        // 删除后我的作品列表需要同步（精选 / 公开列表在应用广场页刷新）
        await fetchMyApps()
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
  }
})
</script>

<style scoped>
/* 页面整体：hero 通栏，内容区限宽，两者通过渐变自然衔接 */
#homePage {
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
}

/* 顶部 hero：100% 宽深色科技渐变 + 网格纹理，底部通过 ::after 渐隐接入页面背景 */
.hero {
  position: relative;
  overflow: hidden;
  padding: 64px 24px 104px;
  background:
    radial-gradient(900px 420px at 12% 0%, rgba(99, 102, 241, 0.55), transparent 62%),
    radial-gradient(760px 400px at 88% 6%, rgba(34, 211, 238, 0.4), transparent 60%),
    radial-gradient(680px 380px at 50% 108%, rgba(56, 189, 248, 0.26), transparent 66%),
    linear-gradient(135deg, #131a3a 0%, #1e2a5e 48%, #332a6b 100%);
  text-align: center;
}

/* 科技网格纹理：径向遮罩让线条只在顶部中间可见 */
.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px);
  background-size: 48px 48px;
  -webkit-mask-image: radial-gradient(760px 440px at 50% 0%, #000 18%, transparent 78%);
  mask-image: radial-gradient(760px 440px at 50% 0%, #000 18%, transparent 78%);
  pointer-events: none;
}

/* 底部渐隐：颜色与内容区背景一致，抹平 hero 与列表区的边界 */
.hero::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 96px;
  background: linear-gradient(
    180deg,
    rgba(245, 247, 252, 0) 0%,
    rgba(245, 247, 252, 0.72) 62%,
    var(--page-bg) 100%
  );
  pointer-events: none;
}

.hero__inner {
  position: relative;
  z-index: 1;
  max-width: 900px;
  margin: 0 auto;
}

/* Logo 玻璃胶囊 */
.hero__brand {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 18px;
  backdrop-filter: blur(8px);
}

.hero__logo {
  width: 36px;
  height: 36px;
}

.hero__title {
  margin: 0;
  font-size: 42px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #fff;
  text-shadow: 0 8px 30px rgba(8, 14, 40, 0.45);
}

.hero__subtitle {
  margin: 14px 0 36px;
  font-size: 18px;
  letter-spacing: 6px;
  color: rgba(255, 255, 255, 0.82);
}

/* 提示词输入卡片：浅色玻璃卡片，去掉多余边框按钮 */
.prompt-card {
  max-width: 860px;
  margin: 0 auto;
  padding: 18px 20px 14px;
  text-align: left;
  background: rgba(255, 255, 255, 0.97);
  border: none;
  border-radius: 18px;
  box-shadow: 0 20px 48px rgba(8, 14, 40, 0.28);
  backdrop-filter: blur(10px);
}

/* 兼容两种渲染结构：类名直接落在 textarea 上（无 allowClear）或外层包裹元素上 */
.prompt-card__textarea,
.prompt-card__textarea :deep(.ant-input),
.prompt-card :deep(.ant-input:focus),
.prompt-card :deep(.ant-input:hover),
.prompt-card :deep(.ant-input-focused) {
  padding: 0;
  border: none;
  box-shadow: none;
  font-size: 15px;
  line-height: 1.75;
  resize: none;
  background: transparent;
}

.prompt-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.prompt-card__hint {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}

/* 示例提示词：玻璃胶囊，hover 提亮 */
.examples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 26px;
}

.examples__item {
  padding: 8px 18px;
  font-family: inherit;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  cursor: pointer;
  backdrop-filter: blur(6px);
  transition:
    color 0.2s,
    background-color 0.2s,
    border-color 0.2s,
    transform 0.2s;
}

.examples__item:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.24);
  border-color: rgba(255, 255, 255, 0.45);
  transform: translateY(-1px);
}

/* 下方内容区：与 hero 等宽居中，两块列表卡片并排堆叠 */
.home-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
  max-width: var(--page-max-width);
  margin: 0 auto;
  padding: 8px 24px 48px;
}

/* 应用列表分区：材质来自全局 .app-panel */
.app-section {
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

/* 标题 + 说明文案（我的作品区块使用） */
.app-section__heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.app-section__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.app-section__desc {
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

.app-section :deep(.ant-empty) {
  margin: 32px 0;
}

@media (max-width: 768px) {
  .hero {
    padding: 40px 16px 80px;
  }

  .hero__brand {
    width: 56px;
    height: 56px;
    margin-bottom: 16px;
  }

  .hero__logo {
    width: 30px;
    height: 30px;
  }

  .hero__title {
    font-size: 26px;
    letter-spacing: 1px;
  }

  .hero__subtitle {
    margin: 10px 0 24px;
    font-size: 15px;
    letter-spacing: 3px;
  }

  .prompt-card {
    padding: 14px 16px 12px;
  }

  .home-body {
    gap: 16px;
    padding: 4px 12px 32px;
  }

  .app-section {
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
