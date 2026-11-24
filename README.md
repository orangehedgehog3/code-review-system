# 多 Agent 代码审查系统（rocketmq-a2a-code-review）

基于 **Apache RocketMQ 5.x + rocketmq-a2a SDK** 的多 Agent 协作代码审查系统。Supervisor 接收待审查代码后，并行分发到风格 / 逻辑 / 安全三个审查 Agent，最后由 Summary Agent 汇总生成 Markdown 审查报告。

支持**两条可选审查路径**：

| 路径 | 说明 | 依赖 |
| --- | --- | --- |
| 规则引擎（非 AI） | 内置正则规则离线扫描，开箱即用、结果稳定 | 无 |
| DeepSeek 大模型（AI） | 每个维度调用 DeepSeek 大模型审查，结论也由 AI 生成；调用失败自动回退规则引擎 | DeepSeek API Key |

通信模型：**Normal Topic 任务分发 + LiteTopic 会话级结果回传**——每次审查动态创建会话 LiteTopic，多个审查任务并发执行时结果互不串扰。

```
                         ┌──────────────┐
        {"code": "..."}  │   Supervisor │  交互输入 / 入口 Topic 订阅
              └──────────┤  （主控 Agent）├──────────┐
                         └──────┬───────┘          │
           并行分发（Normal Topic）│                  │
        ┌────────────┬───────────┴───────┬──────────┤
        ▼            ▼                   ▼          ▼
  ┌──────────┐ ┌──────────┐       ┌──────────┐ ┌──────────┐
  │  Style   │ │  Logic   │       │ Security │ │ Summary  │
  │  Agent   │ │  Agent   │       │  Agent   │ │  Agent   │
  └────┬─────┘ └────┬─────┘       └────┬─────┘ └────┬─────┘
       └────────────┴─── 结果回传（LiteTopic，按会话隔离）──┴────────┘
```

## 环境要求

- **JDK 17+**（`java -version` 确认；默认 JDK 8 会报 `UnsupportedClassVersionError`）
- **Maven 3.6+**
- **阿里云 RocketMQ 5.x 实例**：需支持 LiteTopic（5.x 标准版或 Serverless 独享均可）
- （仅 AI 路径）**DeepSeek API Key**：<https://platform.deepseek.com>

## 快速开始（5 步）

1. 创建阿里云 RocketMQ 实例（带 `lite-topic` 标签）
2. 创建 6 个 Topic 与 6 个 Group
3. 修改启动脚本配置（接入点 / 用户名密码 / 可选 API Key）
4. `mvn clean package` 构建
5. 双击启动脚本，在 Supervisor 窗口输入 `demo` 开始审查

下面分步说明。

## 一、创建阿里云 RocketMQ 实例

1. 登录阿里云控制台 → 消息队列 RocketMQ 版 → 实例列表 → **创建实例**
2. 版本选择 **5.x 系列**；实例类型选择 **标准版**（非 Serverless）——LiteTopic 能力仅 5.x 标准版 / Serverless 独享支持
3. **关键步骤**：给实例打标签 `version_capability` = `lite-topic`（LiteTopic 能力开关）
   - 创建流程中有标签配置项则直接填写；否则实例创建完成后：实例详情 → 标签管理 → 新增标签
4. 网络选择 **VPC 与交换机**（留空会报「VPC ID 不允许为空」）
5. 购买后等待实例状态变为「运行中」
6. 记下两个信息（后面配置脚本要用）：
   - **实例 ID**（形如 `rmq-cn-xxxx`）
   - **公网 TCP 接入点**（实例详情 → 接入点 → 公网 TCP 接入点）。本地电脑运行必须用**公网**接入点，内网接入点本机连不上
7. 创建访问账号：控制台 → 访问控制 → **智能身份识别** → 创建用户名与密码
   - ⚠️ 这里填的是实例级用户名 / 密码，**不是**阿里云主账号的 AccessKey（用 AccessKey 会报 40100 鉴权失败）

> 费用提示：实例按量计费，验证完成后建议在控制台**释放实例**，避免持续扣费。

## 二、创建 Topic 与 Group

