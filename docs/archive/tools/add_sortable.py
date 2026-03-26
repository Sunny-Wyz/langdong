import os
import re

dir_path = "/Users/weiyaozhou/Documents/langdong/frontend/src/views"

# Matches <el-table-column ... > or <el-table-column ... /> (dot matches newline)
column_pattern = re.compile(r'<el-table-column([^>]*?)>', re.DOTALL)

def process_file(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Failed to read {file_path}: {e}")
        return False

    def repl(match):
        attrs = match.group(1)
        # Check exclusions
        # If it already has sortable, skip
        if re.search(r'\bsortable\b', attrs):
            return match.group(0)
        # Skip specific types
        if 'type="selection"' in attrs or "type='selection'" in attrs:
            return match.group(0)
        if 'type="index"' in attrs or "type='index'" in attrs:
            return match.group(0)
        if 'type="expand"' in attrs or "type='expand'" in attrs:
            return match.group(0)
        # Skip action column
        if 'label="操作"' in attrs or "label='操作'" in attrs:
            return match.group(0)

        # Append sortable
        if attrs.endswith('/'):
            # Self-closing <el-table-column ... />
            new_attrs = attrs[:-1].rstrip() + ' sortable /'
        else:
            # Open tag <el-table-column ... >
            new_attrs = attrs.rstrip() + ' sortable '
        
        return f'<el-table-column{new_attrs}>'

    try:
        new_content = column_pattern.sub(repl, content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
        return False

    if new_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    return False

modified_count = 0
for root, dirs, files in os.walk(dir_path):
    for fl in files:
        if fl.endswith('.vue'):
            if process_file(os.path.join(root, fl)):
                print(f"Modified: {fl}")
                modified_count += 1

print(f"\nTotal modified files: {modified_count}")
