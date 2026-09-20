/**
 * 应用（app）相关常量，取值与后端 CodeGenTypeEnum / AppConstant 保持一致
 */

/** 代码生成类型选项（对应后端 CodeGenTypeEnum） */
export const CODE_GEN_TYPE_OPTIONS = [
  { label: '原生 HTML', value: 'html' },
  { label: '原生多文件', value: 'multi_file' },
]

/** 代码生成类型 value -> 展示文案 */
export const CODE_GEN_TYPE_LABEL: Record<string, string> = {
  html: '原生 HTML',
  multi_file: '原生多文件',
}
/** 普通应用优先级（未上精选），对应后端 AppConstant.DEFAULT_APP_PRIORITY */
export const DEFAULT_APP_PRIORITY = 0

/** 精选应用优先级，对应后端 AppConstant.GOOD_APP_PRIORITY */
export const GOOD_APP_PRIORITY = 99

/** 普通用户分页查询应用列表时每页最多数量，对应后端 AppConstant.MAX_PAGE_SIZE */
export const MAX_PAGE_SIZE = 20

/** 公开列表（应用广场）最多可翻页数，对应后端 AppConstant.MAX_PAGE_NUM */
export const MAX_PAGE_NUM = 50

/** 应用卡片默认每页数量（需 <= MAX_PAGE_SIZE） */
export const DEFAULT_APP_PAGE_SIZE = 12

/** 代码生成类型取值 -> 标签颜色，用于列表中的 tag 展示 */
export const CODE_GEN_TYPE_TAG_COLOR: Record<string, string> = {
  html: 'orange',
  multi_file: 'blue',
}

/**
 * 单个应用最多保留的历史版本数量，对应后端 AppConstant.MAX_VERSION_COUNT
 *
 * 超出后后端会自动淘汰最旧的版本（当前版本受保护），前端仅用于展示提示文案
 */
export const MAX_VERSION_COUNT = 20

/** 应用可见范围：私有（默认），仅创建者与管理员可见，对应后端 AppVisibilityEnum.PRIVATE */
export const APP_VISIBILITY_PRIVATE = 'private'

/** 应用可见范围：公开，所有登录用户可见并展示在应用广场，对应后端 AppVisibilityEnum.PUBLIC */
export const APP_VISIBILITY_PUBLIC = 'public'

/** 可见范围选项（编辑表单的单选选项） */
export const APP_VISIBILITY_OPTIONS = [
  { label: '私有（仅自己和管理员可见）', value: APP_VISIBILITY_PRIVATE },
  { label: '公开（所有登录用户可见，会展示在应用广场）', value: APP_VISIBILITY_PUBLIC },
]

/** 可见范围 value -> 展示文案 */
export const APP_VISIBILITY_LABEL: Record<string, string> = {
  [APP_VISIBILITY_PRIVATE]: '私有',
  [APP_VISIBILITY_PUBLIC]: '公开',
}

/** 可见范围 value -> 标签颜色，用于列表 / 详情中的 tag 展示 */
export const APP_VISIBILITY_TAG_COLOR: Record<string, string> = {
  [APP_VISIBILITY_PRIVATE]: 'default',
  [APP_VISIBILITY_PUBLIC]: 'green',
}

/**
 * 是否为公开应用
 *
 * visibility 为空（历史数据）时按私有处理，与后端 AppVisibilityEnum.isPublic 保持一致
 */
export const isAppPublic = (visibility?: string) => visibility === APP_VISIBILITY_PUBLIC

/** 可见范围 value -> 说明文案（详情 / 表单提示共用） */
export const getVisibilityTip = (visibility?: string) =>
  isAppPublic(visibility) ? '所有登录用户可见，会展示在应用广场' : '仅创建者与管理员可见'

/**
 * 可见范围 value -> 切换后的目标值
 *
 * 私有与公开之间互切；历史数据（visibility 为空）按私有处理，切换后变为公开
 */
export const getToggledVisibility = (visibility?: string) =>
  isAppPublic(visibility) ? APP_VISIBILITY_PRIVATE : APP_VISIBILITY_PUBLIC

/** 可见范围切换按钮的悬浮提示文案 */
export const VISIBILITY_SWITCH_TIP = '点击切换可见范围（私有 / 公开）'
