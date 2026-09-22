/**
 * HTTP请求工具类
 * 提供统一的HTTP请求封装和响应处理
 */

// API基础URL配置
export const API_BASE_URL = 'http://localhost:8080/api';

/**
 * 数据请求类
 * 对应后端DataRequest
 */
export class DataRequest {
    data: Map<string, any>;

    constructor() {
        this.data = new Map();
    }

    /**
     * 添加数据
     */
    add(key: string, value: any): void {
        this.data.set(key, value);
    }

    /**
     * 获取数据
     */
    get(key: string): any {
        return this.data.get(key);
    }

    /**
     * 转换为普通对象用于JSON序列化
     */
    toObject(): { [key: string]: any } {
        const obj: { [key: string]: any } = {};
        this.data.forEach((value, key) => {
            obj[key] = value;
        });
        return obj;
    }

    /**
     * 转换为JSON格式
     */
    toJSON(): string {
        return JSON.stringify({ data: this.toObject() });
    }
}

/**
 * 数据响应类
 * 对应后端DataResponse
 */
export interface DataResponse<T = any> {
    code: number;      // 0-成功 1-失败
    data: T;           // 返回数据
    msg: string;       // 返回信息
}

/**
 * HTTP请求配置
 */
export interface RequestConfig {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    headers?: { [key: string]: string };
    body?: any;
}

/**
 * 发送HTTP请求
 */
export async function httpRequest<T = any>(
    url: string,
    config: RequestConfig = {}
): Promise<DataResponse<T>> {
    const { method = 'POST', headers = {}, body } = config;

    const defaultHeaders: { [key: string]: string } = {
        'Content-Type': 'application/json',
        ...headers
    };

    try {
        const response = await fetch(`${API_BASE_URL}${url}`, {
            method,
            headers: defaultHeaders,
            body: body ? JSON.stringify(body) : undefined
        });

        if (!response.ok) {
            throw new Error(`HTTP错误! 状态: ${response.status}`);
        }

        const result: DataResponse<T> = await response.json();
        return result;
    } catch (error) {
        console.error('请求失败:', error);
        return {
            code: 1,
            data: null as any,
            msg: error instanceof Error ? error.message : '未知错误'
        };
    }
}

/**
 * POST请求封装
 */
export async function post<T = any>(
    url: string,
    dataRequest: DataRequest
): Promise<DataResponse<T>> {
    return httpRequest<T>(url, {
        method: 'POST',
        body: { data: dataRequest.toObject() }
    });
}

/**
 * GET请求封装
 */
export async function get<T = any>(url: string): Promise<DataResponse<T>> {
    return httpRequest<T>(url, {
        method: 'GET'
    });
}

/**
 * 带Token的请求
 */
export async function postWithAuth<T = any>(
    url: string,
    dataRequest: DataRequest,
    token: string
): Promise<DataResponse<T>> {
    return httpRequest<T>(url, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`
        },
        body: { data: dataRequest.toObject() }
    });
}

/**
 * 文件上传
 */
export async function uploadFile(
    url: string,
    file: File,
    additionalData?: { [key: string]: string }
): Promise<DataResponse> {
    const formData = new FormData();
    formData.append('file', file);

    if (additionalData) {
        Object.keys(additionalData).forEach(key => {
            formData.append(key, additionalData[key]);
        });
    }

    try {
        const response = await fetch(`${API_BASE_URL}${url}`, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error(`HTTP错误! 状态: ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('文件上传失败:', error);
        return {
            code: 1,
            data: null,
            msg: error instanceof Error ? error.message : '上传失败'
        };
    }
}
