<template>
  <div id="userManagePage">
    <!-- 搜索区 -->
    <a-card class="panel search-panel" :bordered="false">
      <a-form layout="inline" :model="searchParams" class="search-form" @finish="doSearch">
        <a-form-item label="账号">
          <a-input v-model:value="searchParams.userAccount" placeholder="请输入账号" allow-clear />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="searchParams.userName" placeholder="请输入用户名" allow-clear />
        </a-form-item>
        <a-form-item>
          <a-space :size="8">
            <a-button type="primary" html-type="submit" :loading="loading">
              <template #icon><SearchOutlined /></template>
              <span>搜索</span>
            </a-button>
            <a-button @click="doReset">
              <template #icon><ReloadOutlined /></template>
              <span>重置</span>
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 列表区 -->
    <a-card class="panel table-panel" :bordered="false">
      <template #title>
        <span class="panel-title">用户列表</span>
      </template>
      <template #extra>
        <a-space :size="8">
          <a-button :loading="loading" @click="fetchData">
            <template #icon><ReloadOutlined /></template>
            <span>刷新</span>
          </a-button>
          <a-button type="primary" @click="openAddModal">
            <template #icon><PlusOutlined /></template>
            <span>新增用户</span>
          </a-button>
        </a-space>
      </template>

      <a-table
        row-key="id"
        table-layout="fixed"
        :columns="columns"
        :data-source="data"
        :pagination="pagination"
        :loading="loading"
        :scroll="{ x: 1200 }"
        @change="doTableChange"
      >
        <template #headerCell="{ column, title }">
          <template v-if="column.dataIndex === 'userName'">
            <span class="header-cell">
              {{ title }}
            </span>
          </template>
        </template>

        <template #bodyCell="{ column, record }">
          <!-- 头像：固定 48px 圆形，点击可预览大图；无头像时展示占位图标 -->
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-image
              v-if="record.userAvatar"
              class="user-avatar"
              :src="record.userAvatar"
              :width="48"
              :height="48"
            />
            <a-avatar v-else class="user-avatar user-avatar--empty" :size="48">
              <template #icon><UserOutlined /></template>
            </a-avatar>
          </template>
          <!-- 文本类字段：统一单行省略，过长时 hover 展示完整内容 -->
          <template v-else-if="column.dataIndex === 'userAccount'">
            <EllipsisText :text="record.userAccount" />
          </template>
          <template v-else-if="column.dataIndex === 'userName'">
            <EllipsisText :text="record.userName" />
          </template>
          <template v-else-if="column.dataIndex === 'userProfile'">
            <EllipsisText :text="record.userProfile" />
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <a-tag v-if="record.userRole === 'admin'" color="green">
              <CrownOutlined /> 管理员
            </a-tag>
            <a-tag v-else-if="record.userRole === 'user'" color="blue">
              <UserOutlined /> 普通用户
            </a-tag>
            <EllipsisText v-else :text="record.userRole" />
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space :size="4">
              <a-button type="link" size="small" @click="openEditModal(record)">
                <template #icon><EditOutlined /></template>
                <span>编辑</span>
              </a-button>
              <a-popconfirm
                :title="`确定删除用户「${record.userName || record.userAccount || record.id}」吗？`"
                description="删除后不可恢复，请谨慎操作"
                ok-text="删除"
                cancel-text="取消"
                ok-type="danger"
                placement="topRight"
                @confirm="doDelete(record)"
              >
                <a-button type="link" size="small" danger :loading="deletingId === record.id">
                  <template #icon><DeleteOutlined /></template>
                  <span>删除</span>
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新增 / 编辑弹窗：两种模式共用一套表单 -->
    <a-modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :width="560"
      :confirm-loading="submitting"
      :mask-closable="false"
      destroy-on-close
      centered
      ok-text="确定"
      cancel-text="取消"
      @ok="handleModalOk"
    >
      <a-form
        ref="formRef"
        class="user-form"
        :model="formState"
        :rules="formRules"
        :label-col="{ span: 5 }"
        :wrapper-col="{ span: 19 }"
        autocomplete="off"
      >
        <!-- 账号：唯一登录标识，仅新增可填 -->
        <a-form-item v-if="isAddMode" label="账号" name="userAccount">
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入账号（4-20 位）"
            :maxlength="20"
            allow-clear
          />
        </a-form-item>
        <a-form-item v-else label="账号">
          <a-input :value="formState.userAccount" disabled />
          <template #extra>账号是用户的唯一登录标识，创建后不可修改</template>
        </a-form-item>

        <a-form-item label="用户名" name="userName">
          <a-input
            v-model:value="formState.userName"
            placeholder="请输入用户名（选填）"
            :maxlength="32"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="头像" name="userAvatar">
          <div class="avatar-field">
            <a-avatar
              class="avatar-field__preview"
              :size="56"
              :src="formState.userAvatar || undefined"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <div class="avatar-field__main">
              <a-input
                v-model:value="formState.userAvatar"
                placeholder="请输入头像图片链接（选填）"
                allow-clear
              />
              <div class="avatar-field__tip">填写图片链接后左侧会实时预览，留空则展示默认头像</div>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="简介" name="userProfile">
          <a-textarea
            v-model:value="formState.userProfile"
            placeholder="请输入用户简介（选填，最长 200 字）"
            :rows="3"
            :maxlength="200"
            show-count
            allow-clear
          />
        </a-form-item>

        <a-form-item label="用户角色" name="userRole">
          <a-select
            v-model:value="formState.userRole"
            :options="roleOptions"
            placeholder="请选择用户角色"
          />
        </a-form-item>

        <a-form-item v-if="isAddMode" :wrapper-col="{ offset: 5, span: 19 }">
          <a-alert
            type="info"
            show-icon
            message="新增用户的初始密码为 12345678"
            description="请提醒用户登录后及时修改密码。"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import {
  CrownOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { addUser, deleteUser, listUserVoByPage, updateUser } from '@/api/userController.ts'
import EllipsisText from '@/components/EllipsisText.vue'

/* ------------------------------ 表格配置 ------------------------------ */

/** 用户角色选项，取值与后端 userRole 一致 */
const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

/**
 * 列宽策略：
 * 1. 各列给出固定宽度，配合 table-layout="fixed"，列宽不再被单元格内容（尤其是长简介）撑开；
 * 2. 「简介」不设宽度，作为弹性列吸收宽屏下的剩余空间，保证表格整体仍自适应；
 * 3. 表格最小宽度 1200，窄屏时横向滚动，而非压缩列宽。
 */
const columns: TableColumnsType = [
  // id 列暂不展示，保留配置，需要时取消注释即可（行的唯一标识仍由 row-key="id" 提供）
  // { title: 'id', dataIndex: 'id', width: 80, align: 'center' },
  { title: '账号', dataIndex: 'userAccount', width: 150 },
  { title: '用户名', dataIndex: 'userName', width: 150 },
  { title: '头像', dataIndex: 'userAvatar', width: 96, align: 'center' },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '用户角色', dataIndex: 'userRole', width: 110, align: 'center' },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 170, align: 'center', fixed: 'right' },
]

