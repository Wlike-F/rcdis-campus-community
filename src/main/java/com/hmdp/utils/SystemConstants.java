package com.hmdp.utils;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = "D:\\hm\\nginx-1.18.0\\html\\hmdp\\imgs\\"; // 图片上传目录,注意路径斜杠
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

    // GEO 周边检索半径（米）：默认 5km，上限 50km 防止无界扫描拖垮 Redis
    public static final double DEFAULT_SEARCH_RADIUS_M = 5000D;
    public static final double MAX_SEARCH_RADIUS_M = 50000D;
}
