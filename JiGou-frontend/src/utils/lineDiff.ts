/**
 * 行级文本对比（自研实现，不依赖第三方 diff 库）
 *
 * 采用经典的 LCS（最长公共子序列）动态规划算法：
 * 1. 先裁剪掉开头与结尾的公共行（代码修改往往集中在局部，裁剪后参与 DP 的行数会大幅减少）；
 * 2. 对中间部分做 DP 求 LCS 长度，再回溯得到「保留 / 删除 / 新增」三类操作；
 * 3. 若中间部分规模超过阈值，退化为「整体替换」，避免大文件造成内存与耗时爆炸。
 *
 * 输出的 DiffLine 同时携带新旧行号，便于在页面中像 Git 一样展示行号列。
 */

/** 单行差异类型：add 新增行、del 删除行、context 未变化的上下文行 */
export type DiffLineType = 'add' | 'del' | 'context'

/** 文件差异类型：added 新增文件、deleted 删除文件、modified 内容修改 */
export type FileDiffStatus = 'added' | 'deleted' | 'modified'

/** 单行差异 */
export interface DiffLine {
  /** 差异类型 */
  type: DiffLineType
  /** 行内容（不含换行符） */
  content: string
  /** 该行在旧版本中的行号（新增行没有） */
  oldLineNumber?: number
  /** 该行在新版本中的行号（删除行没有） */
  newLineNumber?: number
}

/** 单文件行级对比结果 */
export interface DiffResult {
  /** 按顺序排列的差异行 */
  lines: DiffLine[]
  /** 新增行数 */
  addedCount: number
  /** 删除行数 */
  deletedCount: number
}

/** 文件级差异 */
export interface FileDiff extends DiffResult {
  /** 文件名（相对路径） */
  fileName: string
  /** 文件差异类型 */
  status: FileDiffStatus
}

/** 整个版本的文件级对比结果 */
export interface VersionDiff {
  /** 有差异的文件（按文件名排序，内容完全相同的文件不会出现在这里） */
  files: FileDiff[]
  /** 新增文件数 */
  addedFileCount: number
  /** 删除文件数 */
  deletedFileCount: number
  /** 修改文件数 */
  modifiedFileCount: number
  /** 全部文件的新增行数合计 */
  addedCount: number
  /** 全部文件的删除行数合计 */
  deletedCount: number
}

/**
 * DP 单元上限：超过后不再做 LCS，直接按「整体替换」处理
 * 例如 1400 x 1400 行约 196 万个单元，Int32Array 占用约 7.8MB，属于可接受范围
 */
const MAX_DIFF_CELLS = 2_000_000

/** 文件差异状态 -> 展示文案 */
export const FILE_DIFF_STATUS_LABEL: Record<FileDiffStatus, string> = {
  added: '新增',
  deleted: '删除',
  modified: '修改',
}

/** 文件差异状态 -> 标签颜色 */
export const FILE_DIFF_STATUS_COLOR: Record<FileDiffStatus, string> = {
  added: 'green',
  deleted: 'red',
  modified: 'blue',
}

/**
 * 拆分文本为行数组
 *
 * 统一换行符为 \n，并且去掉「文末换行」产生的最后一个空行，
 * 使行号与编辑器展示一致（例如 "a\n" 视为 1 行而不是 2 行）
 *
 * @param text 文本内容（可为空）
 */
export const splitLines = (text?: string): string[] => {
  if (!text) {
    return []
  }
  const lines = text.replace(/\r\n?/g, '\n').split('\n')
  if (lines.length > 1 && lines[lines.length - 1] === '') {
    lines.pop()
  }
  return lines
}

/**
 * 行级对比：返回旧文本 -> 新文本的差异行序列
 *
 * @param oldText 旧文本（可为空，表示文件原先不存在）
 * @param newText 新文本（可为空，表示文件已删除）
 */
export const diffLines = (oldText?: string, newText?: string): DiffResult => {
  const oldLines = splitLines(oldText)
  const newLines = splitLines(newText)
  const lines: DiffLine[] = []
  let addedCount = 0
  let deletedCount = 0

  const pushDeleted = (content: string, oldLineNumber: number) => {
    lines.push({ type: 'del', content, oldLineNumber })
    deletedCount += 1
  }
  const pushAdded = (content: string, newLineNumber: number) => {
    lines.push({ type: 'add', content, newLineNumber })
    addedCount += 1
  }
  const pushContext = (content: string, oldLineNumber: number, newLineNumber: number) => {
    lines.push({ type: 'context', content, oldLineNumber, newLineNumber })
  }

  // 1. 裁剪公共前缀（前缀行完全相同，一定属于 LCS）
  let prefixLength = 0
  while (
    prefixLength < oldLines.length &&
    prefixLength < newLines.length &&
    oldLines[prefixLength] === newLines[prefixLength]
  ) {
    pushContext(oldLines[prefixLength], prefixLength + 1, prefixLength + 1)
    prefixLength += 1
  }

  // 2. 裁剪公共后缀（末尾行完全相同，同样属于 LCS）
  let oldSuffixStart = oldLines.length
  let newSuffixStart = newLines.length
  while (
    oldSuffixStart > prefixLength &&
    newSuffixStart > prefixLength &&
    oldLines[oldSuffixStart - 1] === newLines[newSuffixStart - 1]
  ) {
    oldSuffixStart -= 1
    newSuffixStart -= 1
  }

  const oldMiddle = oldLines.slice(prefixLength, oldSuffixStart)
  const newMiddle = newLines.slice(prefixLength, newSuffixStart)

  // 3. 中间部分做 LCS 对比，行号从 prefixLength + 1 开始
  if (oldMiddle.length * newMiddle.length > MAX_DIFF_CELLS) {
    // 差异规模过大：退化为「先整体删除、再整体新增」，保证页面可用且不会卡死
    oldMiddle.forEach((content, index) => pushDeleted(content, prefixLength + index + 1))
    newMiddle.forEach((content, index) => pushAdded(content, prefixLength + index + 1))
  } else {
    compareMiddle(oldMiddle, newMiddle, prefixLength, pushDeleted, pushAdded, pushContext)
  }

  // 4. 补回公共后缀行
  for (let index = oldSuffixStart; index < oldLines.length; index += 1) {
    pushContext(oldLines[index], index + 1, newSuffixStart + (index - oldSuffixStart) + 1)
  }

  return { lines, addedCount, deletedCount }
}

