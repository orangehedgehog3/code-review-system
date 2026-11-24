package org.example.codereview.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.rocketmq.a2a.common.model.RocketMQRequest;
import org.apache.rocketmq.a2a.common.model.RocketMQResponse;
import org.apache.rocketmq.a2a.common.uitl.RocketMQUtil;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息收发封装：基于 rocketmq-a2a SDK 的 RocketMQRequest/RocketMQResponse 协议
 * 实现 Agent 间的异步请求-响应。请求发到目标 Agent 的 Normal Topic（任务分发），
 * 响应发回请求方指定的 LiteTopic（会话级结果回传），通过 requestId 关联。
 * 等待响应复用 SDK 的 {@link RocketMQUtil#getResult}（底层为 Future 关联表，超时 120 秒）。
 */
public final class ReviewMessaging {
    private static final Logger log = LoggerFactory.getLogger(ReviewMessaging.class);
    private static final ClientServiceProvider PROVIDER = ClientServiceProvider.loadService();

    private ReviewMessaging() {
    }

    /**
     * 发送审查请求到目标 Agent 的 Normal Topic。
     *
     * @param producer          目标 Topic 对应的 Producer
     * @param targetTopic       目标 Agent 的 Normal Topic（任务分发）
     * @param sessionLiteTopic  本次审查的会话 LiteTopic
     * @param responseLiteTopic 结果回传 LiteTopic（所有 Agent 共用）
     * @param payload           业务载荷（ReviewRequest 或结果列表）
     * @param agent             目标 Agent 标识（用于构造含资源前缀的 Message Key）
     * @return requestId，用于与响应关联
     */
    public static String sendRequest(Producer producer, String targetTopic, String sessionLiteTopic,
        String responseLiteTopic, Object payload, String agent) throws Exception {
        String requestId = UUID.randomUUID().toString();
        RocketMQRequest request = RocketMQRequest.builder()
            .requestBody(JSON.toJSONString(payload))
            .destAgentTopic(targetTopic)
            .workAgentResponseTopic(responseLiteTopic)
            .liteTopic(sessionLiteTopic)
            .requestId(requestId)
            .build();
        Message message = PROVIDER.newMessageBuilder()
            .setTopic(targetTopic)
            // Message Key 包含资源前缀，便于在控制台按 Key 查询消息轨迹
            .setKeys(ReviewConstants.buildMessageKey(agent, requestId))
            .setBody(JSON.toJSONString(request).getBytes(StandardCharsets.UTF_8))
            .build();
        producer.send(message);
        log.info("请求已发送: agent={}, topic={}, requestId={}, session={}", agent, targetTopic, requestId, sessionLiteTopic);
        return requestId;
    }

    /**
     * Worker 端发送响应：发送到请求方指定的 LiteTopic（会话级结果回传），
     * 并通过 {@code setLiteTopic} 将消息绑定到请求方会话。
     */
    public static void sendResponse(Producer producer, String responseLiteTopic, String sessionLiteTopic,
        RocketMQResponse response, String agent) throws Exception {
        Message message = PROVIDER.newMessageBuilder()
            .setTopic(responseLiteTopic)
            .setLiteTopic(sessionLiteTopic)
            .setKeys(ReviewConstants.buildMessageKey(agent, response.getRequestId()))
            .setBody(JSON.toJSONString(response).getBytes(StandardCharsets.UTF_8))
            .build();
        producer.send(message);
        log.info("响应已回传: agent={}, requestId={}, liteTopic={}", agent, response.getRequestId(), sessionLiteTopic);
    }

    /** 从消费到的消息中解析出 RocketMQRequest */
    public static RocketMQRequest parseRequest(MessageView messageView) {
        ByteBuffer body = messageView.getBody();
        byte[] bytes = new byte[body.remaining()];
        body.get(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        try {
            return JSON.parseObject(json, RocketMQRequest.class);
        } catch (Exception e) {
            log.error("解析 RocketMQRequest 失败，消息体: {}", json, e);
            return null;
        }
    }

    /** 阻塞等待与 requestId 关联的响应（复用 SDK 的响应关联机制，超时 120 秒） */
    public static String waitResponse(String requestId) throws Exception {
        return RocketMQUtil.getResult(requestId, ReviewConstants.NAMESPACE, new TypeReference<Object>() {
        });
    }
}
