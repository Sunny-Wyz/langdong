import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import matplotlib

# 设置中文字体支持
matplotlib.rcParams['font.sans-serif'] = ['Heiti TC', 'STHeiti', 'PingFang SC', 'Arial Unicode MS', 'SimHei']
matplotlib.rcParams['axes.unicode_minus'] = False

def analyze_jtl(file_path):
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} does not exist.")
        return

    # 读取 JTL 文件
    print(f"Reading {file_path}...")
    df = pd.read_csv(file_path)

    # 过滤掉 setup 登录接口
    df_filtered = df[df['label'] != '登录接口'].copy()
    
    # 实际测试时长 (单位：秒)
    min_ts = df_filtered['timeStamp'].min()
    max_ts = df_filtered['timeStamp'].max()
    test_duration = (max_ts - min_ts) / 1000.0
    print(f"Test duration: {test_duration:.2f} seconds")

    # 按接口和整体进行统计
    results = []
    labels = df_filtered['label'].unique()
    
    # 我们定义排序，保证输出顺序美观
    sorted_labels = sorted(labels)
    
    for label in sorted_labels:
        df_label = df_filtered[df_filtered['label'] == label]
        stats = calculate_stats(df_label, label, test_duration)
        results.append(stats)
        
    # 计算整体(合计)
    total_stats = calculate_stats(df_filtered, '合计', test_duration)
    results.append(total_stats)

    # 转化为 DataFrame
    df_stats = pd.DataFrame(results)
    
    # 格式化表格输出
    df_stats_formatted = df_stats.copy()
    df_stats_formatted['平均(ms)'] = df_stats_formatted['平均(ms)'].round(2)
    df_stats_formatted['中位(ms)'] = df_stats_formatted['中位(ms)'].round(2)
    df_stats_formatted['最大(ms)'] = df_stats_formatted['最大(ms)'].round(2)
    df_stats_formatted['P90'] = df_stats_formatted['P90'].round(2)
    df_stats_formatted['P95'] = df_stats_formatted['P95'].round(2)
    df_stats_formatted['P99'] = df_stats_formatted['P99'].round(2)
    df_stats_formatted['错误率'] = (df_stats_formatted['错误率'] * 100).map('{:.2f}%'.format)
    df_stats_formatted['吞吐量(次/s)'] = df_stats_formatted['吞吐量(次/s)'].round(2)

    # 生成 markdown 表格
    markdown_table = df_stats_formatted.to_markdown(index=False)
    with open('table_4_5.md', 'w', encoding='utf-8') as f:
        f.write("# 表 4-5 备件管理系统性能测试复测结果\n\n")
        f.write(markdown_table)
        f.write("\n")
    print("Generated table_4_5.md.")

    # 打印论文正文引用指标
    total_count = len(df_filtered)
    pct_10 = (df_filtered['elapsed'] <= 10).sum() / total_count * 100
    pct_15 = (df_filtered['elapsed'] <= 15).sum() / total_count * 100
    pct_30 = (df_filtered['elapsed'] <= 30).sum() / total_count * 100
    
    print("\n=== 论文引用指标 ===")
    print(f"10ms 内完成请求占比: {pct_10:.2f}%")
    print(f"15ms 内占比: {pct_15:.2f}%")
    print(f"30ms 内占比: {pct_30:.2f}%")
    print("====================\n")

    # 生成图表
    generate_charts(df_filtered, df_stats, test_duration)

def calculate_stats(df_sub, label, duration):
    count = len(df_sub)
    avg = df_sub['elapsed'].mean()
    median = df_sub['elapsed'].median()
    maximum = df_sub['elapsed'].max()
    p90 = df_sub['elapsed'].quantile(0.90)
    p95 = df_sub['elapsed'].quantile(0.95)
    p99 = df_sub['elapsed'].quantile(0.99)
    err_rate = (df_sub['success'] == False).sum() / count if count > 0 else 0
    tps = count / duration if duration > 0 else 0

    return {
        '接口': label,
        '样本数': count,
        '平均(ms)': avg,
        '中位(ms)': median,
        '最大(ms)': maximum,
        'P90': p90,
        'P95': p95,
        'P99': p99,
        '错误率': err_rate,
        '吞吐量(次/s)': tps
    }

