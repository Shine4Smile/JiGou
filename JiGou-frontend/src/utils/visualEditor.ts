/**
 * 预览页可视化编辑工具
 *
 * 应用对话页右侧的预览 iframe 中展示的是 AI 生成的网站（后端直接返回的静态 HTML），
 * 无法提前埋点，因此可视化编辑采用「主站向 iframe 注入编辑脚本」的方案：
 *
 * 主站 --> iframe：TOGGLE_EDIT_MODE（开关编辑模式）/ CLEAR_SELECTION（取消选中）/
 *                  CLEAR_ALL_EFFECTS（清除全部编辑效果）
 * iframe --> 主站：ELEMENT_SELECTED（点击选中的元素信息）/ EDIT_MODE_CHANGED（iframe 内按 ESC 退出）
 *
 * 注入脚本依赖 iframe.contentDocument，因此要求预览页与主站同源：
 * 开发环境由 vite.config.ts 的 server.proxy 把 /api 代理到后端，生产环境由 nginx 同域代理。
 */

/** 注入脚本的 script 元素 id：同一文档重复开启编辑模式时据此判断是否已注入过 */
const EDITOR_SCRIPT_ID = 'visual-editor-script'

/** 编辑样式与编辑提示的元素 id（均位于 iframe 文档内部） */
const EDITOR_STYLE_ID = 'visual-editor-style'
const EDITOR_TIP_ID = 'visual-editor-tip'

/** 悬浮 / 选中效果使用的类名（生成选择器时会过滤掉带此前缀的类名） */
const HOVER_CLASS_NAME = 'visual-editor-hover'
const SELECTED_CLASS_NAME = 'visual-editor-selected'
const CLASS_NAME_PREFIX = 'visual-editor-'

/** 注入脚本的重试间隔与最大次数：iframe 触发 load 时文档可能仍未就绪 */
const INJECT_RETRY_INTERVAL = 100
const INJECT_MAX_ATTEMPTS = 20

/** 回传的元素文本最大长度，避免提示词过长 */
const MAX_TEXT_LENGTH = 100

/** 生成选择器时的最大层级，避免选择器过长 */
const MAX_SELECTOR_DEPTH = 6

/** 编辑提示自动消失时间（毫秒） */
const TIP_DURATION = 3000

/** iframe -> 主站 的消息类型 */
export const VISUAL_EDITOR_MESSAGE = {
  /** 用户点击选中了某个元素 */
  ELEMENT_SELECTED: 'ELEMENT_SELECTED',
  /** iframe 内按 ESC 退出编辑模式，主站据此同步按钮状态 */
  EDIT_MODE_CHANGED: 'EDIT_MODE_CHANGED',
} as const

/** 主站 -> iframe 的消息类型 */
export const VISUAL_EDITOR_COMMAND = {
  /** 开关编辑模式 */
  TOGGLE_EDIT_MODE: 'TOGGLE_EDIT_MODE',
  /** 清除选中元素及其高亮（关闭输入框上方的 alert 时触发） */
  CLEAR_SELECTION: 'CLEAR_SELECTION',
  /** 清除全部编辑效果并退出编辑模式 */
  CLEAR_ALL_EFFECTS: 'CLEAR_ALL_EFFECTS',
} as const

/**
 * 选中元素信息
 *
 * 由 iframe 内的注入脚本采集，经 postMessage 回传主站后：
 * 1. 展示在输入框上方的 alert 中，让用户确认选中的是哪块区域；
 * 2. 发送消息时由 buildElementPrompt 拼进提示词，交给后端让 AI 定位到具体元素。
 */
export type ElementInfo = {
  /** 标签名（小写，如 div） */
  tagName: string
  /** 元素 id（无 id 时为空字符串） */
  id: string
  /** 元素 class 字符串（无 class 时为空字符串） */
  className: string
  /** 元素文本（已压缩空白并截断） */
  textContent: string
  /** 用于定位元素的 CSS 选择器 */
  selector: string
  /** 页面路径（相对预览根目录，含 query 与 hash，用于多页面站点） */
  pagePath: string
}

