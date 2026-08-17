package io.llmplatform.pojo.entity;

/** 已安装插件。 */
public record PluginRecord(
        String id, String name, String version, boolean enabled, String directory, String status) {}
