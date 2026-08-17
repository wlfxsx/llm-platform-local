package io.llmplatform.pojo.vo;

/** 可用于推理的设备。 */
public record HardwareDevice(
        String id,
        String type,
        String name,
        boolean available,
        boolean recommended,
        String unavailableReason) {}
