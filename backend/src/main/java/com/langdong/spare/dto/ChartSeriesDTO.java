package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

/**
 * ECharts 通用图表数据结构
 * xAxis: ['1月','2月',...]
 * series: [{ name:'系列A', data:[10,20,...] }, ...]
 */
@Data
public class ChartSeriesDTO {
    private List<String> xAxis;
    private List<SeriesItem> series;

    @Data
    public static class SeriesItem {
        private String name;
        private List<Object> data;
    }
}
