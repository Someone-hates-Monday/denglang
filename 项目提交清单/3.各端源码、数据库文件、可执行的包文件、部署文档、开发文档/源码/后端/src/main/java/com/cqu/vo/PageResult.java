package com.cqu.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private String total;

    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(Long total, List<T> records) {
        return new PageResult<>(String.valueOf(total), records);
    }
}
