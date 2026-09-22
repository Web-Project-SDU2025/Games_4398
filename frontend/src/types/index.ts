/**
 * API接口类型定义
 */

/**
 * 分页请求参数
 */
export interface PageRequest {
    currentPage: number;    // 当前页码
    pageSize: number;       // 每页大小
}

/**
 * 分页响应数据
 */
export interface PageResponse<T> {
    content: T[];           // 数据列表
    totalElements: number;  // 总记录数
    totalPages: number;     // 总页数
    currentPage: number;    // 当前页码
    pageSize: number;       // 每页大小
}

/**
 * 用户信息
 */
export interface User {
    id: number;
    username: string;
    password?: string;
    email?: string;
    phone?: string;
    avatar?: string;
    role?: string;
    status?: number;
    createTime?: string;
    updateTime?: string;
}

/**
 * 登录请求
 */
export interface LoginRequest {
    username: string;
    password: string;
}

/**
 * 登录响应
 */
export interface LoginResponse {
    token: string;
    user: User;
}

/**
 * 菜单项
 */
export interface MenuItem {
    id: number;
    name: string;
    title: string;
    path?: string;
    icon?: string;
    parentId?: number;
    orderNum?: number;
    children?: MenuItem[];
}

/**
 * 选项项（用于下拉框等）
 */
export interface OptionItem {
    value: string | number;
    label: string;
    disabled?: boolean;
}

/**
 * 表单规则
 */
export interface FormRule {
    required?: boolean;
    message?: string;
    pattern?: RegExp;
    min?: number;
    max?: number;
    validator?: (value: any) => boolean | string;
}

/**
 * 表格列配置
 */
export interface TableColumn<T = any> {
    key: string;
    title: string;
    dataIndex?: keyof T;
    width?: number;
    align?: 'left' | 'center' | 'right';
    fixed?: 'left' | 'right';
    sortable?: boolean;
    render?: (value: any, record: T, index: number) => string | HTMLElement;
}

/**
 * 文件上传响应
 */
export interface UploadResponse {
    url: string;
    filename: string;
    size: number;
}
