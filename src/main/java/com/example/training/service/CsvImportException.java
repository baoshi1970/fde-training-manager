package com.example.training.service;

import java.util.List;

/**
 * CSV 导入时的行级错误（F10）。
 * 所有行都检查完后，把全部错误一起抛出；有错误时一条学员也不登记。
 */
public class CsvImportException extends BusinessException {

    private final List<String> errors;

    /** @param errors 「第 N 行：…」形式的错误列表（按行号顺序） */
    public CsvImportException(List<String> errors) {
        super("CSV 中有 " + errors.size() + " 处错误，未登记任何学员。请修改后重新上传。");
        this.errors = List.copyOf(errors);
    }

    /** 全部错误（不可修改），给画面逐条列出用 */
    public List<String> getErrors() {
        return errors;
    }
}
