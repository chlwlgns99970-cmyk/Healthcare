"""Lossless ingredient spans; parenthesized equivalents belong to their ingredient."""
import re, html
NUMBER = r'(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)'
QUANTITY = re.compile(r'(?P<value>'+NUMBER+r')\s*\(?(?P<unit>kg|g|mL|ml|ML|L|l|큰술|작은술|컵|개|쪽|장|봉|뿌리|마리|모|대|줌|통|톨|알)\)?(?![A-Za-z])')

def number(value):
    value=value.strip()
    if ' ' in value:
        whole,fraction=value.split();return float(whole)+number(fraction)
    if '/' in value:
        a,b=value.split('/');return float(a)/float(b)
    return float(value)

def split_top(text):
    depth=0;start=0
    for pos,char in enumerate(text):
        if char in '([':depth+=1
        elif char in ')]':depth=max(0,depth-1)
        elif char in ',;\n' and depth==0:
            yield text[start:pos];start=pos+1
    yield text[start:]

def parse(text):
    text=html.unescape(text)
    text=text.replace('½','1/2').replace('⅓','1/3').replace('⅔','2/3').replace('¼','1/4')
    text=re.sub(r'^.*?재료량\s*\(?\s*\d+인분\)?\s*[-:]?','',text)
    text=re.sub(r'^재료량\s*[-:]\s*','',text)
    text=re.sub(r'<[^>]+>|\[[^\]]+(?:양념|소스|조림장)[^\]]*\]|\[(?:양념|양념장|초고추장|조림장)\]|\([^)]*(?:양념|소스)[^)]*\)|(?:양념장|양념|간장소스|소스)\s*:',',',text)
    rows=[]
    for part in split_top(text):
        part=part.strip(' :-')
        if not part:continue
        if part in ('약간','적량','적당량') and rows:
            rows[-1]['originalSpan']+=', '+part
            continue
        # A source span may list multiple weighed ingredients without commas.
        starts=[m.start() for m in re.finditer(r'(?<=[)g])\s+(?=[가-힣][^,\d]{0,20}\d)',part)]
        spans=[];offset=0
        for end in starts:spans.append(part[offset:end]);offset=end
        spans.append(part[offset:])
        for span in spans:
            span=span.strip();match=QUANTITY.search(span)
            interval=re.search(NUMBER+r'\s*[~～]\s*'+NUMBER,span)
            if match:
                name=span[:match.start()].strip(' :-')
                name=re.sub(r'\s*\d+토막\($','',name)
                quantity=number(match['value']);unit=match['unit']
                equivalents=[dict(value=number(m['value']),unit=m['unit']) for m in QUANTITY.finditer(span,match.end())]
                if re.search(r'\d\($',name):
                    name=re.split(r'\d',name)[0].strip();quantity=None
                if interval:
                    name=span[:interval.start()].strip(' :-');quantity=None
            else:
                name=re.split(r'\s*(?:약간|적량|적당량|조금|필요량)|\d',span,maxsplit=1)[0].strip()
                quantity=None;unit=None;equivalents=[]
            rows.append(dict(originalSpan=span,ingredient=name,quantity=quantity,unit=unit,equivalents=equivalents,quantityRange=interval.group(0) if interval else None))
    return rows
