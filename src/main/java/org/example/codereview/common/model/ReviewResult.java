package org.example.codereview.common.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个维度的审查结果：Worker Agent 完成后通过 LiteTopic 回传给 Supervisor。
 */
public class ReviewResult {
    public String reviewId;
    public String sessionId;
    /** 审查维度：style / logic / security */
    public String agent;
    /** 该维度得分（0-100） */
    public int score;
    public List<ReviewIssue> issues = new ArrayList<>();
    public String summary;

    public ReviewResult() {}

    public ReviewResult(String reviewId, String sessionId, String agent, int score,
        List<ReviewIssue> issues, String summary) {
        this.reviewId = reviewId;
        this.sessionId = sessionId;
        this.agent = agent;
        this.score = score;
        this.issues = issues;
        this.summary = summary;
    }
}
