"""Capture new primary sources and index every queue item without fuzzy publication."""
import hashlib,json,re,urllib.request,concurrent.futures
from pathlib import Path
from lxml import html
from pypdf import PdfReader
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-residual-91'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def compact(s):return re.sub(r'[^가-힣a-zA-Z0-9]','',s)
def run():
 queue=load(OUT/'queue-91.json');rawdir=OUT/'raw';rawdir.mkdir(exist_ok=True)
 urls={
 'https://www.dietitian.or.kr/assets/ver2/food_online/html/main_popup/kda2019recipe2/index_100.html',
 'https://www.diabetes.or.kr/general/dietary/dietary_07.php',
 'https://www.kca.go.kr/webzine/board/view?div=kca_2304&linkId=536&menuId=MENU00324',
 'https://tfs.ourhome.co.kr/menu/recommend/detail/317',
 'https://www.dietitian.or.kr/paper_board_view_contents.do?bbs_idx=3033',
 'https://www.foodnuri.go.kr/portal/bbs/B0000279/view.do?menuNo=300056&nttId=215224&pageIndex=1'}
 urls.update('https://www.dietitian.or.kr/assets/ver2/food_online/html/main_popup/kda2019recipe2/index_'+str(n)+'.html' for n in range(10,100,10))
 for p in list(OUT.glob('route-*.json'))+list(OUT.glob('focused-*.json')):
  result=str(load(p)['result'])
  for u in re.findall(r'\((https?://[^\s)]+)\)',result):
   if any(h in u for h in ('dietitian.or.kr/board/old_down/','mhc.cbe.go.kr/upload/','fsis.go.kr/front/contents/','kca.go.kr/webzine/','semie.cooking/recipe-lab/','goe.go.kr/resource/','rda.go.kr/download_file/','foodnuri.go.kr/portal/bbs/B0000279/','tfs.ourhome.co.kr/menu/recommend/','dietitian.or.kr/paper_board_view_contents')):urls.add(u)
 cache={}
 for p in (ROOT/'data-source').rglob('*index.json'):
  if OUT in p.parents:continue
  try:rs=load(p)
  except (ValueError,OSError):continue
  if isinstance(rs,list):
   for r in rs:
    if isinstance(r,dict) and r.get('rawFile') and r.get('sha256'):cache[r.get('url')]=r
 for p in (ROOT/'data-source').rglob('*captures.json'):
  if OUT in p.parents:continue
  try:rs=load(p)
  except (ValueError,OSError):continue
  if isinstance(rs,list):
   for r in rs:
    if isinstance(r,dict) and r.get('rawFile') and r.get('sha256'):cache[r.get('url')]=r
 if (OUT/'new-document-index.json').exists():
  cache.update({r['url']:r for r in load(OUT/'new-document-index.json') if r.get('status')=='CAPTURED'})
 def fetch(u):
  r=dict(url=u,checkedAt='2026-10-06',status='FAILED',matches=[])
  try:
   old=cache.get(u);p=ROOT/old['rawFile'] if old else None
   if p and p.exists() and hashlib.sha256(p.read_bytes()).hexdigest()==old['sha256']:
    data=p.read_bytes();r['cacheReused']=True
   else:
    with urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'Mozilla/5.0'}),timeout=30) as response:data=response.read()
    p=rawdir/hashlib.sha256(u.encode()).hexdigest()[:20];p.write_bytes(data);r['cacheReused']=False
   r.update(rawFile=p.relative_to(ROOT).as_posix(),sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),status='CAPTURED')
   if data.startswith(b'%PDF') and old and old.get('textFile') and (ROOT/old['textFile']).exists():
    pages=re.split(r'(?:^|\n\n)PAGE \d+\n',(ROOT/old['textFile']).read_text(encoding='utf-8'))[1:]
   elif data.startswith(b'%PDF'):pages=[p.extract_text() or '' for p in PdfReader(p).pages]
   else:
    doc=html.fromstring(data.decode('utf-8'))
    for node in doc.xpath('//script|//style'):node.drop_tree()
    pages=[doc.text_content()+' '+' '.join(doc.xpath('//img/@alt'))]
   textfile=OUT/(p.name+'.txt');textfile.write_text('\n\n'.join('PAGE '+str(n+1)+'\n'+s for n,s in enumerate(pages)),encoding='utf-8')
   r.update(textFile=textfile.relative_to(ROOT).as_posix(),pageCount=len(pages))
   compact_pages=[compact(s) for s in pages]
   for food in queue:
    variants=[v for v in food['searchVariants'] if len(compact(v))>=3]
    hits=[n+1 for n,s in enumerate(compact_pages) if any(compact(v) in s for v in variants)]
    if hits:r['matches'].append(dict(recipeId=food['recipeId'],name=food['name'],pages=hits))
  except Exception as e:r['error']=str(e)
  return r
 records=[]
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  for r in pool.map(fetch,sorted(urls)):
   records.append(r);print(json.dumps({k:r.get(k) for k in ('url','status','cacheReused','pageCount','matches','error')},ensure_ascii=False),flush=True)
   (OUT/'new-document-index.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':run()
