# Kotlin CarModel/Scenario/RearCamView 와 같은 수식으로 시작 자세를 그려 기하를 검증한다.
import math, sys
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon

class Spec:
    def __init__(s, name, L, W, wb, ro): s.name,s.length,s.width,s.wheelbase,s.rearOverhang=name,L,W,wb,ro; s.maxWheelDeg=35.0; s.ratio=540/35
    @property
    def frontOverhang(s): return s.length-s.wheelbase-s.rearOverhang
AVANTE=Spec("아반떼",4.765,1.855,2.750,1.050); GRANDEUR=Spec("그랜저",5.050,1.880,2.895,1.150)

def rot(x,y,t): return (x*math.cos(t)-y*math.sin(t), x*math.sin(t)+y*math.cos(t))
def obb(c,h,hx,hy):
    ax=(math.cos(h),math.sin(h)); ay=(-math.sin(h),math.cos(h))
    return [(c[0]+ax[0]*sx*hx+ay[0]*sy*hy, c[1]+ax[1]*sx*hx+ay[1]*sy*hy) for sx,sy in [(1,1),(1,-1),(-1,-1),(-1,1)]]
def wheel(spec,steer): return math.radians(max(-spec.maxWheelDeg,min(spec.maxWheelDeg,-steer/spec.ratio)))
def step(spec,p,steer,v,dt):
    x,y,h=p; d=wheel(spec,steer); ds=v*dt
    if abs(d)<1e-6: return (x+ds*math.cos(h), y+ds*math.sin(h), h)
    r=spec.wheelbase/math.tan(d); dth=ds/r; cx=x-r*math.sin(h); cy=y+r*math.cos(h); th=h+dth
    return (cx+r*math.sin(th), cy-r*math.cos(th), th)
def body(spec,p):
    x,y,h=p; ca=(spec.wheelbase+spec.frontOverhang-spec.rearOverhang)/2
    return obb((x+ca*math.cos(h), y+ca*math.sin(h)), h, spec.length/2, spec.width/2)
def rear_corners(spec,p):
    x,y,h=p; bx,by=x-spec.rearOverhang*math.cos(h), y-spec.rearOverhang*math.sin(h); sx,sy=-math.sin(h)*spec.width/2, math.cos(h)*spec.width/2
    return (bx+sx,by+sy),(bx-sx,by-sy)
def path(spec,p,steer,reverse,rng=4.0,n=40):
    L=[];R=[];q=p;v=-1 if reverse else 1
    for i in range(n+1):
        l,r=rear_corners(spec,q); L.append(l); R.append(r); q=step(spec,q,steer,v,rng/n)
    return L,R
def scenario_perp(spec,pillar=False):
    SW,SL=2.5,5.0
    slot=obb((0,-SL/2),math.pi/2,SL/2,SW/2)
    obs=[("car",obb((-SW,-SL/2),math.pi/2,2.3,0.9)),("car",obb((SW,-SL/2),math.pi/2,2.3,0.9)),("wall",obb((0,6.6),0,12,0.3))]
    if pillar: obs.append(("pillar",obb((SW/2+0.25,-0.6),0,0.25,0.25)))
    start=(spec.wheelbase/math.tan(math.radians(35))+0.3,3.4,0.0)
    return slot,obs,start,(0,1,7.5,6.5)
def scenario_par(spec):
    gap=spec.length+1.6; slot=obb((0,-1.3),0,gap/2,1.3)
    obs=[("car",obb(((gap/2+2.4),-1.3),0,2.4,0.95)),("car",obb((-(gap/2+2.4),-1.3),0,2.4,0.95)),("curb",obb((0,-2.75),0,10,0.15))]
    start=(gap/2+spec.rearOverhang,1.6,0.0)
    return slot,obs,start,(0,0,9,5)

def draw_top(ax,spec,slot,obs,p,steer,title):
    ax.set_facecolor("#26282e")
    ax.add_patch(Polygon(slot,closed=True,fill=False,ec="#5ac878",lw=2))
    for k,o in obs: ax.add_patch(Polygon(o,closed=True,fc="#78787f" if k=="car" else "#c8a050",ec="none"))
    L,R=path(spec,p,0,True,3,2); ax.plot(*zip(*L),"--",c="#50aaff"); ax.plot(*zip(*R),"--",c="#50aaff")
    L,R=path(spec,p,steer,True,4,40); ax.plot(*zip(*L),c="#ffd228",lw=2); ax.plot(*zip(*R),c="#ffd228",lw=2)
    L,R=path(spec,p,steer,True,0.5,6); ax.plot(*zip(*L),c="#f05050",lw=2.5); ax.plot(*zip(*R),c="#f05050",lw=2.5)
    ax.add_patch(Polygon(body(spec,p),closed=True,fc="#4682f0",ec="w"))
    ax.plot(p[0],p[1],"wo",ms=3); ax.set_aspect("equal"); ax.set_title(title,color="w",fontsize=9)

