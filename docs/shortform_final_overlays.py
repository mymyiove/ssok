from PIL import Image, ImageDraw, ImageFont
W,H=1080,1920
B='fonts/NotoSansKR-Bold.ttf'; K='fonts/NotoSansKR-Black.ttf'
def F(p,s): return ImageFont.truetype(p,s)
def text_center(d,xy,txt,font,fill,stroke=0,sfill=(0,0,0,255)):
    x,y=xy; bb=d.textbbox((0,0),txt,font=font,stroke_width=stroke); w=bb[2]-bb[0]; h=bb[3]-bb[1]
    d.text((x-w/2-bb[0], y-h/2-bb[1]),txt,font=font,fill=fill,stroke_width=stroke,stroke_fill=sfill)
    return w,h
# 1) 나레이션 자막 3장 (Vrew 꼴: 흰 굵은 글자 + 검정 외곽선, 아래쪽)
subs=[("sub1.png",["세워 둔 내 차에서, 내 핸들로."]),("sub2.png",["핸들을 돌리면 화면 속 차가","똑같이 움직여요."]),("sub3.png",["어디서 틀렸는지도 바로 보여요."])]
for fn,lines in subs:
    im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im); f=F(B,58)
    y0=1700-(len(lines)-1)*36
    for i,l in enumerate(lines): text_center(d,(W/2,y0+i*72),l,f,(255,255,255,255),stroke=9)
    im.save(fn)
# 2) 제목(첫 컷 위): 위 작은 브랜드 줄 + 가운데 큰 문구
im=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(im)
text_center(d,(W/2,300),"쏙 SSOK",F(K,54),(255,255,255,255),stroke=8)
text_center(d,(W/2,380),"세워 둔 내 차에서 하는 주차 연습",F(B,40),(255,255,255,255),stroke=7)
text_center(d,(W/2,960),"주차 연습,",F(K,112),(255,255,255,255),stroke=12)
text_center(d,(W/2,1100),"어디서 하세요?",F(K,112),(255,255,255,255),stroke=12)
im.save("title.png")
# 3) 클로징 카드 5장 (흰 바탕)
ACC=(0,170,210,255); INK=(23,25,29,255); GREY=(110,116,128,255)
logo=Image.open('logo.png').convert('RGBA'); logo=logo.crop(logo.getbbox()).resize((300,300),Image.LANCZOS)
def card(n):
    im=Image.new('RGBA',(W,H),(255,255,255,255)); d=ImageDraw.Draw(im)
    words=["주차,","한 번에,","쏙."]; ys=[700,860,1040]
    for i in range(min(n,3)):
        f=F(K,150 if i==2 else 120); col=ACC if i==2 else INK
        text_center(d,(W/2,ys[i]),words[i],f,col)
    if n>=4:
        im.paste(logo,(W//2-150,1180),logo)
        text_center(d,(W/2,1560),"쏙 SSOK",F(K,72),INK)
        text_center(d,(W/2,1640),"Pleos Connect 주차 연습 앱",F(B,40),GREY)
    if n>=5:
        text_center(d,(W/2,1780),"#HMG차량용앱개발공모전  #PleosAppMarket  #HMG앱공모전",F(B,30),GREY)
    im.save(f"card{n}.png")
for n in range(1,6): card(n)
print('overlays ok')
