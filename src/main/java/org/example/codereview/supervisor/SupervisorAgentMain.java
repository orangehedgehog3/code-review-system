package org.example.codereview.supervisor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.shaded.commons.lang3.StringUtils;
import org.example.codereview.common.ReviewConstants;
import org.example.codereview.common.ReviewMessaging;
import org.example.codereview.common.model.ReviewReport;
import org.example.codereview.common.model.ReviewRequest;
import org.example.codereview.common.model.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.buildPushConsumer;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.getOrCreateLitePushConsumer;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.getOrCreateProducer;

/**
 * SupervisorAgent：主控 Agent。接收代码后拆分为风格/逻辑/安全三个维度并行分发到
 * Normal Topic，通过 LiteTopic 按会话收集结果，最后交给 SummaryAgent 生成审查报告。
 */
public class SupervisorAgentMain {
    private static final Logger log = LoggerFactory.getLogger(SupervisorAgentMain.class);

    /** 并行分发三个审查维度 */
    private final ExecutorService reviewPool = Executors.newFixedThreadPool(3);

    private LitePushConsumer litePushConsumer;
    private Producer styleProducer;
    private Producer logicProducer;
    private Producer securityProducer;
    private Producer summaryProducer;

    public static void main(String[] args) throws Exception {
        new SupervisorAgentMain().start();
    }

    /** 启动 Supervisor：初始化消费者与生产者，进入交互循环 */
    public void start() throws Exception {
        if (StringUtils.isEmpty(ReviewConstants.ENDPOINT) || StringUtils.isEmpty(ReviewConstants.NAMESPACE)) {
            throw new IllegalArgumentException("缺少必需参数: -DrocketMQEndpoint= -DrocketMQNamespace=");
        }
        // 1. 结果回传消费者：LitePushConsumer 订阅结果回传 LiteTopic（初始会话占位）
        litePushConsumer = getOrCreateLitePushConsumer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.RESPONSE_LITE_TOPIC,
            ReviewConstants.GID_SUPERVISOR_LITE, ReviewConstants.SESSION_PREFIX + "init");
        log.info("结果回传消费者就绪: LiteTopic={}, Group={}", ReviewConstants.RESPONSE_LITE_TOPIC, ReviewConstants.GID_SUPERVISOR_LITE);

