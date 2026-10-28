from PIL import Image, ImageDraw, ImageFont
W,H=1080,1920; B='fonts/NotoSansKR-Bold.ttf'; K='fonts/NotoSansKR-Black.ttf'
ACC=(0,170,210,255); INK=(23,25,29,255); GREY=(110,116,128,255)
def center(d,y,txt,font,fill,punct=''):
    # 문장부호(, .)는 폭 계산에서 빼고 글자 몸통만 가운데에 둔 뒤 부호를 뒤에 매단다(시각 중심)
    bb=d.textbbox((0,0),txt,font=font); w=bb[2]-bb[0]; h=bb[3]-bb[1]
    x=W/2-w/2-bb[0]; yy=y-h/2-bb[1]
    d.text((x,yy),txt,font=font,fill=fill)
    if punct:
        adv=d.textlength(txt,font=font); d.text((x+adv,yy),punct,font=font,fill=fill)
DY=-250
logo=Image.open('logo.png').convert('RGBA'); logo=logo.crop(logo.getbbox()).resize((300,300),Image.LANCZOS)
words=[("주차",","),("한 번에",","),("쏙",".")]; ys=[700,860,1040]
for n in range(1,6):
    im=Image.new('RGBA',(W,H),(255,255,255,255)); d=ImageDraw.Draw(im)
    for i in range(min(n,3)):
        f=ImageFont.truetype(K,150 if i==2 else 120); col=ACC if i==2 else INK
        center(d,ys[i]+DY,words[i][0],f,col,words[i][1])
    if n>=4:
        im.paste(logo,(W//2-150,1180+DY),logo)
        center(d,1560+DY,"쏙 SSOK",ImageFont.truetype(K,72),INK)
        center(d,1640+DY,"Pleos Connect 주차 연습 앱",ImageFont.truetype(B,40),GREY)
    if n>=5:
        center(d,1780+DY,"#HMG차량용앱개발공모전  #PleosAppMarket  #HMG앱공모전",ImageFont.truetype(B,30),GREY)
    im.save(f"card{n}.png")
print('ok')