/** 可视化编辑器回调 */
export type VisualEditorOptions = {
  /** 选中元素回调（在预览页点击元素时触发） */
  onElementSelected?: (elementInfo: ElementInfo) => void
  /** 编辑模式变化回调（iframe 内按 ESC 退出时触发，用于同步主站按钮状态） */
  onEditModeChanged?: (editMode: boolean) => void
}

/** 判断是否为普通对象（postMessage 的消息体不可信，需要逐项校验） */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

/** 把 iframe 回传的原始数据转换为 ElementInfo，结构不完整时返回 null */
const toElementInfo = (value: unknown): ElementInfo | null => {
  if (!isRecord(value)) {
    return null
  }
  const { tagName, selector } = value
  if (typeof tagName !== 'string' || typeof selector !== 'string') {
    return null
  }
  const pickString = (field: unknown) => (typeof field === 'string' ? field : '')
  return {
    tagName,
    selector,
    id: pickString(value.id),
    className: pickString(value.className),
    textContent: pickString(value.textContent),
    pagePath: pickString(value.pagePath),
  }
}

/** 延时（毫秒） */
const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

/** 元素信息提示词的分隔标题（buildElementPrompt 与 parseElementPrompt 共用，保证编解码同源） */
export const ELEMENT_PROMPT_TITLE = '选中元素信息：'

/** 元素信息行的键（与 buildElementPrompt 生成的键一致，反解时用于严格校验） */
const ELEMENT_PROMPT_KEYS: readonly string[] = ['页面路径', '标签', '选择器', '当前内容']

/** 从用户消息中反解出的元素信息（仅用于展示，字段可能缺省） */
export type ElementPromptInfo = {
  /** 页面路径（选择单页站点中的元素时为空） */
  pagePath?: string
  /** 标签名 */
  tagName: string
  /** 用于定位元素的 CSS 选择器 */
  selector: string
  /** 元素文本（元素没有文本时为空） */
  textContent?: string
}

/** 用户消息解析结果：用户原话 + 元素信息（消息里没有元素信息时 element 为 null、content 为原文） */
export type ParsedUserPrompt = {
  /** 用户输入的原话（不含元素信息） */
  content: string
  /** 可视化编辑选中的元素信息 */
  element: ElementPromptInfo | null
}

/**
 * 元素摘要（标签 + id + 最多两个 class）
 *
 * 用于输入框上方的选中信息标题与消息气泡上的「引用元素」标识：
 * - ElementInfo（iframe 回传）带有 id / className，直接拼成形如 h1#title.profile-name 的摘要；
 * - 从提示词反解出的元素信息只有选择器，取选择器末段并去掉伪类（形如 div.card:nth-child(2) -> div.card）。
 *
 * @param info 元素信息（id / className 缺省时按选择器推断）
 */
export const formatElementLabel = (info: {
  tagName: string
  id?: string
  className?: string
  selector?: string
}) => {
  const classNames = (info.className ?? '').split(/\s+/).filter(Boolean).slice(0, 2)
  const idPart = info.id ? `#${info.id}` : ''
  const classPart = classNames.length ? `.${classNames.join('.')}` : ''
  if (idPart || classPart) {
    return `${info.tagName}${idPart}${classPart}`
  }
  const tail = (info.selector ?? '').split(/(?:\s*>\s*|\s+)/).pop() ?? ''
  const label = tail.replace(/:{1,2}[\w-]+(\([^)]*\))?/g, '').trim()
  return label || info.tagName.toLowerCase()
}

/**
 * 把选中元素信息拼装为提示词上下文
 *
 * 形如：
 *   帮我改成红色的按钮
 *
 *   选中元素信息：
 *   - 页面路径: about.html
 *   - 标签: button
 *   - 选择器: div.card:nth-child(2) > button.primary:nth-child(1)
 *   - 当前内容: 立即购买
 *
 * 展示时由 parseElementPrompt 反解成「用户原话 + 元素信息」两段，拼接格式不要随意调整。
 *
 * @param elementInfo 选中元素信息
 */
