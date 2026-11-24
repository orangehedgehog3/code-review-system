@echo off
chcp 65001 >nul
cd /d %~dp0

rem ============================================================
rem  多 Agent 代码审查系统 - 一键启动（Windows）
rem  本脚本启动规则引擎路径（非 AI）。AI 路径请双击 start-agents-ai.bat
rem  使用前请先修改下方【配置区】，并已执行 mvn clean package 构建
rem ============================================================

rem ---------- 配置区 1：阿里云 RocketMQ 实例（控制台获取） ----------
rem 公网 TCP 接入点：控制台 -> 实例详情 -> 接入点 -> 公网 TCP 接入点
set EP=rmq-cn-xxxx.cn-hangzhou.rmq.aliyuncs.com:8080
rem 实例 ID（命名空间）
set NS=rmq-cn-xxxx
rem 智能身份识别 用户名/密码（控制台 -> 访问控制 -> 智能身份识别，不是阿里云 AccessKey）
set AK=你的实例用户名
set SK=你的实例密码
rem 资源前缀：所有 Topic/Group/消息Key 的统一前缀（默认 rmqai，可自定义）
set PREFIX=rmqai

rem ---------- 配置区 2：审查路径（默认规则引擎，无需额外配置） ----------
if not defined MODE set MODE=rule
rem DeepSeek API Key：仅 AI 路径使用（start-agents-ai.bat 会传入），这里保持留空
if not defined DSK set DSK=

rem ---------- 配置区 3：JDK 17+（一般无需修改） ----------
rem 若 PATH 中的 java 不是 JDK 17，取消下一行注释并改成你的 JDK17 安装路径
rem set JAVA_HOME=C:\Program Files\Java\jdk-17
if not defined JAVA_HOME (set JAVA=java) else (set JAVA=%JAVA_HOME%\bin\java.exe)

if not exist target\code-review-system.jar (
    echo [错误] 未找到 target\code-review-system.jar，请先执行: mvn clean package
    pause
    exit /b 1
)

rem ---------- 启动 5 个 Agent 进程（先启动 4 个 Worker，最后启动 Supervisor） ----------
set LOGMGR=-Djava.util.logging.manager=org.jboss.logmanager.LogManager
start "StyleAgent"      cmd /k "%JAVA%" %LOGMGR% -Dagent=style      -DrocketMQEndpoint=%EP% -DrocketMQNamespace=%NS% -DrocketMQAK=%AK% -DrocketMQSK=%SK% -DresourcePrefix=%PREFIX% -DreviewMode=%MODE% -DdeepseekKey=%DSK% -jar target\code-review-system.jar
start "LogicAgent"      cmd /k "%JAVA%" %LOGMGR% -Dagent=logic      -DrocketMQEndpoint=%EP% -DrocketMQNamespace=%NS% -DrocketMQAK=%AK% -DrocketMQSK=%SK% -DresourcePrefix=%PREFIX% -DreviewMode=%MODE% -DdeepseekKey=%DSK% -jar target\code-review-system.jar
start "SecurityAgent"   cmd /k "%JAVA%" %LOGMGR% -Dagent=security   -DrocketMQEndpoint=%EP% -DrocketMQNamespace=%NS% -DrocketMQAK=%AK% -DrocketMQSK=%SK% -DresourcePrefix=%PREFIX% -DreviewMode=%MODE% -DdeepseekKey=%DSK% -jar target\code-review-system.jar
start "SummaryAgent"    cmd /k "%JAVA%" %LOGMGR% -Dagent=summary    -DrocketMQEndpoint=%EP% -DrocketMQNamespace=%NS% -DrocketMQAK=%AK% -DrocketMQSK=%SK% -DresourcePrefix=%PREFIX% -DreviewMode=%MODE% -DdeepseekKey=%DSK% -jar target\code-review-system.jar
start "SupervisorAgent" cmd /k "%JAVA%" %LOGMGR% -Dagent=supervisor -DrocketMQEndpoint=%EP% -DrocketMQNamespace=%NS% -DrocketMQAK=%AK% -DrocketMQSK=%SK% -DresourcePrefix=%PREFIX% -jar target\code-review-system.jar

echo 已启动 5 个 Agent 窗口，请在 SupervisorAgent 窗口中输入 demo 或文件路径开始审查。
