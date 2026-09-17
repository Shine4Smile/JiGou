/**
 * 应用 id 的类型兼容工具
 *
 * 后端通过全局 Jackson 配置（JsonConfig）将所有 Long 类型序列化为字符串，
 * 因此接口实际返回的 id 是形如 "2099793133386174464" 的字符串。
 * 雪花 id 远超 JS 安全整数范围（2^53 - 1），一旦用 Number() 转换就会精度丢失，
 * 导致查询 / 修改 / 删除到错误的记录上，所以字符串必须原样透传。
 *
 * 但 openapi 自动生成的类型声明把 id 标成了 number，
 * 这里只做「类型层面」的兼容，运行时不会改变任何取值。
 *
 * @param id 后端返回或路由中携带的 id（运行时是字符串）
 * @returns 仅用于满足生成类型的 number 声明
 */
export const asApiId = (id: string | number): number => id as unknown as number
