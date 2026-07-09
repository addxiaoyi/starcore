package dev.starcore.starcore.api.v1;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.List;

/**
 * API 错误响应
 * 统一所有错误响应的格式
 */
public record ApiError(
    @SerializedName("code")
    String code,

    @SerializedName("message")
    String message,

    @SerializedName("details")
    List<String> details,

    @SerializedName("timestamp")
    Instant timestamp
) {
    /**
     * 常用错误码
     */
    public static class Codes {
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        public static final String FORBIDDEN = "FORBIDDEN";
        public static final String NOT_FOUND = "NOT_FOUND";
        public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
        public static final String CONFLICT = "CONFLICT";
        public static final String RATE_LIMITED = "RATE_LIMITED";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        public static final String BAD_REQUEST = "BAD_REQUEST";
        public static final String INVALID_API_KEY = "INVALID_API_KEY";
        public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
        public static final String ACCESS_EXPIRED = "ACCESS_EXPIRED";
    }

    /**
     * 创建验证错误
     */
    public static ApiError validation(String message, List<String> details) {
        return new ApiError(Codes.VALIDATION_ERROR, message, details, Instant.now());
    }

    /**
     * 创建未授权错误
     */
    public static ApiError unauthorized() {
        return new ApiError(Codes.UNAUTHORIZED, "Authentication required", null, Instant.now());
    }

    /**
     * 创建禁止访问错误
     */
    public static ApiError forbidden() {
        return new ApiError(Codes.FORBIDDEN, "Access denied", null, Instant.now());
    }

    /**
     * 创建未找到错误
     */
    public static ApiError notFound(String resource) {
        return new ApiError(Codes.NOT_FOUND, resource + " not found", null, Instant.now());
    }

    /**
     * 创建内部错误
     */
    public static ApiError internal(String message) {
        return new ApiError(Codes.INTERNAL_ERROR, message, null, Instant.now());
    }

    /**
     * 创建服务不可用错误
     */
    public static ApiError unavailable(String reason) {
        return new ApiError(Codes.SERVICE_UNAVAILABLE, reason, null, Instant.now());
    }

    /**
     * 创建无效 API Key 错误
     */
    public static ApiError invalidApiKey() {
        return new ApiError(Codes.INVALID_API_KEY, "Invalid or expired API key", null, Instant.now());
    }

    /**
     * 创建无效签名错误
     */
    public static ApiError invalidSignature() {
        return new ApiError(Codes.INVALID_SIGNATURE, "Invalid request signature", null, Instant.now());
    }

    /**
     * 创建速率限制错误
     */
    public static ApiError rateLimited() {
        return new ApiError(Codes.RATE_LIMITED, "Too many requests, please try again later", null, Instant.now());
    }
}
