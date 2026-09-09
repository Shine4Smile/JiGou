<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />
    <!-- 中间内容区：根据路由切换页面 -->
    <a-layout-content class="basic-layout__content">
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
/* 上中下布局：min-height 撑满视口，内容区 flex:1 占据剩余高度，保证页脚始终贴底 */
.basic-layout {
  min-height: 100vh;
}

.basic-layout__content {
  flex: 1;
  padding: 24px;
  background: #f0f2f5;
}

@media (max-width: 768px) {
  .basic-layout__content {
    padding: 12px;
  }
}
</style>
