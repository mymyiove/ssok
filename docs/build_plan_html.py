# 기획 페이지(site/plan/index.html) 01~07 + 부록을 plan_content 로 다시 채운다. 페이지 껍데기·facts·steps 는 그대로.
import re, os, sys, html
sys.path.insert(0, os.path.dirname(__file__)); import plan_content as C
P = '/home/user/ssok/site/plan/index.html'; s = open(os.path.join(os.path.dirname(__file__), 'plan_orig.html'), encoding='utf-8').read()   # 늘 원본(동결 전 페이지)에서 만든다

def inline(t):
    t = html.escape(t, quote=False); t = re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', t); return re.sub(r'\[(\d+)\]', r'<sup class="cite">[\1]</sup>', t)
def render(blocks):
    out = []; ul = []
    def flush():
        if ul: out.append('<ul class="pt">' + ''.join(f'<li>{inline(x)}</li>' for x in ul) + '</ul>'); ul.clear()
    for b in blocks:
        k = b[0]
        if k == 'b': ul.append(b[1]); continue
        flush()
        if k == 'h': out.append(f'<h3>{inline(b[1])}</h3>')
        elif k == 'p': out.append(f'<p>{inline(b[1])}</p>')
        elif k == 'small': out.append(f'<p class="src">{inline(b[1])}</p>')
        elif k == 'img': out.append(f'<figure class="photo"><img src="img/{C.IMG[b[1]][1]}" alt="{html.escape(b[2])}" width="1600" height="900"><figcaption>{inline(b[2])}</figcaption></figure>')
        elif k == 'tbl': out.append('<table><thead><tr>' + ''.join(f'<th>{inline(h)}</th>' for h in b[1]) + '</tr></thead><tbody>' + ''.join('<tr>' + ''.join(f'<td>{inline(c)}</td>' for c in r) + '</tr>' for r in b[2]) + '</tbody></table>')
    flush(); return '\n    '.join(out)

def section(sid, body):
    global s
    m = re.search(r'(<section id="%s">\s*<h2>.*?</h2>)(.*?)(</section>)' % sid, s, re.S); assert m, sid
    s = s[:m.start(2)] + '\n    ' + body + '\n  ' + s[m.end(2):]

def block(cls):   # <div class="cls"> 부터 짝이 맞는 </div> 까지
    i = s.index(f'<div class="{cls}">'); j = i; depth = 0
    for m in re.finditer(r'<div\b|</div>', s[i:]):
        depth += 1 if m.group(0).startswith('<div') else -1
        if depth == 0: j = i + m.end(); break
    return s[i:j]
facts = block('facts'); steps = block('steps')
section('s1', f'<div class="pull">{inline(C.S1)}</div>')
s2 = C.S2[:]; i = next(k for k, b in enumerate(s2) if b[0] == 'img'); s2body = render(s2[:i + 1]) + '\n    ' + facts + '\n    ' + render(s2[i + 1:])
section('s2', s2body)
section('s3', steps + '\n    ' + render(C.S3))
section('s4', render(C.S4))
section('s5', render(C.S5))
section('s6', render(C.S6))
src = '<ol class="src cites">' + ''.join(f'<li>{inline(x)}</li>' for x in C.SOURCES) + '</ol>'
section('s7', '<p>' + inline(C.S7_LINKS) + '</p>\n    <h3>출처</h3>\n    ' + src)
qa = '<dl>' + ''.join(f'<dt>{inline(q)}</dt><dd>{inline(a)}</dd>' for q, a in C.QA) + '</dl>'
m = re.search(r'(<section id="qa">\s*<h2>.*?</h2>)(.*?)(</section>)', s, re.S); s = s[:m.start(2)] + '\n    ' + qa + '\n  ' + s[m.end(2):]
if '.cites' not in s: s = s.replace('.src{color:var(--fg3);font-size:13px}', '.src{color:var(--fg3);font-size:13px}.cites{padding-left:22px;line-height:1.6}.cites li{margin:2px 0}sup.cite{font-size:10px;color:var(--fg3)}')
s = s.replace('심사 전까지 검색엔진 비노출 · 개인정보는 01 참가신청서에만.', '심사 전까지 검색엔진 비노출 · 개인정보는 01 참가신청서에만 · 2026-10-28 동결 화면 기준.')
open(P, 'w', encoding='utf-8').write(s); print('plan html ok', len(s))
