from pathlib import Path
import json,re
rows=json.loads(Path('data-source/full-adjudication/rda-10.4-nutrients.json').read_text(encoding='utf-8'))
terms=['표고','실파','쪽파','모시','보리','미역줄기','참다래','완두','들깨','미나리','전복','홍합','굴,','팥,','호두','미역,','다시마','부추','상추','고사리','매생이','유채','머위','고구마줄기','떡볶이','식빵','빵가루','당면','멸치젓','멸치,','생강','모차렐라','모짜렐라','국간장','간장','겨자','녹두','청포묵','대구','옥수수','참나물','과립','고추,','고비','목이','강낭콩','미꾸라지']
for term in terms:
    print(term,[(r['code'],r['name']) for r in rows if term in r['name']][:35])
