package io.llmplatform.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 启动 llamafile 时指定的参数策略；为空表示沿用当前模型已保存的配置。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlamafileStartRequest(String profileId) {}
