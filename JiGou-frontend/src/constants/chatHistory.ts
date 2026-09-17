/**
 * 对话历史（chat_history）相关常量，取值与后端 ChatHistoryMessageTypeEnum / ChatHistoryConstant 保持一致
 */

/** 消息类型取值（对应后端 ChatHistoryMessageTypeEnum） */
export const CHAT_MESSAGE_TYPE = {
  USER: 'user',
  AI: 'ai',
  ERROR: 'error',
} as const

/** 消息类型选项，用于筛选下拉框 */
export const CHAT_MESSAGE_TYPE_OPTIONS = [
  { label: '用户', value: CHAT_MESSAGE_TYPE.USER },
  { label: 'AI', value: CHAT_MESSAGE_TYPE.AI },
  { label: '错误', value: CHAT_MESSAGE_TYPE.ERROR },
]

/** 消息类型 value -> 展示文案 */
export const CHAT_MESSAGE_TYPE_LABEL: Record<string, string> = {
  [CHAT_MESSAGE_TYPE.USER]: '用户',
  [CHAT_MESSAGE_TYPE.AI]: 'AI',
  [CHAT_MESSAGE_TYPE.ERROR]: '错误',
}

/** 消息类型 value -> 标签颜色，用于列表中的 tag 展示 */
export const CHAT_MESSAGE_TYPE_TAG_COLOR: Record<string, string> = {
  [CHAT_MESSAGE_TYPE.USER]: 'blue',
  [CHAT_MESSAGE_TYPE.AI]: 'green',
  [CHAT_MESSAGE_TYPE.ERROR]: 'red',
}

/** 应用对话历史每次加载条数（类似聊天软件），对应后端 ChatHistoryConstant.DEFAULT_PAGE_SIZE */
export const DEFAULT_CHAT_HISTORY_PAGE_SIZE = 10

/** 应用对话历史每次最多加载条数，对应后端 ChatHistoryConstant.MAX_PAGE_SIZE */
export const MAX_CHAT_HISTORY_PAGE_SIZE = 20

/** 应用至少有多少条对话历史时，说明已经生成过网站（用户消息 + AI 回复） */
export const GENERATED_HISTORY_SIZE = 2
