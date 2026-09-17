import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AppManagePage from '@/pages/admin/AppManagePage.vue'
import AppChatPage from '@/pages/app/AppChatPage.vue'
import AppEditPage from '@/pages/app/AppEditPage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserCenterPage from '@/pages/user/UserCenterPage.vue'
import NoAuth from '@/components/NoAuth.vue'
import AccessEnum from '@/access/accessEnum.ts'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: '主页',
      component: HomePage,
      meta: { access: AccessEnum.NOT_LOGIN },
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
      meta: { access: AccessEnum.NOT_LOGIN },
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
      meta: { access: AccessEnum.NOT_LOGIN },
    },
    {
      path: '/user/center',
      name: '个人中心',
      component: UserCenterPage,
      meta: { access: AccessEnum.USER },
    },
    {
      path: '/app/chat/:appId',
      name: '应用生成对话',
      component: AppChatPage,
      meta: { access: AccessEnum.USER },
    },
    {
      path: '/app/edit/:appId',
      name: '应用信息修改',
      component: AppEditPage,
      meta: { access: AccessEnum.USER },
    },
    {
      path: '/admin/userManage',
      name: '用户管理',
      component: UserManagePage,
      meta: { access: AccessEnum.ADMIN },
    },
    {
      path: '/admin/appManage',
      name: '应用管理',
      component: AppManagePage,
      meta: { access: AccessEnum.ADMIN },
    },
    {
      path: '/noAuth',
      name: '无权限',
      component: NoAuth,
      meta: { access: AccessEnum.NOT_LOGIN },
    },
  ],
})

export default router
