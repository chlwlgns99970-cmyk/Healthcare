"""Food-by-food residual publication, retaining every previously shipped row."""
import collections,csv,hashlib,json,re
from pathlib import Path
from build_recipe_calorie_references import FIELDS
from maximize_recipe_evidence import additional_nutrients
from relink_recipe_strategy import bulk_index,legacy_index
from recipe_composition_validation import composition_problem
from recipe_context_identity import supported_title_keys
from maximize_recipe_evidence import context_for

ROOT=Path(__file__).resolve().parents[1]
PRIOR=ROOT/'data-source/recipe-full-reference'
OUT=ROOT/'data-source/recipe-final-residual'
ASSETS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(name,x):(OUT/name).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def read(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def norm(s):
    for a,b in [('쇠고기','소고기'),('떡만둣국','떡만두국'),('만둣국','만두국'),('야채','채소'),('계란','달걀'),('마늘종','마늘쫑')]:s=s.replace(a,b)
    return re.sub(r'\s+','',s)
def keys(s):
    result={norm(s)}
    parts=s.split('_')
    # K-FIND labels make the qualifier explicit; reorder only a single qualifier.
    if len(parts)==2 and '(' not in parts[0]:result.add(norm(parts[1]+parts[0]))
    # Some K-FIND labels repeat the base ingredient as their first qualifier,
    # e.g. 감자볶음_감자_베이컨. Collapse only that exact repetition while
    # retaining every other qualifier and the cooking method.
    if len(parts)==3 and all(re.fullmatch(r'[가-힣]+',p) for p in parts):
        for method in ('볶음','조림','무침','구이','찜','전','국','밥'):
            if parts[0]==parts[1]+method:
                result.add(norm(parts[1]+parts[2]+method))
    if s=='닭볶음(닭갈비)':result.add('닭갈비')
    for a,b in [('닭고기찜','닭찜'),('닭고기조림','닭조림'),('닭고기볶음','닭볶음')]:
        if norm(s)==a:result.add(b)
    qualified=re.fullmatch(r'([^()]+)\(([^(),]+)\)',s)
    if qualified:result.add(norm(qualified[2]+qualified[1]))
    return result
FOOD_ALIASES={
    '돼지두루치기':('돼지고기볶음_채소','MFDS-317 원문은 돼지고기·양배추·양파·당근을 고추장 양념으로 팬에서 함께 볶는 조리법. MENUZEN-D102007 돼지고기볶음(고추장, 야채)의 같은 핵심 재료·조리형태 전체 독립 구성만 연결. 소고기·국물 전골·간장 불고기 제외; 원본량과 참고량 혼합하지 않음'),
    '가지쇠고기볶음':('소고기볶음_채소','MFDS-135 가지·소고기를 팬에 볶는 조리법과 MENUZEN-D102019 소고기볶음(가지) 같은 전체 배합. 무브랜드 소고기 채소 볶음에 가지 참고명 보존; 돼지고기·가지나물 제외'),
    '두부김치국':('김치국_두부','MFDS-257 김치·두부 국과 KDCA-281-11299 김치국, 두부 같은 핵심 재료·국. 공공 조사 평균으로만 표시; 순두부찌개·돼지고기 두부김치 제외'),
    '모시조개된장국':('된장국_해물','MFDS-147 모시조개·된장 국에 MENUZEN-D052158 모시조개시금치된장국 독립 전체 배합. 공식 확인한 모시조개=가무락조개 종 유지; 바지락 된장국에 연결하지 않으며 시금치 포함 참고명 보존'),
    '피망두부완자전':('완자전_돼지고기','MFDS-197 원문 돼지고기·두부를 치대 완자를 빚어 팬에 지지는 조리법. MENUZEN-D092006 돼지고기·두부 완자전의 독립 전체 구성; 피망 포함 원본량을 역대입하지 않으며 소고기 완자전 제외'),
    '두릅쇠고기완자전':('완자전_소고기','MFDS-207 원문 소고기·두부를 치대 밀가루·달걀옷으로 지지는 완자전. MENUZEN-D092007 소고기·두부 완자전의 독립 전체 구성; 두릅 포함 원본량과 혼합하지 않고 돼지고기 제외'),
    '취나물볶음':('취나물_간장_소금','MFDS-329 취를 기름 두른 팬에서 양념하여 볶는 숙채. MENUZEN-D103051 취나물볶음 같은 조리형태·간장 구성; 말린 취나물이나 된장 변형에 연결하지 않음. 데친 재료의 공개량 그대로 보존'),
    '감자미역국':('미역국','MFDS-293 감자·미역 국과 MENUZEN-D051043 미역국(감자) 전체 배합. 감자 포함 참고명 및 삶은 재료 상태 보존; 소고기·바지락 변형에 연결하지 않음'),
    '미역조갯국':('조개 미역국','MFDS-160 종을 한정하지 않은 조갯살·미역 국. MENUZEN-D051287 모시조개미역국 전체 독립 구성의 종 명칭 유지. 국립수산과학원 20250714210014102VCP 및 국립국어원 2002119에서 모시조개=가무락조개 동의종 확인; 원본 종을 모시조개로 확정하지 않음'),
    '모시조개콩나물국':('콩나물국','MFDS-262 모시조개·콩나물 국과 MENUZEN-D051309 동일 음식 전체 배합. 모시조개=가무락조개 동의종은 국립수산과학원 20250714210014102VCP 및 국립국어원 2002119 확인. 바지락 국에 연결하지 않고 참고명 보존'),
    '딸기바나나연두부쉐이크':('딸기바나나 스무디','MFDS-146 딸기100g·바나나50g·연두부50g·올리고당10g를 믹서로 갈아 혼합하는 과일 음료. K-FIND 무브랜드 딸기바나나 스무디의 같은 딸기·바나나 블렌딩 음식 계열; 주스·가공 제조사 제품 제외. 연두부 포함 원본 참고명과 영양 그대로 보존'),
    '배오이무침':('오이무침','MFDS-217 생 오이·배를 채 썰어 소금·고춧가루·파·마늘로 무치는 생채. K-FIND 오이무침 생채·무침류의 배 포함 변형; 절임 오이지 제외. 원본 배오이무침 명칭·배 재료량 보존'),
    '송이버섯구이':('버섯구이','MFDS-345 송이버섯280g을 썰어 콩기름4g으로 팬에 굽는 조리법. K-FIND 종을 한정하지 않는 버섯구이 구이류에 원본 송이버섯 참고명 보존; 새송이·표고로 대체하지 않음'),
    '바지락무국':('바지락국','MFDS-248 바지락·무 국과 MENUZEN-D051192 같은 명칭 전체 배합. 무브랜드 바지락 국에 무 포함 참고명 보존'),
    '냉이바지락국':('냉이 된장국','MFDS-187 냉이·바지락·된장·고추장 국과 MENUZEN-D052010 같은 핵심 구성; 바지락·고추장 변형 참고명 유지'),
    '냉이무침':('냉이나물','MFDS-259 냉이를 데쳐 고추장으로 무치는 숙채와 MENUZEN-D132109 냉이나물 전체 배합; 생 오이무침과 구분'),
    '버섯육개장':('육개장','MFDS-227 소고기·표고·느타리·고사리의 육개장과 MENUZEN-D053103 같은 주재료·국 조리형태. 버섯찌개와 구분'),
    '고사리육개장':('육개장','MFDS-299 소고기·고사리 육개장 국과 MENUZEN-D053027 같은 소고기·고사리 육개장 전체 배합; 오리 육개장 제외'),
    '새우살미역국':('미역국_새우','MFDS-316 생 새우살·미역 국과 MENUZEN-D051256 같은 전체 배합. 건새우 미역국에 대입하지 않음'),
    '다시마무국':('무국','MFDS-330 다시마·무 국과 MENUZEN-D051175 같은 명칭 전체 배합. 소고기 무국 제외; 다시마 포함 참고명 보존'),
    '닭가슴살채소조림':('닭조림','MFDS-191 닭가슴살·채소 양념 조림과 MENUZEN-D112031 같은 닭가슴살 채소조림 전체 배합. 닭구이와 구분'),
    '미나리유부초밥':('초밥_유부초밥','MFDS-310 초밥 양념 밥을 유부 속에 채우는 조리법과 MENUZEN-D016010 유부·초밥 전체 독립 구성. 시금치 변형명·재료를 유지하며 원본 미나리량에 대입하지 않음'),
    '쇠고기불고기(너비아니) <방법1>':('소불고기','RDA-90835 원문 등심·배·간장 팬 불고기와 MENUZEN-D102016 소고기·배 불고기 전체 배합. 돼지고기 제외'),
    '달래불고기':('소불고기','MFDS-104 등심 양념 불고기와 MENUZEN-D102015 소고기 불고기 전체 독립 배합. 달래는 원본에 보존하고 참고에 추가 합성하지 않음'),
    '우엉불고기':('돼지불고기_간장','MFDS-309 원문 돼지고기·간장 불고기. MENUZEN-D102012 돼지 간장 불고기의 독립 전체 배합. 소고기·우엉조림 후보 제외'),
    '주꾸미불고기':('주꾸미볶음_채소','MFDS-286 원문 주꾸미·채소·고추장 팬 볶음과 KDCA-281-3710 같은 음식의 조사평균. 소불고기·구이와 구분'),
    '감자채튀김':('감자튀김','MFDS-219 원문 감자280g·콩기름40g 및 채 썬 감자를 바삭하게 튀기는 조리법. 무브랜드 K-FIND 감자튀김 튀김류의 절단 형태 변형; 원본 구성명 감자채튀김 보존'),
    '봄동겉절이':('배추겉절이','MFDS-312 조리법이 배추 겉잎·절인 배추를 명시. 농사로 cntntsNo205161은 봄동을 결구하지 않은 배추로 설명하고 겉절이 활용 명시. 원본 봄동 nutrition과 명칭 보존'),
    '풋고추튀김':('고추튀김','MFDS-176 풋고추 밀가루 옷 튀김과 MENUZEN-D123002 풋고추·밀가루·기름의 고추튀김 전체 배합. 고기소 채운 고추전과 구분'),
    '참나물무침':('참나물','MFDS-336 참나물 데친 나물 무침. K-FIND 참나물 나물·숙채류에 명명된 깨즙무침 참고를 별도로 표시; 생 겉절이·된장 변형에 연결하지 않음'),
    '참나물두부무침':('참나물','MFDS-337 참나물·두부를 무치는 숙채와 MENUZEN-D132116 참나물·두부·참깨 전체 배합. 두부 포함 깨즙무침 참고명 보존'),
    '쇠고기토란탕':('토란국','MFDS-285 소고기·토란 중심 국과 MENUZEN-D053062 같은 소고기·토란 탕 전체 배합. 들깨 포함 참고의 고유 배합 보존'),
    '피망야채전':('채소전','MFDS-303 피망·깻잎·당근을 밀가루와 부치는 채소전. MENUZEN-D093014 피망·깻잎·밀가루의 독립 전체 배합만 사용'),
    '서리태밥':('잡곡밥_서리태','MFDS-326 서리태 쌀밥과 MENUZEN-D012101 서리태·쌀·흑미 밥. 서리태 qualifier 보존; 검정콩의 임의 품종을 원본 nutrition에 대입하지 않음'),
    '돌솥밥':('영양돌솥밥','MFDS-349 재료 목록이 쌀·찹쌀·대추·밤·은행·표고버섯으로 MENUZEN-D014009 영양돌솥밥과 같은 핵심 구성. 불일치한 원본 단호박 조리문은 참고에 합성하지 않음'),
    '김칫국(김치콩나물국)':('김치국_콩나물','RDA-90747 괄호 동의명 김치콩나물국과 김치·콩나물·두부 국 원문. MENUZEN-D051019 같은 전체 배합; 찌개 제외'),
    '버섯칼국수':('칼국수','MFDS-232 느타리·표고·칼국수 국과 MENUZEN-D031115 버섯칼국수 독립 전체 배합. 같은 버섯·밀면 국 형태로 참고의 소고기 및 버섯 종 명칭 보존. 바지락·닭고기 한정 음식 제외'),
    '낙지채소볶음':('낙지볶음','MFDS-234 생 낙지·채소·고추장 팬 볶음과 MENUZEN-D101006 동일 낙지 고추장 볶음. 낙지전골 제외'),
    '건새우아욱국':('아욱 된장국_건새우','MFDS-275 아욱·건새우·된장 국과 MENUZEN-D052124 마른새우아욱국 같은 핵심 재료·국 조리형태; 건새우 qualifier 보존'),
    '도라지오이무침':('도라지생채_오이','MFDS-297 오이·도라지·고추장 생채 무침과 MENUZEN-D132096 동일 도라지오이무침; 북어·오징어채 variant 제외'),
    '마늘쫑새우볶음':('마늘쫑볶음_건새우','MFDS-324 원문 건새우30g·마늘쫑130g 볶음과 KDCA-281-12175 마늘쫑볶음, 건새우 동일 재료·볶음; 멸치 variant 제외'),
    '콩나물겨자채':('콩나물냉채','MFDS-205 콩나물·오이·해파리·겨자 냉채. 전체 콩나물 겨자 무침 참고의 변형명 보존; 원본 해파리를 참고 배합에 삽입하지 않음'),
    '콩나물겨자냉채':('콩나물냉채','MFDS-346 콩나물·오이·연겨자·식초의 냉채. 콩나물 겨자 무침 별도 전체 참고명 보존; 오이 포함 food variant에 오연결하지 않음'),
    '메추리알조림':('메추리알장조림','MFDS-141 메추리알·진간장·설탕 조림과 MENUZEN-D112009 삶은 메추리알 간장 조림의 동일 핵심 재료·조림류'),
    '숙주나물무침':('숙주나물','MFDS-258 숙주·파·마늘·소금 무침과 MENUZEN-D132020 데친 숙주 나물 무침의 동일 숙채류'),
    '배추들깨국':('배추국_들깨','MFDS-307 배추·들깨·멸치·다시마 국과 MENUZEN-D051194 동일 명칭 전체 배합; 들깨 qualifier 보존'),
    '취나물된장무침':('취나물_된장','MFDS-320 취나물·된장 무침과 MENUZEN-D132034 동일 된장 취나물 숙채; 간장 variant 제외'),
    '두부양념조림':('두부조림','MFDS-328 두부·돼지고기·간장·고춧가루 조림과 MENUZEN-D114004 두부조림(돼지고기) 전체 별도 배합; 돼지고기 변형명 보존'),
    '북어맑은국':('북어국_무','MFDS-314 북어채·무·멸치·다시마의 국. 무 qualifier 일치; 콩나물 북어국 제외'),
    '오징어무국':('오징어국','MFDS-225 오징어·무·고춧가루 국과 MENUZEN-D051131 동일 오징어·무 국; 갑오징어에 역대입하지 않음'),
    '꼬치어묵국':('어묵국','MFDS-284 어묵·무·멸치·다시마의 국과 MENUZEN-D051173 꼬치어묵국 전체 배합; 어묵 볶음 제외'),
    '달걀야채오믈렛':('오믈렛','MFDS-228 달걀·우유·채소를 부치는 오믈렛과 MENUZEN-D095013 동일 달걀 오믈렛 조리형태; 두부 오믈렛 제외'),
    '고구마줄기볶음':('고구마줄기나물','MFDS-271 고구마줄기·마늘·기름 볶음과 MENUZEN-D103009 고구마줄기볶음의 동일 고구마줄기 숙채; 참고 볶음명 보존'),
    '밀가루수제비(수제비, 밀가루자베기)':('수제비','RDA-89945 원본 제목이 수제비 동의명을 명시; 밀가루 수제비 국 조리형태'),
    '아귀탕(아구탕, 물꽁탕)':('아구탕','RDA-91426 원본 제목의 명시 동의명 아구탕. 아귀찜과 구분'),
    '바람떡(개피떡)':('개피떡(바람떡)','RDA-90925 원본 제목의 명시 동의명 순서 교환; 쌀떡 일반값을 재료 배합으로 대체하지 않음'),
    '노각생채':('노각무침','노각 생채의 양념 무침: MFDS-183 원문 전체 구성 및 조리 문맥'),
    '열무된장무침':('열무나물_된장','MFDS-278 열무를 삶아 된장으로 무치는 조리 문맥; 간장 나물은 제외'),
    '오징어야채볶음':('오징어볶음_채소','오징어와 양배추·양파·당근의 고추장 볶음; 건오징어채 제외'),
    '가리탕(갈비탕)':('갈비탕','원본 제목이 갈비탕 동의명을 명시'),
    '감자부침(감자전)':('감자전','원본 제목이 감자전 동의명을 명시'),
    '곰국(곰탕)':('곰탕','원본 제목이 곰탕 동의명을 명시'),
    '뼈다귀감자탕(감자탕)':('감자탕','원본 제목이 감자탕 동의명을 명시'),
    '빈대떡':('녹두빈대떡','원본 녹두 재료 및 녹두전 조리 문맥; 보리빈대떡 제외'),
    '갈치조림':('갈치조림_무','선택한 MENUZEN-D111002 전체 구성에 생갈치70g·무40g 명시; 건갈치 제외'),
    '순두부찌개':('순두부찌개_해물','MENUZEN-D065021 바지락50g·순두부100g 구성. 돼지고기 variant 제외'),
    '호박볶음':('애호박볶음','MENUZEN-D103055가 애호박을 명시하는 전체 참고 구성; 늙은 호박에 역대입하지 않음'),
}
REVIEWED_REFERENCES={
    '감자베이컨볶음':('KDCA-281-6394','감자·베이컨을 함께 볶는 동일 음식의 질병관리청 조사 평균 전체 구성. K-FIND 감자볶음_감자_베이컨은 같은 주재료·볶음이며 햄 변형 제외. 조리용 레시피로 표시하지 않고 원본량을 변경하지 않음.'),
    '양상추샐러드':('DAEGU-LOW-SODIUM-P40','대구시 조리책 p40 전체 10인분 정량. 양상추 중심 생채소와 소스를 버무리는 동일 샐러드; 마요네즈 소스 참고 변형명 보존.'),
    '두릅산적':('MENUZEN-D093044','MFDS-186 두릅·소고기를 꼬치에 끼워 밀가루·달걀옷을 입히고 지지는 적. MENUZEN 두릅적의 독립 전체 정량 구성. 참고가 지정한 땅두릅 재료명을 그대로 보존하며 원본 두릅 종을 추정하지 않음'),
    '도라지양념구이':('MENUZEN-D083004','MFDS-238 도라지를 애벌구이하고 고추장 양념을 발라 굽는 조리법과 같은 도라지 고추장 구이 전체 구성. 원본 파 identity를 참고 대파로 해결했다고 주장하지 않음'),
    '돼지두루치기':('MENUZEN-D102007','MFDS-317 팬 돼지고기·채소·고추장 볶음과 같은 독립 전체 배합. 참고의 등심·데친 채소 상태와 고추장 변형명을 보존하며 원본 생재료량을 변경하지 않음'),
    '가지쇠고기볶음':('MENUZEN-D102019','가지·소고기 볶음 같은 전체 독립 구성; 원본 생량과 공개 익힌량 합성 금지'),
    '두부김치국':('KDCA-281-11299','김치·두부 국의 공공 조사 평균 구성. 원본 누락 양념의 해결로 주장하지 않음'),
    '모시조개된장국':('MENUZEN-D052158','모시조개·된장 국의 시금치 포함 전체 독립 구성; 종 동의어는 공식 수산자료 확인'),
    '피망두부완자전':('MENUZEN-D092006','돼지고기·두부를 빚어 지지는 완자전 독립 전체 배합. 피망·달걀 원본 구성으로 주장하지 않음'),
    '두릅쇠고기완자전':('MENUZEN-D092007','소고기·두부 완자전 독립 전체 배합. 원본 두릅 추가량과 합성하지 않음'),
    '취나물볶음':('MENUZEN-D103051','같은 취나물 볶음의 공개 데친 참취·간장 배합 그대로 유지; 원본 생 취량의 조리환산 금지'),
    '감자미역국':('MENUZEN-D051043','같은 감자 미역국 전체 배합; 원본 생중량을 공개 삶은 중량으로 바꾸지 않음'),
    '미역조갯국':('MENUZEN-D051287','모시조개·미역 국의 독립 전체 구성. 원본 조갯살 종 추정 없이 참고의 실제 동의종 가무락 유지'),
    '모시조개콩나물국':('MENUZEN-D051309','같은 모시조개·콩나물 국 전체 구성; 공식 동의종 검증으로 가무락 유지'),
    '버섯칼국수':('MENUZEN-D031115','같은 버섯 칼국수 국의 전체 독립 배합; 소고기 포함 원문 구성 보존'),
    '바지락무국':('MENUZEN-D051192','동일 바지락·무 국 전체 참고; 원본 국량과 합성하지 않음'),
    '냉이바지락국':('MENUZEN-D052010','같은 냉이·바지락·된장·고추장 국의 두부 포함 독립 전체 참고'),
    '냉이무침':('MENUZEN-D132109','냉이·고추장 나물 무침 전체 참고. 쌈장·식초를 원본 조리법에 역대입하지 않음'),
    '버섯육개장':('MENUZEN-D053103','동일 소고기·버섯 육개장 전체 참고'),
    '고사리육개장':('MENUZEN-D053027','동일 소고기·고사리 육개장 전체 참고'),
    '새우살미역국':('MENUZEN-D051256','생 새우살·미역 국 전체 참고; 건새우 변형 제외'),
    '다시마무국':('MENUZEN-D051175','같은 다시마·무 국 전체 배합'),
    '닭가슴살채소조림':('MENUZEN-D112031','닭 가슴 부위·채소·양념 조림의 같은 전체 배합'),
    '미나리유부초밥':('MENUZEN-D016010','같은 유부 초밥의 시금치 포함 전체 독립 배합. 원본 미나리 배합으로 주장하지 않음'),
    '쇠고기불고기(너비아니) <방법1>':('MENUZEN-D102016','등심·배·간장의 팬 불고기 전체 참고; 원본 후춧가루 약간은 별도 미해결 유지'),
    '달래불고기':('MENUZEN-D102015','소고기 불고기 전체 참고. 원본 달래 추가량과 섞지 않음'),
    '우엉불고기':('MENUZEN-D102012','돼지고기·간장의 불고기 전체 참고. 우엉 소고기 조림을 잘못 연결하지 않음'),
    '주꾸미불고기':('KDCA-281-3710','주꾸미 채소 팬 볶음의 독립 전체 조사평균; 조리용 레시피 표시 금지'),
    '풋고추튀김':('MENUZEN-D123002','풋고추·밀가루 옷·콩기름의 튀김 전체 배합, 고기소 없는 같은 음식'),
    '참나물무침':('MENUZEN-D132116','참나물 나물의 두부·깨즙 포함 명명 변형 전체 참고; 원본 양념량과 혼합하지 않음'),
    '참나물두부무침':('MENUZEN-D132116','참나물·두부 중심 무침 전체 배합. 깨즙 변형명과 별도 참고임을 유지'),
    '쇠고기토란탕':('MENUZEN-D053062','소고기·토란 중심 탕의 들깨 포함 전체 참고. 원본 무·국간장량에 역대입하지 않음'),
    '피망야채전':('MENUZEN-D093014','피망·깻잎·밀가루 채소전 전체 참고, 참고에 명시된 익힌 재료 nutrition을 그대로 유지'),
    '서리태밥':('MENUZEN-D012101','서리태·쌀밥의 흑미 포함 명명 변형 전체 배합. 원본은 그대로 미해결 유지'),
    '돌솥밥':('MENUZEN-D014009','쌀·찹쌀·대추·밤·은행·표고버섯 영양돌솥밥 전체 참고'),
    '버섯칼국수':('MENUZEN-D031095','칼국수·표고·느타리 등 버섯 중심 같은 면 국. 들깨 포함 고유 참고명 유지'),
    '낙지채소볶음':('MENUZEN-D101006','낙지·채소·고추장 볶음의 전체 참고 배합. 같은 낙지·볶음이고 전골 제외'),
    '아귀탕(아구탕, 물꽁탕)':('MENUZEN-D061040','원문 아귀·콩나물·무·고춧가루 국과 같은 아구매운탕 전체 배합. 고추장 포함 고유 참고명 유지; 찜 제외'),
    '건새우아욱국':('MENUZEN-D052124','아욱·된장·마른 새우 중심 국 전체 배합; 건새우 qualifier 보존'),
    '도라지오이무침':('MENUZEN-D132096','같은 도라지·오이 고추장 생채 전체 배합. 참고의 감식초는 원본 없는 식초량에 역대입하지 않음'),
    '마늘쫑새우볶음':('KDCA-281-12175','마늘쫑볶음, 건새우의 전체 조사평균; 건새우 qualifier와 조사용 표시 보존'),
    '콩나물겨자채':('MENUZEN-D132037','콩나물무침(겨자, 미나리)의 데친 콩나물 겨자 냉채 전체 참고 배합. 미나리 변형명 유지; 원본 해파리 nutrition을 대체하지 않음'),
    '콩나물겨자냉채':('MENUZEN-D132037','콩나물무침(겨자, 미나리)의 데친 콩나물 겨자 냉채 전체 배합. 미나리 변형명 유지; 오이 양을 역대입하지 않음'),
    '취나물된장무침':('MENUZEN-D132034','취나물무침(된장)의 정확한 된장 취나물 숙채 전체 구성; qualifier 보존'),
    '두부김치':('KDCA-281-3306','두부·김치·돼지고기를 중심으로 한 두부김치, 돼지고기의 전체 조사 평균. 찌개와 구분하며 조사 평균 표시 유지'),
    '주꾸미볶음':('KDCA-281-3710','주꾸미볶음, 채소의 같은 주꾸미·채소 볶음 전체 조사평균. 먹물 자체 영양의 원본 누락에 역대입하지 않음'),
    '달래오이무침':('KDCA-281-7626','오이생채, 달래의 생 오이·달래 무침 전체 조사평균. 오이 개량종 그대로의 공개nutrition 항목을 사용; 다다기 품종을 추정하지 않음'),
    '두부양념조림':('MENUZEN-D114004','두부 중심 돼지고기 간장 조림 전체 독립 배합, 돼지고기 qualifier 보존'),
    '북어맑은국':('KDCA-281-8494','북어국, 무 조사평균의 전체 구성; 무 포함 명칭·조사용 표시 보존'),
    '꼬치어묵국':('MENUZEN-D051173','같은 꼬치어묵국 제목·어묵 국 조리형태; 전체 배합 사용'),
    '파래무침':('KDCA-281-13104','생 파래 중심 무침의 별도 전체 조사 평균 구성. 무 포함 변형명 및 조리용 레시피 아님 표시 유지; 원본 오이·파래 상태에 역대입하지 않음'),
    '감자샐러드':('MENUZEN-D135066','감자·마요네즈 샐러드의 전체 배합. 마늘칩 변형명을 유지하며 준비된 감자샐러드 1행 구성과 구분'),
    '떡갈비':('MENUZEN-D082037','소고기 중심 다진 고기 구이의 전체 구성. 표고버섯 포함 버섯떡갈비 변형명 유지; 오리나 다른 조리법 제외'),
    '국밥':('MENUZEN-D015004','원본 등심·배추·토란대·쌀밥과 같은 소고기 국밥 조리형태. 참고 배합의 양지 부위는 원본 등심에 역대입하지 않음'),
    '감자국':('MENUZEN-D051168','감자·무·육수의 맑은 감자국 전체 배합. 원본과 같은 국 조리형태; 맑은국 명칭 보존'),
    '애호박볶음':('MENUZEN-D103055','공식 호박볶음의 명시된 주재료는 애호박; 삶은 애호박·볶음 구성 전체 사용'),
    '호박볶음':('MENUZEN-D103055','애호박 주재료를 명시하는 볶음 참고 구성만 애호박볶음에 연결'),
    '된장찌개':('MENUZEN-D063007','된장·두부·감자·애호박 찌개의 전체 명명 변형; 고추장 포함 변형명 유지'),
    '곰탕':('MENUZEN-D053003','원본 곰국(곰탕)의 명시적 동의명; 소고기 양지·마늘·소금의 별도 전체 구성'),
    '콩나물김치국':('MENUZEN-D051019','김치·콩나물·두부의 김칫국(콩나물) 전체 배합; 두 조리법에서 같은 김치 콩나물 국'),
    '김칫국(김치콩나물국)':('MENUZEN-D051019','원본 괄호명과 동일 김치·콩나물 국의 어순 명칭 변형'),
    '머위나물무침':('MENUZEN-D132009','데친 머위 나물의 무침 구성. 고추장 포함 명명 변형을 그대로 표시'),
    '미나리무침':('MENUZEN-D132015','데친 미나리 초고추장 나물의 독립 무침 구성; 원본 생/익힘을 확정하는 근거로 쓰지 않음'),
    '표고버섯볶음':('MENUZEN-D103036','표고버섯60g 중심 볶음 전체 구성; 소고기20g 포함 변형명을 그대로 표시'),
    '버섯잡채':('MENUZEN-D105008','표고버섯·당면의 잡채 전체 구성; 소고기 포함 명명 변형 유지'),
    '어죽':('MENUZEN-D040043','쏘가리·쌀죽·소면의 민물고기 죽 전체 구성; 인삼어죽 이름과 추가 인삼 유지'),
    '어죽<방법1>':('MENUZEN-D040043','원본 민물고기·쌀·밀가루 죽과 동일 조리형태의 인삼어죽 전체 별도 구성'),
    '어죽<방법2>':('MENUZEN-D040043','민물고기 쌀죽의 인삼어죽 명명 변형 전체 별도 구성'),
}
BLOCKED_BASE={'곤달비밥(곤드레밥, 곤드레나물밥)','낙지볶음(낙지전골)'}
def original_keys(name):
    result={norm(name)}
    if name not in BLOCKED_BASE:
        # Original title names its own method/region; only the leading same dish
        # family is used to select an independently labelled whole reference.
        result.add(norm(re.split(r'\(|<|\[',name)[0]))
    if name in FOOD_ALIASES:result.add(norm(FOOD_ALIASES[name][0]))
    return result

def run():
    OUT.mkdir(exist_ok=True)
    before=load(OUT/'baseline-recipe-final-states.json')
    originals=load(PRIOR/'recipe-final-audit.json')['audit']
    originalById={r['recipeId']:r for r in originals}
    contextualKeys={}
    for original in originals:
        if '(' not in original['name']:continue
        raw=ROOT/'app/build/food-quality-qa/recipe-source'/(original['recipeId'].lower().replace('rda-diet-','nongsaro-diet-')+'.html')
        if raw.exists():
            assert hashlib.sha256(raw.read_bytes()).hexdigest()==original['ingredients'][0]['sourceSha256']
            contextualKeys[original['name']]=set().union(*(keys(k) for k in supported_title_keys(original['name'],context_for(original,raw))))
    refs=load(PRIOR/'reference-composition-audit.json')
    new_reference_path=ROOT/'data-source/recipe-residual-89/new-validated-reference-compositions.json'
    if new_reference_path.exists():
        additions=load(new_reference_path)
        assert not {r['recipeId'] for r in refs} & {r['recipeId'] for r in additions}
        refs.extend(additions)
    for ref in refs:
        problem=None
        if ref['recipeId'] in ('MENUZEN-D015074','MENUZEN-D051212'):
            problem='NAMED_PRIMARY_INGREDIENT_ABSENT_FROM_DECLARED_COMPOSITION'
        if ref['recipeId'] in ('MENUZEN-D135038','MENUZEN-D082045'):
            problem='TITLE_PRIMARY_SPECIES_DIFFERS_FROM_DECLARED_INGREDIENT'
        if ref['recipeId'] in ('MENUZEN-D230002','MENUZEN-D230003'):
            problem='PREPARED_RICE_CAKE_WITHOUT_INGREDIENT_DECOMPOSITION'
        if composition_problem(ref)=='SELF_COMPOSITE_WITHOUT_INGREDIENT_DECOMPOSITION':
            problem='SELF_COMPOSITE_WITHOUT_INGREDIENT_DECOMPOSITION'
        if '된장찌개' in ref['name'] and not any('된장' in x['ingredient'] for x in ref['inputs']):
            problem='NAMED_PRIMARY_INGREDIENT_ABSENT_FROM_DECLARED_COMPOSITION'
        if problem:ref.update(complete=False,compositionValidationProblem=problem)
    save('validated-reference-compositions.json',refs)
    nutrients={n['code']:n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    nutrients.update(additional_nutrients());nutrients.update(bulk_index());nutrients.update(legacy_index())
    approved_path=ROOT/'data-source/recipe-residual-93/approved-new-food-evidence.json'
    approved=load(approved_path) if approved_path.exists() else []
    approved_ids={r['foodId'] for r in approved}
    approved_targets={r['name']:{r['foodId']} for r in approved}
    foods=[f for f in read(ASSETS/'food_items.csv') if not f['brand'] and (f['sourceType']=='K-FIND' or f['id'] in approved_ids)]
    rows=read(OUT/'baseline-official_recipe_reference_estimates.csv')
    primary=read(OUT/'baseline-recipe_ingredient_estimates.csv')
    existing={(r['foodId'],r['recipeId']) for r in rows+primary}
    byFood=collections.defaultdict(set)
    for f in foods:
        for k in keys(f['name']):byFood[k].add(f['id'])
    foodById={f['id']:f for f in foods}
    originalAdded=[];mappings=[];queue=[];states=[]
    def targets(name):return sorted(approved_targets.get(name,set()) | set().union(*(byFood[k] for k in original_keys(name)|contextualKeys.get(name,set()))))
    def publish(ref,targetIds):
        inputs=[x for x in ref['inputs'] if x.get('nutrient') and x['amountGrams']>0]
        combined={}
        for x in inputs:
            n=x['nutrient'];entry=combined.setdefault(n['code'],[x['ingredient'],0.0,n]);entry[1]+=x['amountGrams']
        text=ref.get('ingredientText') or ', '.join(f"{x['ingredient']} {x['amountGrams']:g}g" for x in ref['inputs'])
        for fid in targetIds:
            if (fid,ref['recipeId']) in existing:continue
            f=foodById[fid]
            for code,(name,grams,n) in combined.items():
                rows.append(dict(foodId=fid,recipeId=ref['recipeId'],recipeName=ref['name'],recipeBasis=ref['basis'],
                    ingredientText=text,ingredientName=name,amountGrams=f'{grams:.15g}',nutrientFoodId='official-reference-'+code,
                    nutrientName=n['name'],kcalPer100g=f"{n['energyKcal']:g}",recipeUrl=ref['sourceUrl'],
                    nutrientUrl=n['sourceUrl'],recipeSha256=ref['sourceSha256'],checkedAt='2026-10-05',
                    recipeComplete=str(ref['complete']).lower(),foodReferenceKcal=f['energyKcal'],
                    foodReferenceAmount=f['referenceAmount'],foodReferenceUnit=f['unit'],
                    compositionKind=ref['compositionKind'],sourceInstitution=ref['sourceInstitution']))
            existing.add((fid,ref['recipeId']))
    for old in before:
        s=dict(old)
        old_reference=next((r for r in refs if r['recipeId']==s.get('referenceId')),None)
        if old_reference and not old_reference['complete']:
            s['referenceComplete']=False
        if old['appCompleteAvailable']:states.append(s);continue
        original=originalById[s['recipeId']]
        targetIds=targets(s['name'])
        s['targetFoodIds']=targetIds
        candidateKeys=original_keys(s['name'])|contextualKeys.get(s['name'],set())
        candidates=[r for r in refs if keys(r['name']) & candidateKeys or
            r['compositionKind']=='REFERENCE_RECIPE' and keys(r['name'].split('(',1)[0]) & candidateKeys]
        reviewed=REVIEWED_REFERENCES.get(s['name'])
        if reviewed:
            reviewedRef=next(r for r in refs if r['recipeId']==reviewed[0])
            candidates=[reviewedRef]+[r for r in candidates if r['recipeId']!=reviewed[0]]
        candidates.sort(key=lambda r:(not r['complete'],norm(r['name']) not in candidateKeys,
            r['compositionKind']=='SURVEY_AVERAGE',-sum(bool(x.get('nutrient')) for x in r['inputs']),r['recipeId']))
        selected=next((r for r in candidates if r['complete']),None)
        if reviewed and reviewedRef['complete']:selected=reviewedRef
        if original['complete'] and targetIds:
            inputs=[dict(ingredient=d['ingredient'],amountGrams=d['amountGrams'],nutrient=nutrients[d['nutrientId']])
                for d in original['ingredients'] if d['status']=='LINKED']
            ref=dict(recipeId=original['recipeId'],name=original['name'],basis='원본 공식 레시피 전체 재료량 · 원본 완전 연결',
                ingredientText=original['ingredientText'],sourceUrl=original['sourceUrl'],sourceSha256=original['ingredients'][0]['sourceSha256'],
                inputs=inputs,complete=True,compositionKind='ORIGINAL',sourceInstitution='식품의약품안전처')
            publish(ref,targetIds);originalAdded.append(ref)
            s.update(appCompleteAvailable=True,targetFoodIds=targetIds)
            mappings.append(dict(originalRecipeId=s['recipeId'],originalName=s['name'],selectedId=ref['recipeId'],
                selectedName=ref['name'],foodIds=targetIds,method='REVIEWED_SAME_DISH_COOKING_CONTEXT',
                reason=FOOD_ALIASES.get(s['name'],('', '동일 음식명·단일 명시 qualifier 어순 및 표기 동의어'))[1],sourceUrl=ref['sourceUrl']))
        elif selected and targetIds:
            publish(selected,targetIds)
            s.update(state='REFERENCE_COMPLETE',referenceId=selected['recipeId'],referenceComplete=True,
                referencePublished=True,appCompleteAvailable=True,targetFoodIds=targetIds)
            mappings.append(dict(originalRecipeId=s['recipeId'],originalName=s['name'],selectedId=selected['recipeId'],
                selectedName=selected['name'],foodIds=targetIds,method='WHOLE_NAMED_SAME_DISH_REFERENCE',
                reason=reviewed[1] if reviewed and selected['recipeId']==reviewed[0] else '동일 명칭·명시 qualifier·조리형태의 독립 전체 구성. 변형명 보존; 원본 누락량에 역대입하지 않음',sourceUrl=selected['sourceUrl'],sourceSha256=selected['sourceSha256']))
        elif selected and reviewed:
            # A verified whole composition is separately complete even when the
            # app has no same-dish Food. Never invent an ID or mark it available.
            s.update(state='REFERENCE_COMPLETE',referenceId=selected['recipeId'],
                referenceComplete=True,referencePublished=False,appCompleteAvailable=False)
        queue.append(dict(recipeId=s['recipeId'],name=s['name'],beforeState=old['state'],afterState=s['state'],
            completeInApp=s['appCompleteAvailable'],targetFoodIds=targetIds,
            candidates=[dict(recipeId=r['recipeId'],name=r['name'],complete=r['complete'],sourceUrl=r['sourceUrl']) for r in candidates],
            originalIngredientIssues=[dict(ingredient=d['ingredient'],span=d['originalSpan'],status=d['status'],
                identityStatus=d['identityStatus'],unitStatus=d['unitStatus']) for d in original['ingredients']
                if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')],
            sourceUrl=original['sourceUrl'],reason='PUBLISHED' if s['appCompleteAvailable'] else
                'NO_VERIFIED_SAME_DISH_FOOD_ID' if not targetIds else 'NO_COMPLETE_WHOLE_REFERENCE'))
        states.append(s)
    # Direct grams printed by the same original recipe take precedence over
    # cross-recipe household-size conflicts. Publish its whole known subset,
    # retaining incomplete status; do not invent the other missing inputs.
    direct=load(OUT/'direct-original-mass-decisions.json') if (OUT/'direct-original-mass-decisions.json').exists() else []
    for rid in sorted({d['recipeId'] for d in direct}):
        original=originalById[rid]
        changes={d['ingredientIndex']:d for d in direct if d['recipeId']==rid}
        ds=[changes.get(d['ingredientIndex'],d) for d in original['ingredients']]
        inputs=[dict(ingredient=d['ingredient'],amountGrams=d['amountGrams'],nutrient=nutrients[d['nutrientId']]) for d in ds if d['status']=='LINKED']
        ref=dict(recipeId=rid,name=original['name'],basis='원본 공식 레시피에서 확인 가능한 재료 기준',ingredientText=original['ingredientText'],sourceUrl=original['sourceUrl'],sourceSha256=ds[0]['sourceSha256'],inputs=inputs,complete=all(d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in ds),compositionKind='ORIGINAL',sourceInstitution='농촌진흥청')
        ids=targets(original['name'])
        publish(ref,ids)
        if ids:originalAdded.append(ref)
    assert len(queue)==254 and len(states)==516
    for mapping in mappings:
        original=originalById[mapping['originalRecipeId']]
        mapping.update(originalSourceUrl=original['sourceUrl'],
            originalSourceSha256=original['ingredients'][0]['sourceSha256'],
            contextSupportedTitleKeys=sorted(contextualKeys.get(original['name'],set())),
            matchingConfidence='REVIEWED_SAME_DISH',checkedAt='2026-10-05')
    assert len({(r['foodId'],r['recipeId'],r['ingredientName']) for r in rows})==len(rows)
    with (ASSETS/'official_recipe_reference_estimates.csv').open('w',encoding='utf-8',newline='') as stream:
        w=csv.DictWriter(stream,fieldnames=FIELDS+['compositionKind','sourceInstitution']);w.writeheader();w.writerows(rows)
    save('food-mapping-decisions.json',mappings);save('additional-original-compositions.json',originalAdded)
    save('food-work-queue.json',queue);save('recipe-final-states.json',states)
    baselinePublished={r['recipeId'] for r in load(OUT/'baseline-reference-publication-audit.json') if r['foodIds']}
    currentPublished={r['recipeId'] for r in rows}
    completeFoodIds={r['foodId'] for r in rows+primary if r['recipeComplete']=='true'}
    unpublished=[]
    validatedById={r['recipeId']:r for r in refs}
    for baselineRef in load(OUT/'baseline-reference-composition-audit.json'):
        ref=validatedById.get(baselineRef['recipeId'],baselineRef)
        if ref['recipeId'] in baselinePublished:continue
        same=[r for r in rows if norm(r['recipeName'])==norm(ref['name']) and r['recipeComplete']=='true']
        targetIds=targets(ref['name'])
        status='B_PUBLISHED' if ref['recipeId'] in currentPublished else 'D_INCOMPLETE_REFERENCE' if not ref['complete'] else \
            'A_DUPLICATE_UNNECESSARY' if same or targetIds and all(fid in completeFoodIds for fid in targetIds) else 'C_COMPLETE_NO_VERIFIED_FOOD_ID'
        unpublished.append(dict(recipeId=ref['recipeId'],name=ref['name'],classification=status,complete=ref['complete'],
            candidateFoodIds=targetIds,sourceUrl=ref['sourceUrl']))
    assert len(unpublished)==246
    save('unpublished-reference-dispositions.json',unpublished)
    save('progress.json',dict(beforeAppComplete=262,appComplete=sum(s['appCompleteAvailable'] for s in states),
        states=dict(collections.Counter(s['state'] for s in states)),originalCompleteNoApp=sum(s['originalComplete'] and not s['appCompleteAvailable'] for s in states),
        newCompleteFoods=sum(q['completeInApp'] for q in queue),unpublishedDispositions=dict(collections.Counter(r['classification'] for r in unpublished)),
        assetRows=len(rows),assetSha256=hashlib.sha256((ASSETS/'official_recipe_reference_estimates.csv').read_bytes()).hexdigest()))
    print(json.dumps(load(OUT/'progress.json'),ensure_ascii=False))

if __name__=='__main__':run()
