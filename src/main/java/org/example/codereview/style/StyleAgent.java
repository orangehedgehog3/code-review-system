package org.example.codereview.style;

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
 * StyleAgent：订阅风格审查 Normal Topic，检查命名与格式规范，结果经 LiteTopic 回传。
 */
public class StyleAgent extends ReviewWorkerBase {

    public StyleAgent() {
        super("style", ReviewConstants.STYLE_TASK_TOPIC, ReviewConstants.GID_STYLE);
    }

    public static void main(String[] args) throws Exception {
        new StyleAgent().start();
    }

    @Override
    protected String process(String payloadJson) {
        ReviewRequest req = JSON.parseObject(payloadJson, ReviewRequest.class);
        List<ReviewIssue> issues = DeepSeekReviewer.review("style", req.code, () -> review(req.code));
        ReviewResult result = new ReviewResult(req.reviewId, req.sessionId, "style", score(issues), issues,
            issues.isEmpty() ? "代码风格良好，未发现明显问题" : "共发现 " + issues.size() + " 处风格问题，建议统一命名与格式规范");
        log.info("[style] 审查完成[{}]: reviewId={}, 问题 {} 条, 得分 {}",
            ReviewConstants.AI_MODE ? "AI" : "规则", req.reviewId, issues.size(), result.score);
        return JSON.toJSONString(result);
    }

    /** 风格规则：类名大驼峰、常量全大写、魔法数字、单字符变量、System.out */
    public static List<ReviewIssue> review(String code) {
        List<ReviewIssue> issues = new ArrayList<>();
        ReviewRules.add(issues, code, "\\bclass\\s+[a-z]", ReviewIssue.ERROR, "命名规范",
            "类名不符合大驼峰命名规范", "重命名为 UpperCamelCase，如 UserService");
        ReviewRules.add(issues, code, "static\\s+final\\s+\\w+\\s+[a-z]", ReviewIssue.WARNING, "命名规范",
            "常量应使用全大写+下划线命名", "重命名为 DB_PASSWORD 形式");
        ReviewRules.add(issues, code, "(?<![A-Za-z0-9_.])\\d{3,}(?!\\w)", ReviewIssue.WARNING, "魔法数字",
            "魔法数字降低代码可读性", "提取为具名常量");
        ReviewRules.add(issues, code, "\\b(String|int|long|boolean|double|float|char|File|Scanner|Exception)\\s+(\\w)\\b",
            ReviewIssue.WARNING, "命名规范", "单字符变量名含义不明", "使用有意义的变量名");
        ReviewRules.add(issues, code, "System\\.out", ReviewIssue.WARNING, "输出方式",
            "使用 System.out 打印日志", "改用日志框架如 SLF4J");
        return issues;
    }

    /** 得分：100 - 3*错误 - 1*警告，最低 0 */
    private static int score(List<ReviewIssue> issues) {
        int score = 100;
        for (ReviewIssue issue : issues) {
            score -= issue.severity == ReviewIssue.ERROR ? 3 : 1;
        }
        return Math.max(score, 0);
    }
}
