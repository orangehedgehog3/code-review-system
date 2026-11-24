package org.example.codereview.common;

import java.util.UUID;

/**
 * 系统常量：所有 RocketMQ 资源命名均包含统一前缀（-DresourcePrefix，默认 rmqai），
 * 避免与他人实例冲突、便于控制台追溯。
 */
public final class ReviewConstants {

    /** 资源前缀：所有 Topic / Group / 消息 Key 共用，可通过 -DresourcePrefix=xxx 自定义 */
    public static final String RESOURCE_PREFIX = System.getProperty("resourcePrefix", "rmqai");

    // ============ Normal Topic（任务分发） ============
    /** 总入口 Topic：外部向此 Topic 发送待审查代码，Supervisor 订阅 */
    public static final String REVIEW_TASK_TOPIC = RESOURCE_PREFIX + "-review-task";
    /** Style Agent 任务 Topic */
    public static final String STYLE_TASK_TOPIC = RESOURCE_PREFIX + "-style-task";
    /** Logic Agent 任务 Topic */
    public static final String LOGIC_TASK_TOPIC = RESOURCE_PREFIX + "-logic-task";
    /** Security Agent 任务 Topic */
    public static final String SECURITY_TASK_TOPIC = RESOURCE_PREFIX + "-security-task";
    /** Summary Agent 任务 Topic */
    public static final String SUMMARY_TASK_TOPIC = RESOURCE_PREFIX + "-summary-task";

    // ============ LiteTopic（会话级结果返回） ============
    /** 结果回传 LiteTopic：所有 Worker Agent 的审查结果都回传到该 LiteTopic */
    public static final String RESPONSE_LITE_TOPIC = RESOURCE_PREFIX + "-review-response";
    /** 会话级 LiteTopic 前缀：每次审查任务动态创建一个会话，实现会话隔离 */
    public static final String SESSION_PREFIX = RESOURCE_PREFIX + "-session-";

    // ============ Consumer Group ============
    /** Supervisor 入口任务消费组（普通消息） */
    public static final String GID_SUPERVISOR = "GID_" + RESOURCE_PREFIX + "_supervisor";
    /** Supervisor 结果回传消费组（轻量消息，控制台需选"轻量消息"类型并指定 review-response Topic） */
    public static final String GID_SUPERVISOR_LITE = "GID_" + RESOURCE_PREFIX + "_supervisor_lite";
    public static final String GID_STYLE = "GID_" + RESOURCE_PREFIX + "_style";
    public static final String GID_LOGIC = "GID_" + RESOURCE_PREFIX + "_logic";
    public static final String GID_SECURITY = "GID_" + RESOURCE_PREFIX + "_security";
    public static final String GID_SUMMARY = "GID_" + RESOURCE_PREFIX + "_summary";

    // ============ 环境配置（与官方示例保持一致的参数名） ============
    /** RocketMQ 接入点，如 rmq-cn-xxxx.cn-hangzhou.rmq.aliyuncs.com:8080 */
    public static final String ENDPOINT = System.getProperty("rocketMQEndpoint", "");
    /** 命名空间（阿里云实例 ID） */
    public static final String NAMESPACE = System.getProperty("rocketMQNamespace", "");
    /** 实例用户名（阿里云控制台：访问控制 → 智能身份识别，非阿里云 AccessKey） */
    public static final String ACCESS_KEY = System.getProperty("rocketMQAK", "");
    /** 实例密码 */
    public static final String SECRET_KEY = System.getProperty("rocketMQSK", "");

    // ============ 审查路径（可选）：rule=规则引擎（默认，非 AI）；ai=DeepSeek 大模型 ============
    /** 审查模式：rule=规则引擎（默认）；ai=DeepSeek 大模型 */
    public static final String REVIEW_MODE = System.getProperty("reviewMode", "rule");
    /** DeepSeek API Key（仅 AI 路径使用；留空则 AI 路径自动回退规则引擎） */
    public static final String DEEPSEEK_KEY = System.getProperty("deepseekKey", "");
    /** 是否启用 AI 审查路径 */
    public static final boolean AI_MODE = "ai".equalsIgnoreCase(REVIEW_MODE) && !DEEPSEEK_KEY.isEmpty();

    private ReviewConstants() {
    }

    /** 构造包含资源前缀的消息 Key，用于控制台消息轨迹查询 */
    public static String buildMessageKey(String agent, String requestId) {
        return RESOURCE_PREFIX + "-" + agent + "-" + requestId;
    }

    /** 生成包含资源前缀的审查任务 ID */
    public static String newReviewId() {
        return RESOURCE_PREFIX + "-review-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 生成会话级 LiteTopic（每次审查任务一个会话，实现数据隔离） */
    public static String newSessionLiteTopic() {
        return SESSION_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }
}
