"""Exact, visually reviewed official image table. No invented product IDs.

Only single-serving bakery names are supported. Other sizes, hot/iced drinks,
unreviewed rows and changed image bytes are held for review, never guessed.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
from lxml import html

TABLE = Path(__file__).resolve().parents[1] / 'data-source/franchise-nutrition-followup-20261016/reviewed-table.json'
IMAGE_URL = 'https://cdn.imweb.me/upload/S202112085ca3700daccb4/eba8c48373f46.jpg'
IMAGE_SHA = '1d563eb05640fdc844a1daed39662685f3d82a315174bf8b3d5015b184404d13'
HEADERS = ['열량(kcal)','나트륨(mg)','탄수화물(g)','당류(g)','포화지방(g)','단백질(g)','카페인(mg)']
MATCH = 'EXACT_REVIEWED_IMAGE_NAME_SINGLE_SERVING'
FIELDS = ('energyKcal','sodiumMilligrams','carbohydrateGrams','sugarsGrams','saturatedFatGrams','proteinGrams')


def reviewed_fact(menu):
    assert menu['brand']=='우지커피' and menu['externalId']=='', 'No cross-brand or fabricated product identity'
    assert menu['sourceUrl']=='https://www.oozycoffee.com/32', 'Only the confirmed official bakery menu source'
    table=json.loads(TABLE.read_text(encoding='utf-8'))
    assert table['schemaVersion']==1 and table['headers']==HEADERS and table['totalFatPublished'] is False
    assert table['imageUrl']==IMAGE_URL and table['imageSha256']==IMAGE_SHA
    norm=lambda value:re.sub(r'\s+', '', value)
    matches=[row for row in table['rows'] if norm(row['name'])==norm(menu['name'])]
    assert len(matches)==1, 'Exact reviewed bakery name/single serving not found; no alias or size assumption'
    row=matches[0]
    assert row['category']=='베이커리' and row['basis']=='1회 제공량 당' and len(row['values'])==6
    return dict(zip(FIELDS,row['values'])), table


def parse(menu, notice, image_fetcher, checked_at=None):
    values,table=reviewed_fact(menu)
    root=html.fromstring(notice)
    images=root.xpath('//div[contains(concat(" ",normalize-space(@class)," ")," board_txt_area ")]//img/@src')
    assert images==[IMAGE_URL], 'Official nutrition image reference changed; review required'
    image=image_fetcher(IMAGE_URL)
    assert isinstance(image,bytes) and hashlib.sha256(image).hexdigest()==IMAGE_SHA, 'Official nutrition image changed; old values cannot be reused as fresh facts'
    checked=checked_at or datetime.now(timezone.utc).isoformat()
    return dict(values, fatGrams=None, servingAmount=1.0,servingUnit='인분',
        servingDescription='공식 1회 제공량 기준 · 중량 미공개 · 표준 레시피 · 2026-09 자료',
        matchedBy=MATCH,retainedFieldEvidence={field:dict(sourceUrl=IMAGE_URL,sourceSha256=IMAGE_SHA,checkedAt=checked) for field in FIELDS})


def validate_replay(menu, fact):
    values,table=reviewed_fact(menu)
    assert fact.get('matchedBy')==MATCH and fact.get('sourceUrl')==table['sourceUrl']
    assert fact.get('fatGrams') is None and fact.get('servingAmount')==1 and fact.get('servingUnit')=='인분'
    assert all(fact.get(key)==value for key,value in values.items())
    for field in FIELDS:
        proof=fact.get('retainedFieldEvidence',{}).get(field,{})
        assert proof.get('sourceUrl')==IMAGE_URL and proof.get('sourceSha256')==IMAGE_SHA
