<template>
  <div id="noAuthPage">
    <!-- 中间内容：无权限提示 -->
    <a-layout-content class="content">
      <a-result status="403" title="403" sub-title="抱歉，你没有权限访问该页面。">
        <template #extra>
          <a-button type="primary" @click="goHome">返回首页</a-button>
          <!-- 已登录用户无需再显示「去登录」 -->
          <a-button v-if="!isLogin" @click="goLogin">去登录</a-button>
        </template>
      </a-result>
    </a-layout-content>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const loginUserStore = useLoginUserStore()

/**
 * 是否已登录：登录态存于全局 loginUserStore 中，已登录时 loginUser 带有 id
 */
const isLogin = computed(() => !!loginUserStore.loginUser.id)

/**
 * 返回首页
 */
const goHome = () => {
  router.push('/')
}

/**
 * 去登录页
 */
const goLogin = () => {
  router.push('/user/login')
}
</script>

<style scoped>
#noAuthPage {
  min-height: 100vh;
}

#noAuthPage .header {
  padding: 0;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

#noAuthPage .content {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 64px - 70px); /* 减去 header 和 footer 高度 */
  background: #f5f5f5;
  padding: 24px;
}

#noAuthPage .footer {
  text-align: center;
  background: #f0f2f5;
  padding: 16px 0;
}
</style>
