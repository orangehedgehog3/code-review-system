package org.example.codereview.common.model;

/**
 * 审查任务载荷：Supervisor 通过 Normal Topic 分发给 Style/Logic/Security Agent。
 */
public class ReviewRequest {
    /** 审查任务 ID（含资源前缀） */
    public String reviewId;
    /** 会话 LiteTopic，用于结果按会话回传 */
    public String sessionId;
    /** 目标 Agent 标识：style / logic / security */
    public String agent;
    /** 待审查的代码内容 */
    public String code;
    /** 任务创建时间戳 */
    public long createdAt;

    public ReviewRequest() {}

    public ReviewRequest(String reviewId, String sessionId, String agent, String code, long createdAt) {
        this.reviewId = reviewId;
        this.sessionId = sessionId;
        this.agent = agent;
        this.code = code;
        this.createdAt = createdAt;
    }
}
