/**
 * Markdown 渲染与代码高亮
 *
 * 1. marked：把 AI 输出的 markdown 文本转成 HTML；
 * 2. DOMPurify：净化 marked 产物，避免 AI 输出里的脚本 / 事件属性通过 v-html 注入页面；
 * 3. highlight.js：代码高亮，按需注册语言（只引入用到的语言，避免全量引入造成包体积膨胀）。
 *
 * 渲染与净化拆成两步（renderMarkdownToHtml / sanitizeHtml），
 * 便于在没有 DOM 的环境（如单元测试）中复用渲染逻辑。
 */
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { marked } from 'marked'
import { normalizeCodeLanguage } from '@/utils/messageContent'
// 高亮主题：highlight.js 生成的类名依赖主题样式，这里作为渲染器的配套样式全局引入
import 'highlight.js/styles/atom-one-dark.css'

/** 按需注册语言：html / vue 都复用 xml 语法（与 messageContent 的语言归一保持一致） */
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)

/** DOMPurify 配置：只放行基础 HTML 标签，链接额外允许 target 属性 */
const DOMPURIFY_CONFIG = {
  USE_PROFILES: { html: true },
  ADD_ATTR: ['target'],
}

/** 转义 HTML 特殊字符（无语言或语言未注册时按纯文本展示） */
const escapeHtml = (text: string): string =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

/**
 * 代码高亮
 *
 * @param code 代码内容
 * @param language 语言（可为空；未标注或未注册的语言按纯文本转义输出）
 * @returns 高亮后的 HTML 片段（可直接配合 v-html 使用）
 */
export const highlightCode = (code: string, language?: string): string => {
  const normalized = normalizeCodeLanguage(language)
  if (normalized && hljs.getLanguage(normalized)) {
    try {
      return hljs.highlight(code, { language: normalized, ignoreIllegals: true }).value
    } catch {
      // 高亮失败（如语言定义异常）时降级为纯文本，保证内容仍可阅读
      return escapeHtml(code)
    }
  }
  return escapeHtml(code)
}

/**
 * markdown -> HTML（未净化）
 *
 * 配置说明：开启 gfm（表格 / 删除线）与 breaks（单个换行展示为 <br>，贴近 AI 输出的分段习惯）；
 * 围栏代码块交给 highlight.js 高亮，行内代码与其它语法沿用 marked 默认渲染。
 */
marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    code({ text, lang }) {
      const language = normalizeCodeLanguage(lang)
      const languageClass = language ? ` language-${language}` : ''
      return `<pre class="hljs"><code class="hljs${languageClass}">${highlightCode(text, language)}</code></pre>\n`
    },
  },
})

export const renderMarkdownToHtml = (text: string): string =>
  marked.parse(text, { async: false }) as string

/**
 * 净化 HTML（DOMPurify）
 *
 * 在浏览器中 DOMPurify 已完成初始化（依赖 window）；若运行环境没有 DOM，
 * 则不做净化直接返回，避免在非浏览器环境下抛错（浏览器渲染路径始终会净化）。
 */
export const sanitizeHtml = (html: string): string =>
  DOMPurify.isSupported ? DOMPurify.sanitize(html, DOMPURIFY_CONFIG) : html

/**
 * markdown -> 可安全 v-html 的 HTML
 *
 * @param text AI 输出的 markdown 文本
 */
export const renderMarkdown = (text: string): string => sanitizeHtml(renderMarkdownToHtml(text))
