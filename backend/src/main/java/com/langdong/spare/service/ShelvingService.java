package com.langdong.spare.service;

import com.langdong.spare.dto.ShelvingSubmitDTO;
import com.langdong.spare.entity.Location;
import com.langdong.spare.entity.SparePartLocationStock;
import com.langdong.spare.entity.StockInItem;
import com.langdong.spare.mapper.LocationMapper;
import com.langdong.spare.mapper.SparePartLocationStockMapper;
import com.langdong.spare.mapper.StockInItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShelvingService {

    @Autowired
    private StockInItemMapper stockInItemMapper;
    @Autowired
    private LocationMapper locationMapper;
    @Autowired
    private SparePartLocationStockMapper sparePartLocationStockMapper;

    public List<StockInItem> getPendingShelvingItems() {
        return stockInItemMapper.findPendingShelving();
    }

    @Transactional
    public void submitShelving(List<ShelvingSubmitDTO> requests) {
        for (ShelvingSubmitDTO req : requests) {
            // 获取入库明细记录，以获取可上架的剩余额度和备件ID
            // 注意：这里需要单独的 mapper 方法通过ID查 StockInItem。如果尚未创建则需要增加一个。
            StockInItem item = getStockInItemById(req.getStockInItemId());
            if (item == null) {
                throw new RuntimeException("入库明细不存在，ID=" + req.getStockInItemId());
            }

            int totalPutQty = 0;
            for (ShelvingSubmitDTO.ShelvingDistribution dist : req.getDistributions()) {
                if (dist.getPutQty() == null || dist.getPutQty() <= 0)
                    continue;
                totalPutQty += dist.getPutQty();

                // 1. 校验目标货位容量
                Location loc = locationMapper.findById(dist.getLocationId());
                if (loc == null) {
                    throw new RuntimeException("货位不存在，ID=" + dist.getLocationId());
                }
                if (loc.getCapacity() != null && !loc.getCapacity().trim().isEmpty()) {
                    try {
                        int maxCapacity = Integer.parseInt(loc.getCapacity());
                        Integer load = sparePartLocationStockMapper.sumQuantityByLocationId(dist.getLocationId());
                        int currentLoad = load == null ? 0 : load;
                        if (currentLoad + dist.getPutQty() > maxCapacity) {
                            throw new RuntimeException(String.format("货位 %s 容量不足！（当前量:%d, 拟放入:%d, 最大容量:%d）",
                                    loc.getName(), currentLoad, dist.getPutQty(), maxCapacity));
                        }
                    } catch (NumberFormatException e) {
                        // ignore non-integer capacity or handle properly
                    }
                }

                // 2. 更新或插入该货位针对该备件的局部台账
                SparePartLocationStock locStock = sparePartLocationStockMapper
                        .findByLocationAndSparePart(dist.getLocationId(), item.getSparePartId());
                if (locStock == null) {
                    locStock = new SparePartLocationStock();
                    locStock.setLocationId(dist.getLocationId());
                    locStock.setSparePartId(item.getSparePartId());
                    locStock.setQuantity(dist.getPutQty());
                    sparePartLocationStockMapper.insert(locStock);
                } else {
                    sparePartLocationStockMapper.addQuantity(dist.getLocationId(), item.getSparePartId(),
                            dist.getPutQty());
                }
            }

            // 3. 校验并累加原入库明细的已上架数量
            int remaining = item.getActualQuantity()
                    - (item.getShelvedQuantity() == null ? 0 : item.getShelvedQuantity());
            if (totalPutQty > remaining) {
                throw new RuntimeException("提交的上架总数（" + totalPutQty + "）超过该批次待上架剩余量（" + remaining + "）");
            }

            if (totalPutQty > 0) {
                stockInItemMapper.updateShelvedQuantity(item.getId(), totalPutQty);
            }
        }
    }

    private StockInItem getStockInItemById(Long id) {
        // Find inside pending list (shortcut) or require a real findById mapping
        List<StockInItem> list = stockInItemMapper.findPendingShelving();
        return list.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }
}
