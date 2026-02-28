import random
import uuid
import datetime

# --- Helpers ---
def esc(s):
    return "'" + str(s).replace("'", "''") + "'"

def rnd_date(start_days_ago, end_days_ago):
    base = datetime.datetime.now()
    d1 = base - datetime.timedelta(days=start_days_ago)
    d2 = base - datetime.timedelta(days=end_days_ago)
    dt = d2 + datetime.timedelta(seconds=random.randint(0, int((d1 - d2).total_seconds())))
    return dt.strftime("%Y-%m-%d %H:%M:%S")

with open('sql/mock_data.sql', 'w', encoding='utf-8') as f:
    f.write("USE spare_db;\n")
    f.write("SET FOREIGN_KEY_CHECKS = 0;\n\n")

    # 1. User & User Role (10 users)
    f.write("-- Users & Roles\n")
    users = []
    roles = [1, 2, 3, 4, 5, 6] # existing roles: admin, warehouse, engineer, purchaser, etc.
    for i in range(1, 11):
        username = f"testuser_{i}"
        password_hash = "$2a$10$wY1vzscyQx3i0.g2K6wV/.R63/aN.iJ5E1wXZi9sJv.mUe.V09.Uu" # '123456'
        real_name = f"测试用户_{i}号"
        phone = f"1380000{i:04d}"
        f.write(f"INSERT INTO user (username, password, real_name, phone) VALUES ({esc(username)}, {esc(password_hash)}, {esc(real_name)}, {esc(phone)});\n")
        users.append(i + 1) # assuming admin is id=1, new users start at 2
    f.write("INSERT INTO user_role (user_id, role_id) VALUES\n")
    ur_vals = []
    for uid in users:
        ur_vals.append(f"({uid}, {random.choice(roles)})")
    f.write(",\n".join(ur_vals) + ";\n\n")

    # 2. Spare Part Categories (10)
    f.write("-- Categories\n")
    cats = ["通用消耗", "电气组件", "机械传动", "气动元件", "液压配件", "刀具夹具", "传感器类", "紧固件", "密封件", "轴承类"]
    cat_ids = []
    for i, c in enumerate(cats):
        f.write(f"INSERT INTO spare_part_category (name, description) VALUES ({esc(c)}, {esc(c+'描述')});\n")
        cat_ids.append(i + 1)
    f.write("\n")

    # 3. Suppliers (100)
    f.write("-- Suppliers\n")
    supplier_ids = list(range(1, 101))
    for i in supplier_ids:
        name = f"自动化科技供应商_{i}厂"
        contact = f"联系人_{i}"
        phone = f"1390000{i:04d}"
        email = f"supply{i}@test.com"
        f.write(f"INSERT INTO supplier (name, contact_person, contact_phone, email, status) VALUES ({esc(name)}, {esc(contact)}, {esc(phone)}, {esc(email)}, 1);\n")
    f.write("\n")

    # 4. Equipment (50)
    f.write("-- Equipment\n")
    equip_ids = list(range(1, 51))
    for i in equip_ids:
        code = f"EQ-{1000+i}"
        name = f"生产产线设备_{i}型"
        f.write(f"INSERT INTO equipment (code, name, status) VALUES ({esc(code)}, {esc(name)}, '运行中');\n")
    f.write("\n")

    # 5. Spare Parts (100)
    f.write("-- Spare Parts\n")
    part_ids = list(range(1, 101))
    for i in part_ids:
        code = f"SP{20000+i}"
        name = f"工业标准备件_{i}型"
        spec = f"规格{random.choice(['A','B','C'])}-{random.randint(10,99)}"
        unit = random.choice(["件", "个", "套", "米", "把"])
        cat = random.choice(cat_ids)
        price = round(random.uniform(10.0, 5000.0), 2)
        qty = random.randint(0, 500)
        min_qty = random.randint(5, 50)
        max_qty = min_qty + random.randint(50, 200)
        f.write(f"INSERT INTO spare_part (code, name, specification, unit, category_id, price, quantity, min_stock, max_stock) VALUES ({esc(code)}, {esc(name)}, {esc(spec)}, {esc(unit)}, {cat}, {price}, {qty}, {min_qty}, {max_qty});\n")
    f.write("\n")

    # 6. Part Classify (M4) (100)
    f.write("-- Part Classify ABC\n")
    for i in part_ids:
        level = random.choice(["A", "A", "B", "B", "B", "C", "C", "C", "C", "C"])
        score = random.randint(50, 100)
        f.write(f"INSERT INTO biz_part_classify (spare_part_id, classify_level, total_score) VALUES ({i}, {esc(level)}, {score});\n")
    f.write("\n")

    # 7. Work Orders (100)
    f.write("-- Work Orders\n")
    wo_ids = list(range(1, 101))
    for i in wo_ids:
        no = f"WO202602{i:04d}"
        eq = random.choice(equip_ids)
        reporter = random.choice(users)
        status = random.choice(["已完成", "维修中", "待派工", "待验收"])
        f.write(f"INSERT INTO biz_work_order (work_order_no, device_id, reporter_id, fault_desc, fault_level, order_status, report_time) VALUES ({esc(no)}, {eq}, {reporter}, '测试设备故障详情', '一般', {esc(status)}, '{rnd_date(60, 0)}');\n")
    f.write("\n")

    # 8. Requisition (100) & Items
    f.write("-- Requisitions\n")
    req_ids = list(range(1, 101))
    for i in req_ids:
        no = f"REQ-2026-{i:04d}"
        app = random.choice(users)
        wo = random.choice(wo_ids)
        status = random.choice(["INSTALLED", "OUTBOUND", "APPROVED", "PENDING", "REJECTED"])
        f.write(f"INSERT INTO biz_requisition (req_no, applicant_id, work_order_id, purpose, req_status, created_at) VALUES ({esc(no)}, {app}, {wo}, '生产线日常维修消耗', {esc(status)}, '{rnd_date(90, 0)}');\n")
        
        # 1-3 items per req
        for _ in range(random.randint(1, 3)):
            pid = random.choice(part_ids)
            qty = random.randint(1, 10)
            f.write(f"INSERT INTO biz_requisition_item (req_id, spare_part_id, apply_qty) VALUES ({i}, {pid}, {qty});\n")
    f.write("\n")

    # 9. Reorder Suggest (100)
    f.write("-- Reorder Suggest (M6)\n")
    for i in part_ids:
        mth = f"2026-{random.choice(['01','02','03'])}"
        cur = random.randint(0, 20)
        rop = cur + random.randint(5, 20)
        sug = random.randint(20, 100)
        urg = random.choice(["正常", "正常", "紧急"])
        stat = random.choice(["待处理", "已采购", "已忽略"])
        part_code = f"SP{20000+i}"
        f.write(f"INSERT INTO biz_reorder_suggest (part_code, suggest_month, current_stock, reorder_point, suggest_qty, forecast_qty, lower_bound, upper_bound, urgency, status) VALUES ({esc(part_code)}, {esc(mth)}, {cur}, {rop}, {sug}, {sug*0.9}, {sug*0.8}, {sug*1.2}, {esc(urg)}, {esc(stat)});\n")
    f.write("\n")

    # 10. Purchase Orders (100)
    f.write("-- Purchase Orders (M6)\n")
    po_ids = list(range(1, 101))
    for i in po_ids:
        no = f"PO202602{i:04d}"
        sp = random.choice(part_ids)
        sup = random.choice(supplier_ids)
        qty = random.randint(10, 200)
        price = round(random.uniform(10.0, 5000.0), 2)
        total = round(qty * price, 2)
        stat = random.choice(["验收通过", "待收货", "已询价", "验收失败", "已发货", "草稿"])
        exp = (datetime.datetime.now() + datetime.timedelta(days=random.randint(-10, 30))).strftime("%Y-%m-%d")
        act = exp if stat == "验收通过" else "NULL"
        f.write(f"INSERT INTO biz_purchase_order (order_no, spare_part_id, supplier_id, order_qty, unit_price, total_amount, order_status, expected_date, actual_date, created_at) VALUES ({esc(no)}, {sp}, {sup}, {qty}, {price}, {total}, {esc(stat)}, '{exp}', {esc(act) if act != 'NULL' else act}, '{rnd_date(30, 0)}');\n")
    f.write("\n")

    # 11. Supplier Quotes (300)
    f.write("-- Supplier Quotes (M6)\n")
    for po in po_ids:
        no = f"PO202602{po:04d}"
        sp = random.choice(part_ids)
        # 3 quotes per PO
        quotes = random.sample(supplier_ids, 3)
        base_price = round(random.uniform(50.0, 2000.0), 2)
        selected_idx = random.randint(0, 2)
        for idx, sup in enumerate(quotes):
            p = round(base_price * random.uniform(0.8, 1.2), 2)
            sel = 1 if idx == selected_idx else 0
            f.write(f"INSERT INTO biz_supplier_quote (order_no, supplier_id, spare_part_id, quote_price, delivery_days, is_selected) VALUES ({esc(no)}, {sup}, {sp}, {p}, {random.randint(3,15)}, {sel});\n")
    f.write("\n")

    f.write("SET FOREIGN_KEY_CHECKS = 1;\n")
