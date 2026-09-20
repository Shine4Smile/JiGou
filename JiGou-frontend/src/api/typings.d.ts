declare namespace API {
  type AppAddRequest = {
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
  }

  type AppDeployRequest = {
    appId?: number
  }

  type AppEditRequest = {
    id?: number
    appName?: string
    visibility?: string
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    visibility?: string
    priority?: number
    userId?: number
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    priority?: number
    visibility?: string
  }

  type AppVersionCommitRequest = {
    appId?: number
  }

  type AppVersionDetailVO = {
    version?: number
    commitTime?: string
    fileMap?: Record<string, any>
  }

  type AppVersionListVO = {
    currentVersion?: number
    hasCode?: boolean
    uncommitted?: boolean
    versionList?: AppVersionVO[]
  }

  type AppVersionRollbackRequest = {
    appId?: number
    version?: number
  }

  type AppVersionVO = {
    version?: number
    commitTime?: string
    current?: boolean
  }

  type AppVO = {
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    version?: number
    visibility?: string
    priority?: number
    userId?: number
    createTime?: string
    updateTime?: string
    userVO?: UserVO
  }

  type BaseResponseAppVersionDetailVO = {
    code?: number
    data?: AppVersionDetailVO
    message?: string
  }

  type BaseResponseAppVersionListVO = {
    code?: number
    data?: AppVersionListVO
    message?: string
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseChatHistoryPageVO = {
    code?: number
    data?: ChatHistoryPageVO
    message?: string
  }

  type BaseResponseInteger = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistoryVO = {
    code?: number
    data?: PageChatHistoryVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistoryPageVO = {
    records?: ChatHistoryVO[]
    hasMore?: boolean
    nextLastCreateTime?: string
    nextLastId?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appId?: number
    userId?: number
    messageType?: string
    message?: string
    lastCreateTime?: string
    lastId?: number
    createTimeStart?: string
    createTimeEnd?: string
  }

  type ChatHistoryVO = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    appName?: string
    userId?: number
    createTime?: string
    updateTime?: string
    userVO?: UserVO
  }

  type chatToGenCodeParams = {
    appId: number
    message: string
  }

  type DeleteRequest = {
    id?: number
  }

  type getAppByIdParams = {
    id: number
  }

  type getAppVOByIdParams = {
    id: number
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type getVersionDetailParams = {
    appId: number
    version: number
  }

  type listVersionsParams = {
    appId: number
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistoryVO = {
    records?: ChatHistoryVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserEditRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userName?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
