/**
 * 应用代码下载工具
 *
 * 后端 GET /app/download/{appId} 会把应用工作区代码打包成 ZIP 直接写入响应流，
 * 响应头形如：
 *   Content-Type: application/zip
 *   Content-Disposition: attachment; filename="{appId}.zip"
 *
 * 这里需要注意两点（与普通 JSON 接口不同）：
 * 1. 必须按二进制（responseType: 'blob'）接收，否则 axios 会按 JSON 解析导致文件损坏；
 * 2. 打包成功只体现在响应头与响应体上，后端业务异常（应用不存在、无权限、代码未生成等）
 *    仍由全局异常处理器返回 HTTP 200 + JSON 错误体，因此要按 Content-Type 区分，
 *    把错误信息读出来提示用户，而不是把错误 JSON 当成 ZIP 下载。
 */
import axios from 'axios'
import { downloadAppCode } from '@/api/appController.ts'
import { asApiId } from '@/utils/apiId'

/** 下载结果：由调用方决定提示文案与失败处理 */
export type AppCodeDownloadResult = {
  /** 是否已成功触发下载 */
  success: boolean
  /** 失败原因或下载文件名（由调用方决定如何展示） */
  message: string
  /** 后端返回的错误码（如 40100 未登录、40101 无权限），成功时为空 */
  code?: number
}

/** 延迟释放 Blob 地址，避免部分浏览器还没开始下载就回收了资源 */
const REVOKE_DELAY = 1000

/**
 * 兜底下载文件名
 *
 * 后端固定以应用 id 命名（fileName 中不含中文），正常情况下一定能从
 * Content-Disposition 解析到；仅在响应头缺失时使用该兜底命名
 *
 * @param appId 应用 id
 */
const getFallbackFileName = (appId: string | number) => `app-${appId}.zip`

/**
 * 解析 Content-Disposition 中的文件名
 *
 * 兼容 attachment; filename="xxx.zip" 与 filename*=UTF-8''xxx.zip 两种写法
 *
 * @param disposition 响应头 Content-Disposition 的值
 * @returns 解析出的文件名，解析失败时返回空字符串
 */
const parseFileName = (disposition?: string) => {
  if (!disposition) {
    return ''
  }
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  const fileName = match?.[1]?.trim()
  if (!fileName) {
    return ''
  }
  try {
    return decodeURIComponent(fileName)
  } catch {
    // 文件名未编码时 decodeURIComponent 会抛错，此时原样使用
    return fileName
  }
}

/**
 * 读取错误响应体（JSON Blob），取出后端统一返回的错误信息
 *
 * @param data 二进制响应体
 */
const parseErrorBody = async (data: Blob): Promise<{ message?: string; code?: number }> => {
  try {
    const text = await data.text()
    const parsed = JSON.parse(text) as { message?: string; code?: number }
    return { message: parsed?.message, code: parsed?.code }
  } catch {
    // 非 JSON（如后端写入响应流中途失败）时无法取到具体原因，交由调用方给通用提示
    return {}
  }
}

/**
 * 触发浏览器保存文件
 *
 * @param blob     文件内容
 * @param fileName 保存的文件名
 */
const saveBlobAsFile = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  // 部分浏览器要求链接在文档中才会触发下载
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY)
}

/**
 * 下载应用代码（后端打包为 ZIP 返回）
 *
 * 后端仅允许应用创建者下载，因此调用方需要在发起前做好身份判断，
 * 未登录（40100）等场景由调用方决定跳转登录还是提示
 *
 * @param appId 应用 id（雪花 id，全程按字符串透传，避免精度丢失）
 */
export const downloadAppCodeZip = async (
  appId: string | number,
): Promise<AppCodeDownloadResult> => {
  try {
    const res = await downloadAppCode(
      { appId: asApiId(appId) },
      // ZIP 包按二进制接收，同时保留响应头以读取后端指定的文件名
      { responseType: 'blob' },
    )
    const contentType = String(res.headers['content-type'] ?? '')
    // 业务异常：后端返回 JSON 错误体（HTTP 状态码仍为 200），读出提示信息
    if (contentType.includes('application/json')) {
      const { message, code } = await parseErrorBody(res.data as Blob)
      return { success: false, message: message ?? '请稍后重试', code }
    }
    const fileName = parseFileName(res.headers['content-disposition']) || getFallbackFileName(appId)
    saveBlobAsFile(res.data as Blob, fileName)
    return { success: true, message: fileName }
  } catch (error) {
    // 非 2xx 响应（如应用代码目录缺失时的 404）响应体同样是后端统一错误体，尽量把真实原因提示给用户
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const { message, code } = await parseErrorBody(error.response.data)
      return { success: false, message: message ?? '请稍后重试', code }
    }
    return { success: false, message: '请检查网络后重试' }
  }
}
