#!/usr/bin/env bash
# ============================================================
#  多 Agent 代码审查系统 - 一键启动（Linux / macOS）
#  本脚本启动规则引擎路径（非 AI）。AI 路径请运行 ./start-agents-ai.sh
#  使用前请先修改下方【配置区】，并已执行 mvn clean package 构建
# ============================================================
set -e
cd "$(dirname "$0")"

# ---------- 配置区 1：阿里云 RocketMQ 实例（控制台获取） ----------
# 公网 TCP 接入点：控制台 -> 实例详情 -> 接入点 -> 公网 TCP 接入点
EP="rmq-cn-xxxx.cn-hangzhou.rmq.aliyuncs.com:8080"
# 实例 ID（命名空间）
NS="rmq-cn-xxxx"
# 智能身份识别 用户名/密码（控制台 -> 访问控制 -> 智能身份识别，不是阿里云 AccessKey）
AK="你的实例用户名"
SK="你的实例密码"
# 资源前缀：所有 Topic/Group/消息Key 的统一前缀（默认 rmqai，可自定义）
PREFIX="rmqai"

# ---------- 配置区 2：审查路径（默认规则引擎，无需额外配置） ----------
MODE="${MODE:-rule}"
DSK="${DSK:-}"

# ---------- 配置区 3：JDK 17+（一般无需修改） ----------
# 若 PATH 中的 java 不是 JDK 17，改成你的 JDK17 路径，如 JAVA="/path/to/jdk-17/bin/java"
JAVA="${JAVA:-java}"

JAR="target/code-review-system.jar"
if [ ! -f "$JAR" ]; then
  echo "[错误] 未找到 $JAR，请先执行: mvn clean package"
  exit 1
fi

COMMON="-DrocketMQEndpoint=$EP -DrocketMQNamespace=$NS -DrocketMQAK=$AK -DrocketMQSK=$SK -DresourcePrefix=$PREFIX"
AI_ARGS="-DreviewMode=$MODE -DdeepseekKey=$DSK"
LOGMGR="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# 先启动 4 个 Worker Agent，最后启动 Supervisor
"$JAVA" $LOGMGR -Dagent=style      $COMMON $AI_ARGS -jar "$JAR" &
"$JAVA" $LOGMGR -Dagent=logic      $COMMON $AI_ARGS -jar "$JAR" &
"$JAVA" $LOGMGR -Dagent=security   $COMMON $AI_ARGS -jar "$JAR" &
"$JAVA" $LOGMGR -Dagent=summary    $COMMON $AI_ARGS -jar "$JAR" &
"$JAVA" $LOGMGR -Dagent=supervisor $COMMON -jar "$JAR" &

wait
