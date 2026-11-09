import math
import subprocess
from pathlib import Path

ROWS = [
    ('锦绣江南西门烤串', '天坛一街与文萃路交叉路口往北约100米(锦绣江南)', 123.459953, 41.765175),
    ('香乐美食楼', '滨河街道大南街359号(近省人民医院)', 123.456963, 41.776974),
    ('肯德基', '文艺路80号（大福源超市一层）', 123.452332, 41.778301),
    ('永和大王', '文艺路80号', 123.453331, 41.778282),
    ('蜀城春川味小吃楼', '北文萃路16号楼', 123.441345, 41.766434),
    ('华莱士·全鸡汉堡', '大南街343号(近大南边门)', 123.45694, 41.778633),
    ('老倪肉串', '文艺路八十巷47-2', 123.45401, 41.779259),
    ('娘仨卷饼', '文萃路30号', 123.440514, 41.765255),
    ('小红帽串吧', '五里河街道北文萃路1-1-511号(早市路南)', 123.439674, 41.766182),
    ('天天肥牛', '文萃路22号', 123.439217, 41.765316),
    ('小帽串店', '北文萃路五里河东巷3号', 123.438225, 41.766197),
    ('沈家小厨', '南关路49号', 123.456238, 41.78056),
    ('必胜客', '文化路81号（近青年大街）', 123.435898, 41.770313),
]


def convert(lon: float, lat: float) -> tuple:
    x, y = lon - 105, lat - 35
    a, ee = 6378245.0, 0.006693421622965943
    dy = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*math.sqrt(abs(x))
    dy += (20*math.sin(6*x*math.pi) + 20*math.sin(2*x*math.pi))*2/3
    dy += (20*math.sin(y*math.pi) + 40*math.sin(y/3*math.pi))*2/3
    dy += (160*math.sin(y/12*math.pi) + 320*math.sin(y*math.pi/30))*2/3
    dx = 300+x+2*y+.1*x*x+.1*x*y+.1*math.sqrt(abs(x))
    dx += (20*math.sin(6*x*math.pi) + 20*math.sin(2*x*math.pi))*2/3
    dx += (20*math.sin(x*math.pi) + 40*math.sin(x/3*math.pi))*2/3
    dx += (150*math.sin(x/12*math.pi) + 300*math.sin(x/30*math.pi))*2/3
    rad = lat / 180 * math.pi
    magic = 1 - ee * math.sin(rad)**2
    root = math.sqrt(magic)
    return (round(lon-dx*180/(a/root*math.cos(rad)*math.pi), 6),
            round(lat-dy*180/((a*(1-ee))/(magic*root)*math.pi), 6))


def quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    lines = []
    for name, address, lon, lat in ROWS:
        x, y = convert(lon, lat)
        values = ','.join([quote(name), '1', "''", quote('公开POI·沈阳药科大学周边'), quote(address), str(x), str(y), '0', '0', '0'])
        lines.append('INSERT INTO tb_shop (name,type_id,images,area,address,x,y,sold,comments,score) SELECT ' + values + ' WHERE NOT EXISTS (SELECT 1 FROM tb_shop WHERE name=' + quote(name) + ' AND address=' + quote(address) + ');')
    path = Path('scripts/import_shenyang_public_poi.sql')
    source = path.read_text(encoding='utf-8')
    if quote(ROWS[0][0]) not in source:
        path.write_text(source.replace('COMMIT;', '\n'.join(lines) + '\nCOMMIT;'), encoding='utf-8')
    sql = 'SET NAMES utf8mb4;\nSTART TRANSACTION;\n' + '\n'.join(lines) + '\nCOMMIT;\n'
    mysql = ['mysql', '--default-character-set=utf8mb4', '-h', '127.0.0.1', '-P', '3306', '-u', 'admin', 'hmdianping']
    subprocess.run(mysql, input=sql.encode('utf-8'), capture_output=True, check=True)
    query = "SELECT id,x,y FROM tb_shop WHERE area='公开POI·沈阳药科大学周边' ORDER BY id"
    result = subprocess.run(mysql + ['-N', '-B', '-e', query], capture_output=True, check=True)
    entries = [line.split('\t') for line in result.stdout.decode('utf-8').splitlines()]
    for shop_id, lon, lat in entries:
        subprocess.run(['redis-cli', '-h', '127.0.0.1', '-p', '6380', 'GEOADD', 'shop:geo:1', lon, lat, shop_id], capture_output=True, check=True)
    print('Public POIs indexed:', len(entries))


if __name__ == '__main__':
    main()
