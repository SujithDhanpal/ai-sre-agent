package com.sre.agent.plugin.api.model;

public record QueueHealth(
        String queueName,
        long approximateMessageCount,
        long approximateAgeOfOldestMessageSeconds,
        long deadLetterQueueCount,
        long inflightMessageCount
) {}