资源前缀统一使用 `rmqai`（与脚本中 `PREFIX` 配置一致；多人共用一个实例时可改成自己的前缀避免冲突）。

### Topic（6 个）

| 类型 | 名称 | 消息类型 |
| --- | --- | --- |
| Normal Topic | `rmqai-review-task` | 普通消息 |
| Normal Topic | `rmqai-style-task` | 普通消息 |
| Normal Topic | `rmqai-logic-task` | 普通消息 |
| Normal Topic | `rmqai-security-task` | 普通消息 |
| Normal Topic | `rmqai-summary-task` | 普通消息 |
| **Lite Topic** | `rmqai-review-response` | **Lite 消息** |

### Group（6 个）

| 名称 | 消息类型 | 绑定 Topic |
| --- | --- | --- |
| `GID_rmqai_supervisor` | 普通消息 | `rmqai-review-task` |
| `GID_rmqai_supervisor_lite` | **轻量消息** | `rmqai-review-response` |
| `GID_rmqai_style` | 普通消息 | `rmqai-style-task` |
| `GID_rmqai_logic` | 普通消息 | `rmqai-logic-task` |
| `GID_rmqai_security` | 普通消息 | `rmqai-security-task` |
| `GID_rmqai_summary` | 普通消息 | `rmqai-summary-task` |

> ⚠️ `GID_rmqai_supervisor_lite` 必须选择「轻量消息」类型并绑定 Lite Topic `rmqai-review-response`，其余 5 个组选择「普通消息」。
> ⚠️ 若创建 Lite Topic 失败或没有「Lite 消息」选项，说明实例未开启能力：检查实例是否为 5.x 标准版、是否打了 `version_capability=lite-topic` 标签。

## 三、获取 DeepSeek API Key（可选，仅 AI 路径）

1. 注册并登录 <https://platform.deepseek.com>
2. 控制台 → API Keys → 创建 API Key
3. 把 Key 填入 `start-agents-ai.bat`（或 `start-agents-ai.sh`）的 `DSK` 变量

不填 Key 也可以运行 AI 启动脚本：系统检测到 Key 为空会自动回退规则引擎路径，不会报错。

## 四、配置启动脚本

编辑 `start-agents.bat`（Windows）/ `start-agents.sh`（Linux / macOS）开头的配置区：

```bat
set EP=rmq-cn-xxxx.cn-hangzhou.rmq.aliyuncs.com:8080   :: 公网 TCP 接入点
set NS=rmq-cn-xxxx                                      :: 实例 ID
set AK=你的实例用户名                                    :: 智能身份识别用户名
set SK=你的实例密码                                      :: 智能身份识别密码
set PREFIX=rmqai                                        :: 资源前缀（默认即可）
```

## 五、构建

```bat
:: Windows（若默认 java 是 JDK 8，先切换到 JDK 17）
set JAVA_HOME=C:\Program Files\Java\jdk-17
mvn clean package
```

```bash
# Linux / macOS
JAVA_HOME=/path/to/jdk-17 mvn clean package
```

产物：`target/code-review-system.jar`（fat jar，一个 jar 启动全部 5 种 Agent）。

## 六、启动

**Windows**：双击 `start-agents.bat`（规则引擎路径）或 `start-agents-ai.bat`（AI 路径）。
**Linux / macOS**：运行 `./start-agents.sh` 或 `./start-agents-ai.sh`（首次需 `chmod +x start-agents*.sh`）。

脚本会打开 5 个 Agent 窗口（Style / Logic / Security / Summary / Supervisor）。

也可以手动开 5 个终端逐个启动（公共参数相同）：

```bash
java -Dagent=style      -DrocketMQEndpoint=<接入点> -DrocketMQNamespace=<实例ID> -DrocketMQAK=<用户名> -DrocketMQSK=<密码> -DresourcePrefix=rmqai -DreviewMode=rule -DdeepseekKey=<可选> -jar target/code-review-system.jar
java -Dagent=logic      ...（同上）
java -Dagent=security   ...（同上）
java -Dagent=summary    ...（同上）
java -Dagent=supervisor ...（同上，无需 reviewMode/deepseekKey）
```

