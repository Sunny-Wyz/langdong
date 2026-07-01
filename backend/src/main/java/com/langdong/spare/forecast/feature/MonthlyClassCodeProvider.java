package com.langdong.spare.forecast.feature;

/**
 * 按「截止月份」提供某备件的 ABC_code / XYZ_code。
 *
 * <p>特征 #8/#9 需取「目标月前一月」的分类结果。分类需横向比较全量备件（帕累托），
 * 无法在单备件上下文内计算，故由批量分类器（{@code AbcXyzClassifier}）预先算好、
 * 通过本接口按月注入 FeatureBuilder，实现关注点分离与防泄露。</p>
 */
@FunctionalInterface
public interface MonthlyClassCodeProvider {

    /**
     * 返回该备件在 {@code asOfMonth}（含）为止数据所对应的分类编码。
     *
     * @param partCode  备件编码
     * @param asOfMonth 截止月份（yyyy-MM），通常为目标月的前一月
     * @return 长度 2 的数组 {ABC_code, XYZ_code}；无分类时约定返回保守默认 {1, 3}（C/Z）
     */
    int[] codesAsOf(String partCode, String asOfMonth);
}
