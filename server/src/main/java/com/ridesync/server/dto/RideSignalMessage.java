package com.ridesync.server.dto;

public record RideSignalMessage(String type, Long senderId, String signal) {}
