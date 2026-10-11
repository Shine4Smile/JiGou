/**
 * AI 回复内容切分：把后端拼接好的流式文本按出现顺序切成「文本段 / 工具段 / 代码段」
 *
 * 后端（SimpleTextStreamHandler / JsonMessageStreamHandler）把流式内容按顺序拼成一段纯文本：
 * 1. HTML 类型应用：AI 直接输出 markdown，代码放在 ``` 围栏里；
 * 2. VUE_PROJECT 类型应用：AI 的说明文字 + 「[选择工具] 写入文件」协议行 +
 *    「[工具调用] 写入文件 src/App.vue」协议行 + ```vue 围栏代码块；
 *    多个文件时这组内容会重复出现，前端要按原顺序展示成一条工具链。
 *
 * 前端需要按顺序分别渲染（文本走 markdown、工具链进度单独展示、代码走高亮文件卡片），
 * 因此这里实现一个容错的扫描解析器：
 * - 流式输出中围栏可能尚未闭合，此时把剩余内容都当作代码并标记 closed = false；
 * - 协议行解析成工具段用于展示工具链，同时从中提取文件名；
 * - 还没解析出内容的协议行先不展示，等后续 chunk 补全后再输出，避免出现半截内容；
 * - 解析不出文件名时按语言给出兜底文件名。
 */

/** 文本片段：交给 markdown 渲染 */
export interface TextMessageSegment {
  type: 'text'
  /** 片段序号，同一条消息内唯一 */
  index: number
  /** 渲染 key（同一条消息内唯一） */
  key: string
  /** 原始文本 */
  text: string
}

/** 代码片段：渲染成文件卡片（文件名 + 高亮代码 + 折叠） */
export interface CodeMessageSegment {
  type: 'code'
  /** 片段序号，同一条消息内唯一 */
  index: number
  /** 渲染 key：序号 + 文件名，避免同名的多个文件片段互相覆盖折叠状态 */
  key: string
  /** 文件名（优先取协议行，其次按语言兜底，都取不到时为空字符串） */
  fileName: string
  /** 代码语言（已归一化，未标注时为空字符串，表示不高亮） */
  language: string
  /** 代码内容（不含围栏行） */
  code: string
  /** 围栏是否已闭合：流式输出中可能是未闭合的半截代码 */
  closed: boolean
  /** 代码行数 */
  lines: number
}

/**
 * 工具段：展示后端的工具链进度信息（对应协议行）
 *
 * 后端 TOOL_REQUEST / TOOL_EXECUTED 会分别输出：
 * - `[选择工具] 写入文件`；
 * - `[工具调用] 写入文件 src/App.vue`。
 */
export interface ToolMessageSegment {
  type: 'tool'
  /** 片段序号，同一条消息内唯一 */
  index: number
  /** 渲染 key（同一条消息内唯一） */
  key: string
  /** 阶段：select 选择工具、call 工具调用（带目标文件） */
  stage: 'select' | 'call'
  /** 工具动作，如「写入文件」 */
  action: string
  /** 目标文件，如 src/App.vue；选择工具阶段通常为空 */
  target: string
}

/** 消息片段 */
export type MessageSegment = TextMessageSegment | CodeMessageSegment | ToolMessageSegment

/**
 * 语言 -> 默认文件名
 * 与后端 MultiFileCodeFileSaverTemplate 落盘的文件名保持一致，
 * 用于 AI 直接输出代码块（HTML 类型应用）时给出符合直觉的文件名
 */
export const CODE_FILE_NAME: Record<string, string> = {
  html: 'index.html',
  css: 'style.css',
  js: 'script.js',
  javascript: 'script.js',
}

/** 语言别名归一表：统一成 highlight.js 中注册的语言名（空串表示不高亮） */
const LANGUAGE_ALIAS: Record<string, string> = {
  htm: 'html',
  vue: 'html',
  js: 'javascript',
  jsx: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  sh: 'bash',
  shell: 'bash',
  zsh: 'bash',
  yml: 'yaml',
  md: 'markdown',
  text: '',
  txt: '',
  plain: '',
  plaintext: '',
}