def project(spec,pt,p,W,H,z0=0.0,camH=0.95,pitch=math.radians(25),hfov=math.radians(130)):
    x,y,h=p; cx,cy=x-spec.rearOverhang*math.cos(h), y-spec.rearOverhang*math.sin(h); dx,dy=pt[0]-cx,pt[1]-cy
    back=-(dx*math.cos(h)+dy*math.sin(h)); right=-(-dx*math.sin(h)+dy*math.cos(h)); yc=z0-camH
    zc=back*math.cos(pitch)-yc*math.sin(pitch); yr=back*math.sin(pitch)+yc*math.cos(pitch)
    if zc<0.15: return None
    f=(W/2)/math.tan(hfov/2); return (W/2+f*right/zc, H/2-f*yr/zc)
def draw_rear(ax,spec,slot,obs,p,steer):
    W,H=800,450; ax.set_xlim(0,W); ax.set_ylim(H,0); ax.set_facecolor("#1c1e24")
    hor=H/2-((W/2)/math.tan(math.radians(65)))*math.tan(math.radians(25)); ax.axhspan(hor,H,color="#34363c")
    def poly(pts,**kw):
        q=[project(spec,pt,p,W,H,kw.pop("z0",0.0)) for pt in pts]; q=[a for a in q if a]
        if len(q)>=3: ax.add_patch(Polygon(q,closed=True,**kw))
    poly(slot,fill=False,ec="#ebebeb",lw=1.5)
    for k,o in obs: poly(o,fc="#6e6e78",ec="none"); poly(o,fc="#6e6e78",ec="none",z0=1.4 if k=="car" else 2.2)
    def line(pts,**kw):
        q=[project(spec,pt,p,W,H) for pt in pts]; q=[a for a in q if a]
        if len(q)>=2: ax.plot(*zip(*q),**kw)
    L,R=path(spec,p,0,True,3,2); line(L,ls="--",c="#50aaff"); line(R,ls="--",c="#50aaff")
    L,R=path(spec,p,steer,True,4,40); line(L,c="#ffd228",lw=2); line(R,c="#ffd228",lw=2)
    L,R=path(spec,p,steer,True,0.5,6); line(L,c="#f05050",lw=2.5); line(R,c="#f05050",lw=2.5)
    a,b=rear_corners(spec,p)
    for d in (0.5,1.0,2.3):
        bk=(-math.cos(p[2])*d,-math.sin(p[2])*d); pa=project(spec,(a[0]+bk[0],a[1]+bk[1]),p,W,H); pb=project(spec,(b[0]+bk[0],b[1]+bk[1]),p,W,H)
        if pa and pb: ax.plot([pa[0],pb[0]],[pa[1],pb[1]],c="w",lw=1); ax.text(pb[0]+6,pb[1],f"{d}m",color="w",fontsize=7)
    ax.axhspan(H*0.9,H,color="#141418"); ax.set_xticks([]); ax.set_yticks([]); ax.set_title("rear cam (steer %+d°)"%steer,color="w",fontsize=9)

plt.rcParams["font.family"]="DejaVu Sans"
fig,axs=plt.subplots(3,3,figsize=(15,12),facecolor="#0e0f12")
for row,(spec,mk) in enumerate([(AVANTE,"perp"),(AVANTE,"pillar"),(GRANDEUR,"par")]):
    slot,obs,start,b=scenario_perp(spec,pillar=(mk=="pillar")) if mk!="par" else scenario_par(spec)
    for col,steer in enumerate([0,540]):
        ax=axs[row][col]; draw_top(ax,spec,slot,obs,start,steer,f"{spec.name} {mk} top, steer {steer:+d}"); ax.set_xlim(b[0]-b[2],b[0]+b[2]); ax.set_ylim(b[1]-b[3],b[1]+b[3])
    draw_rear(axs[row][2],spec,slot,obs,start,540)
    # 시뮬: 후진 + 우조향 끝까지 2.5초 → 어디로 가나 (충돌 여부 대략)
    q=start; target=math.pi/2 if mk!="par" else math.pi/4
    for i in range(2000):
        q=step(spec,q,540,-0.6,0.01)
        if q[2]>=target: break
    if mk!="par":
        for i in range(300): q=step(spec,q,0,-0.6,0.01)
    axs[row][1].add_patch(Polygon(body(spec,q),closed=True,fill=False,ec="#ff80ff",lw=1.5,ls=":"))
plt.tight_layout(); out="/tmp/claude-0/-home-user/601bb229-5eb8-5611-8750-9eef53f25fdd/scratchpad/preview/preview.png"; plt.savefig(out,dpi=80); print(out)