        // 2. 四个目标 Agent 的 Producer（按 Topic 缓存复用）
        styleProducer = getOrCreateProducer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.STYLE_TASK_TOPIC);
        logicProducer = getOrCreateProducer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.LOGIC_TASK_TOPIC);
        securityProducer = getOrCreateProducer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.SECURITY_TASK_TOPIC);
        summaryProducer = getOrCreateProducer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.SUMMARY_TASK_TOPIC);
        log.info("任务分发 Producer 就绪");

        // 3. 订阅总入口 Topic：接收外部投递的审查任务（消息体为 {"code": "..."} 或纯代码文本）
        PushConsumer entryConsumer = buildPushConsumer(ReviewConstants.ENDPOINT, ReviewConstants.NAMESPACE,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.GID_SUPERVISOR,
            ReviewConstants.REVIEW_TASK_TOPIC, messageView -> {
                String body = new String(messageView.getBody().array(),
                    messageView.getBody().position(), messageView.getBody().remaining(), StandardCharsets.UTF_8);
                String code = extractCode(body);
                log.info("从入口 Topic 收到审查任务: key={}", messageView.getKeys());
                startReview(code);
                return ConsumeResult.SUCCESS;
            });
        log.info("总入口 Topic 订阅就绪: {}", ReviewConstants.REVIEW_TASK_TOPIC);

        // 4. 交互循环
        interact();
        entryConsumer.close();
        reviewPool.shutdown();
    }

    /** 交互式输入：文件路径 / demo（内置样例）/ quit */
    private void interact() {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8.name());
        System.out.println("======================================================");
        System.out.println("  多 Agent 代码审查系统 - SupervisorAgent（资源前缀 " + ReviewConstants.RESOURCE_PREFIX + "）");
        System.out.println("======================================================");
        System.out.println("  输入待审查 Java 文件的路径，或输入 demo 使用内置样例代码，quit 退出");
        while (true) {
            System.out.print("请输入 > ");
            String input = scanner.nextLine().trim();
            if (StringUtils.isEmpty(input)) {
                continue;
            }
            if ("quit".equalsIgnoreCase(input)) {
                System.out.println("Supervisor 退出");
                break;
            }
            try {
                String code;
                if ("demo".equalsIgnoreCase(input)) {
                    code = loadDemoCode();
                    System.out.println("已加载内置样例代码（" + code.split("\\r?\\n").length + " 行）");
                } else {
                    Path path = Paths.get(input);
                    if (!Files.exists(path)) {
                        System.out.println("文件不存在: " + input);
                        continue;
                    }
                    code = Files.readString(path, StandardCharsets.UTF_8);
                }
                startReview(code);
            } catch (Exception e) {
                log.error("审查流程异常", e);
            }
        }
    }

    /** 完整审查流程：创建会话 -> 并行分发三维度 -> 收集结果 -> 汇总生成报告 -> 输出保存 */
    private synchronized void startReview(String code) {
        String reviewId = ReviewConstants.newReviewId();
        String sessionLiteTopic = ReviewConstants.newSessionLiteTopic();
        try {
            litePushConsumer.subscribeLite(sessionLiteTopic);
            log.info("审查开始: reviewId={}, 会话 LiteTopic={}", reviewId, sessionLiteTopic);
            long start = System.currentTimeMillis();

            // ---- 阶段 1：并行分发三个审查维度 ----
            List<Future<ReviewResult>> futures = new ArrayList<>();
            futures.add(reviewPool.submit(() -> dispatchAndWait(
                styleProducer, ReviewConstants.STYLE_TASK_TOPIC, "style", reviewId, sessionLiteTopic, code)));
            futures.add(reviewPool.submit(() -> dispatchAndWait(
                logicProducer, ReviewConstants.LOGIC_TASK_TOPIC, "logic", reviewId, sessionLiteTopic, code)));
            futures.add(reviewPool.submit(() -> dispatchAndWait(
                securityProducer, ReviewConstants.SECURITY_TASK_TOPIC, "security", reviewId, sessionLiteTopic, code)));

            // ---- 阶段 2：收集三个维度的结果 ----
            List<ReviewResult> results = new ArrayList<>();
            for (Future<ReviewResult> future : futures) {
                results.add(future.get(130, TimeUnit.SECONDS));
            }
            log.info("三个维度审查全部完成，耗时 {} ms", System.currentTimeMillis() - start);

            // 阶段 3：汇总生成最终报告（载荷为三个维度结果的 JSON 数组）
            String summaryRequestId = ReviewMessaging.sendRequest(summaryProducer,
                ReviewConstants.SUMMARY_TASK_TOPIC, sessionLiteTopic, ReviewConstants.RESPONSE_LITE_TOPIC,
                results, "summary");
            ReviewReport report = JSON.parseObject(ReviewMessaging.waitResponse(summaryRequestId), ReviewReport.class);

            // 阶段 4：输出并保存报告
            saveReport(report);
            System.out.println();
            System.out.println(report.markdown);
            log.info("审查完成: reviewId={}, 总体得分={}, 总耗时 {} ms",
                reviewId, report.totalScore, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("审查流程失败: reviewId={}", reviewId, e);
        }
    }

    /** 分发单个维度任务并阻塞等待该维度的审查结果 */
    private ReviewResult dispatchAndWait(Producer producer, String topic, String agent,
        String reviewId, String sessionLiteTopic, String code) throws Exception {
        ReviewRequest request = new ReviewRequest(reviewId, sessionLiteTopic, agent, code, System.currentTimeMillis());
        String requestId = ReviewMessaging.sendRequest(producer, topic, sessionLiteTopic,
            ReviewConstants.RESPONSE_LITE_TOPIC, request, agent);
        String resultJson = ReviewMessaging.waitResponse(requestId);
        return JSON.parseObject(resultJson, ReviewResult.class);
    }

    /** 保存报告到 reports 目录 */
    private void saveReport(ReviewReport report) throws Exception {
        Path dir = Paths.get("reports");
        Files.createDirectories(dir);
        Path file = dir.resolve(report.reviewId + ".md");
        Files.writeString(file, report.markdown, StandardCharsets.UTF_8);
        System.out.println("报告已保存: " + file.toAbsolutePath());
    }

    /** 从入口 Topic 消息体中提取代码（支持 JSON 与纯文本两种格式） */
    private String extractCode(String body) {
        try {
            JSONObject obj = JSON.parseObject(body);
            if (obj != null && obj.getString("code") != null) {
                return obj.getString("code");
            }
        } catch (Exception ignore) {
            // 非 JSON，按纯文本代码处理
        }
        return body;
    }

    /** 加载内置样例代码（classpath: demo/DemoCode.java） */
    private String loadDemoCode() throws Exception {
        try (InputStream in = SupervisorAgentMain.class.getClassLoader().getResourceAsStream("demo/DemoCode.java")) {
            if (in == null) {
                throw new IllegalStateException("未找到内置样例代码");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
