from PIL import Image, ImageDraw, ImageFont, ImageEnhance
W,H=1080,1920; K='fonts/NotoSansKR-Black.ttf'
YEL=(255,214,0); CY=(0,200,245); RED=(235,40,40)
def crop(fn,y0):
    im=Image.open(fn).convert('RGB'); c=im.crop((0,y0,720,y0+640)).resize((1080,960),Image.LANCZOS)
    return c
top=crop('thumb/w3.png',200); bot=crop('thumb/w6.png',300)
top=ImageEnhance.Color(top).enhance(0.55); top=ImageEnhance.Brightness(top).enhance(0.8)   # 걱정 = 칙칙하게
bot=ImageEnhance.Color(bot).enhance(1.25); bot=ImageEnhance.Brightness(bot).enhance(1.08)  # 성공 = 밝게
im=Image.new('RGB',(W,H)); im.paste(top,(0,0)); im.paste(bot,(0,960))
d=ImageDraw.Draw(im)
d.rectangle((0,948,W,972),fill=(255,255,255))   # 가운데 흰 띠
def label(y,txt,size,fg,bg,pad=26):
    f=ImageFont.truetype(K,size); bb=d.textbbox((0,0),txt,font=f); w=bb[2]-bb[0]; h=bb[3]-bb[1]
    x=(W-w)/2; d.rectangle((x-pad,y-pad*0.6,x+w+pad,y+h+pad*0.9),fill=bg); d.text((x-bb[0],y-bb[1]),txt,font=f,fill=fg)
label(70,"장롱면허 10년",118,(255,255,255),(0,0,0))
# 아래 큰 문구: '한 번에' + '쏙?!'
f1=ImageFont.truetype(K,150); f2=ImageFont.truetype(K,230)
y=1860
a="한 번에 "; b="쏙?!"
wa=d.textlength(a,font=f1); wb=d.textlength(b,font=f2); x=(W-(wa+wb))/2
d.text((x,y),a,font=f1,fill=(255,255,255),stroke_width=14,stroke_fill=(0,0,0),anchor='ls')
d.text((x+wa,y+18),b,font=f2,fill=YEL,stroke_width=16,stroke_fill=(0,0,0),anchor='ls')
# 빨간 화살표(위 → 아래), 가운데 띠를 가로지름
ax=930
d.line((ax,760,ax,1150),fill=RED,width=34)
d.polygon([(ax-78,1120),(ax+78,1120),(ax,1230)],fill=RED)
# 위 작은 감정 단어
f3=ImageFont.truetype(K,84)
d.text((60,850),"주차 무서워…",font=f3,fill=(255,255,255),stroke_width=10,stroke_fill=(0,0,0),anchor='ls')
im.save('thumb/쏙_쇼츠_썸네일_C.jpg',quality=92); im.resize((360,640)).save('thumb/previewC.jpg',quality=85)
print('ok')
