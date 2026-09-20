// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/version/commit */
export async function commitVersion(
  body: API.AppVersionCommitRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseInteger>('/app/version/commit', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/detail */
export async function getVersionDetail(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getVersionDetailParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVersionDetailVO>('/app/version/detail', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/list */
export async function listVersions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listVersionsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVersionListVO>('/app/version/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/version/rollback */
export async function rollbackVersion(
  body: API.AppVersionRollbackRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/version/rollback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
