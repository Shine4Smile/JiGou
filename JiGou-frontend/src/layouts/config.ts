import { h } from 'vue'
import type { MenuProps } from 'ant-design-vue'
import { AppstoreOutlined, HomeOutlined, TeamOutlined } from '@ant-design/icons-vue'

/**
 * 站点基础配置
 */
export const siteConfig = {
  /** Logo 图片路径（对应 public 目录下的 bulb.png） */
  logo: '/bulb.png',
  /** 网站标题 */
  title: '即构-零代码应用生成',
  /** 宣传语 */
  slogan: '所建即所想',
}

/**
 * 导航菜单配置，key 与路由 path 一一对应
 * 新增菜单项只需在此处追加配置即可（icon 使用渲染函数，避免复用同一个 VNode）
 */
export const originMenuItems: MenuProps['items'] = [
  { key: '/', label: '主页', icon: () => h(HomeOutlined) },
  { key: '/admin/appManage', label: '应用管理', icon: () => h(AppstoreOutlined) },
  { key: '/admin/userManage', label: '用户管理', icon: () => h(TeamOutlined) },
]
