package io.llmplatform.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 平台路径与 llamafile 启动参数。 */
@ConfigurationProperties(prefix = "llm")
public class PlatformProperties {

    private final Platform platform = new Platform();
    private final Llamafile llamafile = new Llamafile();

    public Platform getPlatform() {
        return platform;
    }

    public Llamafile getLlamafile() {
        return llamafile;
    }

    /** 平台控制面和用户数据目录配置，默认仅使用本机范围。 */
    public static class Platform {
        private String dataDir =
                Path.of(System.getProperty("user.home"), ".llm-platform").toString();
        private String bindHost = "127.0.0.1";

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }

        public String getBindHost() {
            return bindHost;
        }

        public void setBindHost(String bindHost) {
            this.bindHost = bindHost;
        }
    }

    /** llamafile 安装级默认值。模型相关运行参数已迁到 model_configs，此处旧字段只保留配置兼容。 */
    public static class Llamafile {
        private String binaryPath = "";
        private String host = "127.0.0.1";
        private int port = 17891;
        private int contextSize = 4096;
        private int threads = 4;
        private int gpuLayers = 0;

        public String getBinaryPath() {
            return binaryPath;
        }

        public void setBinaryPath(String binaryPath) {
            this.binaryPath = binaryPath;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getContextSize() {
            return contextSize;
        }

        public void setContextSize(int contextSize) {
            this.contextSize = contextSize;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getGpuLayers() {
            return gpuLayers;
        }

        public void setGpuLayers(int gpuLayers) {
            this.gpuLayers = gpuLayers;
        }
    }
}
