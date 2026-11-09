import json
import subprocess
from pathlib import Path
from import_extra_poi import convert, quote


def main() -> None:
    target = Path('frontend-vue3/src/data/category-pois.json')
    selected = json.loads(target.read_text(encoding='utf-8'))
    lines = ['-- Source URLs and original GCJ-02 coordinates: frontend-vue3/src/data/category-pois.json',
             '-- Converted coordinates are approximate WGS-84; business status requires confirmation.',
             'SET NAMES utf8mb4;', 'START TRANSACTION;']
    for item in selected:
        x, y = convert(item['lon'], item['lat'])
        values = ','.join([quote(item['name']), str(item['typeId']), "''", quote('公开POI·沈阳药科大学周边'), quote(item['address']), str(x), str(y), '0', '0', '0'])
        lines.append('INSERT INTO tb_shop (name,type_id,images,area,address,x,y,sold,comments,score) SELECT ' + values + ' WHERE NOT EXISTS (SELECT 1 FROM tb_shop WHERE name=' + quote(item['name']) + ' AND address=' + quote(item['address']) + ');')
    lines.append('COMMIT;')
    sql = '\n'.join(lines) + '\n'
    Path('scripts/import_category_pois.sql').write_text(sql, encoding='utf-8')
    mysql = ['mysql', '--default-character-set=utf8mb4', '-h', '127.0.0.1', '-P', '3306', '-u', 'admin', 'hmdianping']
    subprocess.run(mysql, input=sql.encode('utf-8'), capture_output=True, check=True)
    query = "SELECT id,type_id,x,y FROM tb_shop WHERE area='公开POI·沈阳药科大学周边'"
    result = subprocess.run(mysql + ['-N', '-B', '-e', query], capture_output=True, check=True)
    for line in result.stdout.decode('utf-8').splitlines():
        shop_id, type_id, lon, lat = line.split('\t')
        subprocess.run(['redis-cli', '-h', '127.0.0.1', '-p', '6380', 'GEOADD', 'shop:geo:' + type_id, lon, lat, shop_id], capture_output=True, check=True)
    for type_id in sorted({item['typeId'] for item in selected}):
        print(type_id, sum(item['typeId'] == type_id for item in selected))


if __name__ == '__main__':
    main()
