import sys, subprocess
VREW, V2, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
OFF=31.75
S=[('vrew',0.0,3.0),('vrew',4.6,9.5),('vrew',10.8,15.5),('vrew',16.6,21.9),('vrew',22.4,27.9),('vrew',28.3,31.0),
   ('v2',33.2,39.8),('v2',41.4,45.9),('v2',46.7,50.4),('vrew',51.6,54.4),('vrew',55.6,60.9),('vrew',61.35,63.9),('vrew',64.45,69.0)]
fc=[]; vl=[]; al=[]; t=0; marks={}
for i,(src,a,b) in enumerate(S):
    if src=='vrew': fc.append(f"[0:v]trim={a}:{b},setpts=PTS-STARTPTS,scale=1080:1920:flags=lanczos,fps=30,format=yuv420p[v{i}]")
    else: fc.append(f"[1:v]trim={a-OFF}:{b-OFF},setpts=PTS-STARTPTS,fps=30,format=yuv420p[v{i}]")
    fc.append(f"[0:a]atrim={a}:{b},asetpts=PTS-STARTPTS[a{i}]")
    vl.append(f"[v{i}]"); al.append(f"[a{i}]"); marks[i]=t; t+=b-a
body=t
def out(vt,seg): return marks[seg]+(vt-S[seg][1])
subs=[('sub1.png',out(36.8,6),out(39.5,6)),('sub2.png',out(41.8,7),out(45.8,7)),('sub3.png',out(46.9,8),out(49.5,8))]
fc.append(''.join(vl)+f"concat=n={len(S)}:v=1:a=0[vb]")
fc.append(''.join(al)+f"concat=n={len(S)}:v=0:a=1[ab]")
fc.append("[2:v]format=rgba,fade=t=in:st=0.3:d=0.4:alpha=1,fade=t=out:st=2.3:d=0.4:alpha=1[ti]")
fc.append("[vb][ti]overlay=0:0:enable='between(t,0,2.9)'[vt]")
prev='vt'
for k,(f,a,b) in enumerate(subs):
    fc.append(f"[{3+k}:v]format=rgba[s{k}]"); fc.append(f"[{prev}][s{k}]overlay=0:0:enable='between(t,{a:.2f},{b:.2f})'[vs{k}]"); prev=f'vs{k}'
durs=[0.45,0.45,0.6,0.9,1.3]
for k,dd in enumerate(durs): fc.append(f"[{6+k}:v]loop=loop={int(dd*30)}:size=1:start=0,trim=duration={dd},setpts=PTS-STARTPTS,fps=30,format=yuv420p[c{k}]")
fc.append(''.join(f'[c{k}]' for k in range(5))+"concat=n=5:v=1:a=0[close]")
fc.append(f"[{prev}][close]concat=n=2:v=1:a=0[vall]")
close=sum(durs); total=body+close
# 효과음 '쏙': 차가 칸에 멈추는 순간(11번 끝 0.6초 전) + 클로징 '쏙.' 글자
sfx1=out(60.3,10); sfx2=body+durs[0]+durs[1]
fc.append(f"[ab]apad=whole_dur={total:.2f}[dlg0]")
fc.append(f"[12:a]asplit=2[x1][x2]")
fc.append(f"[x1]adelay={int(sfx1*1000)}|{int(sfx1*1000)},apad=whole_dur={total:.2f},volume=0.9[fx1]")
fc.append(f"[x2]adelay={int(sfx2*1000)}|{int(sfx2*1000)},apad=whole_dur={total:.2f},volume=0.9[fx2]")
fc.append("[dlg0][fx1][fx2]amix=inputs=3:duration=first:normalize=0[dlg]")
fc.append(f"[11:a]atrim=0:{total:.2f},asetpts=PTS-STARTPTS,afade=t=in:st=0:d=1.0,afade=t=out:st={total-1.6:.2f}:d=1.6,volume=0.55[mus0]")
fc.append("[dlg]asplit=2[dlg1][key]")
fc.append("[mus0][key]sidechaincompress=threshold=0.015:ratio=8:attack=15:release=500:makeup=1[musd]")
fc.append("[dlg1][musd]amix=inputs=2:duration=first:dropout_transition=0:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11[aall]")
cmd=['ffmpeg','-v','error','-y','-i',VREW,'-i',V2,'-loop','1','-t','3','-i','title.png']
for f in ['sub1.png','sub2.png','sub3.png']: cmd+=['-loop','1','-t','5','-i',f]
for k in range(1,6): cmd+=['-i',f'card{k}.png']
cmd+=['-i','music.mp3','-i','sfx_ssok.m4a','-filter_complex',';\n'.join(fc),'-map','[vall]','-map','[aall]','-c:v','libx264','-crf','18','-preset','medium','-c:a','aac','-b:a','192k','-movflags','+faststart',OUT]
r=subprocess.run(cmd,capture_output=True,text=True); print('rc',r.returncode,'body',round(body,2),'total',round(total,2),'sfx',round(sfx1,2),round(sfx2,2)); print(r.stderr[-800:])
