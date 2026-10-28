from PIL import Image, ImageDraw, ImageFont, ImageFilter
W,H=1080,1920; B='fonts/NotoSansKR-Bold.ttf'; K='fonts/NotoSansKR-Black.ttf'
ACC=(0,190,235); 
bg=Image.open('thumb/c3.png').convert('RGB').resize((W,H),Image.LANCZOS)
im=bg.convert('RGBA')
# 위·아래 어둡게(글자 읽힘)
ov=Image.new('RGBA',(W,H),(0,0,0,0)); od=ImageDraw.Draw(ov)
for y in range(0,560): od.line([(0,y),(W,y)],fill=(0,0,0,int(170*(1-y/560))))
for y in range(1180,H): od.line([(0,y),(W,y)],fill=(0,0,0,255 if y>=1600 else int(235*(y-1180)/420)))
im=Image.alpha_composite(im,ov); d=ImageDraw.Draw(im)
def tc(y,txt,font,fill,stroke=0,x=W/2):
    bb=d.textbbox((0,0),txt,font=font,stroke_width=stroke); w=bb[2]-bb[0]; h=bb[3]-bb[1]
    d.text((x-w/2-bb[0],y-h/2-bb[1]),txt,font=font,fill=fill,stroke_width=stroke,stroke_fill=(0,0,0))
    return w
# 위 글자
tc(170,"장롱면허 10년",ImageFont.truetype(K,104),(255,255,255),10)
# 둘째 줄: '주차가 한 번에 ' + '쏙!'(강조)
f2=ImageFont.truetype(K,112); a="주차가 한 번에 "; b="쏙!"
wa=d.textlength(a,font=f2); wb=d.textlength(b,font=ImageFont.truetype(K,132))
x0=W/2-(wa+wb)/2; y2=330
d.text((x0,y2),a,font=f2,fill=(255,255,255),stroke_width=11,stroke_fill=(0,0,0),anchor='lm')
d.text((x0+wa,y2-6),b,font=ImageFont.truetype(K,132),fill=ACC,stroke_width=11,stroke_fill=(0,0,0),anchor='lm')
# 아래 앱 화면 카드
app=Image.open('../disp/연습_핸들540_디스플레이비율.png').convert('RGB')
cw=900; ch=int(app.height*cw/app.width); app=app.resize((cw,ch),Image.LANCZOS)
mask=Image.new('L',(cw,ch),0); ImageDraw.Draw(mask).rounded_rectangle((0,0,cw,ch),radius=28,fill=255)
cx=(W-cw)//2; cy=1390
sh=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle((cx-6,cy-6,cx+cw+6,cy+ch+6),radius=34,fill=(255,255,255,255))
im=Image.alpha_composite(im,sh); im.paste(app,(cx,cy),mask); d=ImageDraw.Draw(im)
tc(1325,"세워 둔 내 차에서, 내 핸들로 연습",ImageFont.truetype(B,46),(255,255,255),6)
# 맨 아래 로고 줄
logo=Image.open('logo.png').convert('RGBA'); logo=logo.crop(logo.getbbox()).resize((92,92),Image.LANCZOS)
f3=ImageFont.truetype(K,62); txt="쏙 SSOK"; tw=d.textlength(txt,font=f3)
lx=int(W/2-(92+20+tw)/2); ly=1830
im.paste(logo,(lx,ly-46),logo); d=ImageDraw.Draw(im)
d.text((lx+112,ly),txt,font=f3,fill=(255,255,255),anchor='lm')
im.convert('RGB').save('thumb/쏙_쇼츠_썸네일.jpg',quality=92)
im.convert('RGB').resize((360,640)).save('thumb/preview.jpg',quality=85)
print('ok', cy+ch)
