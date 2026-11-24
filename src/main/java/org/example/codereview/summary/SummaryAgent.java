package org.example.codereview.summary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.alibaba.fastjson.JSON;
import org.example.codereview.common.DeepSeekReviewer;
import org.example.codereview.common.ReviewConstants;
import org.example.codereview.common.model.ReviewIssue;
import org.example.codereview.common.model.ReviewReport;
import org.example.codereview.common.model.ReviewResult;
import org.example.codereview.worker.ReviewWorkerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SummaryAgent：订阅汇总 Normal Topic，聚合三个维度结果生成 Markdown 审查报告，经 LiteTopic 回传。
 */
public class SummaryAgent extends ReviewWorkerBase {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryAgent.class);

    public SummaryAgent() {
        super("summary", ReviewConstants.SUMMARY_TASK_TOPIC, ReviewConstants.GID_SUMMARY);
    }

    public static void main(String[] args) throws Exception {
        new SummaryAgent().start();
    }

    @Override
    protected String process(String payloadJson) {
        List<ReviewResult> results = JSON.parseArray(payloadJson, ReviewResult.class);
        ReviewReport report = generate(results);
        log.info("[summary] 报告生成完成: reviewId={}, 总体得分 {}", report.reviewId, report.totalScore);
        return JSON.toJSONString(report);
    }

    /** 汇总三个维度结果，生成 Markdown 审查报告 */
    public static ReviewReport generate(List<ReviewResult> results) {
        ReviewReport report = new ReviewReport();
        report.reviewId = results.get(0).reviewId;
        report.sessionId = results.get(0).sessionId;
        report.mode = ReviewConstants.AI_MODE ? "DeepSeek 大模型（AI）" : "规则引擎（非 AI）";
        report.totalScore = (int) Math.round(results.stream().mapToInt(r -> r.score).average().orElse(0));
        report.generatedAt = System.currentTimeMillis();

        StringBuilder md = new StringBuilder();
        md.append("# 代码审查报告\n\n");
        md.append("- 审查任务 ID：").append(report.reviewId).append('\n');
        md.append("- 审查模式：").append(report.mode).append('\n');
        md.append("- 审查维度：").append(results.size()).append(" 个（")
            .append(results.stream().map(r -> r.agent).collect(Collectors.joining(" / "))).append("）\n");
        md.append("- 生成时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(report.generatedAt))).append("\n\n");

        md.append("## 一、总体评分\n\n| 审查维度 | 得分 | 问题数 |\n| --- | --- | --- |\n");
        for (ReviewResult r : results) {
            md.append("| ").append(r.agent).append(" | ").append(r.score)
                .append(" | ").append(r.issues.size()).append(" |\n");
        }
        md.append("| **总体** | **").append(report.totalScore).append("** | **")
            .append(results.stream().mapToInt(r -> r.issues.size()).sum()).append("** |\n\n");

        md.append("## 二、问题清单\n\n| 维度 | 行号 | 级别 | 分类 | 问题描述 | 修复建议 |\n| --- | --- | --- | --- | --- | --- |\n");
        for (ReviewResult r : results) {
            for (ReviewIssue issue : r.issues) {
                md.append("| ").append(r.agent).append(" | ").append(issue.line)
                    .append(" | ").append(levelName(issue.severity))
                    .append(" | ").append(issue.category).append(" | ").append(issue.description)
                    .append(" | ").append(issue.suggestion).append(" |\n");
            }
        }

        md.append("\n## 三、改进建议\n\n");
        Set<String> suggestions = new LinkedHashSet<>();
        results.forEach(r -> r.issues.forEach(i -> suggestions.add(i.suggestion)));
        int index = 1;
        for (String suggestion : suggestions) {
            md.append(index++).append(". ").append(suggestion).append('\n');
        }

        // AI 路径下结论由 DeepSeek 根据报告内容生成，失败回退固定模板
        if (ReviewConstants.AI_MODE) {
            try {
                report.conclusion = DeepSeekReviewer.conclude(md.toString());
                LOG.info("[summary] AI 路径：DeepSeek 结论生成完成");
            } catch (Exception e) {
                LOG.warn("[summary] AI 路径：DeepSeek 结论生成失败({})，已回退模板结论", e.getMessage());
                report.conclusion = conclude(report.totalScore);
            }
        } else {
            report.conclusion = conclude(report.totalScore);
        }

        md.append("\n## 四、结论\n\n").append(report.conclusion).append('\n');
        report.markdown = md.toString();
        return report;
    }

    /** 根据总体得分给出结论 */
    private static String conclude(int totalScore) {
        if (totalScore >= 90) {
            return "代码质量良好，仅需关注少量提示项。";
        }
        if (totalScore >= 70) {
            return "代码存在一定问题，建议修复警告与错误后合入。";
        }
        return "代码存在较多问题，其中包含高危缺陷，必须修复后才能上线。";
    }

    private static String levelName(int severity) {
        return severity == ReviewIssue.ERROR ? "错误" : severity == ReviewIssue.WARNING ? "警告" : "提示";
    }
}
