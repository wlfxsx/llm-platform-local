package io.llmplatform.service;

import io.llmplatform.infra.llamafile.LlamafileManager;
import io.llmplatform.pojo.vo.ShutdownAccepted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/** 先停模型再退出控制面，给桌面一层优雅关机入口。 */
@Service
public class ShutdownService {

    private static final Logger log = LoggerFactory.getLogger(ShutdownService.class);
    private final LlamafileManager llamafileManager;
    private final ConfigurableApplicationContext context;

    public ShutdownService(
            LlamafileManager llamafileManager, ConfigurableApplicationContext context) {
        this.llamafileManager = llamafileManager;
        this.context = context;
    }

    public ShutdownAccepted shutdown() {
        Long pid = llamafileManager.pid();
        llamafileManager.stop();
        log.info("已接受关机，准备退出控制面，llamafilePid={}", pid);
        Thread exit =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(250);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            SpringApplication.exit(context, () -> 0);
                        },
                        "platform-shutdown");
        exit.setDaemon(true);
        exit.start();
        return new ShutdownAccepted(true, pid);
    }
}
