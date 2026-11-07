package com.ridesync.server.dto;

public record WebSocketErrorMessage(String type, String code, String message) {
}
