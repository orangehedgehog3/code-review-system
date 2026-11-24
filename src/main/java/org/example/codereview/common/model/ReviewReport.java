package org.example.codereview.common.model;

/**
 * 最终审查报告：Summary Agent 汇总生成后通过 LiteTopic 回传给 Supervisor。
 */
public class ReviewReport {
    public String reviewId;
    public String sessionId;
    /** 审查模式：规则引擎（非 AI）/ DeepSeek 大模型（AI） */
    public String mode;
    /** 总体得分（0-100，取三个维度平均） */
    public int totalScore;
    public String conclusion;
    /** 完整的 Markdown 格式报告文本 */
    public String markdown;
    public long generatedAt;
}
