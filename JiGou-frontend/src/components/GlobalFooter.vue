<template>
  <!-- 底部版权信息 -->
  <a-layout-footer class="footer">
    <p class="copyright">学习项目 by simple</p>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  padding: 16px 24px;
  background: #fff;
  text-align: center;
}

.copyright {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
}

@media (max-width: 768px) {
  .footer {
    padding: 12px 16px;
  }
}
</style>
