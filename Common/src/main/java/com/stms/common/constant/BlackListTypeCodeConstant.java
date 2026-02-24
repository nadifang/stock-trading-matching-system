package com.stms.common.constant;

public class BlackListTypeCodeConstant {

    //黑名单类型：股东/资金账号
    public static final int BLACKLIST_TYPE_SHAREHOLDER = 1;

    //黑名单类型：IP地址
    public static final int BLACKLIST_TYPE_IP = 2;

    //黑名单类型：设备（如设备ID、UserAgent、设备指纹）
    public static final int BLACKLIST_TYPE_DEVICE = 3;

    //黑名单类型：账户（如用户ID、登录账号）
    public static final int BLACKLIST_TYPE_ACCOUNT = 4;

    //黑名单类型：行为（如高频交易、对敲行为等，可封禁行为主体）
    public static final int BLACKLIST_TYPE_BEHAVIOR = 5;

}
