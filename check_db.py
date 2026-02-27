import os
import re

entities = [
    "PurchaseOrder", "PurchaseOrderItem", "StockInReceipt", "StockInItem",
    "SparePartStock", "SparePartLocationStock", "Location"
]

with open("sql/init.sql", "r", encoding="utf-8") as f:
    sql_content = f.read()

existing_tables = re.findall(r"CREATE TABLE IF NOT EXISTS `([^`]+)`", sql_content)
print("Existing:", existing_tables)