先启动 4 个 Worker，最后启动 Supervisor。

## 七、触发审查

在 **SupervisorAgent** 窗口：

- 输入 `demo`：使用内置样例代码（含 SQL 注入、除零、硬编码密码、死循环等缺陷），一键跑通全流程
- 输入 Java 文件路径：审查你自己的代码，如 `D:\code\MyClass.java`
- 输入 `quit`：退出

审查报告输出到 `reports/<reviewId>.md` 并在 Supervisor 窗口打印。

也可以在 RocketMQ 控制台直接向 `rmqai-review-task` 发送一条消息（消息体 `{"code": "..."}` 或纯代码文本），Supervisor 同样会触发审查——方便观察完整消息轨迹（按消息 Key `rmqai-*` 查询）。

## 八、参数说明

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `-Dagent` | 是 | `supervisor` / `style` / `logic` / `security` / `summary` |
| `-DrocketMQEndpoint` | 是 | 公网 TCP 接入点（含端口 8080） |
| `-DrocketMQNamespace` | 是 | 阿里云实例 ID |
| `-DrocketMQAK` | 是 | 智能身份识别用户名（非 AccessKey） |
| `-DrocketMQSK` | 是 | 智能身份识别密码 |
| `-DresourcePrefix` | 否 | 资源前缀，默认 `rmqai`；多人共用实例时改它避免冲突 |
| `-DreviewMode` | 否 | `rule`（默认，规则引擎）/ `ai`（DeepSeek 大模型） |
| `-DdeepseekKey` | 否 | DeepSeek API Key；AI 模式且 Key 为空时自动回退规则引擎 |

## 九、常见问题

| 现象 | 原因与解决 |
| --- | --- |
| 报错 40100 鉴权失败 | AK/SK 必须是实例「智能身份识别」的用户名密码，不是阿里云 AccessKey |
| `UnsupportedClassVersionError` | jar 用 JDK 17 编译，请用 JDK 17+ 运行 |
| 创建 Lite Topic 失败 / 没有 Lite 消息选项 | 实例需 5.x 标准版且打标签 `version_capability=lite-topic` |
| 创建实例报「VPC ID 不允许为空」 | 创建实例时选择 VPC 与交换机 |
| 连接超时 / 一直连不上 | 本地运行必须用「公网」接入点（含 :8080 端口）；内网接入点仅 VPC 内可用 |
| AI 路径日志提示「回退规则引擎」 | 检查 DSK 是否填写正确、网络能否访问 api.deepseek.com；回退是容错设计，系统仍可用 |
| 修改代码后启动报错 | 先关闭全部 5 个 Agent 窗口（Windows 下运行中的 jar 被占用，无法重新构建） |

## 十、目录结构

```
├── pom.xml                      # Maven 配置（rocketmq-a2a 1.0.9）
├── start-agents.bat / .sh       # 规则引擎路径一键启动
├── start-agents-ai.bat / .sh    # AI（DeepSeek）路径一键启动
├── src/main/java/org/example/codereview/
│   ├── Launcher.java            # -Dagent 分发入口
│   ├── supervisor/              # SupervisorAgent：任务拆分、并行分发、收集汇总
│   ├── style/ logic/ security/  # 三个审查 Worker Agent
│   ├── summary/                 # SummaryAgent：聚合生成 Markdown 报告
│   ├── worker/                  # Worker 公共基类（订阅任务 Topic + LiteTopic 回传）
│   └── common/
│       ├── ReviewConstants.java # 资源命名与运行参数
│       ├── ReviewMessaging.java # RocketMQRequest/Response 收发封装
│       ├── ReviewRules.java     # 规则引擎正则扫描
│       ├── DeepSeekReviewer.java# DeepSeek 调用与回退逻辑
│       └── model/               # ReviewRequest/Result/Issue/Report 模型
└── src/main/resources/
    ├── demo/DemoCode.java       # 内置演示样例（故意含缺陷）
    └── logback.xml
```

## 许可证

[MIT](LICENSE)
