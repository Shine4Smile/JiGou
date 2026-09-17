import { API_BASE_URL } from '@/request'

/**
 * 应用预览地址（浏览 AI 生成的网站效果）
 *
 * 后端 StaticResourceController 会将 /api/static/{codeGenType}_{appId}/ 映射到
 * 代码生成根目录下的同名目录，并默认返回该目录下的 index.html
 */
export const getPreviewUrl = (codeGenType?: string, appId?: string | number) => {
  return `${API_BASE_URL}/static/${codeGenType ?? ''}_${appId ?? ''}/`
}

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