export const buildElementPrompt = (elementInfo: ElementInfo) => {
  const lines = ['', '', ELEMENT_PROMPT_TITLE]
  if (elementInfo.pagePath) {
    lines.push(`- 页面路径: ${elementInfo.pagePath}`)
  }
  lines.push(`- 标签: ${elementInfo.tagName.toLowerCase()}`)
  lines.push(`- 选择器: ${elementInfo.selector}`)
  if (elementInfo.textContent) {
    lines.push(`- 当前内容: ${elementInfo.textContent}`)
  }
  return lines.join('\n')
}

/**
 * 从用户消息中反解出「用户原话」与「选中元素信息」
 *
 * 用户消息（含可视化编辑选中的元素）发给后端时是「用户输入 + 元素信息」的拼接文本，
 * 落库后历史消息与实时消息格式一致，因此展示时统一在这里还原：
 * 1. 只在消息尾部按分隔标题定位（用户在输入框里的换行不影响定位），找不到时原样返回；
 * 2. 标题后的每一行都必须是 `- 键: 值`（键限定为 ELEMENT_PROMPT_KEYS），
 *    出现任何不认识的行即判定为普通文本并原样返回，避免误切用户手写的相似内容；
 * 3. 必须同时解析出「标签」与「选择器」才认为是元素信息。
 *
 * @param message 用户消息原文
 */
export const parseElementPrompt = (message: string): ParsedUserPrompt => {
  const fallback: ParsedUserPrompt = { content: message, element: null }
  const separator = `\n\n${ELEMENT_PROMPT_TITLE}\n`
  const detailStart = message.lastIndexOf(separator)
  if (detailStart < 0) {
    return fallback
  }
  const fields = new Map<string, string>()
  for (const line of message.slice(detailStart + separator.length).split('\n')) {
    const matched = /^- ([^:]+): (.*)$/.exec(line)
    if (!matched || !ELEMENT_PROMPT_KEYS.includes(matched[1])) {
      return fallback
    }
    fields.set(matched[1], matched[2])
  }
  const tagName = fields.get('标签')
  const selector = fields.get('选择器')
  if (!tagName || !selector) {
    return fallback
  }
  return {
    content: message.slice(0, detailStart).trimEnd(),
    element: {
      tagName,
      selector,
      pagePath: fields.get('页面路径') || undefined,
      textContent: fields.get('当前内容') || undefined,
    },
  }
}

/**
 * 生成注入到 iframe 内的编辑脚本
 *
 * 脚本运行在预览页中，负责：
 * 1. 悬浮元素时加虚线高亮，点击时固定为加深的实线高亮，并阻止预览页自身的交互与跳转；
 * 2. 采集元素信息（选择器 / 文本 / 页面路径）后 postMessage 回主站；
 * 3. 响应主站指令（开关编辑模式 / 取消选中 / 清除全部效果），并支持 ESC 退出。
 *
 * 注意：脚本以字符串形式注入，内部不使用模板字符串，转义字符统一写成双反斜杠
 * （形如 /\\s+/），避免被外层模板字符串提前转义。
 *
 * @param basePath 预览页所在目录的路径，用于计算相对页面路径
 */
