package com.langdong.spare.mapper;

import com.langdong.spare.dto.WarningItemDTO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface WarningMapper {
    List<WarningItemDTO> getLowStockWarnings();

    List<WarningItemDTO> getOverdueWorkOrders();

    List<WarningItemDTO> getOverduePurchaseOrders();
}
