package org.example.codereview.common;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.codereview.common.model.ReviewIssue;

/**
 * 审查规则公共工具：三个审查 Agent 共用的正则扫描逻辑。
 */
public final class ReviewRules {

    private ReviewRules() {}

    /** 用正则扫描代码，每处命中记录一条问题（自动计算行号） */
    public static void add(List<ReviewIssue> issues, String code, String regex, int severity,
        String category, String description, String suggestion) {
        Matcher m = Pattern.compile(regex).matcher(code);
        while (m.find()) {
            issues.add(new ReviewIssue(category, severity, lineOf(code, m.start()), description, suggestion));
        }
    }

    /** 计算字符串下标对应的行号（从 1 开始） */
    public static int lineOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
