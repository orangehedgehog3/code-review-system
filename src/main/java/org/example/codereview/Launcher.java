package org.example.codereview;

import org.example.codereview.logic.LogicAgent;
import org.example.codereview.security.SecurityAgent;
import org.example.codereview.style.StyleAgent;
import org.example.codereview.summary.SummaryAgent;
import org.example.codereview.supervisor.SupervisorAgentMain;

/**
 * 统一入口：通过 -Dagent=supervisor|style|logic|security|summary 启动对应 Agent 进程。
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        String agent = System.getProperty("agent", "");
        switch (agent) {
            case "supervisor" -> SupervisorAgentMain.main(args);
            case "style" -> StyleAgent.main(args);
            case "logic" -> LogicAgent.main(args);
            case "security" -> SecurityAgent.main(args);
            case "summary" -> SummaryAgent.main(args);
            default -> printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("用法: java -Dagent=<agent> -jar code-review-system.jar");
        System.out.println("  -Dagent=supervisor  启动 SupervisorAgent（任务分发与结果汇总入口）");
        System.out.println("  -Dagent=style       启动 StyleAgent（代码风格审查）");
        System.out.println("  -Dagent=logic       启动 LogicAgent（逻辑缺陷审查）");
        System.out.println("  -Dagent=security    启动 SecurityAgent（安全漏洞审查）");
        System.out.println("  -Dagent=summary     启动 SummaryAgent（汇总生成审查报告）");
        System.out.println();
        System.out.println("必需参数: -DrocketMQEndpoint= -DrocketMQNamespace= -DrocketMQAK= -DrocketMQSK=");
    }
}
