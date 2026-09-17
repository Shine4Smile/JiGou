import { API_BASE_URL } from '@/request'

/** 流式对话异常：携带后端返回的错误码，便于页面区分「未登录」等场景 */
export class AppChatStreamError extends Error {
  code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'AppChatStreamError'
    this.code = code
  }
}

/** SSE 帧 */
type SseFrame = {
  event: string
  data: string
}

/**
 * 解析单个 SSE 帧（形如 "event: done\ndata: {...}"）
 *
 * @param frame 帧文本
 */
const parseFrame = (frame: string): SseFrame => {
  const result: SseFrame = { event: 'message', data: '' }
  const dataLines: string[] = []
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) {
      result.event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      // SSE 规范：冒号后最多去掉一个空格
      const value = line.slice('data:'.length)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  })
  result.data = dataLines.join('\n')
  return result
}

/**
 * 取出数据帧中的文本分片
 *
 * 后端将每个分片包装为 {"d": "内容"} 的 JSON，避免空格被 SSE 协议吞掉；
 * 若数据不是该格式（如后续接口调整），则原样返回，保证兼容性
 *
 * @param data 帧中的 data 内容
 */
const parseChunk = (data: string) => {
  if (!data) {
    return ''
  }
  try {
    const parsed = JSON.parse(data) as { d?: string }
    return typeof parsed?.d === 'string' ? parsed.d : data
  } catch {
    return data
  }
}

export type GenCodeStreamOptions = {
  /** 应用 id（后端 Long 以字符串返回，原样透传） */
  appId: string | number
  /** 用户消息 */
  message: string
  /** 每收到一个分片时的回调 */
  onChunk?: (chunk: string) => void
  /** 流正常结束（收到 event: done）时的回调，入参为完整内容 */
  onDone?: (content: string) => void
  /** 中断信号，用于「停止生成」 */
  signal?: AbortSignal
}

/**
 * 调用后端 SSE 接口流式生成代码
 *
 * 这里选择 fetch + ReadableStream 而非 EventSource：
 * 1. 可以拿到 HTTP 状态码与后端统一返回的错误体（BaseResponse），错误提示更准确；
 * 2. 可配合 AbortController 精确中断，且不会像 EventSource 那样自动重连；
 * 3. 无需额外引入第三方依赖。
 *
 * @param options 请求参数与流式回调
 * @returns 最终拼接出的完整内容
 */
export const streamGenCode = async (options: GenCodeStreamOptions) => {
  const { appId, message, onChunk, onDone, signal } = options
  const url =
    `${API_BASE_URL}/app/chat/gen/code` +
    `?appId=${encodeURIComponent(String(appId))}&message=${encodeURIComponent(message)}`
  const response = await fetch(url, {
    method: 'GET',
    // SSE 需要携带 Cookie 完成登录校验（后端 CorsConfig 已允许携带凭证）
    credentials: 'include',
    headers: { Accept: 'text/event-stream' },
    signal,
  })
  if (!response.ok || !response.body) {
    // 业务异常时后端会返回统一的 JSON 错误体
    const errorBody = await response.json().catch(() => undefined)
    throw new AppChatStreamError(
      errorBody?.message ?? `生成失败（${response.status}）`,
      errorBody?.code,
    )
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let content = ''
  let finished = false

  while (!finished) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    // decode 时开启 stream，避免多字节字符被拆包后乱码
    buffer += decoder.decode(value, { stream: true })
    // 统一换行符，避免 \r\n 导致帧切分失败
    buffer = buffer.replace(/\r\n/g, '\n')
    const frames = buffer.split('\n\n')
    // 最后一段可能是不完整帧，留到下一次读取后拼接
    buffer = frames.pop() ?? ''
    for (const frameText of frames) {
      const frame = parseFrame(frameText)
      // 后端在流末尾额外发送 event: done，主动告知生成完成
      if (frame.event === 'done') {
        finished = true
        break
      }
      const chunk = parseChunk(frame.data)
      if (!chunk) {
        continue
      }
      content += chunk
      onChunk?.(chunk)
    }
  }
  // 流结束时同步取消底层读取，释放连接
  await reader.cancel().catch(() => undefined)
  onDone?.(content)
  return content
}
