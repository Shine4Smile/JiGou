/**
 * 应用可见范围快捷切换工具
 *
 * 列表卡片与应用状态栏都需要「一键切换私有 / 公开」，这里统一封装提交逻辑：
 * - 创建者本人：走普通编辑接口 /app/edit
 * - 管理员编辑他人应用：走管理员接口 /app/admin/update（与编辑页保持一致）
 *
 * 后端为部分更新（只提交 id 与 visibility，未提交字段保持原值），
 * 因此调用方不需要先查完整应用信息再回传。
 */
import { editApp, updateAppByAdmin } from '@/api/appController.ts'
import { asApiId } from '@/utils/apiId'

/** 提交身份：owner 表示创建者本人，admin 表示管理员编辑他人应用 */
export type AppVisibilitySwitchMode = 'owner' | 'admin'

/** 切换结果：由调用方决定提示文案与失败处理 */
export type AppVisibilitySwitchResult = {
  /** 是否切换成功 */
  success: boolean
  /** 失败原因（成功时为空字符串） */
  message: string
}

/**
 * 切换应用可见范围
 *
 * @param appId 应用 id（雪花 id，全程按字符串透传，避免精度丢失）
 * @param visibility 目标可见范围（APP_VISIBILITY_PRIVATE / APP_VISIBILITY_PUBLIC）
 * @param mode 提交身份，决定调用哪个接口
 */
export const switchAppVisibility = async (
  appId: string | number | undefined,
  visibility: string,
  mode: AppVisibilitySwitchMode,
): Promise<AppVisibilitySwitchResult> => {
  const body = { id: asApiId(appId ?? ''), visibility }
  try {
    const res = mode === 'admin' ? await updateAppByAdmin(body) : await editApp(body)
    if (res.data.code === 0) {
      return { success: true, message: '' }
    }
    return { success: false, message: res.data.message ?? '请稍后重试' }
  } catch {
    return { success: false, message: '请检查网络后重试' }
  }
}
