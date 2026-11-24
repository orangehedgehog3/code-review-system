package org.example.codereview.security;

import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson.JSON;
import org.example.codereview.common.DeepSeekReviewer;
import org.example.codereview.common.ReviewConstants;
import org.example.codereview.common.ReviewRules;
import org.example.codereview.common.model.ReviewIssue;
import org.example.codereview.common.model.ReviewRequest;
import org.example.codereview.common.model.ReviewResult;
import org.example.codereview.worker.ReviewWorkerBase;

/**
 * SecurityAgent：订阅安全审查 Normal Topic，识别 SQL 注入、XSS、路径穿越等安全漏洞，
 * 结果经 LiteTopic 回传。
 */
public class SecurityAgent extends ReviewWorkerBase {

    public SecurityAgent() {
        super("security", ReviewConstants.SECURITY_TASK_TOPIC, ReviewConstants.GID_SECURITY);
    }

    public static void main(String[] args) throws Exception {
        new SecurityAgent().start();
    }

    @Override
    protected String process(String payloadJson) {
        ReviewRequest req = JSON.parseObject(payloadJson, ReviewRequest.class);
        List<ReviewIssue> issues = DeepSeekReviewer.review("security", req.code, () -> review(req.code));
        ReviewResult result = new ReviewResult(req.reviewId, req.sessionId, "security", score(issues), issues,
            issues.isEmpty() ? "未发现明显安全漏洞" : "共发现 " + issues.size() + " 处安全隐患，存在高危漏洞，必须修复后才能上线");
        log.info("[security] 审查完成[{}]: reviewId={}, 问题 {} 条, 得分 {}",
            ReviewConstants.AI_MODE ? "AI" : "规则", req.reviewId, issues.size(), result.score);
        return JSON.toJSONString(result);
    }

    /** 安全规则：SQL 拼接、Statement、硬编码密码、路径穿越、XSS */
    public static List<ReviewIssue> review(String code) {
        List<ReviewIssue> issues = new ArrayList<>();
        ReviewRules.add(issues, code, "(?i)(select|insert|update|delete)\\b[^;]*\\+", ReviewIssue.ERROR, "SQL注入",
            "SQL 语句拼接用户输入，存在 SQL 注入风险", "使用 PreparedStatement 参数化查询");
        ReviewRules.add(issues, code, "createStatement\\s*\\(", ReviewIssue.WARNING, "SQL注入",
            "使用 Statement 执行 SQL，无法防止注入", "改用 PreparedStatement");
        ReviewRules.add(issues, code, "(?i)(password|passwd|pwd)\\s*=\\s*\"", ReviewIssue.ERROR, "敏感信息",
            "代码中硬编码了密码", "从配置文件或密钥管理系统读取");
        ReviewRules.add(issues, code, "new\\s+File\\s*\\(\\s*\\w+\\s*\\)", ReviewIssue.ERROR, "路径穿越",
            "文件路径直接使用用户输入，存在路径穿越风险", "校验并白名单过滤路径参数");
        ReviewRules.add(issues, code, "(?i)(innerHTML|<script|document\\.write)", ReviewIssue.WARNING, "XSS",
            "直接输出未转义内容存在 XSS 风险", "对输出内容进行 HTML 转义");
        return issues;
    }

    /** 得分：100 - 10*错误 - 3*警告，最低 0 */
    private static int score(List<ReviewIssue> issues) {
        int score = 100;
        for (ReviewIssue issue : issues) {
            score -= issue.severity == ReviewIssue.ERROR ? 10 : 3;
        }
        return Math.max(score, 0);
    }
}
