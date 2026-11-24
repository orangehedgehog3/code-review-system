package org.example.codereview.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.example.codereview.common.model.ReviewIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 审查路径：调用 DeepSeek 大模型完成单维度的代码审查与报告结论生成。
 * 未配置 Key（-DdeepseekKey=）或调用失败时自动回退到规则引擎路径，保证系统始终可用。
 */
public final class DeepSeekReviewer {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekReviewer.class);
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    /** 输入过长时截断，控制 Token 消耗 */
    private static final int MAX_INPUT_LEN = 8000;

    private DeepSeekReviewer() {}

    /**
     * 按审查模式返回该维度的问题清单：
     * AI 路径（-DreviewMode=ai 且配置 Key）调用 DeepSeek，调用失败自动回退；规则路径直接使用 fallback。
     */
    public static List<ReviewIssue> review(String agent, String code, Supplier<List<ReviewIssue>> fallback) {
        if (!ReviewConstants.AI_MODE) {
            return fallback.get();
        }
        try {
            List<ReviewIssue> issues = call(agent, code);
            log.info("[{}] AI 路径：DeepSeek 审查完成，发现问题 {} 条", agent, issues.size());
            return issues;
        } catch (Exception e) {
            log.warn("[{}] AI 路径：DeepSeek 调用失败({})，已自动回退规则引擎", agent, e.getMessage());
            return fallback.get();
        }
    }

    /** AI 路径：根据报告前三部分内容生成结论段，失败时由调用方回退模板结论 */
    public static String conclude(String report) throws Exception {
        String context = report.length() > MAX_INPUT_LEN ? report.substring(0, MAX_INPUT_LEN) : report;
        return clean(chat("你是资深 Java 代码审查专家。请根据提供的代码审查报告，写一段中文结论（100 字以内），"
            + "概括总体质量、最严重的问题和上线建议。只输出结论文字本身，不要任何其他内容。", context, 300));
    }

    /** 调用 DeepSeek 审查单个维度，返回问题 JSON 数组 */
    private static List<ReviewIssue> call(String agent, String code) throws Exception {
        String codePart = code.length() > MAX_INPUT_LEN ? code.substring(0, MAX_INPUT_LEN) : code;
        String content = chat("你是资深 Java 代码审查专家。请针对【" + dimension(agent) + "】维度审查用户代码，"
            + "只输出一个 JSON 数组，不要输出任何其他文字。数组元素格式："
            + "{\"line\":行号,\"severity\":级别,\"category\":分类,\"description\":问题描述,\"suggestion\":修复建议}。"
            + "severity 取值：3=错误必须修复，2=警告建议修复，1=提示可选。没有问题时输出 []。",
            "请审查以下代码：\n```java\n" + codePart + "\n```", 2000);
        return parseIssues(content);
    }

    /** 通用 DeepSeek chat/completions 调用，返回 assistant 文本内容 */
    private static String chat(String system, String user, int maxTokens) throws Exception {
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", system);
        JSONObject usr = new JSONObject();
        usr.put("role", "user");
        usr.put("content", user);

        JSONObject payload = new JSONObject();
        payload.put("model", "deepseek-chat");
        payload.put("temperature", 0.2);
        payload.put("max_tokens", maxTokens);
        List<JSONObject> messages = new ArrayList<>();
        messages.add(sys);
        messages.add(usr);
        payload.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
            .timeout(Duration.ofSeconds(90))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + ReviewConstants.DEEPSEEK_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
            .build();
        HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return JSON.parseObject(response.body()).getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message").getString("content");
    }

    /** 去掉模型可能包裹的 ```json 代码块标记后解析为问题清单 */
    private static List<ReviewIssue> parseIssues(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start >= 0 && end > start) {
            content = content.substring(start, end + 1);
        }
        List<ReviewIssue> issues = JSON.parseArray(content, ReviewIssue.class);
        return issues == null ? new ArrayList<>() : issues;
    }

    /** 去掉模型输出可能带上的代码块/引号包裹 */
    private static String clean(String content) {
        content = content.trim();
        if (content.startsWith("```")) {
            int nl = content.indexOf('\n');
            content = nl >= 0 ? content.substring(nl + 1) : content.substring(3);
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
        }
        return content.trim();
    }

    /** 各维度对应的提示词口径 */
    private static String dimension(String agent) {
        switch (agent) {
            case "style": return "代码风格（命名规范、魔法数字、日志输出方式）";
            case "logic": return "代码逻辑（除零、边界条件、资源泄漏、异常处理）";
            default: return "代码安全（SQL 注入、XSS、敏感信息、路径穿越）";
        }
    }
}