const buildEditorScript = (basePath: string) => `(function () {
  var COMMAND = ${JSON.stringify(VISUAL_EDITOR_COMMAND)};
  var MESSAGE = ${JSON.stringify(VISUAL_EDITOR_MESSAGE)};
  var BASE_PATH = ${JSON.stringify(basePath)};
  var EDITOR_STYLE_ID = '${EDITOR_STYLE_ID}';
  var EDITOR_TIP_ID = '${EDITOR_TIP_ID}';
  var HOVER_CLASS_NAME = '${HOVER_CLASS_NAME}';
  var SELECTED_CLASS_NAME = '${SELECTED_CLASS_NAME}';
  var CLASS_NAME_PREFIX = '${CLASS_NAME_PREFIX}';
  var MAX_TEXT_LENGTH = ${MAX_TEXT_LENGTH};
  var MAX_SELECTOR_DEPTH = ${MAX_SELECTOR_DEPTH};
  var TIP_DURATION = ${TIP_DURATION};

  var editMode = true;
  var hoverElement = null;
  var selectedElement = null;
  var tipTimer = null;

  /** 注入编辑态样式：outline 不占布局空间，不会导致预览页元素位移 */
  function injectStyles() {
    if (document.getElementById(EDITOR_STYLE_ID)) {
      return;
    }
    var style = document.createElement('style');
    style.id = EDITOR_STYLE_ID;
    style.textContent =
      '.' + HOVER_CLASS_NAME + ' { outline: 2px dashed #1677ff !important; outline-offset: 2px !important; cursor: crosshair !important; }' +
      '.' + SELECTED_CLASS_NAME + ' { outline: 2px solid #0958d9 !important; outline-offset: 2px !important; }' +
      '#' + EDITOR_TIP_ID + ' { position: fixed; top: 16px; left: 50%; z-index: 2147483647; padding: 8px 14px;' +
      ' border-radius: 6px; background: rgba(0, 0, 0, 0.75); color: #fff; font-size: 12px; line-height: 1.5;' +
      ' font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; pointer-events: none;' +
      ' opacity: 0; transform: translateX(-50%); transition: opacity 0.3s ease; }' +
      '.' + CLASS_NAME_PREFIX + 'tip-visible { opacity: 1 !important; }';
    var parent = document.head || document.documentElement;
    if (parent) {
      parent.appendChild(style);
    }
  }

  /** 编辑提示：自动消失，避免长期遮挡预览内容 */
  function removeTip() {
    var tip = document.getElementById(EDITOR_TIP_ID);
    if (tip && tip.parentNode) {
      tip.parentNode.removeChild(tip);
    }
    if (tipTimer) {
      window.clearTimeout(tipTimer);
      tipTimer = null;
    }
  }

  function showTip() {
    removeTip();
    if (!document.body) {
      return;
    }
    var tip = document.createElement('div');
    tip.id = EDITOR_TIP_ID;
    tip.textContent = '编辑模式已开启：悬浮查看元素，点击选中，按 ESC 退出';
    document.body.appendChild(tip);
    window.requestAnimationFrame(function () {
      tip.className = CLASS_NAME_PREFIX + 'tip-visible';
    });
    tipTimer = window.setTimeout(removeTip, TIP_DURATION);
  }

  /** 元素 class 列表：过滤编辑器自身添加的类名，并兼容 SVG 的 className 对象 */
  function getClassNames(element) {
    var raw = typeof element.className === 'string'
      ? element.className
      : element.getAttribute('class') || '';
    return raw.split(/\\s+/).filter(function (name) {
      return name && name.indexOf(CLASS_NAME_PREFIX) !== 0;
    });
  }

  /** 生成元素选择器：优先 id，其次 class + nth-child，最多向上追溯 MAX_SELECTOR_DEPTH 层 */
  function generateSelector(element) {
    var parts = [];
    var current = element;
    var depth = 0;
    while (current && current.nodeType === 1 && depth < MAX_SELECTOR_DEPTH) {
      var tagName = current.tagName ? current.tagName.toLowerCase() : '';
      if (!tagName || tagName === 'body' || tagName === 'html') {
        break;
      }
      var selector = tagName;
      if (current.id) {
        selector += '#' + current.id;
        parts.unshift(selector);
        break;
      }
      var classNames = getClassNames(current);
      if (classNames.length) {
        selector += '.' + classNames.join('.');
      }
      var parent = current.parentElement;
      if (parent) {
        selector += ':nth-child(' + (Array.prototype.indexOf.call(parent.children, current) + 1) + ')';
      }
      parts.unshift(selector);
      current = parent;
      depth += 1;
    }
    return parts.join(' > ');
  }

  /** 页面路径：相对预览根目录的路径（入口 index.html 省略）+ query + hash，便于多页面站点定位 */
  function getPagePath() {
    var pathname = window.location.pathname || '';
    var relative = BASE_PATH && pathname.indexOf(BASE_PATH) === 0
      ? pathname.slice(BASE_PATH.length)
      : pathname;
    if (relative.length >= 10 && relative.slice(-10) === 'index.html') {
      relative = '';
    }
    return relative + (window.location.search || '') + (window.location.hash || '');
  }

  function getElementInfo(element) {
    var text = element.textContent ? element.textContent.replace(/\\s+/g, ' ').trim() : '';
    if (text.length > MAX_TEXT_LENGTH) {
      text = text.slice(0, MAX_TEXT_LENGTH) + '...';
    }
    return {
      tagName: element.tagName ? element.tagName.toLowerCase() : '',
      id: element.id || '',
      className: getClassNames(element).join(' '),
      textContent: text,
      selector: generateSelector(element),
      pagePath: getPagePath()
    };
  }

  function postToParent(type, data) {
    try {
      window.parent.postMessage({ type: type, data: data }, window.location.origin);
    } catch (ignored) {
      // 忽略发送失败：预览页可能已被销毁
    }
  }

  function clearHover() {
    if (hoverElement) {
      hoverElement.classList.remove(HOVER_CLASS_NAME);
      hoverElement = null;
    }
  }

  function clearSelected() {
    if (selectedElement) {
      selectedElement.classList.remove(SELECTED_CLASS_NAME);
      selectedElement = null;
    }
  }

  /** 是否是可选中元素：排除文档结构标签与编辑器自身节点 */
  function isEditable(target) {
    if (!target || target.nodeType !== 1) {
      return false;
    }
    var tagName = target.tagName;
    if (tagName === 'HTML' || tagName === 'BODY' || tagName === 'HEAD' || tagName === 'SCRIPT' ||
      tagName === 'STYLE' || tagName === 'LINK' || tagName === 'META' || tagName === 'TITLE') {
      return false;
    }
    return target.id !== EDITOR_TIP_ID && target.id !== EDITOR_STYLE_ID;
  }

  function setEditMode(next) {
    editMode = !!next;
    if (editMode) {
      showTip();
      return;
    }
    // 退出编辑模式只移除悬浮效果，保留选中高亮，便于用户填写修改需求时确认选中的是哪块区域
    clearHover();
    removeTip();
  }

  function onMouseOver(event) {
    if (!editMode) {
      return;
    }
    var target = event.target;
    if (!isEditable(target) || target === hoverElement || target === selectedElement) {
      return;
    }
    clearHover();
    target.classList.add(HOVER_CLASS_NAME);
    hoverElement = target;
  }

  function onMouseOut(event) {
    if (!editMode) {
      return;
    }
    var target = event.target;
    if (target !== hoverElement) {
      return;
    }
    var related = event.relatedTarget;
    // 在元素内部移动时不取消高亮
    if (related && related.nodeType === 1 && target.contains(related)) {
      return;
    }
    clearHover();
  }

  function onClick(event) {
    if (!editMode) {
      return;
    }
    var target = event.target;
    if (!isEditable(target)) {
      return;
    }
    // 选中优先于预览页自身的交互：阻止默认行为与后续传播，预览页的点击事件不会再触发
    event.preventDefault();
    event.stopPropagation();
    clearSelected();
    clearHover();
    target.classList.add(SELECTED_CLASS_NAME);
    selectedElement = target;
    postToParent(MESSAGE.ELEMENT_SELECTED, { elementInfo: getElementInfo(target) });
  }

  function onKeyDown(event) {
    if (!editMode || event.key !== 'Escape') {
      return;
    }
    setEditMode(false);
    // 通知主站同步按钮状态
    postToParent(MESSAGE.EDIT_MODE_CHANGED, { editMode: false });
  }

  function onMessage(event) {
    // 只接受来自父窗口且同源的消息，避免被其它页面伪造指令
    if (event.source !== window.parent || event.origin !== window.location.origin) {
      return;
    }
    var message = event.data || {};
    if (!message.type) {
      return;
    }
    var payload = message.data || {};
    if (message.type === COMMAND.TOGGLE_EDIT_MODE) {
      setEditMode(!!payload.editMode);
      return;
    }
    if (message.type === COMMAND.CLEAR_SELECTION) {
      clearSelected();
      return;
    }
    if (message.type === COMMAND.CLEAR_ALL_EFFECTS) {
      setEditMode(false);
      clearSelected();
    }
  }

  window.addEventListener('message', onMessage);
  document.addEventListener('mouseover', onMouseOver, true);
  document.addEventListener('mouseout', onMouseOut, true);
  document.addEventListener('click', onClick, true);
  document.addEventListener('keydown', onKeyDown, true);

  injectStyles();
  showTip();
})();`

