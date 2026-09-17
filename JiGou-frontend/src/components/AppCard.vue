<template>
  <!-- 卡片整体可点击：进入该应用的对话生成页 -->
  <div class="app-card" @click="emit('open')">
    <!-- 封面区域：有封面用封面，否则用占位块（保证卡片高度一致） -->
    <div class="app-card__cover">
      <img v-if="app.cover" class="app-card__image" :src="app.cover" :alt="app.appName" />
      <div v-else class="app-card__placeholder">
        <img class="app-card__placeholder-logo" :src="siteConfig.logo" alt="logo" />
        <span class="app-card__placeholder-name">{{ app.appName || '未命名应用' }}</span>
      </div>
    </div>

    <div class="app-card__body">
      <div class="app-card__title-row">
        <span class="app-card__title" :title="app.appName">{{ app.appName || '未命名应用' }}</span>
        <!-- 创建者与管理员可管理：用包裹元素阻止冒泡（避免点击时同时进入对话页） -->
        <div v-if="canManage" class="app-card__more-wrap" @click.stop>
          <a-dropdown placement="bottomRight" :trigger="['click']">
            <a-button type="text" size="small" class="app-card__more">
              <template #icon><MoreOutlined /></template>
            </a-button>
            <template #overlay>
              <a-menu @click="handleMenuClick">
                <a-menu-item key="detail">
                  <template #icon><EyeOutlined /></template>
                  <span>查看详情</span>
                </a-menu-item>
                <a-menu-item key="edit">
                  <template #icon><EditOutlined /></template>
                  <span>编辑应用信息</span>
                </a-menu-item>
                <!-- 删除仅限创建者本人 -->
                <template v-if="isOwner">
                  <a-menu-divider />
                  <a-menu-item key="delete" danger>
                    <template #icon><DeleteOutlined /></template>
                    <span>删除应用</span>
                  </a-menu-item>
                </template>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>

      <!-- 创建者信息：接口返回的 userVO 中带昵称与头像 -->
      <div class="app-card__author">
        <a-avatar :size="22" class="app-card__avatar" :src="app.userVO?.userAvatar || undefined">
          <template #icon><UserOutlined /></template>
        </a-avatar>
        <span class="app-card__author-name" :title="authorName">{{ authorName }}</span>
        <a-tag v-if="isFeatured" class="app-card__featured" color="gold">精选</a-tag>
      </div>

      <div class="app-card__meta">
        <a-tag :color="CODE_GEN_TYPE_TAG_COLOR[app.codeGenType ?? ''] ?? 'default'">
          {{ CODE_GEN_TYPE_LABEL[app.codeGenType ?? ''] ?? '未知类型' }}
        </a-tag>
        <span class="app-card__time">创建于 {{ formatRelativeTime(app.createTime) }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { MenuProps } from 'ant-design-vue'
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  MoreOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { siteConfig } from '@/layouts/config'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import ACCESS_ENUM from '@/access/accessEnum'
import { CODE_GEN_TYPE_LABEL, CODE_GEN_TYPE_TAG_COLOR, GOOD_APP_PRIORITY } from '@/constants/app'
import { formatRelativeTime } from '@/utils/time'

const props = defineProps<{
  /** 应用信息（列表接口返回的 AppVO） */
  app: API.AppVO
}>()

const emit = defineEmits<{
  (e: 'open'): void
  (e: 'detail'): void
  (e: 'edit'): void
  (e: 'delete'): void
}>()

const loginUserStore = useLoginUserStore()

/**
 * 是否当前用户创建的应用
 * 后端 Long 序列化为字符串，此处统一转成字符串比较，避免 number/string 混用导致判断失败
 */
const isOwner = computed(
  () =>
    !!loginUserStore.loginUser.id &&
    String(props.app.userId) === String(loginUserStore.loginUser.id),
)

/** 是否管理员：管理员可以编辑任意应用的应用信息 */
const isAdmin = computed(() => loginUserStore.loginUser.userRole === ACCESS_ENUM.ADMIN)

/** 是否展示管理入口：创建者本人或管理员 */
const canManage = computed(() => isOwner.value || isAdmin.value)

/** 创建者展示名：优先昵称，其次账号，都没有时兜底为「未知用户」 */
const authorName = computed(
  () => props.app.userVO?.userName || props.app.userVO?.userAccount || '未知用户',
)

/** 是否为精选应用（后端以 priority = 99 标记精选） */
const isFeatured = computed(() => props.app.priority === GOOD_APP_PRIORITY)

// 下拉菜单：将操作抛给父组件处理（父组件负责跳转 / 调接口）
const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  if (key === 'detail') {
    emit('detail')
  } else if (key === 'edit') {
    emit('edit')
  } else if (key === 'delete') {
    emit('delete')
  }
}
</script>

<style scoped>
.app-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.app-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.app-card__cover {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: #fafafa;
}

.app-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 无封面时的占位：浅色渐变 + logo，视觉上比空白更接近原型的卡片效果 */
.app-card__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #f6f9ff 0%, #eefbf6 100%);
}

.app-card__placeholder-logo {
  width: 40px;
  height: 40px;
  opacity: 0.75;
}

.app-card__placeholder-name {
  max-width: 100%;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
}

.app-card__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.app-card__title {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-card__more-wrap {
  flex: none;
}

.app-card__more {
  color: rgba(0, 0, 0, 0.45);
}

/* 创建者信息行：头像 + 昵称（+ 精选标记） */
.app-card__author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.55);
}

.app-card__avatar {
  flex: none;
  background: var(--brand-color);
}

.app-card__author-name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-card__featured {
  flex: none;
  margin-inline-end: 0;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  border-radius: 9px;
}

.app-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-card__meta :deep(.ant-tag) {
  margin-inline-end: 0;
}

.app-card__time {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
