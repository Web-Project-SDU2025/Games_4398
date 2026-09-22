/**
 * API接口封装
 * 提供所有业务接口的调用方法
 */

import { DataRequest, post, postWithAuth, uploadFile, DataResponse } from '../utils/http';
import { LoginRequest, LoginResponse, User, MenuItem, PageRequest, PageResponse, OptionItem } from '../types';

/**
 * 用户相关API
 */
export const userApi = {
    /**
     * 用户登录
     */
    login: async (loginData: LoginRequest): Promise<DataResponse<LoginResponse>> => {
        const request = new DataRequest();
        request.add('username', loginData.username);
        request.add('password', loginData.password);
        return post<LoginResponse>('/auth/login', request);
    },

    /**
     * 用户登出
     */
    logout: async (token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        return postWithAuth('/auth/logout', request, token);
    },

    /**
     * 获取当前用户信息
     */
    getCurrentUser: async (token: string): Promise<DataResponse<User>> => {
        const request = new DataRequest();
        return postWithAuth<User>('/user/getCurrentUser', request, token);
    },

    /**
     * 修改密码
     */
    updatePassword: async (
        oldPassword: string,
        newPassword: string,
        token: string
    ): Promise<DataResponse> => {
        const request = new DataRequest();
        request.add('oldPassword', oldPassword);
        request.add('newPassword', newPassword);
        return postWithAuth('/base/updatePassword', request, token);
    },

    /**
     * 获取用户列表（分页）
     */
    getUserList: async (
        pageRequest: PageRequest,
        token: string
    ): Promise<DataResponse<PageResponse<User>>> => {
        const request = new DataRequest();
        request.add('currentPage', pageRequest.currentPage);
        request.add('pageSize', pageRequest.pageSize);
        return postWithAuth<PageResponse<User>>('/user/getUserList', request, token);
    },

    /**
     * 保存用户信息
     */
    saveUser: async (user: Partial<User>, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        Object.keys(user).forEach(key => {
            request.add(key, user[key as keyof User]);
        });
        return postWithAuth('/user/userSave', request, token);
    },

    /**
     * 删除用户
     */
    deleteUser: async (userId: number, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        request.add('id', userId);
        return postWithAuth('/user/userDelete', request, token);
    }
};

/**
 * 菜单相关API
 */
export const menuApi = {
    /**
     * 获取菜单列表
     */
    getMenuList: async (token: string): Promise<DataResponse<MenuItem[]>> => {
        const request = new DataRequest();
        return postWithAuth<MenuItem[]>('/base/getMenuList', request, token);
    },

    /**
     * 获取菜单树
     */
    getMenuTreeNodeList: async (token: string): Promise<DataResponse<MenuItem[]>> => {
        const request = new DataRequest();
        return postWithAuth<MenuItem[]>('/base/getMenuTreeNodeList', request, token);
    },

    /**
     * 保存菜单
     */
    saveMenu: async (menu: Partial<MenuItem>, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        Object.keys(menu).forEach(key => {
            request.add(key, menu[key as keyof MenuItem]);
        });
        return postWithAuth('/base/menuSave', request, token);
    },

    /**
     * 删除菜单
     */
    deleteMenu: async (menuId: number, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        request.add('id', menuId);
        return postWithAuth('/base/menuDelete', request, token);
    }
};

/**
 * 字典相关API
 */
export const dictionaryApi = {
    /**
     * 获取字典选项列表
     */
    getDictionaryOptionItemList: async (
        code: string,
        token?: string
    ): Promise<DataResponse<OptionItem[]>> => {
        const request = new DataRequest();
        request.add('code', code);
        if (token) {
            return postWithAuth<OptionItem[]>('/base/getDictionaryOptionItemList', request, token);
        } else {
            return post<OptionItem[]>('/base/getDictionaryOptionItemList', request);
        }
    },

    /**
     * 获取字典树
     */
    getDictionaryTreeNodeList: async (token: string): Promise<DataResponse<any[]>> => {
        const request = new DataRequest();
        return postWithAuth<any[]>('/base/getDictionaryTreeNodeList', request, token);
    },

    /**
     * 保存字典
     */
    saveDictionary: async (dictionary: any, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        Object.keys(dictionary).forEach(key => {
            request.add(key, dictionary[key]);
        });
        return postWithAuth('/base/dictionarySave', request, token);
    },

    /**
     * 删除字典
     */
    deleteDictionary: async (dictionaryId: number, token: string): Promise<DataResponse> => {
        const request = new DataRequest();
        request.add('id', dictionaryId);
        return postWithAuth('/base/dictionaryDelete', request, token);
    }
};

/**
 * 文件相关API
 */
export const fileApi = {
    /**
     * 上传图片
     */
    uploadPhoto: async (
        file: File,
        remoteFile: string,
        token: string
    ): Promise<DataResponse> => {
        return uploadFile('/base/uploadPhotoWeb', file, {
            remoteFile,
            uploader: 'user'
        });
    },

    /**
     * 获取图片URL
     */
    getPhotoUrl: (remoteFile: string): string => {
        return `/base/getFileByteData?remoteFile=${encodeURIComponent(remoteFile)}`;
    },

    /**
     * 获取图片Base64字符串
     */
    getPhotoImageStr: async (remoteFile: string, token: string): Promise<DataResponse<string>> => {
        const request = new DataRequest();
        request.add('remoteFile', remoteFile);
        return postWithAuth<string>('/base/getPhotoImageStr', request, token);
    }
};

/**
 * 角色相关API
 */
export const roleApi = {
    /**
     * 获取角色选项列表
     */
    getRoleOptionItemList: async (token: string): Promise<DataResponse<OptionItem[]>> => {
        const request = new DataRequest();
        return postWithAuth<OptionItem[]>('/base/getRoleOptionItemList', request, token);
    }
};
