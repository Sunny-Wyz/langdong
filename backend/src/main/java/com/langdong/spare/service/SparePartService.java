package com.langdong.spare.service;

import com.alibaba.excel.EasyExcel;
import com.langdong.spare.dto.SparePartImportDTO;
import com.langdong.spare.entity.Location;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.entity.SparePartCategory;
import com.langdong.spare.entity.Supplier;
import com.langdong.spare.mapper.LocationMapper;
import com.langdong.spare.mapper.SparePartCategoryMapper;
import com.langdong.spare.mapper.SparePartMapper;
import com.langdong.spare.mapper.SupplierMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SparePartService {

    @Autowired
    private SparePartMapper sparePartMapper;

    @Autowired
    private SparePartCategoryMapper categoryMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private LocationMapper locationMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importSpareParts(MultipartFile file) throws IOException {
        // 同步读取所有数据
        List<SparePartImportDTO> list = EasyExcel.read(file.getInputStream()).head(SparePartImportDTO.class).sheet().doReadSync();

        // 预加载基础数据进行名称匹配
        List<SparePartCategory> categories = categoryMapper.findAll();
        Map<String, SparePartCategory> categoryMap = categories.stream()
                .filter(c -> c.getName() != null)
                .collect(Collectors.toMap(SparePartCategory::getName, c -> c, (v1, v2) -> v1)); // 名称可能重复，只取第一个

        List<Supplier> suppliers = supplierMapper.findAll();
        Map<String, Long> supplierMap = suppliers.stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(Supplier::getName, Supplier::getId, (v1, v2) -> v1));

        List<Location> locations = locationMapper.findAll();
        Map<String, Long> locationMap = locations.stream()
                .filter(l -> l.getName() != null)
                .collect(Collectors.toMap(Location::getName, Location::getId, (v1, v2) -> v1));

        // 维护分类的当前最大序列号，避免同一批次生成相同的编码
        Map<String, Integer> categoryNextSeqMap = new HashMap<>();

        int successCount = 0;
        int failCount = 0;
        StringBuilder failMsgs = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            SparePartImportDTO dto = list.get(i);
            int rowNum = i + 2; // Excel行号从2开始(含表头)

            if (dto.getName() == null || dto.getName().isBlank()) {
                failCount++;
                failMsgs.append("第").append(rowNum).append("行：备件名称为空；");
                continue;
            }

            if (dto.getCategoryName() == null || !categoryMap.containsKey(dto.getCategoryName())) {
                failCount++;
                failMsgs.append("第").append(rowNum).append("行：分类名称不存在；");
                continue;
            }

            SparePartCategory categoryInfo = categoryMap.get(dto.getCategoryName());
            String prefix = categoryInfo.getCode();

            // 确定下一个编号
            int nextNum = 1;
            if (categoryNextSeqMap.containsKey(prefix)) {
                nextNum = categoryNextSeqMap.get(prefix) + 1;
            } else {
                String maxCode = sparePartMapper.findMaxCodeByPrefix(prefix);
                if (maxCode != null && maxCode.length() == 8) {
                    try {
                        nextNum = Integer.parseInt(maxCode.substring(4)) + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
            categoryNextSeqMap.put(prefix, nextNum);
            String generatedCode = prefix + String.format("%04d", nextNum);

            SparePart sparePart = new SparePart();
            BeanUtils.copyProperties(dto, sparePart);
            sparePart.setCode(generatedCode);
            sparePart.setCategoryId(categoryInfo.getId());

            if (dto.getSupplierName() != null && supplierMap.containsKey(dto.getSupplierName())) {
                sparePart.setSupplierId(supplierMap.get(dto.getSupplierName()));
            }

            if (dto.getLocationName() != null && locationMap.containsKey(dto.getLocationName())) {
                sparePart.setLocationId(locationMap.get(dto.getLocationName()));
            }

            if (sparePart.getQuantity() == null) {
                sparePart.setQuantity(0);
            }

            sparePartMapper.insert(sparePart);
            successCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failMsgs", failMsgs.toString());
        return result;
    }
}
