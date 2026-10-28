# 접수양식 DOCX 의 02 항목 1~7 셀을 plan_content 로 다시 채운다. 캡처는 word/media 에 넣고 inline 그림으로.
import re, os, sys, zipfile, shutil, html
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__)); import plan_content as C

SRC = '/home/user/ssok/docs/submission/쏙_접수양식_기획서.docx'
CAP = '/home/user/ssok/docs/submission/captures/'
OUT = next((a for a in sys.argv[1:] if not a.startswith('--')), SRC)
TMP = '/tmp/claude-0/-home-user/601bb229-5eb8-5611-8750-9eef53f25fdd/scratchpad/docx/build'
shutil.rmtree(TMP, ignore_errors=True); os.makedirs(TMP)
with zipfile.ZipFile(SRC) as z: z.extractall(TMP)
doc = open(TMP + '/word/document.xml', encoding='utf-8').read()
rels_p = TMP + '/word/_rels/document.xml.rels'; rels = open(rels_p, encoding='utf-8').read()

def esc(t): return html.escape(t, quote=False)
LANG = '<w:lang w:eastAsia="ko-KR"/>'
def runs(text, extra=''):
    out = []; parts = re.split(r'(\*\*[^*]+\*\*)', text)
    for p in parts:
        if not p: continue
        if p.startswith('**') and p.endswith('**'): out.append(f'<w:r><w:rPr><w:b/><w:bCs/>{extra}{LANG}</w:rPr><w:t xml:space="preserve">{esc(p[2:-2])}</w:t></w:r>')
        else: out.append(f'<w:r><w:rPr>{extra}{LANG}</w:rPr><w:t xml:space="preserve">{esc(p)}</w:t></w:r>')
    return ''.join(out)
def P_h(t): return f'<w:p><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="80" w:line="300" w:lineRule="exact"/></w:pPr>{runs("**" + t + "**")}</w:p>'
def P_p(t): return f'<w:p><w:pPr><w:spacing w:before="0" w:after="100" w:line="300" w:lineRule="exact"/></w:pPr>{runs(t)}</w:p>'
def P_b(t): return f'<w:p><w:pPr><w:spacing w:before="0" w:after="100" w:line="300" w:lineRule="exact"/><w:ind w:left="300" w:hanging="220"/></w:pPr>{runs("• " + t)}</w:p>'
def P_small(t): return f'<w:p><w:pPr><w:spacing w:before="0" w:after="40" w:line="260" w:lineRule="exact"/><w:ind w:left="300" w:hanging="300"/></w:pPr>{runs(t, "<w:color w:val=\"555555\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/>")}</w:p>'
def P_empty(): return '<w:p><w:pPr><w:spacing w:before="0" w:after="60" w:line="200" w:lineRule="exact"/></w:pPr></w:p>'

