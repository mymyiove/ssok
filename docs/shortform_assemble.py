import subprocess
VREW,V2,OUT='vrew3.mp4','../c9b/09_연습_합본_v3.mp4','쏙_숏폼_ver02_final5.mp4'
OFF=31.75
S=[('vrew',0.0,3.0),('vrew',4.6,9.5),('vrew',10.8,15.5),('vrew',16.6,21.9),('vrew',22.4,27.9),('vrew',28.3,31.0),
   ('v2',33.2,39.8),('v2',41.4,45.9),('v2',46.7,50.4),('vrew',51.6,54.4),('vrew',55.8,60.2),('vrew',60.4,62.9),('vrew',63.45,67.6)]
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
fc.append("[2:v]format=rgba,fade=t=in:st=0.2:d=0.3:alpha=1,fade=t=out:st=2.55:d=0.4:alpha=1[ti]")
fc.append("[vb][ti]overlay=0:0:enable='between(t,0,2.99)'[vt]")
prev='vt'
for k,(f,a,b) in enumerate(subs):
    fc.append(f"[{3+k}:v]format=rgba[s{k}]"); fc.append(f"[{prev}][s{k}]overlay=0:0:enable='between(t,{a:.2f},{b:.2f})'[vs{k}]"); prev=f'vs{k}'
NAR_C=0.0   # 클로징 나레이션 시작(클로징 기준)
durs=[1.14, 1.94-1.14, 0.9, 0.8, 1.5]
for k,dd in enumerate(durs): fc.append(f"[{6+k}:v]loop=loop={int(dd*30)+1}:size=1:start=0,trim=duration={dd:.2f},setpts=PTS-STARTPTS,fps=30,format=yuv420p[c{k}]")
fc.append(''.join(f'[c{k}]' for k in range(5))+"concat=n=5:v=1:a=0[close]")
fc.append(f"[{prev}][close]concat=n=2:v=1:a=0[vall]")
total=body+sum(durs)
sfx1=out(59.4,10); sfx2=body+NAR_C+1.94
no=0.3; nc=body+NAR_C
fc.append(f"[ab]apad=whole_dur={total:.2f}[dlg0]")
fc.append("[12:a]asplit=2[x1][x2]")
fc.append(f"[x1]adelay={int(sfx1*1000)}|{int(sfx1*1000)},apad=whole_dur={total:.2f},volume=0.8[fx1]")
fc.append(f"[x2]adelay={int(sfx2*1000)}|{int(sfx2*1000)},apad=whole_dur={total:.2f},volume=0.8[fx2]")
fc.append(f"[13:a]aformat=sample_rates=48000:channel_layouts=stereo,adelay={int(nc*1000)}|{int(nc*1000)},apad=whole_dur={total:.2f}[n2]")
fc.append("[dlg0][n2]amix=inputs=2:duration=first:normalize=0[voice]")
fc.append("[voice]asplit=2[voice1][key]")
fc.append(f"[11:a]atrim=0:{total:.2f},asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.8,afade=t=out:st={total-1.4:.2f}:d=1.4,volume=0.55[mus0]")
fc.append("[mus0][key]sidechaincompress=threshold=0.015:ratio=8:attack=15:release=500:makeup=1[musd]")
fc.append("[voice1][musd][fx1][fx2]amix=inputs=4:duration=first:dropout_transition=0:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11[aall]")
cmd=['ffmpeg','-v','error','-y','-i',VREW,'-i',V2,'-loop','1','-t','3.2','-i','title2.png']
for f in ['sub1.png','sub2.png','sub3.png']: cmd+=['-loop','1','-t','5','-i',f]
for k in range(1,6): cmd+=['-i',f'card{k}.png']
cmd+=['-i','music.mp3','-i','sfx_ssok.m4a','-i','nar_close_h.mp3','-filter_complex',';\n'.join(fc),'-map','[vall]','-map','[aall]','-c:v','libx264','-crf','18','-preset','medium','-c:a','aac','-b:a','192k','-movflags','+faststart',OUT]
r=subprocess.run(cmd,capture_output=True,text=True)
print('rc',r.returncode,'body',round(body,2),'total',round(total,2),'sfx',round(sfx1,2),round(sfx2,2),'subs',[(round(a,1),round(b,1)) for _,a,b in subs]); print(r.stderr[-1200:])
