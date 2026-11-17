package com.example.training.service;

import java.util.List;

/**
 * CSV 的一条记录。
 *
 * @param lineNo 这条记录在文件里开始的行号（表头是第 1 行）。引号内有换行时，一条记录会跨多行，取开始行
 * @param fields 字段列表（原样保存，不做 trim）
 */
public record CsvRecord(int lineNo, List<String> fields) {

    public CsvRecord {
        fields = List.copyOf(fields);
    }
}