img_n = [0]
def P_img(key, cap):
    img_n[0] += 1; n = img_n[0]; src = CAP + C.IMG[key][0]; name = f'cap{n}.jpg'; shutil.copy(src, TMP + '/word/media/' + name)
    rid = f'rIdCap{n}'
    global rels; rels = rels.replace('</Relationships>', f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{name}"/></Relationships>')
    w, h = Image.open(src).size; cx = 5300000 if key != 'kona' else 3400000; cy = round(cx * h / w); did = 900 + n
    drawing = (f'<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
               f'<wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{did}" name="{name}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="{did}" name="{name}"/><pic:cNvPicPr/></pic:nvPicPr>'
               f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>')
    return (f'<w:p><w:pPr><w:keepNext/><w:spacing w:before="120" w:after="0"/><w:jc w:val="center"/></w:pPr>{drawing}</w:p>'
            f'<w:p><w:pPr><w:spacing w:before="40" w:after="140"/><w:jc w:val="center"/></w:pPr>{runs(cap, "<w:color w:val=\"666666\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/>")}</w:p>')

def TBL(head, rows, total=9600):
    n = len(head); first = 1700 if n > 2 else 2600; rest = (total - first) // (n - 1)
    widths = [first] + [rest] * (n - 1)
    def cell(t, w, hdr):
        shade = '<w:shd w:val="clear" w:color="auto" w:fill="EEF1F5"/>' if hdr else ''
        rp = '<w:b/><w:bCs/><w:sz w:val="17"/><w:szCs w:val="17"/>' if hdr else '<w:sz w:val="17"/><w:szCs w:val="17"/>'
        return (f'<w:tc><w:tcPr><w:tcW w:w="{w}" w:type="dxa"/>{shade}<w:tcMar><w:top w:w="50" w:type="dxa"/><w:left w:w="80" w:type="dxa"/><w:bottom w:w="50" w:type="dxa"/><w:right w:w="80" w:type="dxa"/></w:tcMar></w:tcPr>'
                f'<w:p><w:pPr><w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="exact"/></w:pPr>{runs(t, rp)}</w:p></w:tc>')
    b = '<w:tblBorders>' + ''.join(f'<w:{k} w:val="single" w:sz="4" w:space="0" w:color="C3C9D2"/>' for k in ['top', 'left', 'bottom', 'right', 'insideH', 'insideV']) + '</w:tblBorders>'
    grid = ''.join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    tr = lambda cells, hdr: '<w:tr><w:trPr><w:cantSplit/></w:trPr>' + ''.join(cell(c, w, hdr) for c, w in zip(cells, widths)) + '</w:tr>'
    return (f'<w:tbl><w:tblPr><w:tblW w:w="{total}" w:type="dxa"/>{b}<w:tblLayout w:type="fixed"/><w:tblLook w:val="04A0"/></w:tblPr><w:tblGrid>{grid}</w:tblGrid>'
            + tr(head, True) + ''.join(tr(r, False) for r in rows) + '</w:tbl>' + P_empty())

def render(blocks):
    out = []
    for b in blocks:
        k = b[0]
        if k == 'h': out.append(P_h(b[1]))
        elif k == 'p': out.append(P_p(b[1]))
        elif k == 'b': out.append(P_b(b[1]))
        elif k == 'small': out.append(P_small(b[1]))
        elif k == 'img': out.append(P_img(b[1], b[2]))
        elif k == 'tbl': out.append(TBL(b[1], b[2]))
    return ''.join(out)

# 섹션 머리 → 다음 표 첫 셀 내용 교체
def plain(xml): return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', '', xml)).strip()
HEAD = {1: '앱 한 줄 소개', 2: '기획 배경', 3: '앱 주요 기능', 4: '주요 사용 시나리오', 5: '개발 계획', 6: '기대효과', 7: '참고자료'}
def replace_section(doc, n, body):
    pat = re.compile(r'<w:p\b[^>]*>.*?</w:p>', re.S); start = None
    for m in pat.finditer(doc):
        t = plain(m.group(0))
        if re.match(r'^%d\s*\.\s*' % n, t) and HEAD[n] in t: start = m.end(); break
    assert start, n
    tbl = doc.index('<w:tbl>', start); tc = doc.index('</w:tcPr>', tbl) + len('</w:tcPr>'); end = doc.index('</w:tc>', tc)
    head = doc[tbl:tc].replace('<w:cantSplit/>', '')   # 긴 셀이 다음 쪽으로 통째로 밀리지 않게(원본 4·5번 행에 cantSplit)
    out = doc[:tbl] + head + body + doc[end:]
    # 원본 2번 표의 둘째 행(코나 사진)은 지운다(새 글이 사진을 포함)
    m = re.search(r'<w:tr\b(?:(?!<w:tr\b).)*?r:embed="rIdKona".*?</w:tr>', out, re.S)   # 사진은 같은 표의 둘째 행이었다
    if m: out = out[:m.start()] + out[m.end():]
    return out

def fix_keepnext(doc):
    # 항목 표 첫 셀의 첫 문단에서 keepNext 를 뺀다(머리 문단의 keepNext 는 둔다 — 머리와 칸이 붙어 있어야 하므로).
    # 첫 문단까지 keepNext 면 LibreOffice·Word 가 머리 + 표 첫 줄 묶음을 한 덩어리로 보고 긴 표를 통째로 다음 쪽으로 밀어
    # 앞 쪽이 비고 머리와 칸이 떨어졌다(2026-10-28 PM 지적, 17쪽 → 15쪽). 실측: 머리 keepNext 까지 빼면 머리가 쪽 끝에 홀로 남는다.
    pat = re.compile(r'<w:p\b[^>]*>.*?</w:p>', re.S); out = doc; pos = 0
    for n in HEAD:
        for m in pat.finditer(out, pos):
            t = plain(m.group(0))
            if re.match(r'^%d\s*\.\s*' % n, t) and HEAD[n] in t:
                tbl = out.index('<w:tbl>', m.end()); tc = out.index('</w:tcPr>', tbl)
                fp = pat.search(out, tc); first = fp.group(0).replace('<w:keepNext/>', ''); out = out[:fp.start()] + first + out[fp.end():]
                pos = fp.start(); break
    return out

if '--fix-only' in sys.argv:   # 이미 만든 파일에 keepNext 정리만 적용
    doc = fix_keepnext(doc); open(TMP + '/word/document.xml', 'w', encoding='utf-8').write(doc)
    out = [a for a in sys.argv[1:] if not a.startswith('--')]; OUT = out[0] if out else SRC
    if os.path.exists(OUT): os.remove(OUT)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(TMP + '/[Content_Types].xml', '[Content_Types].xml')
        for root, _, files in os.walk(TMP):
            for f in files:
                full = os.path.join(root, f); arc = os.path.relpath(full, TMP)
                if arc != '[Content_Types].xml': z.write(full, arc)
    print('fix-only', OUT); sys.exit(0)

doc = replace_section(doc, 1, P_p(C.S1))
doc = replace_section(doc, 2, render(C.S2))
doc = replace_section(doc, 3, render(C.S3))
doc = replace_section(doc, 4, render(C.S4))
doc = replace_section(doc, 5, render(C.S5))
doc = replace_section(doc, 6, render(C.S6))
src7 = P_p(C.S7_LINKS) + P_h('출처') + ''.join(P_small(f'[{i + 1}] {s}') for i, s in enumerate(C.SOURCES))
doc = replace_section(doc, 7, src7)
doc = fix_keepnext(doc)
# 옛 코나 그림 관계(rIdKona)는 이제 안 쓰이면 그대로 두어도 무해
open(TMP + '/word/document.xml', 'w', encoding='utf-8').write(doc); open(rels_p, 'w', encoding='utf-8').write(rels)
if os.path.exists(OUT): os.remove(OUT)
with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(TMP + '/[Content_Types].xml', '[Content_Types].xml')
    for root, _, files in os.walk(TMP):
        for f in files:
            full = os.path.join(root, f); arc = os.path.relpath(full, TMP)
            if arc == '[Content_Types].xml': continue
            z.write(full, arc)
print('docx', OUT, os.path.getsize(OUT), 'images', img_n[0])