/**
 * 可视化编辑器
 *
 * 负责主站与预览 iframe 之间的消息通信与编辑脚本注入，页面只需：
 * 1. attach(iframe) 绑定预览 iframe（iframe 因刷新被重建时要重新绑定）；
 * 2. enableEditMode() / disableEditMode() 切换编辑模式；
 * 3. clearSelection() / clearAllEffects() 清理预览页中的高亮；
 * 4. dispose() 在页面卸载时移除监听。
 */
export class VisualEditor {
  /** 预览 iframe（刷新预览会重建元素，attach 时更新引用） */
  private iframe: HTMLIFrameElement | null = null

  /** 当前是否处于编辑模式（与 iframe 内部状态保持一致） */
  private editMode = false

  /** 是否已注册 window 消息监听，避免重复注册 */
  private listening = false

  /** 注入任务序号：序号变化说明注入任务已过期（iframe 重建 / 退出编辑 / 页面卸载） */
  private injectToken = 0

  private readonly options: VisualEditorOptions

  /**
   * window 消息监听
   *
   * 使用箭头函数属性保存，保证 addEventListener 与 removeEventListener 传入的是同一个引用。
   */
  private readonly handleWindowMessage = (event: MessageEvent) => {
    this.onWindowMessage(event)
  }

  constructor(options: VisualEditorOptions = {}) {
    this.options = options
  }

