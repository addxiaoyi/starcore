package dev.starcore.starcore.api.v1;

import com.google.gson.annotations.SerializedName;

/**
 * 分页请求参数
 */
public record PageRequest(
    @SerializedName("page")
    int page,

    @SerializedName("pageSize")
    int pageSize
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageRequest {
        if (page < 1) page = DEFAULT_PAGE;
        if (pageSize < 1) pageSize = DEFAULT_PAGE_SIZE;
        if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
    }

    public static PageRequest of(int page, int pageSize) {
        return new PageRequest(page, pageSize);
    }

    public static PageRequest of(String pageStr, String pageSizeStr) {
        return new PageRequest(
            ApiValidator.parseInt(pageStr, DEFAULT_PAGE),
            ApiValidator.parseInt(pageSizeStr, DEFAULT_PAGE_SIZE)
        );
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
