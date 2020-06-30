package com.kakarote.crm9.common.constant;

import com.kakarote.crm9.utils.BaseUtil;

import java.io.Serializable;

/**
 * 一些基础配置
 */
public class BaseConstant implements Serializable {

    public static final String NAME = "72CRM";

    public static final String VERSION = "1.2.1";

    private static final long serialVersionUID = 1L;
    /**
     * 默认的上传文件路径
     * TODO 默认配置文件路径
     */
    public final static String UPLOAD_PATH = BaseUtil.isWindows() ? "D:/upload/" : "/usr/local/upload/";


    /**
     * 角色类型列表
     */
    public static final Integer[] ROLE_TYPES = {1, 2, 3, 4, 5, 0};

    /**
     * 查询数据权限递归次数
     */
    public static final int AUTH_DATA_RECURSION_NUM = 20;

    /**
     * 用户登录错误缓存key
     */
    public static final String USER_LOGIN_ERROR_KEY = "LOGIN_ERROR_USER_";


}
