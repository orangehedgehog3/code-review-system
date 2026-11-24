package org.example.codereview.common.model;

/**
 * 单条审查问题：Style/Logic/Security Agent 发现的具体缺陷（fastjson 按 public 字段序列化）。
 */
public class ReviewIssue {
    public static final int INFO = 1, WARNING = 2, ERROR = 3;

    public String category;
    public int severity;
    public int line;
    public String description;
    public String suggestion;

    public ReviewIssue() {}

    public ReviewIssue(String category, int severity, int line, String description, String suggestion) {
        this.category = category;
        this.severity = severity;
        this.line = line;
        this.description = description;
        this.suggestion = suggestion;
    }
}
