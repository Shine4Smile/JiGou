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

/** 应用卡片默认每页数量（需 <= MAX_PAGE_SIZE） */
export const DEFAULT_APP_PAGE_SIZE = 12

/** 代码生成类型取值 -> 标签颜色，用于列表中的 tag 展示 */
export const CODE_GEN_TYPE_TAG_COLOR: Record<string, string> = {
  html: 'orange',
  multi_file: 'blue',
}