/* ------------------------------ 列表数据 ------------------------------ */

const data = ref<API.UserVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPage({
      ...searchParams,
    })
    if (res.data.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0
    } else {
      message.error('获取数据失败，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('获取数据失败，请检查网络后重试')
  } finally {
    loading.value = false
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格变化处理
const doTableChange = (page: TablePaginationConfig) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索：重置页码后重新查询
const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

// 重置搜索条件
const doReset = () => {
  searchParams.userAccount = undefined
  searchParams.userName = undefined
  searchParams.pageNum = 1
  fetchData()
}

// 时间格式化：后端时间字段可能为空，避免展示 Invalid Date
const formatTime = (time?: string) => {
  if (!time || !dayjs(time).isValid()) {
    return '-'
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

/* ------------------------------ 新增 / 编辑 ------------------------------ */

type UserFormState = {
  id?: number
  userAccount?: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  userRole?: string
}

/** 表单初始值：新增时用空值，编辑时用当前行数据（同时用于还原） */
const createEmptyForm = (): UserFormState => ({
  id: undefined,
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})

const modalOpen = ref(false)
const modalMode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const formState = reactive<UserFormState>(createEmptyForm())

const isAddMode = computed(() => modalMode.value === 'add')
const modalTitle = computed(() => (isAddMode.value ? '新增用户' : '编辑用户'))

// 头像链接校验：兼容 http(s) 地址、data 图片与以 / 开头的相对路径
const avatarValidator = async (_rule: Rule, value?: string) => {
  if (!value) {
    return Promise.resolve()
  }
  const isValid =
    /^https?:\/\/\S+$/i.test(value) || /^data:image\/\S+$/i.test(value) || value.startsWith('/')
  return isValid ? Promise.resolve() : Promise.reject('请输入合法的图片链接')
}

const formRules: Record<string, Rule[]> = {
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度需为 4-20 位', trigger: 'blur' },
  ],
  userName: [{ max: 32, message: '用户名不能超过 32 个字符', trigger: 'blur' }],
  userAvatar: [
    { max: 512, message: '头像链接不能超过 512 个字符', trigger: 'blur' },
    { validator: avatarValidator, trigger: 'blur' },
  ],
  userProfile: [{ max: 200, message: '简介不能超过 200 个字符', trigger: 'blur' }],
  userRole: [{ required: true, message: '请选择用户角色', trigger: 'change' }],
}

// 打开弹窗：等待表单渲染后清空上一次的校验提示
const openModal = async () => {
  modalOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

// 新增
const openAddModal = () => {
  modalMode.value = 'add'
  Object.assign(formState, createEmptyForm())
  openModal()
}

// 编辑：直接使用当前行数据回填表单
const openEditModal = (record: API.UserVO) => {
  modalMode.value = 'edit'
  Object.assign(formState, {
    id: record.id,
    userAccount: record.userAccount ?? '',
    userName: record.userName ?? '',
    userAvatar: record.userAvatar ?? '',
    userProfile: record.userProfile ?? '',
    userRole: record.userRole || 'user',
  })
  openModal()
}

// 提交弹窗表单
const handleModalOk = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    // 校验不通过：错误提示由表单项自行展示
    return
  }
  submitting.value = true
  try {
    const payload = {
      userName: formState.userName?.trim(),
      userAvatar: formState.userAvatar?.trim(),
      userProfile: formState.userProfile?.trim(),
      userRole: formState.userRole,
    }
    const res = isAddMode.value
      ? await addUser({ ...payload, userAccount: formState.userAccount?.trim() })
      : await updateUser({ ...payload, id: formState.id })
    if (res.data.code === 0) {
      message.success(isAddMode.value ? '新增成功' : '保存成功')
      modalOpen.value = false
      fetchData()
    } else {
      message.error(res.data.message ?? (isAddMode.value ? '新增失败' : '保存失败'))
    }
  } catch {
    message.error(isAddMode.value ? '新增失败，请稍后重试' : '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/* ------------------------------ 删除 ------------------------------ */

const deletingId = ref<number>()

const doDelete = async (record: API.UserVO) => {
  if (!record?.id) {
    return
  }
  deletingId.value = record.id
  try {
    // 后端 Long 序列化为 string，此处按原值透传即可
    const res = await deleteUser({ id: record.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      // 删除当前页最后一条数据时，自动回退一页
      if (data.value.length === 1 && (searchParams.pageNum ?? 1) > 1) {
        searchParams.pageNum = (searchParams.pageNum ?? 1) - 1
      }
      await fetchData()
    } else {
      message.error('删除失败，' + (res.data.message ?? '请稍后重试'))
    }
  } catch {
    message.error('删除失败，请稍后重试')
  } finally {
    deletingId.value = undefined
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 卡片容器：白底轻投影，与浅灰页面背景形成层次 */
.panel {
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 1px 6px -1px rgba(0, 0, 0, 0.03);
}

.search-panel :deep(.ant-card-body) {
  padding: 20px 24px;
}

.search-form :deep(.ant-form-item) {
  margin-bottom: 8px;
  margin-inline-end: 16px;
}

.search-form :deep(.ant-form-item:last-child) {
  margin-inline-end: 0;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.panel-subtitle {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.45);
}

.header-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/*
 * 行高固定：
 * 单元格内容统一垂直居中，行高由固定值决定，不再随单元格内容（头像有无、简介长短）变化；
 * 列宽由 table-layout="fixed" + columns.width 固定，长文本只做省略（见 EllipsisText）
 */
.table-panel :deep(.ant-table-tbody > tr > td) {
  height: 72px;
  padding-top: 8px;
  padding-bottom: 8px;
  vertical-align: middle;
  white-space: nowrap;
  overflow: hidden;
}

/* 头像：固定 48px 圆形，图片按比例裁切，点击可预览大图 */
.user-avatar {
  display: block;
  margin: 0 auto;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
}

.user-avatar :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar--empty {
  cursor: default;
  color: rgba(0, 0, 0, 0.35);
  background-color: #f5f5f5;
}

/* 弹窗表单 */
.user-form :deep(.ant-form-item-label) {
  white-space: nowrap;
}

.avatar-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.avatar-field__preview {
  flex: none;
  background-color: #f5f5f5;
  color: rgba(0, 0, 0, 0.35);
}

.avatar-field__main {
  flex: 1;
  min-width: 0;
}

.avatar-field__tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.45);
}

@media (max-width: 768px) {
  .search-panel :deep(.ant-card-body) {
    padding: 16px;
  }

  .search-form :deep(.ant-form-item) {
    margin-inline-end: 8px;
  }
}
</style>
