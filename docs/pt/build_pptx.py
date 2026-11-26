import json, re, copy, sys
from pptx import Presentation
from pptx.util import Emu
from lxml import etree
S = sys.argv[1]; OUT = sys.argv[2]
html = open('/home/user/ssok/site/pt/index.html', encoding='utf-8').read()
notes = re.findall(r'<section class="s[^"]*"[^>]*data-note="([^"]*)"', html)
meta = json.load(open(f'{S}/meta.json'))
prs = Presentation(); prs.slide_width = Emu(12192000); prs.slide_height = Emu(6858000)
W, H = prs.slide_width, prs.slide_height
blank = prs.slide_layouts[6]
NS = 'http://schemas.openxmlformats.org/presentationml/2006/main'
def timing_xml(spid, dur, picid):
    return f'''<p:timing xmlns:p="{NS}"><p:tnLst><p:par><p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot"><p:childTnLst>
<p:seq concurrent="1" nextAc="seek"><p:cTn id="2" dur="indefinite" nodeType="mainSeq"><p:childTnLst>
<p:par><p:cTn id="3" fill="hold"><p:stCondLst><p:cond delay="indefinite"/><p:cond evt="onBegin" delay="0"><p:tn val="2"/></p:cond></p:stCondLst><p:childTnLst>
<p:par><p:cTn id="4" fill="hold"><p:stCondLst><p:cond delay="0"/></p:stCondLst><p:childTnLst>
<p:par><p:cTn id="5" presetID="1" presetClass="mediacall" presetSubtype="0" fill="hold" nodeType="afterEffect"><p:stCondLst><p:cond delay="0"/></p:stCondLst><p:childTnLst>
<p:cmd type="call" cmd="playFrom(0.0)"><p:cBhvr><p:cTn id="6" dur="{dur}" fill="hold"/><p:tgtEl><p:spTgt spid="{spid}"/></p:tgtEl></p:cBhvr></p:cmd>
</p:childTnLst></p:cTn></p:par>
<p:par><p:cTn id="8" presetID="1" presetClass="entr" presetSubtype="0" fill="hold" nodeType="withEffect"><p:stCondLst><p:cond delay="{dur}"/></p:stCondLst><p:childTnLst>
<p:set><p:cBhvr><p:cTn id="9" dur="1" fill="hold"><p:stCondLst><p:cond delay="0"/></p:stCondLst></p:cTn><p:tgtEl><p:spTgt spid="{picid}"/></p:tgtEl><p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst></p:cBhvr><p:to><p:strVal val="visible"/></p:to></p:set>
</p:childTnLst></p:cTn></p:par>
</p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn>
<p:prevCondLst><p:cond evt="onPrev" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst>
<p:nextCondLst><p:cond evt="onNext" delay="0"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst></p:seq>
<p:video><p:cMediaNode vol="0" mute="1"><p:cTn id="7" fill="hold"><p:stCondLst><p:cond delay="indefinite"/></p:stCondLst></p:cTn><p:tgtEl><p:spTgt spid="{spid}"/></p:tgtEl></p:cMediaNode></p:video>
</p:childTnLst></p:cTn></p:par></p:tnLst></p:timing>'''
def fade(slide):
    # 부드러운 페이드 전환(0.5초)
    t = etree.SubElement(slide._element, f'{{{NS}}}transition'); t.set('spd', 'med')
    etree.SubElement(t, f'{{{NS}}}fade')
    # transition 은 timing 앞에 와야 한다
    tm = slide._element.find(f'{{{NS}}}timing')
    if tm is not None: tm.addprevious(t)
def note(slide, text):
    slide.notes_slide.notes_text_frame.text = text
for m in meta:
    sl = prs.slides.add_slide(blank)
    sl.shapes.add_picture(f"{S}/clip/{m['id']}_p0.jpg", 0, 0, W, H)   # 바닥: 애니메이션 첫 장면
    mv = sl.shapes.add_movie(f"{S}/clip/{m['id']}.mp4", 0, 0, W, H, poster_frame_image=f"{S}/clip/{m['id']}_p0.jpg", mime_type='video/mp4')
    fin = sl.shapes.add_picture(f"{S}/png/{m['id']}.jpg", 0, 0, W, H)   # 맨 위: 완성 장면. 편집·인쇄·미리보기에선 늘 보이고, 슬라이드쇼에선 영상이 끝나는 순간 나타난다
    old = sl._element.find(f'{{{NS}}}timing')
    if old is not None: sl._element.remove(old)
    dur = 4600 if m['n'] == 1 else 3900
    sl._element.append(etree.fromstring(timing_xml(mv.shape_id, dur, fin.shape_id)))
    fade(sl)
    note(sl, notes[m['n'] - 1] if m['n'] - 1 < len(notes) else '')
    if m['n'] == 12:   # 시연 장 다음: 시연 영상(누르면 재생)
        d = prs.slides.add_slide(blank)
        bg = d.shapes.add_shape(1, 0, 0, W, H); bg.fill.solid(); from pptx.dml.color import RGBColor; bg.fill.fore_color.rgb = RGBColor(10, 11, 13); bg.line.fill.background()
        d.shapes.add_movie('/home/user/ssok/site/pt/a/demo.mp4', int(W*0.08), int(H*0.08), int(W*0.84), int(H*0.84), poster_frame_image=f'{S}/demo_poster.jpg', mime_type='video/mp4')
        fade(d); note(d, '시연 영상(화면 녹화, 약 2분). 현장 시연은 노트북 에뮬레이터로 따로 진행합니다. 누르면 재생.')
prs.save(OUT); print('saved', len(prs.slides._sldIdLst))