  /** 当前是否处于编辑模式 */
  isEditMode() {
    return this.editMode
  }

  /** 绑定（或重新绑定）预览 iframe，并注册 window 消息监听 */
  attach(iframe: HTMLIFrameElement | null) {
    this.iframe = iframe
    if (!iframe || this.listening) {
      return
    }
    window.addEventListener('message', this.handleWindowMessage)
    this.listening = true
  }

  /**
   * iframe 加载完成后的处理
   *
   * iframe 重建后，文档里的编辑脚本与所有高亮都会随文档一起消失：
   * - 编辑模式下重新注入脚本，保证刷新预览后仍能继续选中元素；
   * - 非编辑模式下无需处理，新文档本身不含任何编辑痕迹。
   */
  handleIframeLoad() {
    if (!this.editMode) {
      return
    }
    void this.injectEditorScript()
  }

  /**
   * 开启编辑模式
   *
   * @returns 是否开启成功：预览 iframe 不存在、与主站跨域或文档未就绪时返回 false（同时保持未编辑状态）
   */
  async enableEditMode(): Promise<boolean> {
    if (!this.iframe) {
      return false
    }
    const token = (this.injectToken += 1)
    const injected = await this.injectEditorScript(token)
    if (!injected) {
      this.editMode = false
      return false
    }
    this.editMode = true
    // 脚本注入时会直接进入编辑态，这里再发一次指令：对「同一文档二次进入编辑模式」同样有效
    this.postToIframe(VISUAL_EDITOR_COMMAND.TOGGLE_EDIT_MODE, { editMode: true })
    return true
  }

  /**
   * 关闭编辑模式
   *
   * 只退出编辑态并移除悬浮高亮，保留已选中元素的高亮：
   * 用户退出编辑后仍能看到自己选中的是哪块区域，直到清除选中或发送消息。
   */
  disableEditMode() {
    this.editMode = false
    this.injectToken += 1
    this.postToIframe(VISUAL_EDITOR_COMMAND.TOGGLE_EDIT_MODE, { editMode: false })
  }

