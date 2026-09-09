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
          <!-- TODO: 接入登录态后，替换为「头像 + 昵称」下拉菜单 -->
          <a-button type="primary">登录</a-button>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuProps } from 'ant-design-vue'
import { menuItems as menuConfig, siteConfig } from '@/layouts/config'

const route = useRoute()
const router = useRouter()

// 当前选中的菜单项：跟随路由变化，点击菜单跳转后 route.path 更新，选中态自动同步
const selectedKeys = computed(() => [route.path])

// 展示在导航栏的菜单项：从配置读取，后续可在此按登录态 / 角色过滤
const menuItems = computed<MenuProps['items']>(() => menuConfig)

// 处理菜单点击：以菜单 key（即路由 path）进行跳转
const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  router.push(String(key))
}
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
