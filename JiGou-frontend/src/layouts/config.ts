import type { MenuProps } from 'ant-design-vue'

/**
 * 站点基础配置
 */
export const siteConfig = {
  /** Logo 图片路径（对应 public 目录下的 bulb.png） */
  logo: '/bulb.png',
  /** 网站标题 */
  title: '即构-零代码应用生成',
}

/**
 * 导航菜单配置，key 与路由 path 一一对应
 * 新增菜单项只需在此处追加配置即可
 */
export const originMenuItems: MenuProps['items'] = [
  { key: '/', label: '主页' },
  { key: '/admin/userManage', label: '用户管理' },
]
