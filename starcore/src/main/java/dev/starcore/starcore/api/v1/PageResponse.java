package dev.starcore.starcore.api.v1;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 分页响应包装
 */
public record PageResponse<T>(
    @SerializedName("items")
    List<T> items,

    @SerializedName("page")
    int page,

    @SerializedName("pageSize")
    int pageSize,

    @SerializedName("total")
    long total,

    @SerializedName("totalPages")
    int totalPages,

    @SerializedName("hasNext")
    boolean hasNext,

    @SerializedName("hasPrevious")
    boolean hasPrevious
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(
            items,
            page,
            pageSize,
            total,
            totalPages,
            page < totalPages,
            page > 1
        );
    }

    public static <T> PageResponse<T> empty(int page, int pageSize) {
        return of(List.of(), page, pageSize, 0);
    }
}
