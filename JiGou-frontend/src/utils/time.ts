import dayjs from 'dayjs'

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 格式化完整时间：后端时间字段可能为空或非法，统一兜底为 '-'
 *
 * @param time 后端返回的时间字符串
 */
export const formatDateTime = (time?: string) => {
  if (!time || !dayjs(time).isValid()) {
    return '-'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

/**
 * 相对时间展示：如「刚刚 / 5 分钟前 / 5 小时前 / 3 天前」，
 * 超过 30 天则直接展示日期（用于应用卡片的「创建于 xxx」）
 *
 * @param time 后端返回的时间字符串
 */
export const formatRelativeTime = (time?: string) => {
  if (!time) {
    return '-'
  }
  const target = dayjs(time)
  if (!target.isValid()) {
    return '-'
  }
  const diff = Date.now() - target.valueOf()
  // 后端时间大于当前时间（时钟差异）时按「刚刚」处理
  if (diff < MINUTE) {
    return '刚刚'
  }
  if (diff < HOUR) {
    return `${Math.floor(diff / MINUTE)} 分钟前`
  }
  if (diff < DAY) {
    return `${Math.floor(diff / HOUR)} 小时前`
  }
  if (diff < 30 * DAY) {
    return `${Math.floor(diff / DAY)} 天前`
  }
  return target.format('YYYY-MM-DD')
}