/**
 * 归一化代码语言
 *
 * @param language 围栏上标注的语言（可能为空或带多余空格）
 * @returns 归一化后的语言名，无法高亮时返回空字符串
 */
export const normalizeCodeLanguage = (language?: string): string => {
  const value = (language ?? '').trim().toLowerCase()
  if (!value) {
    return ''
  }
  return value in LANGUAGE_ALIAS ? (LANGUAGE_ALIAS[value] as string) : value
}

/**
 * 去掉首尾空行（保留内容自身的行首缩进）
 *
 * 围栏代码块的开头 / 结尾通常有一个换行，展示时需要裁掉；中间的缩进必须原样保留
 *
 * @param lines 行数组
 */
const trimBlankLines = (lines: string[]): string => {
  let start = 0
  let end = lines.length
  while (start < end && !(lines[start] ?? '').trim()) {
    start += 1
  }
  while (end > start && !(lines[end - 1] ?? '').trim()) {
    end -= 1
  }
  return lines.slice(start, end).join('\n')
}

/** 后端协议行：[选择工具] 写入文件（TOOL_REQUEST 第一次见到该工具时输出） */
const TOOL_SELECT_PATTERN = /^\[选择工具\]\s*(.*?)\s*$/

/** 后端协议行：[工具调用] 写入文件 src/App.vue（TOOL_EXECUTED 输出，带实际文件路径） */
const TOOL_CALL_PATTERN = /^\[工具调用\]\s*(.+?)\s+(\S+)\s*$/

/** 协议行前缀：用于识别只写了一半的协议行（如「[工具调」「[选择工具] 写入」） */
const TOOL_PROTOCOL_LEADS = ['[工具调用]', '[选择工具]']

/** 围栏行：行首三个及以上反引号或波浪线，后接可选的语言标注（不含反引号，避免误判行内代码） */
const FENCE_PATTERN = /^[ \t]*(`{3,}|~{3,})[ \t]*([^`]*?)[ \t]*$/

/**
 * 解析围栏行
 *
 * @param line 单行文本
 * @returns 围栏标记与语言标注；不是围栏行时返回 null
 */
const parseFence = (line: string): { fence: string; language: string } | null => {
  const matched = FENCE_PATTERN.exec(line)
  if (!matched) {
    return null
  }
  const fence = matched[1] ?? ''
  // 语言标注只取第一个词，后面可能是 showLineNumbers 之类的附加标记
  const info = (matched[2] ?? '').trim().split(/\s+/)[0] ?? ''
  return { fence, language: normalizeCodeLanguage(info) }
}

/**
 * 判断是否为结束围栏：围栏字符相同、长度不小于起始围栏，且行内没有其它内容
 *
 * @param line 单行文本
 * @param fence 起始围栏标记
 */
const isClosingFence = (line: string, fence: string): boolean => {
  const content = line.trim()
  if (content.length < fence.length) {
    return false
  }
  const char = fence.charAt(0)
  for (let i = 0; i < content.length; i += 1) {
    if (content.charAt(i) !== char) {
      return false
    }
  }
  return true
}

/**
 * 解析工具协议行
 *
 * @param line 单行文本
 * @returns 工具段（不含 index / key，由调用方补全）；不是完整协议行时返回 null
 */
const parseToolLine = (line: string): Omit<ToolMessageSegment, 'index' | 'key'> | null => {
  const call = TOOL_CALL_PATTERN.exec(line)
  if (call) {
    return {
      type: 'tool',
      stage: 'call',
      action: (call[1] ?? '').trim(),
      target: (call[2] ?? '').trim(),
    }
  }
  const select = TOOL_SELECT_PATTERN.exec(line)
  const action = (select?.[1] ?? '').trim()
  if (select && action) {
    return { type: 'tool', stage: 'select', action, target: '' }
  }
  return null
}

