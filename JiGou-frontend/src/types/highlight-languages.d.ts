/**
 * highlight.js 的语言文件（highlight.js/lib/languages/*）没有随包提供类型声明，
 * 这里统一声明为 LanguageFn，以便按需注册语言（避免全量引入 highlight.js 造成包体积膨胀）
 */
declare module 'highlight.js/lib/languages/*' {
  import type { LanguageFn } from 'highlight.js'
  const language: LanguageFn
  export default language
}
