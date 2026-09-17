<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />
    <!-- 中间内容区：根据路由切换页面；通栏页面（meta.flush）去掉内边距，由页面自己控制留白 -->
    <a-layout-content
      class="basic-layout__content"
      :class="{ 'basic-layout__content--flush': isFlush }"
    >
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()

/**
 * 是否通栏页面
 *
 * 主页需要 100% 宽的渐变背景、对话页需要撑满一屏且无多余间距，
 * 这类页面在路由 meta 中标记 flush，内容区不再附加 24px 内边距
 */
const isFlush = computed(() => route.meta.flush === true)
</script>

<style scoped>
/* 上中下布局：min-height 撑满视口，内容区 flex:1 占据剩余高度，保证页脚始终贴底 */
.basic-layout {
  min-height: 100vh;
}

.basic-layout__content {
  flex: 1;
  padding: 24px;
  background: var(--page-bg);
}

.basic-layout__content--flush {
  padding: 0;
}

@media (max-width: 768px) {
  .basic-layout__content {
    padding: 12px;
  }

  .basic-layout__content--flush {
    padding: 0;
  }
}
</style>
