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
.header {
  height: 64px;
  padding: 0 24px;
  line-height: normal;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

/* 栅格行撑满头部高度，配合 align="middle" 实现垂直居中 */
.header-row {
  height: 100%;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
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

/* 菜单背景透明、去掉默认下边框，与头部融为一体 */
.header-menu {
  background: transparent;
  border-bottom: none;
}

.user-actions {
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .header {
    padding: 0 12px;
  }

  .site-title {
    font-size: 16px;
  }
}
</style>
