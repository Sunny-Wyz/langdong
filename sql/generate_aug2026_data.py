#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
大型酒企配套厂 2026年8月整月模拟运营数据生成脚本
生成 SQL 文件，通过 mysql 导入到 spare_db 数据库
"""

import random
from datetime import datetime, timedelta

random.seed(42)  # 可复现

# =====================================================================
# 常量与配置
# =====================================================================

AUG_START = datetime(2026, 8, 1, 0, 0, 0)
AUG_END   = datetime(2026, 8, 31, 23, 59, 59)

# 8月工作日列表（周一至周五，排除周末）
WORK_DAYS = []
d = AUG_START
while d <= AUG_END:
    if d.weekday() < 5:  # 0=Mon ... 4=Fri
        WORK_DAYS.append(d)
    d += timedelta(days=1)

# 用户ID映射
ENGINEERS = [17, 21]       # 设备工程师
WAREHOUSE = [16, 20]       # 仓库管理员
BUYER     = [18]           # 采购员
APPROVERS = [1, 19]        # 审批人（管理员 + 管理层）

# =====================================================================
# 前置操作一：设备替换
# =====================================================================

EQUIPMENT_UPDATES = [
    (1,  'EQ-1000', '新美星旋转灌装机',       'XGF-24-24-8',    '包装车间'),
    (2,  'EQ-1001', '永创智能自动旋盖机',     'YXG-12',         '包装车间'),
    (3,  'EQ-1002', '达意隆套标收缩机',       'TBJ-200',        '包装车间'),
    (4,  'EQ-1003', '兄弟牌全自动贴标机',     'BLM-8100',       '包装车间'),
    (5,  'EQ-1004', '阿特拉斯无油空压机',     'ZR75-VSD',       '公用动力站'),
    (6,  'EQ-1005', '西门子PLC控制柜',        'S7-1500',        '包装车间'),
    (7,  'EQ-1006', '基恩士视觉检测系统',     'CV-X480F',       '包装车间'),
    (8,  'EQ-1007', '方快燃气蒸汽锅炉',       'WNS4-1.25-Q',    '公用动力站'),
    (9,  'EQ-1008', '开山螺杆空压机',         'LG-13/8G',       '公用动力站'),
    (10, 'EQ-1009', '阿法拉伐CIP清洗系统',    'Hybrid-M',       '酿造车间'),
    (11, 'EQ-1010', '广州富特自动甑桶蒸馏机', 'FT-ZL3000',      '酿造车间'),
    (12, 'EQ-1011', '南京轻机自动装箱码垛线', 'ZXM-600',        '包装车间'),
    (13, 'EQ-1012', '康明斯静音柴油发电机',   'C150D5',         '公用动力站'),
    (14, 'EQ-1013', '君科酒体制冷机组',       'JK-30WS',        '酿造车间'),
    (15, 'EQ-1014', '大族激光喷码机',         'EP-S200',        '包装车间'),
]

# =====================================================================
# 前置操作二：备件替换
# =====================================================================

SPARE_PART_UPDATES = [
    # (id, name, model, price)
    (1,  'SKF深沟球轴承',           '6205-2RS1',          65.00),
    (2,  'ABB变频器',               'ACS580-01-09A5',     3200.00),
    (3,  '欧姆龙光电传感器',        'E3Z-D62',            165.00),
    (4,  'SMC薄型气缸',             'CDQ2B32-50D',        165.00),
    (5,  '欧姆龙接近开关',          'E2E-X5ME1',          75.00),
    (6,  '施耐德微型断路器',        'iC65N C16A',         42.00),
    (7,  '西门子伺服电机',          '1FL6042-1AF61',      4500.00),
    (8,  '食品级硅胶软管',          'Φ25×Φ33mm',         55.00),
    (9,  'THK直线导轨滑块',         'HSR15R',             135.00),
    (10, '正泰交流接触器',          'CJX2-1810',          65.00),
    (11, '西门子PLC数字量模块',     '6ES7521-1BL00',      1850.00),
    (12, 'NSK圆柱滚子轴承',         'NU206EM',            220.00),
    (13, '食品级硅胶O型圈',         'Φ25×3.5mm',         5.50),
    (14, '304不锈钢内六角螺钉',     'M8×30',             18.00),
    (15, 'SMC两位五通电磁阀',       'SY5120-5LZD',        245.00),
    (16, '灌装阀硬质合金阀芯',      'XGF-24专用',        420.00),
    (17, '南方泵业不锈钢离心泵',    'CHL4-30',            1350.00),
    (18, '不锈钢止回阀',           'H12W-16P DN25',       95.00),
    (19, '西门子工业以太网交换机',  'XB005',               780.00),
    (20, '横河铂电阻温度传感器',    'Pt100-A级',          145.00),
    (21, '明纬开关电源',           'NDR-120-24',          185.00),
    (22, 'NSK推力球轴承',          '51105',               45.00),
    (23, 'SMC气源处理器',          'AC30-03G',            210.00),
    (24, 'SMC磁性开关',            'D-A93',               52.00),
    (25, '正泰中间继电器',         'JZX-22F 24VDC',       28.00),
    (26, '盖茨同步带',             '5M-450',              45.00),
    (27, '梅花弹性联轴器',         'ML-42',               95.00),
    (28, 'SEW蜗轮蜗杆减速机',      'SA37-DRS71',         1200.00),
    (29, '施耐德按钮开关',         'XB4BA31',             22.00),
    (30, '施耐德急停按钮',         'XB4BS8442',           55.00),
    (31, '施耐德指示灯',           'XB4BVB3',             12.00),
    (32, '欧姆龙行程开关',         'D4V-8108SZ-N',        45.00),
    (33, '基恩士光栅传感器',       'GL-R32H',             2800.00),
    (34, '阿特拉斯空压机滤芯',     '1613-7508-00',        85.00),
    (35, 'SMC油压缓冲器',          'RBC1007',             78.00),
    (36, '紫铜垫圈',              'Φ20×Φ14×2mm',       3.50),
    (37, '骨架油封',              'TC25×42×10',          18.00),
    (38, '304不锈钢弹簧垫圈',      'M8',                  8.00),
    (39, '304不锈钢自攻螺丝',      'M5×16',              6.00),
    (40, '304不锈钢膨胀螺栓',      'M10×100',            12.00),
    (41, '旋盖机硅胶旋盖头',       'YXG-12专用',         35.00),
    (42, '食品级润滑脂',           'SKF LGFP2',           120.00),
    (43, 'CIP清洗专用碱液',        'NaOH-2% 25kg桶',     85.00),
    (44, '尼龙扎带',              '4.8×300mm白色',       12.00),
    (45, 'PVC绝缘胶布',           '舒氏3M-1500',         6.00),
    (46, '世达内六角扳手组套',     '09108',               95.00),
    (47, '世达活动扳手',           '47206',               55.00),
    (48, '三丰数显游标卡尺',       '500-196-30',          580.00),
    (49, '三丰外径千分尺',         '103-138',             350.00),
    (50, '福禄克数字万用表',       'Fluke-15B+',          650.00),
]

# =====================================================================
# 前置操作三：供应商替换
# =====================================================================

SUPPLIER_UPDATES = [
    (1,  '西门子(中国)有限公司'),
    (2,  'ABB电气有限公司'),
    (3,  '施耐德电气(中国)有限公司'),
    (4,  'NSK(中国)销售有限公司'),
    (5,  'SMC(中国)有限公司'),
    (6,  '欧姆龙自动化(中国)有限公司'),
    (7,  '基恩士(中国)有限公司'),
    (8,  '阿特拉斯·科普柯(中国)'),
    (9,  '新美星(江苏)有限公司'),
    (10, '永创智能装备股份有限公司'),
    (11, 'SKF(中国)销售有限公司'),
    (12, 'THK(中国)有限公司'),
    (13, 'SEW传动设备(天津)有限公司'),
    (14, '南方泵业股份有限公司'),
    (15, '浙江正泰电器股份有限公司'),
    (16, '明纬(广州)电子有限公司'),
    (17, '盖茨优霓塔传动系统'),
    (18, '福禄克测试仪器(上海)'),
    (19, '横河电机(中国)有限公司'),
    (20, '三丰精密量仪(上海)'),
]

# =====================================================================
# 第一层：维修工单数据
# =====================================================================

# 故障描述池：(fault_desc, fault_cause, repair_method, 关联设备ID列表, 常用备件ID列表)
FAULT_POOL = [
    ('灌装阀密封圈老化滴漏', '密封圈长期接触酒精腐蚀老化', '更换食品级硅胶O型圈，校准灌装量', [1], [13, 16]),
    ('旋盖机扭矩波动盖帽松动', '旋盖硅胶头磨损变形', '更换旋盖硅胶头，重新标定扭矩', [2], [41, 27]),
    ('贴标机光电传感器误触发', '传感器镜片被酒液溅污', '清洁传感器镜片，更换损坏传感器', [4], [3, 5]),
    ('输送链板卡滞异响', '链板磨损润滑不足', '更换磨损链板段，注入食品级润滑脂', [12], [42, 26]),
    ('PLC通讯模块断线重连', '通讯模块接口氧化接触不良', '更换通讯模块，清洁端子排', [6], [11, 19]),
    ('变频器过热保护跳停', '散热风扇故障导致温升', '更换变频器冷却风扇，清洁散热片', [1, 12], [2]),
    ('空压机排气温度超标', '空气滤芯堵塞进气量不足', '更换空压机滤芯，清洗冷却器', [5, 9], [34]),
    ('锅炉水位传感器漂移', '传感器探头结垢影响精度', '清洗探头，校准传感器零点', [8], [20]),
    ('CIP管路电磁阀卡阀', '碱液结晶导致阀芯卡死', '拆洗电磁阀，更换密封件', [10], [15, 13]),
    ('蒸馏甑桶冷凝水温偏高', '冷却水管路流量不足', '清洗冷凝器换热管，更换止回阀', [11], [18, 8]),
    ('码垛机械臂定位偏差', '同步带磨损导致定位误差', '更换同步带，校准机械臂原点', [12], [26, 9]),
    ('发电机组启动蓄电池亏电', '蓄电池内阻升高老化', '更换启动蓄电池，检查充电回路', [13], [10, 25]),
    ('制冷压缩机异常振动', '轴承磨损间隙超标', '更换压缩机轴承，动平衡校正', [14], [1, 12]),
    ('喷码机喷头堵塞', '墨路未及时清洗墨水干涸', '超声波清洗喷头，更换墨路滤芯', [15], [8]),
    ('视觉检测相机镜头模糊', '车间蒸汽导致镜片起雾', '清洁镜头，加装防雾罩', [7], [33]),
    ('伺服电机编码器信号丢失', '编码器接线端子松动', '重新压接端子，更换屏蔽电缆', [1, 2], [7]),
    ('同步带磨损打滑', '长期运行带面磨损齿形变形', '更换同步带及同步轮', [2, 4], [26]),
    ('直线导轨润滑不良', '导轨油脂干涸运动阻力大', '注入食品级润滑脂，清洁导轨', [1, 12], [9, 42]),
    ('气缸活塞杆密封泄漏', '密封圈老化活塞杆磨损', '更换气缸密封件组', [4, 12], [4, 37]),
    ('离心泵机械密封渗漏', '机械密封面磨损超标', '更换机械密封组件', [10, 11], [17, 37]),
]

# 设备故障权重（灌装机、旋盖机、空压机、锅炉更容易出故障）
DEVICE_FAULT_WEIGHT = {
    1: 5,   # 灌装机
    2: 4,   # 旋盖机
    3: 2,   # 套标收缩机
    4: 3,   # 贴标机
    5: 4,   # 空压机
    6: 2,   # PLC控制柜
    7: 1,   # 视觉检测
    8: 4,   # 锅炉
    9: 3,   # 螺杆空压机
    10: 2,  # CIP
    11: 2,  # 蒸馏机
    12: 3,  # 装箱码垛
    13: 1,  # 发电机
    14: 2,  # 制冷
    15: 2,  # 喷码机
}

# 高消耗备件权重
HIGH_CONSUME_PARTS = {
    13: 10,  # O型圈
    14: 8,   # 螺钉
    41: 7,   # 旋盖头
    44: 6,   # 扎带
    45: 6,   # 胶布
    34: 5,   # 空压机滤芯
    36: 5,   # 紫铜垫圈
    37: 5,   # 骨架油封
    38: 5,   # 弹簧垫圈
    39: 5,   # 自攻螺丝
    26: 4,   # 同步带
    42: 4,   # 润滑脂
    43: 4,   # CIP碱液
    8: 4,    # 硅胶软管
    3: 3,    # 光电传感器
    5: 3,    # 接近开关
    24: 3,   # 磁性开关
    29: 3,   # 按钮开关
    16: 2,   # 灌装阀芯
    4: 2,    # 气缸
}
# 其余备件默认权重1
ALL_PART_IDS = list(range(1, 51))

# 备件单价字典
PART_PRICE = {sp[0]: sp[3] for sp in SPARE_PART_UPDATES}

# 安装位置模板
INSTALL_LOC_TEMPLATES = {
    1:  '{code}新美星灌装机{pos}号灌装阀',
    2:  '{code}永创旋盖机{pos}号旋盖工位',
    3:  '{code}达意隆套标机传送段',
    4:  '{code}兄弟贴标机{pos}号贴标头',
    5:  '{code}阿特拉斯空压机主机',
    6:  '{code}PLC控制柜{pos}号模块槽',
    7:  '{code}基恩士视觉检测工位',
    8:  '{code}方快锅炉{pos}号阀门组',
    9:  '{code}开山空压机储气罐侧',
    10: '{code}阿法拉伐CIP清洗站{pos}号管路',
    11: '{code}富特蒸馏甑桶{pos}号冷凝器',
    12: '{code}南京轻机码垛线{pos}号工位',
    13: '{code}康明斯发电机电控柜',
    14: '{code}君科制冷机组压缩机侧',
    15: '{code}大族喷码机喷头模块',
}

# 备件对应的货位（按品类分区）
def get_location_for_part(part_id):
    """根据备件编码确定货位ID"""
    code_prefix = {
        'C02': [1, 2, 3],     # 电气 → A区
        'C03': [1, 2, 3],     # 传动 → A区
        'C04': [4, 5],        # 气动液压 → B区
        'C05': [4, 5],        # 液压管路 → B区
        'C06': [10, 11],      # 工量具 → E区
        'C07': [1, 2, 3],     # 传感器 → A区
        'C08': [8, 9],        # 紧固件 → D区
        'C09': [6, 7],        # 密封件 → C区
        'C10': [6, 7],        # 轴承 → C区
        'C01': [8, 9],        # 消耗品 → D区
    }
    # 通过 SPARE_PART_UPDATES 找到对应编码前3字符
    for sp in SPARE_PART_UPDATES:
        if sp[0] == part_id:
            # 从原始数据中查找编码 —— 用ID到编码的映射
            break
    # 使用简单规则：ID对应不同品类
    if part_id in [2, 6, 7, 10, 11, 19, 21, 25, 29, 30, 31]:  # 电气C02
        return random.choice([1, 2, 3])
    elif part_id in [9, 26, 27, 28]:  # 传动C03
        return random.choice([1, 2, 3])
    elif part_id in [4, 15, 23, 34, 35]:  # 气动C04
        return random.choice([4, 5])
    elif part_id in [8, 17, 18]:  # 液压管路C05
        return random.choice([4, 5])
    elif part_id in [16, 46, 47, 48, 49, 50]:  # 工量具C06
        return random.choice([10, 11])
    elif part_id in [3, 5, 20, 24, 32, 33]:  # 传感器C07
        return random.choice([1, 2, 3])
    elif part_id in [14, 38, 39, 40]:  # 紧固件C08
        return random.choice([8, 9])
    elif part_id in [13, 36, 37]:  # 密封件C09
        return random.choice([6, 7])
    elif part_id in [1, 12, 22]:  # 轴承C10
        return random.choice([6, 7])
    elif part_id in [41, 42, 43, 44, 45]:  # 消耗品C01
        return random.choice([8, 9])
    else:
        return random.choice(range(1, 13))

# 备件ID → supplier_id 映射
PART_SUPPLIER = {
    1: 11,   # SKF轴承 → SKF
    2: 2,    # ABB变频器 → ABB
    3: 6,    # 欧姆龙光电 → 欧姆龙
    4: 5,    # SMC气缸 → SMC
    5: 6,    # 欧姆龙接近 → 欧姆龙
    6: 3,    # 施耐德断路器 → 施耐德
    7: 1,    # 西门子伺服 → 西门子
    8: 9,    # 硅胶软管 → 新美星
    9: 12,   # THK导轨 → THK
    10: 15,  # 正泰接触器 → 正泰
    11: 1,   # 西门子PLC → 西门子
    12: 4,   # NSK轴承 → NSK
    13: 9,   # O型圈 → 新美星
    14: 15,  # 螺钉 → 正泰(通用)
    15: 5,   # SMC电磁阀 → SMC
    16: 9,   # 灌装阀芯 → 新美星
    17: 14,  # 南方泵 → 南方泵业
    18: 14,  # 止回阀 → 南方泵业
    19: 1,   # 西门子交换机 → 西门子
    20: 19,  # 横河传感器 → 横河
    21: 16,  # 明纬电源 → 明纬
    22: 4,   # NSK轴承 → NSK
    23: 5,   # SMC气源 → SMC
    24: 5,   # SMC磁性开关 → SMC
    25: 15,  # 正泰继电器 → 正泰
    26: 17,  # 盖茨同步带 → 盖茨
    27: 13,  # 联轴器 → SEW
    28: 13,  # SEW减速机 → SEW
    29: 3,   # 施耐德按钮 → 施耐德
    30: 3,   # 施耐德急停 → 施耐德
    31: 3,   # 施耐德指示灯 → 施耐德
    32: 6,   # 欧姆龙行程 → 欧姆龙
    33: 7,   # 基恩士光栅 → 基恩士
    34: 8,   # 阿特拉斯滤芯 → 阿特拉斯
    35: 5,   # SMC缓冲器 → SMC
    36: 15,  # 紫铜垫圈 → 正泰(通用)
    37: 11,  # 骨架油封 → SKF
    38: 15,  # 弹簧垫圈 → 正泰(通用)
    39: 15,  # 自攻螺丝 → 正泰(通用)
    40: 15,  # 膨胀螺栓 → 正泰(通用)
    41: 10,  # 旋盖头 → 永创智能
    42: 11,  # 润滑脂 → SKF
    43: 8,   # CIP碱液 → 阿特拉斯(通用)
    44: 15,  # 扎带 → 正泰(通用)
    45: 3,   # 胶布 → 施耐德(通用)
    46: 18,  # 扳手 → 福禄克(通用)
    47: 18,  # 活动扳手 → 福禄克(通用)
    48: 20,  # 卡尺 → 三丰
    49: 20,  # 千分尺 → 三丰
    50: 18,  # 万用表 → 福禄克
}


def rand_work_time(day, h_start=7, h_end=17):
    """在给定工作日内生成随机工作时间"""
    h = random.randint(h_start, h_end)
    m = random.randint(0, 59)
    s = random.randint(0, 59)
    return day.replace(hour=h, minute=m, second=s)


def fmt(dt):
    return dt.strftime('%Y-%m-%d %H:%M:%S')


def esc(s):
    """转义SQL字符串中的单引号"""
    return s.replace("'", "\\'")


# =====================================================================
# 主生成函数
# =====================================================================

def build_sql():
    sql = "-- 大型酒企配套厂 2026年8月模拟运营数据\n"
    sql += "-- 自动生成，请勿手动编辑\n\n"
    sql += "SET NAMES utf8mb4;\n"
    sql += "USE spare_db;\n"
    sql += "SET FOREIGN_KEY_CHECKS = 0;\n\n"

    # ==================================================================
    # 前置1: 设备替换
    # ==================================================================
    sql += "-- ========== 前置操作一：设备替换 ==========\n"
    for eq in EQUIPMENT_UPDATES:
        eid, code, name, model, dept = eq
        sql += (f"UPDATE equipment SET name='{esc(name)}', model='{esc(model)}', "
                f"department='{esc(dept)}' WHERE id={eid};\n")
    sql += "\n"

    # ==================================================================
    # 前置2: 备件替换
    # ==================================================================
    sql += "-- ========== 前置操作二：备件替换 ==========\n"
    for sp in SPARE_PART_UPDATES:
        sid, name, model, price = sp
        sql += (f"UPDATE spare_part SET name='{esc(name)}', model='{esc(model)}', "
                f"price={price} WHERE id={sid};\n")
    sql += "\n"

    # ==================================================================
    # 前置3: 供应商替换
    # ==================================================================
    sql += "-- ========== 前置操作三：供应商替换 ==========\n"
    for sup in SUPPLIER_UPDATES:
        sup_id, name = sup
        sql += f"UPDATE supplier SET name='{esc(name)}' WHERE id={sup_id};\n"
    sql += "\n"

    # ==================================================================
    # 第1层: 维修工单
    # ==================================================================
    sql += "-- ========== 第1层：维修工单 ==========\n"

    work_orders = []
    num_wo = random.randint(42, 48)

    for i in range(num_wo):
        wo_no = f"WO2026{8:02d}{i+1:04d}"
        day = random.choice(WORK_DAYS)

        # 按权重选设备
        fault_info = random.choice(FAULT_POOL)
        f_desc, f_cause, f_method = fault_info[0], fault_info[1], fault_info[2]
        device_ids_pool = fault_info[3]
        part_ids_pool = fault_info[4]

        device_id = random.choice(device_ids_pool)
        reporter_id = random.choice(ENGINEERS)

        levels = ['紧急'] * 10 + ['一般'] * 60 + ['计划'] * 30
        fault_level = random.choice(levels)

        report_time = rand_work_time(day)
        plan_finish = report_time + timedelta(days=random.randint(1, 5))
        actual_finish = report_time + timedelta(hours=random.randint(2, 72))

        mttr = random.randint(30, 480)
        part_cost = round(sum(PART_PRICE.get(pid, 50) for pid in part_ids_pool) * random.uniform(0.8, 1.5), 2)
        labor_cost = round(random.uniform(100, 800), 2)
        outsource_cost = round(random.uniform(0, 300), 2) if random.random() < 0.2 else 0

        sql += (f"INSERT INTO biz_work_order "
                f"(work_order_no, device_id, reporter_id, fault_desc, fault_level, "
                f"order_status, assignee_id, plan_finish, actual_finish, "
                f"fault_cause, repair_method, mttr_minutes, "
                f"part_cost, labor_cost, outsource_cost, report_time, created_at) VALUES ("
                f"'{wo_no}', {device_id}, {reporter_id}, '{esc(f_desc)}', '{fault_level}', "
                f"'已完成', {random.choice(ENGINEERS)}, '{fmt(plan_finish)}', '{fmt(actual_finish)}', "
                f"'{esc(f_cause)}', '{esc(f_method)}', {mttr}, "
                f"{part_cost}, {labor_cost}, {outsource_cost}, "
                f"'{fmt(report_time)}', '{fmt(report_time)}');\n")

        work_orders.append({
            'wo_no': wo_no,
            'device_id': device_id,
            'report_time': report_time,
            'part_ids': part_ids_pool,
        })

    sql += "\n"

    # ==================================================================
    # 第2层: 领用申请 + 明细
    # ==================================================================
    sql += "-- ========== 第2层：领用申请 + 明细 ==========\n"

    num_req = random.randint(85, 95)
    requisitions = []
    req_items = []

    # 状态分布
    status_pool = (
        ['INSTALLED'] * 65 +
        ['OUTBOUND'] * 15 +
        ['APPROVED'] * 10 +
        ['PENDING'] * 5 +
        ['REJECTED'] * 5
    )

    # 备件选取权重列表
    part_weights = []
    for pid in ALL_PART_IDS:
        w = HIGH_CONSUME_PARTS.get(pid, 1)
        part_weights.extend([pid] * w)

    for i in range(num_req):
        req_no = f"REQ2026{8:02d}{i+1:04d}"
        day = random.choice(WORK_DAYS)
        apply_time = rand_work_time(day, 8, 16)

        applicant_id = random.choice(ENGINEERS)
        status = random.choice(status_pool)

        # 70%关联工单，30%日常消耗
        wo_ref = None
        device_id = None
        if random.random() < 0.7 and work_orders:
            wo = random.choice(work_orders)
            wo_ref = wo['wo_no']
            device_id = wo['device_id']

        approve_id = random.choice(APPROVERS) if status != 'PENDING' else None
        approve_time = (apply_time + timedelta(hours=random.uniform(0.5, 8))) if approve_id else None
        approve_remark = None
        if status == 'REJECTED':
            approve_remark = random.choice(['库存不足，建议先采购', '申请数量过大，请核实', '非紧急需求，暂缓'])
        elif approve_id:
            approve_remark = '同意领用'

        is_urgent = 1 if random.random() < 0.1 else 0

        wo_val = f"'{wo_ref}'" if wo_ref else 'NULL'
        dev_val = device_id if device_id else 'NULL'
        app_id_val = approve_id if approve_id else 'NULL'
        app_time_val = f"'{fmt(approve_time)}'" if approve_time else 'NULL'
        app_remark_val = f"'{esc(approve_remark)}'" if approve_remark else 'NULL'

        sql += (f"INSERT INTO biz_requisition "
                f"(req_no, applicant_id, work_order_no, device_id, req_status, is_urgent, "
                f"approve_id, approve_time, approve_remark, apply_time, created_at) VALUES ("
                f"'{req_no}', {applicant_id}, {wo_val}, {dev_val}, '{status}', {is_urgent}, "
                f"{app_id_val}, {app_time_val}, {app_remark_val}, "
                f"'{fmt(apply_time)}', '{fmt(apply_time)}');\n")

        sql += f"SET @req_id_{i} = LAST_INSERT_ID();\n"

        # 明细行：1~3条
        num_items = random.randint(1, 3)
        for j in range(num_items):
            part_id = random.choice(part_weights)
            if device_id and not wo_ref:
                # 无工单时随机选设备相关
                device_id = random.choice(list(range(1, 16)))

            # 小件多领，大件少领
            if part_id in [13, 14, 36, 37, 38, 39, 44, 45]:  # 消耗品/紧固件
                apply_qty = random.randint(5, 20)
            elif part_id in [2, 7, 11, 17, 28, 33]:  # 贵重大件
                apply_qty = 1
            else:
                apply_qty = random.randint(1, 5)

            out_qty = apply_qty if status in ('OUTBOUND', 'INSTALLED') else None
            install_time = None
            installer_id = None
            install_loc = None

            if status == 'INSTALLED':
                eq_id = device_id if device_id else random.choice(list(range(1, 16)))
                eq_code = f"EQ-{999 + eq_id}"
                tpl = INSTALL_LOC_TEMPLATES.get(eq_id, '{code}通用安装位')
                install_loc = tpl.format(code=eq_code, pos=random.randint(1, 8))
                install_time = (approve_time or apply_time) + timedelta(hours=random.uniform(1, 24))
                installer_id = random.choice(ENGINEERS)

            out_val = out_qty if out_qty is not None else 'NULL'
            inst_loc_val = f"'{esc(install_loc)}'" if install_loc else 'NULL'
            inst_time_val = f"'{fmt(install_time)}'" if install_time else 'NULL'
            inst_id_val = installer_id if installer_id else 'NULL'

            sql += (f"INSERT INTO biz_requisition_item "
                    f"(req_id, spare_part_id, apply_qty, out_qty, install_loc, install_time, "
                    f"installer_id, created_at) VALUES ("
                    f"@req_id_{i}, {part_id}, {apply_qty}, {out_val}, "
                    f"{inst_loc_val}, {inst_time_val}, {inst_id_val}, "
                    f"'{fmt(apply_time)}');\n")

            item_info = {
                'req_idx': i,
                'part_id': part_id,
                'out_qty': out_qty,
                'status': status,
                'apply_time': apply_time,
            }
            req_items.append(item_info)

    sql += "\n"

    # ==================================================================
    # 第3层: 入库单 + 入库明细
    # ==================================================================
    sql += "-- ========== 第3层：入库单 + 入库明细 ==========\n"

    num_receipts = random.randint(16, 20)
    stock_in_items = []  # 用于后续出库追踪

    # 入库日期均匀分散
    receipt_days = sorted(random.sample(WORK_DAYS, min(num_receipts, len(WORK_DAYS))))

    for r in range(num_receipts):
        rec_code = f"REC2026{8:02d}{r+1:04d}"
        rec_day = receipt_days[r % len(receipt_days)]
        rec_date = rand_work_time(rec_day, 9, 15)
        handler_id = random.choice(WAREHOUSE)

        sql += (f"INSERT INTO stock_in_receipt "
                f"(receipt_code, receipt_date, status, handler_id, remark, created_at) VALUES ("
                f"'{rec_code}', '{fmt(rec_date)}', 'COMPLETED', {handler_id}, "
                f"'8月常规入库', '{fmt(rec_date)}');\n")
        sql += f"SET @rec_id_{r} = LAST_INSERT_ID();\n"

        # 每批入库2~4种备件
        num_items = random.randint(2, 4)
        batch_parts = random.sample(ALL_PART_IDS, min(num_items, 50))

        for bp in batch_parts:
            # 入库数量：小件多入，大件少入
            if bp in [13, 14, 36, 37, 38, 39, 44, 45]:
                exp_qty = random.randint(50, 200)
            elif bp in [2, 7, 11, 17, 28, 33]:
                exp_qty = random.randint(1, 3)
            else:
                exp_qty = random.randint(5, 30)

            # 90%完全一致，10%略有差异
            if random.random() < 0.9:
                act_qty = exp_qty
            else:
                act_qty = max(1, exp_qty + random.randint(-2, 0))

            loc_id = get_location_for_part(bp)

            sql += (f"INSERT INTO stock_in_item "
                    f"(stock_in_receipt_id, spare_part_id, expected_quantity, actual_quantity, "
                    f"remaining_qty, shelved_quantity, location_id, in_time) VALUES ("
                    f"@rec_id_{r}, {bp}, {exp_qty}, {act_qty}, "
                    f"{act_qty}, {act_qty}, {loc_id}, '{fmt(rec_date)}');\n")

            stock_in_items.append({
                'rec_idx': r,
                'part_id': bp,
                'remaining_qty': act_qty,
                'in_time': rec_date,
            })

    sql += "\n"

    # ==================================================================
    # 第4层: 出库扣减追踪
    # ==================================================================
    sql += "-- ========== 第4层：出库扣减追踪 ==========\n"
    sql += "-- 注：出库追踪关联入库批次，使用 LAST_INSERT_ID 获取动态ID\n"
    sql += "-- 为简化，此处使用子查询匹配最近的入库批次\n\n"

    outbound_items = [it for it in req_items if it['status'] in ('OUTBOUND', 'INSTALLED') and it['out_qty']]

    for idx, ob in enumerate(outbound_items):
        part_id = ob['part_id']
        deduct_qty = ob['out_qty']
        outbound_time = ob['apply_time'] + timedelta(hours=random.uniform(2, 12))

        # 通过子查询关联领用明细和入库批次
        sql += (f"INSERT INTO biz_outbound_batch_trace "
                f"(req_item_id, stock_in_item_id, spare_part_id, deduct_qty, outbound_time) "
                f"SELECT ri.id, "
                f"COALESCE((SELECT si.id FROM stock_in_item si WHERE si.spare_part_id = {part_id} "
                f"AND si.remaining_qty > 0 ORDER BY si.in_time ASC LIMIT 1), 1), "
                f"{part_id}, {deduct_qty}, '{fmt(outbound_time)}' "
                f"FROM biz_requisition_item ri "
                f"WHERE ri.spare_part_id = {part_id} "
                f"AND ri.created_at >= '2026-08-01' "
                f"ORDER BY ri.id DESC LIMIT 1;\n")

    sql += "\n"

    # ==================================================================
    # 第5层: 消耗统计
    # ==================================================================
    sql += "-- ========== 第5层：消耗统计 ==========\n"

    # 汇总各备件出库数量
    part_consumption = {}
    part_repair_count = {}
    for it in req_items:
        if it['out_qty'] and it['status'] in ('OUTBOUND', 'INSTALLED'):
            part_consumption[it['part_id']] = part_consumption.get(it['part_id'], 0) + it['out_qty']

    # 从工单统计维修次数（按关联备件）
    for wo in work_orders:
        for pid in wo['part_ids']:
            part_repair_count[pid] = part_repair_count.get(pid, 0) + 1

    for pid in ALL_PART_IDS:
        outbound_qty = part_consumption.get(pid, 0)
        repair_count = part_repair_count.get(pid, 0)
        avg_price = PART_PRICE.get(pid, 0)

        sql += (f"INSERT INTO spare_part_consumption_log "
                f"(spare_part_id, record_month, outbound_qty, repair_count, "
                f"avg_unit_price, working_days) VALUES ("
                f"{pid}, '2026-08-01', {outbound_qty}, {repair_count}, "
                f"{avg_price}, 22);\n")

    sql += "\n"

    # ==================================================================
    # 第6层: 采购订单 + 询价
    # ==================================================================
    sql += "-- ========== 第6层：采购订单 + 询价 ==========\n"

    # 选消耗量最大的备件生成采购需求
    sorted_consumption = sorted(part_consumption.items(), key=lambda x: x[1], reverse=True)
    purchase_parts = sorted_consumption[:random.randint(22, 28)]

    for idx, (pid, qty) in enumerate(purchase_parts):
        po_no = f"PO2026{8:02d}{idx+1:04d}"
        day = random.choice(WORK_DAYS)
        order_time = rand_work_time(day, 9, 16)

        supplier_id = PART_SUPPLIER.get(pid, random.randint(1, 20))
        order_qty = max(qty, random.randint(5, 50))
        unit_price = PART_PRICE.get(pid, 100)
        total_amount = round(order_qty * unit_price, 2)

        statuses = ['已下单'] * 30 + ['已发货'] * 20 + ['到货'] * 20 + ['验收通过'] * 30
        order_status = random.choice(statuses)

        expected_date = order_time + timedelta(days=random.randint(7, 21))
        actual_date = None
        if order_status in ('到货', '验收通过'):
            actual_date = expected_date + timedelta(days=random.randint(-2, 3))

        exp_date_str = expected_date.strftime('%Y-%m-%d')
        act_date_val = f"'{actual_date.strftime('%Y-%m-%d')}'" if actual_date else 'NULL'

        sql += (f"INSERT INTO biz_purchase_order "
                f"(order_no, spare_part_id, supplier_id, order_qty, unit_price, total_amount, "
                f"order_status, expected_date, actual_date, purchaser_id, remark, created_at) VALUES ("
                f"'{po_no}', {pid}, {supplier_id}, {order_qty}, {unit_price}, {total_amount}, "
                f"'{order_status}', '{exp_date_str}', {act_date_val}, "
                f"{random.choice(BUYER)}, '8月补货采购', '{fmt(order_time)}');\n")

        # 每个采购单 2~3 家供应商报价
        num_quotes = random.randint(2, 3)
        all_suppliers = list(range(1, 21))
        # 确保中标供应商在列表中
        quote_suppliers = [supplier_id]
        others = [s for s in all_suppliers if s != supplier_id]
        quote_suppliers.extend(random.sample(others, num_quotes - 1))

        for qs in quote_suppliers:
            deviation = random.uniform(-0.10, 0.12)
            quote_price = round(unit_price * (1 + deviation), 2)
            delivery_days = random.randint(5, 18)
            is_selected = 1 if qs == supplier_id else 0
            quote_time = order_time - timedelta(days=random.randint(1, 5))

            sql += (f"INSERT INTO biz_supplier_quote "
                    f"(order_no, supplier_id, spare_part_id, quote_price, quote_time, "
                    f"delivery_days, is_selected, created_at) VALUES ("
                    f"'{po_no}', {qs}, {pid}, {quote_price}, '{fmt(quote_time)}', "
                    f"{delivery_days}, {is_selected}, '{fmt(quote_time)}');\n")

    sql += "\n"
    sql += "SET FOREIGN_KEY_CHECKS = 1;\n"
    sql += "-- 数据生成完毕\n"

    return sql


if __name__ == '__main__':
    sql_content = build_sql()
    output_path = '/Users/weiyaozhou/Documents/langdong/sql/mock_aug2026.sql'
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    print(f"SQL 已生成: {output_path}")
    print(f"文件大小: {len(sql_content)} 字节")
