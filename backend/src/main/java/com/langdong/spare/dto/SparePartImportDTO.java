package com.langdong.spare.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 备件 Excel 导入 DTO
 */
@Data
public class SparePartImportDTO {

    @ExcelProperty("备件名称")
    private String name;

    @ExcelProperty("型号规格")
    private String model;

    @ExcelProperty("库存数量")
    private Integer quantity;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("单价（元）")
    private BigDecimal price;

    @ExcelProperty("分类名称")
    private String categoryName;

    @ExcelProperty("供应商名称")
    private String supplierName;

    @ExcelProperty("货位名称")
    private String locationName;

    @ExcelProperty("备注")
    private String remark;
}
