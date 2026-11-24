package org.example.codereview.logic;

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
 * LogicAgent：订阅逻辑审查 Normal Topic，分析逻辑缺陷与边界条件，结果经 LiteTopic 回传。
 */
public class LogicAgent extends ReviewWorkerBase {

    public LogicAgent() {
        super("logic", ReviewConstants.LOGIC_TASK_TOPIC, ReviewConstants.GID_LOGIC);
    }

    public static void main(String[] args) throws Exception {
        new LogicAgent().start();
    }

    @Override
    protected String process(String payloadJson) {
        ReviewRequest req = JSON.parseObject(payloadJson, ReviewRequest.class);
        List<ReviewIssue> issues = DeepSeekReviewer.review("logic", req.code, () -> review(req.code));
        ReviewResult result = new ReviewResult(req.reviewId, req.sessionId, "logic", score(issues), issues,
            issues.isEmpty() ? "未发现明显逻辑缺陷" : "共发现 " + issues.size() + " 处逻辑问题，请重点关注除零、越界与空指针风险");
        log.info("[logic] 审查完成[{}]: reviewId={}, 问题 {} 条, 得分 {}",
            ReviewConstants.AI_MODE ? "AI" : "规则", req.reviewId, issues.size(), result.score);
        return JSON.toJSONString(result);
    }

    /** 逻辑规则：除零、字符串 ==、溢出、死循环、资源泄漏、空 catch、off-by-one */
    public static List<ReviewIssue> review(String code) {
        List<ReviewIssue> issues = new ArrayList<>();
        ReviewRules.add(issues, code, "/\\s*0\\b", ReviewIssue.ERROR, "除零风险",
            "除数可能为 0，将抛出 ArithmeticException", "增加除数非 0 校验");
        ReviewRules.add(issues, code, "==\\s*\"", ReviewIssue.ERROR, "字符串比较",
            "使用 == 比较字符串比较的是引用而非内容", "改用 equals() 方法");
        ReviewRules.add(issues, code, "\\*\\s*\\d{3,}", ReviewIssue.WARNING, "溢出风险",
            "大数乘法可能整型溢出", "使用 long 或 Math.multiplyExact");
        ReviewRules.add(issues, code, "while\\s*\\(\\s*true\\s*\\)", ReviewIssue.WARNING, "死循环",
            "while(true) 缺少退出条件", "增加循环退出条件或 break");
        ReviewRules.add(issues, code, "new\\s+(Scanner|BufferedReader|FileInputStream|FileOutputStream)\\b",
            ReviewIssue.WARNING, "资源泄漏", "资源未关闭，可能造成句柄泄漏", "使用 try-with-resources 自动关闭");
        ReviewRules.add(issues, code, "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}", ReviewIssue.WARNING, "异常处理",
            "空 catch 块吞掉了异常", "至少记录日志或重新抛出异常");
        ReviewRules.add(issues, code, "for\\s*\\([^;]*;\\s*\\w+\\s*<=\\s*\\w+\\s*;", ReviewIssue.INFO, "边界条件",
            "循环条件使用 <= 可能越界（off-by-one）", "确认上界，通常应使用 <");
        return issues;
    }

    /** 得分：100 - 5*错误 - 2*警告 - 1*提示，最低 0 */
    private static int score(List<ReviewIssue> issues) {
        int score = 100;
        for (ReviewIssue issue : issues) {
            score -= issue.severity == ReviewIssue.ERROR ? 5 : issue.severity == ReviewIssue.WARNING ? 2 : 1;
        }
        return Math.max(score, 0);
    }
}
