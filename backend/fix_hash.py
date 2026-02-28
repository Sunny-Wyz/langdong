import mysql.connector

try:
    conn = mysql.connector.connect(
        host="localhost",
        user="admin",
        password="123456",
        database="spare_db"
    )
    cursor = conn.cursor()
    hash_val = "$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa"
    cursor.execute("UPDATE user SET password = %s WHERE username = 'admin'", (hash_val,))
    
    # Also update all testusers because their passwords might be corrupted too
    cursor.execute("UPDATE user SET password = %s WHERE username LIKE 'testuser_%'", (hash_val,))
    
    conn.commit()
    print("Passwords fixed successfully")
except Exception as e:
    print(f"Error: {e}")
finally:
    if 'cursor' in locals():
        cursor.close()
    if 'conn' in locals():
        conn.close()
