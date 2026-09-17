<template>
  <!-- 顶部导航栏 -->
  <a-layout-header class="header">
    <a-row class="header-row" :wrap="false" align="middle">
      <!-- 左侧：Logo 与网站标题，点击返回首页 -->
      <a-col flex="none">
        <RouterLink to="/" class="header-left">
          <img class="logo" :src="siteConfig.logo" alt="logo" />
          <h1 class="site-title">{{ siteConfig.title }}</h1>
        </RouterLink>
      </a-col>

      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu
          class="header-menu"
          mode="horizontal"
          :selected-keys="selectedKeys"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>

      <!-- 右侧：用户操作区 -->
      <a-col flex="none">
        <div class="user-actions">
          <div class="user-login-status">
            <div v-if="loginUserStore.loginUser.id">
              <a-dropdown>
                <a-space>
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                  {{ loginUserStore.loginUser.userName ?? '无名' }}
                </a-space>
                <template #overlay>
                  <a-menu>
                    <a-menu-item @click="goToUserCenter">
                      <UserOutlined />
                      个人中心
                    </a-menu-item>
                    <a-menu-divider />
                    <a-menu-item @click="doLogout">
                      <LogoutOutlined />
                      退出登录
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>

            <div v-else>
              <a-button type="primary" href="/user/login">登录</a-button>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { originMenuItems, siteConfig } from '@/layouts/config'
import { LogoutOutlined, UserOutlined } from '@ant-design/icons-vue'

// JS 中引入 Store
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

const route = useRoute()
const router = useRouter()

// 当前选中的菜单项：跟随路由变化，点击菜单跳转后 route.path 更新，选中态自动同步
const selectedKeys = computed(() => [route.path])

// 处理菜单点击：以菜单 key（即路由 path）进行跳转
const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(String(key))
}

// 跳转到个人中心
const goToUserCenter = () => {
  router.push('/user/center')
}

// 用户注销
const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}

// 根据权限过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => {
  return filterMenus(originMenuItems || [])
})
</script>

<style scoped>
/* 顶栏：sticky 常驻，半透明 + 毛玻璃，与页面渐变背景过渡更自然 */
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  height: var(--header-height);
  padding: 0 24px;
  line-height: normal;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  backdrop-filter: saturate(180%) blur(10px);
}

/* 栅格行撑满头部高度，配合 align="middle" 实现垂直居中 */
.header-row {
  height: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-right: 24px;
}

.logo {
  height: 32px;
  width: auto;
}

.site-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  white-space: nowrap;
}

/* 导航菜单：背景透明，导航项做成胶囊按钮（去掉 antd 默认下划线指示条） */
.header-menu {
  background: transparent;
  border-bottom: none;
  line-height: normal;
}

.header-menu :deep(.ant-menu-item) {
  display: inline-flex;
  align-items: center;
  height: 36px;
  margin: 0 4px;
  padding: 0 16px;
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
  color: rgba(0, 0, 0, 0.68);
  border-radius: 18px;
  transition:
    color 0.2s,
    background-color 0.2s;
}

/* 去掉默认的底部指示条 */
.header-menu :deep(.ant-menu-item::after) {
  display: none;
}

.header-menu :deep(.ant-menu-title-content) {
  line-height: 1;
}

.header-menu :deep(.ant-menu-item .anticon) {
  font-size: 15px;
}

.header-menu :deep(.ant-menu-item:hover) {
  color: var(--brand-color);
  background: #f0f6ff;
}

.header-menu :deep(.ant-menu-item-selected) {
  font-weight: 600;
  color: var(--brand-color);
  background: #e6f4ff;
}

.user-actions {
  display: flex;
  align-items: center;
}

/* 用户区：头像 + 昵称做成可点击胶囊，hover 有底色反馈 */
.user-actions :deep(.ant-space) {
  padding: 4px 10px 4px 4px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.75);
  border-radius: 20px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-actions :deep(.ant-space:hover) {
  background: #f5f7fc;
}

@media (max-width: 768px) {
  .header {
    padding: 0 12px;
  }

  .header-left {
    padding-right: 12px;
  }

  .site-title {
    font-size: 16px;
  }

  .header-menu :deep(.ant-menu-item) {
    margin: 0 2px;
    padding: 0 10px;
  }
}
</style>
