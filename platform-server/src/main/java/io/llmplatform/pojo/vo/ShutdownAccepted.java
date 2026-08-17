package io.llmplatform.pojo.vo;

/** 关机请求已被接受，实际退出在后台线程完成。 */
public record ShutdownAccepted(boolean accepted, Long llamafilePid) {}