def generate_charts(df, df_stats, duration):
    # 过滤掉'合计'
    df_interfaces = df_stats[df_stats['接口'] != '合计']
    interfaces = df_interfaces['接口'].tolist()
    
    # ----------------------------------------------------
    # 图 4-25：三接口响应时间统计分组柱状图
    # ----------------------------------------------------
    plt.figure(figsize=(10, 6), facecolor='white')
    metrics = ['平均(ms)', '中位(ms)', 'P90', 'P95', 'P99', '最大(ms)']
    metric_labels = ['平均值', '中位数', 'P90', 'P95', 'P99', '最大值']
    
    x = np.arange(len(metrics))
    width = 0.25
    
    fig, ax = plt.subplots(figsize=(10, 6), facecolor='white')
    for i, idx in enumerate(df_interfaces.index):
        label_name = df_interfaces.loc[idx, '接口']
        values = [df_interfaces.loc[idx, m] for m in metrics]
        ax.bar(x + (i - 1) * width, values, width, label=label_name, edgecolor='black', alpha=0.8)
        
    ax.set_ylabel('响应时间 (ms)', fontsize=12)
    ax.set_xticks(x)
    ax.set_xticklabels(metric_labels, fontsize=11)
    ax.legend(fontsize=10)
    ax.grid(axis='y', linestyle='--', alpha=0.5)
    plt.tight_layout()
    plt.savefig('图_4-25.png', dpi=200)
    plt.savefig('图_4-25.svg')
    plt.close()
    print("Generated 图_4-25.png and 图_4-25.svg")

    # ----------------------------------------------------
    # 图 4-26：吞吐量-时间曲线 (5秒滑动平均，叠加整体平均值虚线)
    # ----------------------------------------------------
    # 计算每个请求相对于起始时间的时间偏移 (秒)
    start_ts = df['timeStamp'].min()
    df['time_offset'] = (df['timeStamp'] - start_ts) // 1000
    
    # 按秒进行聚合计数
    tps_series = df.groupby('time_offset').size()
    max_offset = int(df['time_offset'].max())
    tps_series = tps_series.reindex(range(0, max_offset + 1), fill_value=0)
    
    # 5秒滑动平均
    rolling_tps = tps_series.rolling(window=5, min_periods=1).mean()
    overall_avg_tps = len(df) / duration
    
    plt.figure(figsize=(10, 5), facecolor='white')
    plt.plot(rolling_tps.index, rolling_tps.values, label='吞吐量 (5秒滑动平均)', color='#1f77b4', linewidth=1.8)
    plt.axhline(y=overall_avg_tps, color='r', linestyle='--', label=f'整体平均吞吐量 ({overall_avg_tps:.2f} 次/s)', linewidth=1.5)
    plt.xlabel('测试时间 (秒)', fontsize=12)
    plt.ylabel('吞吐量 (次/s)', fontsize=12)
    plt.legend(fontsize=10)
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.xlim(0, max_offset)
    plt.tight_layout()
    plt.savefig('图_4-26.png', dpi=200)
    plt.savefig('图_4-26.svg')
    plt.close()
    print("Generated 图_4-26.png and 图_4-26.svg")

    # ----------------------------------------------------
    # 图 4-27：各接口平均响应时间随时间变化折线 + 整体 P99 虚线
    # ----------------------------------------------------
    # 5秒时间窗口聚合
    df['time_bucket_5s'] = (df['timeStamp'] - start_ts) // 5000 * 5
    
    plt.figure(figsize=(10, 5), facecolor='white')
    
    colors = ['#1f77b4', '#ff7f0e', '#2ca02c']
    for idx, (label_name, sub_df) in enumerate(df.groupby('label')):
        bucketed = sub_df.groupby('time_bucket_5s')['elapsed'].mean()
        # 重采样补齐时间轴
        bucketed = bucketed.reindex(range(0, max_offset + 1, 5), fill_value=np.nan).interpolate()
        plt.plot(bucketed.index, bucketed.values, label=label_name, color=colors[idx % len(colors)], linewidth=1.5)
        
    overall_p99 = df['elapsed'].quantile(0.99)
    plt.axhline(y=overall_p99, color='red', linestyle=':', label=f'整体 P99 响应时间 ({overall_p99:.1f} ms)', linewidth=1.5)
    
    plt.xlabel('测试时间 (秒)', fontsize=12)
    plt.ylabel('平均响应时间 (ms)', fontsize=12)
    plt.legend(fontsize=10)
    plt.grid(True, linestyle='--', alpha=0.5)
    plt.xlim(0, max_offset)
    plt.tight_layout()
    plt.savefig('图_4-27.png', dpi=200)
    plt.savefig('图_4-27.svg')
    plt.close()
    print("Generated 图_4-27.png and 图_4-27.svg")

    # ----------------------------------------------------
    # 图 4-28：响应时间直方图 + 累计百分比曲线 (双 Y 轴)
    # ----------------------------------------------------
    elapsed_sorted = np.sort(df['elapsed'])
    y_cdf = np.arange(1, len(elapsed_sorted) + 1) / len(elapsed_sorted) * 100
    
    # 过滤掉长尾高时延，保证直方图可读性 (例如只画到 P99.9)
    p999 = df['elapsed'].quantile(0.999)
    df_hist_data = df[df['elapsed'] <= p999]
    
    fig, ax1 = plt.subplots(figsize=(10, 6), facecolor='white')
    
    # 绘制直方图 (左轴)
    counts, bins, patches = ax1.hist(df_hist_data['elapsed'], bins=40, color='#aec7e8', edgecolor='black', alpha=0.7, label='频数直方图')
    ax1.set_xlabel('响应时间 (ms)', fontsize=12)
    ax1.set_ylabel('频数 (次)', fontsize=12)
    ax1.grid(True, linestyle='--', alpha=0.5)
    
    # 绘制累计分布曲线 (右轴)
    ax2 = ax1.twinx()
    ax2.plot(elapsed_sorted, y_cdf, color='#d62728', linewidth=2, label='累计百分比')
    ax2.set_ylabel('累计百分比 (%)', fontsize=12)
    ax2.set_ylim(0, 105)
    
    # 标出 P50, P90, P95, P99
    p50 = df['elapsed'].quantile(0.50)
    p90 = df['elapsed'].quantile(0.90)
    p95 = df['elapsed'].quantile(0.95)
    p99 = df['elapsed'].quantile(0.99)
    
    percentiles = [50, 90, 95, 99]
    p_values = [p50, p90, p95, p99]
    
    # 在右侧CDF上画出参考点
    for pct, val in zip(percentiles, p_values):
        ax2.axvline(x=val, color='gray', linestyle=':', alpha=0.7)
        # 在直方图里标出文字
        ax1.text(val + 0.5, counts.max() * (1 - pct/100 * 0.8 + 0.1), f'P{pct}={val:.1f}ms', 
                 fontsize=10, fontweight='bold', color='darkred',
                 bbox=dict(facecolor='white', alpha=0.8, boxstyle='round,pad=0.2'))

    # 合并图例
    lines, labels = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines + lines2, labels + labels2, loc='center right', fontsize=10)
    
    plt.xlim(0, max(p99 * 1.5, 10)) # 合理范围
    plt.tight_layout()
    plt.savefig('图_4-28.png', dpi=200)
    plt.savefig('图_4-28.svg')
    plt.close()
    print("Generated 图_4-28.png and 图_4-28.svg")

if __name__ == '__main__':
    analyze_jtl('result.jtl')
