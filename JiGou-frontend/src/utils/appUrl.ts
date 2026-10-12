import { API_BASE_URL } from '@/request'
import { isVueProject } from '@/constants/app'

/**
 * 应用预览地址（浏览 AI 生成的网站效果）
 *
 * 后端 StaticResourceController 会将 /api/static/{codeGenType}_{appId}/ 映射到
 * 代码生成根目录下的同名目录，并默认返回该目录下的 index.html
 */
export const getPreviewUrl = (codeGenType?: string, appId?: string | number) => {
  const baseUrl = `${API_BASE_URL}/static/${codeGenType ?? ''}_${appId ?? ''}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (isVueProject(codeGenType)) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}

/**
 * 预览就绪探测的轮询间隔（毫秒）
 */
const PREVIEW_POLL_INTERVAL = 2000

/**
 * 预览就绪的最长等待时间（毫秒）：Vue 项目回退后需要重新安装依赖并构建，耗时较长
 */
const PREVIEW_WAIT_TIMEOUT = 120000

/** 延时（毫秒） */
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 探测预览资源是否已生成（后端文件不存在时返回 404）
 *
 * 生成结果由后端在流式返回完成后落盘，因此进入历史应用时可先探测一次，
 * 避免 iframe 直接请求一个不存在的目录
 *
 * @param previewUrl getPreviewUrl 生成的预览地址
 */
export const isPreviewAvailable = async (previewUrl: string) => {
  try {
    const res = await fetch(previewUrl, {
      method: 'GET',
      credentials: 'include',
    })
    return res.ok
  } catch {
    return false
  }
}

/**
 * 等待预览资源就绪（用于 Vue 项目回退后重新安装依赖并构建的场景）
 *
 * 版本回退后工作区只剩源码，后端会异步执行依赖安装与构建，
 * dist 生成之前 iframe 只会请求到 404，因此这里轮询探测预览地址，命中后再刷新预览
 *
 * @param previewUrl getPreviewUrl 生成的预览地址
 * @param timeout    最长等待时间（毫秒），默认 2 分钟
 * @returns 是否在超时前探测到预览资源
 */
export const waitPreviewReady = async (previewUrl: string, timeout = PREVIEW_WAIT_TIMEOUT) => {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    // 立即探测一次：非 Vue 项目或已经构建完成的场景可零等待返回
    if (await isPreviewAvailable(previewUrl)) {
      return true
    }
    await delay(PREVIEW_POLL_INTERVAL)
  }
  return false
}
