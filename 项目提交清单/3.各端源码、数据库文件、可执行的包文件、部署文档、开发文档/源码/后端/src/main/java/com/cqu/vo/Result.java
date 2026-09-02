package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String errorMsg;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(200, null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, null, data);
    }

    public static <T> Result<T> fail(String errorMsg) {
        return new Result<>(500, errorMsg, null);
    }

    public static <T> Result<T> fail(Integer code, String errorMsg) {
        return new Result<>(code, errorMsg, null);
    }
}
