package com.langdong.spare;

import com.alibaba.excel.EasyExcel;
import com.langdong.spare.dto.SparePartImportDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestExcelGen {
    @Test
    public void testGen() {
        List<SparePartImportDTO> list = new ArrayList<>();
        SparePartImportDTO dto1 = new SparePartImportDTO();
        dto1.setName("测试导入轴承");
        dto1.setModel("TEST-6205");
        dto1.setQuantity(100);
        dto1.setUnit("个");
        dto1.setPrice(new BigDecimal("45.50"));
        dto1.setCategoryName("轴承类");
        dto1.setSupplierName("日本NSK精工");
        dto1.setLocationName("C区轴承密封件架");
        dto1.setRemark("批量导入测试1");
        list.add(dto1);

        SparePartImportDTO dto2 = new SparePartImportDTO();
        dto2.setName("测试导入传感器");
        dto2.setModel("TEST-PZ");
        dto2.setQuantity(50);
        dto2.setUnit("个");
        dto2.setPrice(new BigDecimal("280.00"));
        dto2.setCategoryName("传感器类");
        dto2.setSupplierName("欧姆龙自动化");
        // A区机电库1架 在已有数据中可能不完全匹配，测试空货位
        dto2.setRemark("批量导入测试2");
        list.add(dto2);

        String filePath = "/Users/weiyaozhou/Documents/langdong/test_spare_parts.xlsx";
        EasyExcel.write(filePath, SparePartImportDTO.class).sheet("备件数据").doWrite(list);
        System.out.println("Generated at: " + filePath);
    }
}