/** LCS 动态规划 + 回溯，结果通过回调按顺序输出 */
const compareMiddle = (
  oldMiddle: string[],
  newMiddle: string[],
  offset: number,
  pushDeleted: (content: string, oldLineNumber: number) => void,
  pushAdded: (content: string, newLineNumber: number) => void,
  pushContext: (content: string, oldLineNumber: number, newLineNumber: number) => void,
) => {
  const rowCount = oldMiddle.length
  const columnCount = newMiddle.length
  const width = columnCount + 1
  // dp[i * width + j] = oldMiddle[i..] 与 newMiddle[j..] 的最长公共子序列长度
  const dp = new Int32Array((rowCount + 1) * width)
  for (let i = rowCount - 1; i >= 0; i -= 1) {
    for (let j = columnCount - 1; j >= 0; j -= 1) {
      dp[i * width + j] =
        oldMiddle[i] === newMiddle[j]
          ? dp[(i + 1) * width + j + 1] + 1
          : Math.max(dp[(i + 1) * width + j], dp[i * width + j + 1])
    }
  }

  let i = 0
  let j = 0
  while (i < rowCount && j < columnCount) {
    if (oldMiddle[i] === newMiddle[j]) {
      pushContext(oldMiddle[i], offset + i + 1, offset + j + 1)
      i += 1
      j += 1
    } else if (dp[(i + 1) * width + j] >= dp[i * width + j + 1]) {
      // 删除行：两种走法等价时优先删除，展示上更符合「旧代码被替换」的直觉
      pushDeleted(oldMiddle[i], offset + i + 1)
      i += 1
    } else {
      pushAdded(newMiddle[j], offset + j + 1)
      j += 1
    }
  }
  while (i < rowCount) {
    pushDeleted(oldMiddle[i], offset + i + 1)
    i += 1
  }
  while (j < columnCount) {
    pushAdded(newMiddle[j], offset + j + 1)
    j += 1
  }
}

/**
 * 把后端返回的 fileMap（文件名 -> 文件内容）规范化为字符串 Map
 *
 * 自动生成的类型为 Record<string, any>，这里统一收敛为字符串，避免下游做类型断言
 *
 * @param fileMap 后端返回的文件内容 Map
 */
export const normalizeFileMap = (fileMap?: Record<string, unknown> | null): Record<string, string> => {
  const result: Record<string, string> = {}
  Object.entries(fileMap ?? {}).forEach(([fileName, content]) => {
    result[fileName] = typeof content === 'string' ? content : String(content ?? '')
  })
  return result
}

/**
 * 文件级对比：对比两个版本的文件集合，输出有变化的文件及其行级差异
 *
 * - 旧版本没有该文件 -> added（全部行按新增展示）
 * - 新版本没有该文件 -> deleted（全部行按删除展示）
 * - 两版本都有但内容不同 -> modified
 * - 内容完全一致的文件不会出现在结果中
 *
 * @param oldFileMap 旧版本文件内容（可为空，表示基准为「空版本」）
 * @param newFileMap 新版本文件内容（可为空，表示对比版本不存在或已被清理）
 */
export const diffFileMap = (
  oldFileMap?: Record<string, unknown> | null,
  newFileMap?: Record<string, unknown> | null,
): VersionDiff => {
  const oldMap = normalizeFileMap(oldFileMap)
  const newMap = normalizeFileMap(newFileMap)
  const fileNames = Array.from(new Set([...Object.keys(oldMap), ...Object.keys(newMap)])).sort()

  const versionDiff: VersionDiff = {
    files: [],
    addedFileCount: 0,
    deletedFileCount: 0,
    modifiedFileCount: 0,
    addedCount: 0,
    deletedCount: 0,
  }

  fileNames.forEach((fileName) => {
    const oldText = oldMap[fileName]
    const newText = newMap[fileName]
    let status: FileDiffStatus
    if (oldText === undefined) {
      status = 'added'
    } else if (newText === undefined) {
      status = 'deleted'
    } else if (oldText === newText) {
      // 内容一致：不计入差异文件
      return
    } else {
      status = 'modified'
    }

    const result = diffLines(status === 'added' ? '' : oldText, status === 'deleted' ? '' : newText)
    versionDiff.files.push({ fileName, status, ...result })
    if (status === 'added') {
      versionDiff.addedFileCount += 1
    } else if (status === 'deleted') {
      versionDiff.deletedFileCount += 1
    } else {
      versionDiff.modifiedFileCount += 1
    }
    versionDiff.addedCount += result.addedCount
    versionDiff.deletedCount += result.deletedCount
  })

  return versionDiff
}