  /** 清除选中元素及其高亮（预览页仍保持编辑模式） */
  clearSelection() {
    this.postToIframe(VISUAL_EDITOR_COMMAND.CLEAR_SELECTION)
  }

  /** 清除全部编辑效果：退出编辑模式并移除所有高亮（发送消息、重建预览时调用） */
  clearAllEffects() {
    this.editMode = false
    this.injectToken += 1
    this.postToIframe(VISUAL_EDITOR_COMMAND.CLEAR_ALL_EFFECTS)
  }

  /** 释放资源：移除 window 消息监听并终止未完成的注入任务 */
  dispose() {
    this.editMode = false
    this.injectToken += 1
    if (this.listening) {
      window.removeEventListener('message', this.handleWindowMessage)
      this.listening = false
    }
    this.iframe = null
  }

  /** 处理 iframe 回传的消息（选中元素 / ESC 退出编辑模式） */
  private onWindowMessage(event: MessageEvent) {
    // 只处理来自预览 iframe 的消息，避免页面中其它 iframe 或弹窗伪造选中数据
    if (!this.iframe || event.source !== this.iframe.contentWindow) {
      return
    }
    const message = event.data as unknown
    if (!isRecord(message) || typeof message.type !== 'string') {
      return
    }
    const payload = isRecord(message.data) ? message.data : {}
    switch (message.type) {
      case VISUAL_EDITOR_MESSAGE.ELEMENT_SELECTED: {
        const elementInfo = toElementInfo(payload.elementInfo)
        if (elementInfo) {
          this.options.onElementSelected?.(elementInfo)
        }
        break
      }
      case VISUAL_EDITOR_MESSAGE.EDIT_MODE_CHANGED: {
        const editMode = payload.editMode === true
        this.editMode = editMode
        this.options.onEditModeChanged?.(editMode)
        break
      }
      default:
        break
    }
  }

  /** 向预览 iframe 发送指令（要求同源，跨域时消息会被浏览器丢弃） */
  private postToIframe(type: string, data?: Record<string, unknown>) {
    const target = this.iframe?.contentWindow
    if (!target) {
      return
    }
    target.postMessage({ type, data }, window.location.origin)
  }

  /** 注入编辑脚本：文档尚未就绪时按固定间隔重试，超过重试上限返回 false */
  private async injectEditorScript(token = this.injectToken): Promise<boolean> {
    for (let attempt = 0; attempt < INJECT_MAX_ATTEMPTS; attempt += 1) {
      // 注入任务已过期（iframe 重建 / 退出编辑模式 / 页面卸载）时立即终止
      if (token !== this.injectToken || !this.iframe) {
        return false
      }
      const doc = this.getIframeDocument()
      if (doc?.body) {
        return this.appendEditorScript(doc)
      }
      await delay(INJECT_RETRY_INTERVAL)
    }
    return false
  }

  /** 读取 iframe 文档：跨域或文档已销毁时返回 null */
  private getIframeDocument(): Document | null {
    try {
      return this.iframe?.contentDocument ?? null
    } catch {
      return null
    }
  }

  /** iframe 中预览页所在目录路径（作为页面路径的基准），跨域时返回空字符串 */
  private getIframeBasePath() {
    try {
      const pathname = this.iframe?.contentWindow?.location.pathname ?? ''
      return pathname.slice(0, pathname.lastIndexOf('/') + 1)
    } catch {
      return ''
    }
  }

  /** 把编辑脚本追加到 iframe 文档（已注入过则直接返回成功，避免重复绑定事件） */
  private appendEditorScript(doc: Document): boolean {
    if (doc.getElementById(EDITOR_SCRIPT_ID)) {
      return true
    }
    const parent = doc.head ?? doc.documentElement
    if (!parent) {
      return false
    }
    const script = doc.createElement('script')
    script.id = EDITOR_SCRIPT_ID
    script.textContent = buildEditorScript(this.getIframeBasePath())
    parent.appendChild(script)
    return true
  }
}
