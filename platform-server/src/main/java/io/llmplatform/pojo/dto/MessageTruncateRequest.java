package io.llmplatform.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;

/** 从指定序号起截断会话消息，用于撤回或修改后重新发送。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageTruncateRequest(@Min(1) int sequenceNo) {}
