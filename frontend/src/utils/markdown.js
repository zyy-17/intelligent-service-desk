import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// 配置 marked：支持代码高亮、GFM 表格/删除线等
marked.setOptions({
  gfm: true,
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (e) { /* ignore */ }
    }
    return hljs.highlightAuto(code).value
  }
})

/**
 * 将 markdown 文本渲染为 HTML 字符串（供 v-html 使用）。
 * 对 XSS 做基础防护：转义原始 HTML 标签，仅保留 markdown 语法。
 */
export function renderMarkdown(text) {
  if (!text) return ''
  // 转义 < 和 > 防止注入，但保留 markdown 需要的结构
  const safe = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return marked.parse(safe)
}
