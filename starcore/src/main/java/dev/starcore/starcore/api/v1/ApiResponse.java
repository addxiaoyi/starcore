package dev.starcore.starcore.api.v1;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 统一 API 响应格式
 * 所有 REST API 端点都应返回此格式的响应
 */
public record ApiResponse<T>(
    @SerializedName("ok")
    boolean success,

    @SerializedName("data")
    T data,

    @SerializedName("meta")
    ResponseMeta meta,

    @SerializedName("error")
    ApiError error
) {
    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * 创建带元数据的成功响应
     */
    public static <T> ApiResponse<T> success(T data, ResponseMeta meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    /**
     * 创建成功响应（无数据）
     */
    @SuppressWarnings("unchecked")
    private static <T> ApiResponse<T> successInternal() {
        return (ApiResponse<T>) new ApiResponse<>(true, null, null, null);
    }

    /**
     * 创建错误响应
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message, null, Instant.now()));
    }

    /**
     * 创建错误响应（带详情）
     */
    public static <T> ApiResponse<T> error(String code, String message, List<String> details) {
        return new ApiResponse<>(false, null, null, new ApiError(code, message, details, Instant.now()));
    }

    /**
     * 创建验证错误响应
     */
    public static <T> ApiResponse<T> validationError(List<String> errors) {
        return new ApiResponse<>(false, null, null, new ApiError("VALIDATION_ERROR", "Request validation failed", errors, Instant.now()));
    }

    /**
     * 创建未授权错误响应
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(false, null, null, new ApiError("UNAUTHORIZED", message, null, Instant.now()));
    }

    /**
     * 创建禁止访问错误响应
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(false, null, null, new ApiError("FORBIDDEN", message, null, Instant.now()));
    }

    /**
     * 创建未找到错误响应
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(false, null, null, new ApiError("NOT_FOUND", message, null, Instant.now()));
    }

    /**
     * 创建内部错误响应
     */
    public static <T> ApiResponse<T> internalError(String message) {
        return new ApiResponse<>(false, null, null, new ApiError("INTERNAL_ERROR", message, null, Instant.now()));
    }

    /**
     * 响应元数据
     */
    public record ResponseMeta(
        @SerializedName("timestamp")
        long timestamp,

        @SerializedName("page")
        Optional<Integer> page,

        @SerializedName("pageSize")
        Optional<Integer> pageSize,

        @SerializedName("total")
        Optional<Long> total,

        @SerializedName("requestId")
        Optional<String> requestId
    ) {
        public static ResponseMeta of() {
            return new ResponseMeta(System.currentTimeMillis(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static ResponseMeta of(int page, int pageSize, long total) {
            return new ResponseMeta(System.currentTimeMillis(), Optional.of(page), Optional.of(pageSize), Optional.of(total), Optional.empty());
        }

        public static ResponseMeta of(int page, int pageSize, long total, String requestId) {
            return new ResponseMeta(System.currentTimeMillis(), Optional.of(page), Optional.of(pageSize), Optional.of(total), Optional.of(requestId));
        }

        public static ResponseMeta of(String requestId) {
            return new ResponseMeta(System.currentTimeMillis(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(requestId));
        }
    }
}
