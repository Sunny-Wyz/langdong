package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class QuoteDTO {
    private Long supplierId;
    private BigDecimal quotePrice;
    private Integer deliveryDays;
}
