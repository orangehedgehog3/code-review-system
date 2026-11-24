package org.example.codereview.worker;

import java.util.concurrent.CountDownLatch;
import org.apache.rocketmq.a2a.common.model.RocketMQRequest;
import org.apache.rocketmq.a2a.common.model.RocketMQResponse;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.shaded.commons.lang3.StringUtils;
import org.example.codereview.common.ReviewConstants;
import org.example.codereview.common.ReviewMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.buildProducer;
import static org.apache.rocketmq.a2a.common.uitl.RocketMQUtil.buildPushConsumer;

/**
 * Worker Agent 基类：订阅自己的 Normal Topic 接收任务，调用子类 {@link #process} 处理，
 * 结果通过 LiteTopic 回传给请求方。
 */
public abstract class ReviewWorkerBase {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String agentName;
    private final String taskTopic;
    private final String consumerGroup;

    protected ReviewWorkerBase(String agentName, String taskTopic, String consumerGroup) {
        this.agentName = agentName;
        this.taskTopic = taskTopic;
        this.consumerGroup = consumerGroup;
    }

    /** 启动 Agent：创建响应 Producer + 订阅任务 Topic，然后阻塞保持进程存活 */
    public void start() throws Exception {
        if (StringUtils.isEmpty(ReviewConstants.ENDPOINT) || StringUtils.isEmpty(ReviewConstants.NAMESPACE)) {
            throw new IllegalArgumentException("缺少必需参数: -DrocketMQEndpoint= -DrocketMQNamespace=");
        }
        Producer responseProducer = buildProducer(ReviewConstants.NAMESPACE, ReviewConstants.ENDPOINT,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, ReviewConstants.RESPONSE_LITE_TOPIC);

        PushConsumer pushConsumer = buildPushConsumer(ReviewConstants.ENDPOINT, ReviewConstants.NAMESPACE,
            ReviewConstants.ACCESS_KEY, ReviewConstants.SECRET_KEY, consumerGroup, taskTopic,
            messageView -> {
                try {
                    RocketMQRequest request = ReviewMessaging.parseRequest(messageView);
                    if (request == null || StringUtils.isEmpty(request.getRequestId())) {
                        return ConsumeResult.SUCCESS;
                    }
                    log.info("[{}] 收到任务: requestId={}, key={}", agentName, request.getRequestId(), messageView.getKeys());
                    String resultJson = process(request.getRequestBody());
                    // 回传响应：requestId 关联请求，liteTopic 指向请求方会话
                    RocketMQResponse response = RocketMQResponse.builder()
                        .requestId(request.getRequestId())
                        .responseBody(resultJson)
                        .stream(false)
                        .end(false)
                        .contextId(request.getLiteTopic())
                        .build();
                    ReviewMessaging.sendResponse(responseProducer, request.getWorkAgentResponseTopic(),
                        request.getLiteTopic(), response, agentName);
                } catch (Exception e) {
                    log.error("[{}] 处理任务失败", agentName, e);
                }
                return ConsumeResult.SUCCESS;
            });

        log.info("[{}] Agent 启动完成，订阅 Topic: {}，Group: {}，结果回传 LiteTopic: {}",
            agentName, taskTopic, consumerGroup, ReviewConstants.RESPONSE_LITE_TOPIC);
        new CountDownLatch(1).await();
    }

    /** 子类实现：解析请求载荷 JSON，返回结果 JSON */
    protected abstract String process(String payloadJson) throws Exception;
}
