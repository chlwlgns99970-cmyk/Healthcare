"""Read-only discovery of nutrition links on each registry official page."""
import concurrent.futures
import hashlib
import json
import re
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin
from lxml import html
from franchise_daily_sync import CONFIG, official_url

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/build/franchise-nutrition/source-discovery'


def discover(brands=None, out=OUT, fetcher=None):
    if out is not None:out.mkdir(parents=True,exist_ok=True)
    brands=brands if brands is not None else json.loads(CONFIG.read_text(encoding='utf-8'))
    def one(brand):
        url=brand['sourceUrl']
        result=dict(brandId=brand['brandId'],brand=brand['name'],url=url,checkedAt=datetime.now(timezone.utc).isoformat(),links=[])
        try:
            if fetcher is None:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'HealthcareMenuAudit/1.0 (official nutrition source discovery)'}),timeout=8) as response:
                    assert official_url(response.url,brand)
                    payload=response.read(2000001);assert len(payload)<=2000000
                    encoding=response.headers.get_content_charset() or 'utf-8'
                    result.update(httpStatus=response.status,sourceSha256=hashlib.sha256(payload).hexdigest())
            else:payload=fetcher(url);encoding='utf-8';result.update(httpStatus=200,sourceSha256=hashlib.sha256(payload).hexdigest())
            markup=payload.decode(encoding,errors='replace')
            if out is not None:(out/(brand['brandId']+'.html')).write_text(markup,encoding='utf-8')
            root=html.fromstring(markup)
            for node in root.xpath('//a[@href]'):
                href=urljoin(url,node.get('href'));label=' '.join(node.itertext()).strip()
                if official_url(href,brand) and re.search('영양정보|영양성분|nutrition|nutrient|성분표',label+' '+href,re.I):
                    result['links'].append(dict(url=href,label=label[:200]))
            result['links']=list({r['url']:r for r in result['links']}.values())
        except Exception as error:result.update(error=type(error).__name__,reason=str(error)[:300])
        if out is not None:(out/(brand['brandId']+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(one,brands))
    if out is not None:(out/'summary.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    return results


def main():
    results=discover()
    print(json.dumps(dict(brands=len(results),accessible=sum('httpStatus' in r for r in results),withNutritionLinks=sum(bool(r['links']) for r in results)),ensure_ascii=False))


if __name__=='__main__':main()