/**
 * 判断是否为「还没解析出内容」的工具协议行
 *
 * 协议行在流式输出中会被切断（如「[工具调」「[选择工具]」「[工具调用] 写入文件」），
 * 这些行解析不出动作 / 目标文件，先不展示，等后续 chunk 补全成完整协议行后再作为工具段输出；
 * 行内文字（如文件名）随 chunk 补全而渐进更新，与 AI 文本的打字机效果一致
 *
 * @param line 单行文本
 */
const isProtocolLine = (line: string): boolean => {
  const trimmed = line.trim()
  if (!trimmed.startsWith('[')) {
    return false
  }
  return TOOL_PROTOCOL_LEADS.some((lead) => lead.startsWith(trimmed) || trimmed.startsWith(lead))
}

/**
 * 切分 AI 回复内容
 *
 * @param content 后端拼接的完整文本（流式过程中是已接收到的部分）
 * @returns 按出现顺序排列的文本段、工具段与代码段
 */
export const splitMessageContent = (content?: string): MessageSegment[] => {
  const text = content ?? ''
  if (!text.trim()) {
    return []
  }
  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  const segments: MessageSegment[] = []
  /** 文本段缓冲 */
  let textLines: string[] = []
  /** 代码段缓冲 */
  let codeLines: string[] = []
  /** 是否处于代码围栏内部 */
  let inFence = false
  /** 起始围栏标记，用于匹配结束围栏 */
  let fenceMark = ''
  /** 当前代码段语言 */
  let fenceLanguage = ''
  /** 协议行声明的文件名，供紧随其后的代码段使用 */
  let pendingFileName = ''
  let index = 0

  /** 输出文本段（协议行在上层已单独解析，不会进入文本缓冲） */
  const flushText = () => {
    const keptText = trimBlankLines(textLines)
    if (keptText) {
      segments.push({ type: 'text', index, key: `text-${index}`, text: keptText })
      index += 1
    }
    textLines = []
  }

  /** 输出工具段：展示工具链进度；工具调用行同时给紧随其后的代码段提供文件名 */
  const flushTool = (tool: Omit<ToolMessageSegment, 'index' | 'key'>) => {
    flushText()
    if (tool.stage === 'call' && tool.target) {
      pendingFileName = tool.target
    }
    segments.push({ ...tool, index, key: `tool-${index}` })
    index += 1
  }

  /** 输出代码段：内容为空时（如只有一个空围栏）不产生卡片 */
  const flushCode = (closed: boolean) => {
    const code = trimBlankLines(codeLines)
    const language = fenceLanguage
    const fileName = pendingFileName || CODE_FILE_NAME[language] || ''
    pendingFileName = ''
    codeLines = []
    if (!code) {
      return
    }
    segments.push({
      type: 'code',
      index,
      key: `${index}-${fileName || 'code'}`,
      fileName,
      language,
      code,
      closed,
      lines: code.split('\n').length,
    })
    index += 1
  }

  lines.forEach((line) => {
    if (inFence) {
      if (isClosingFence(line, fenceMark)) {
        inFence = false
        // 先输出代码段（其中要用到当前语言），再重置围栏状态
        flushCode(true)
        fenceMark = ''
        fenceLanguage = ''
        return
      }
      codeLines.push(line)
      return
    }
    const fence = parseFence(line)
    if (fence) {
      // 进入代码块前先输出已累积的文本
      flushText()
      inFence = true
      fenceMark = fence.fence
      fenceLanguage = fence.language
      return
    }
    const tool = parseToolLine(line)
    if (tool) {
      // 完整协议行：作为工具链进度展示，同时给后面的代码段提供文件名
      flushTool(tool)
      return
    }
    if (isProtocolLine(line)) {
      // 只写了一半的协议行：先丢弃，等后续 chunk 补全后再展示，避免内容闪烁
      return
    }
    textLines.push(line)
  })

  if (inFence) {
    // 流式输出中断在代码块内部：剩余内容仍是代码，标记为未闭合
    flushCode(false)
  } else {
    flushText()
  }
  return segments
}

/** 统计代码段数量（用于「已生成 N 个文件」提示） */
export const countCodeSegments = (segments: MessageSegment[]): number =>
  segments.filter((segment) => segment.type === 'code').length
