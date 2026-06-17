package com.layababateam.xinxiwang_backend.dto

enum class ErrorCode(val code: String, val defaultMessage: String) {
    SUCCESS("0", "OK"),

    // 通用错误 10xxx
    UNKNOWN_ERROR("10000", "系统异常，请稍后重试"),
    INVALID_PARAM("10001", "参数验证失败"),
    NOT_FOUND("10002", "资源不存在"),
    METHOD_NOT_ALLOWED("10003", "请求方法不支持"),
    SERVICE_UNAVAILABLE("10004", "服务暂不可用"),
    DATABASE_ERROR("10005", "数据库操作异常"),

    // 认证授权 20xxx
    UNAUTHORIZED("20001", "未授权，请重新登录"),
    TOKEN_EXPIRED("20002", "登录已过期"),
    FORBIDDEN("20004", "权限不足"),

    // 好友 30xxx
    FRIEND_ALREADY_EXISTS("30001", "已经是好友"),

    // 群聊 31xxx
    GROUP_NOT_FOUND("31001", "群聊不存在"),
    GROUP_FULL("31002", "群成员已满"),
    GROUP_PERMISSION_DENIED("31003", "需要管理员权限"),

    // 会议 32xxx
    MEETING_PASSWORD_REQUIRED("32001", "该会议需要密码"),
    MEETING_PASSWORD_INCORRECT("32002", "会议密码错误"),
    MEETING_ACTIVE_EXISTS("32003", "当前已有进行中的会议"),

    // 钱包 33xxx
    WALLET_INSUFFICIENT_BALANCE("33001", "余额不足"),

    // 宣讲大会 34xxx
    BROADCAST_NOT_STAFF("34001", "仅工作人员可执行此操作"),
    BROADCAST_INVALID_PARAM("34002", "宣讲参数无效"),
    BROADCAST_INVALID_PASSWORD_FORMAT("34003", "密码格式不正确（4-8 位数字）"),
    BROADCAST_INVALID_SCHEDULE("34004", "预约时间无效"),
    BROADCAST_INVALID_TARGET("34005", "操作目标无效"),
    BROADCAST_ENDED("34006", "宣讲已结束"),
    BROADCAST_BANNED("34007", "你已被移出本场宣讲，无法再次加入"),
    BROADCAST_FULL("34008", "当前观看人数已满"),
    BROADCAST_PASSWORD_INCORRECT("34009", "宣讲密码错误"),
    BROADCAST_ALREADY_IN_OTHER("34010", "你正在参加其他宣讲或会议"),
    BROADCAST_SPEAKER_CANNOT_LEAVE("34011", "主讲人无法离开宣讲，请先转交主讲人"),
    BROADCAST_RAISE_HAND_COOLDOWN("34012", "举手过于频繁，请稍后再试"),
    BROADCAST_LINK_MIC_FULL("34013", "连麦人数已满"),
    BROADCAST_ACTIVE_EXISTS("34014", "当前已有活跃红包/福袋"),
    BROADCAST_AMOUNT_INVALID("34015", "积分数量无效"),
    BROADCAST_OPERATOR_INSUFFICIENT("34016", "运营积分池余额不足"),
    BROADCAST_SPEAKER_CONFLICT("34017", "你已是另一场宣讲的主讲人，请先结束后再创建"),
}
