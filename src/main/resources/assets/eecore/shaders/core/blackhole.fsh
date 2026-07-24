#version 150

uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform sampler2D TextureSampler;
uniform sampler2D ColorSampler;

uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 invPV;
uniform vec3 cameraPos;

uniform vec3 entityPos;
uniform float time;
uniform float scale;
uniform float accretionDiskRadiusScale;
uniform float accretionDiskThicknessScale;
uniform float accretionDiskDensity;
uniform float tiltAngle;
uniform float intensity;
uniform float renderQuality;
uniform float ditherStrength;
uniform float lensBoundarySoftness;
uniform float diskNoiseStrength;
uniform float diskTextureStrength;
uniform float coreRadiusScale;
uniform vec3 accretionDiskColor;
uniform vec3 accretionDiskInnerColor;
uniform vec3 accretionDiskOuterColor;
uniform vec2 screenSize;
uniform float noiseTextureSize;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265;
const float CORE_RADIUS = 0.35;
const int MAX_ITER = 120;
const int MIN_ITER = 35;
const float ES = 8.0;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float rnd(vec2 c) { return sat(fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453)); }
float pcurve(float x, float a, float b) { float k=pow(a+b,a+b)/(pow(a,a)*pow(b,b)); return k*pow(x,a)*pow(1.0-x,b); }
mat3 rotX(float a) { float c=cos(a),s=sin(a); return mat3(1,0,0,0,c,-s,0,s,c); }

float noise(in vec3 x) {
    vec3 p=floor(x),f=fract(x); f=f*f*(3.0-2.0*f);
    return -1.0+2.0*textureLod(TextureSampler, ((p.xy+vec2(37.0,17.0)*p.z)+f.xy+0.5)/noiseTextureSize, 0.0).x;
}

vec3 clipToWorld(vec2 uv, float d) {
    vec4 cp = vec4(uv*2.0-1.0, d*2.0-1.0, 1.0);
    vec4 wp = invPV * cp;
    return wp.xyz/wp.w + cameraPos;
}
float w2v(vec3 wp) { return -(modelViewMatrix * vec4(wp - cameraPos, 1.0)).z; }
float getDist(vec2 uv, out vec3 wp) { float d=texture(MainDepthSampler, uv).r; if(d>=0.9999){wp=vec3(0);return 1e10;} wp=clipToWorld(uv,d); return w2v(wp); }
vec2 wd2uv(vec3 origin, vec3 wd) { vec3 fp=origin+wd*1000.0; vec4 vp=modelViewMatrix*vec4(fp-cameraPos,1.0); vec4 cp=projectionMatrix*vp; return cp.xy/cp.w*0.5+0.5; }
bool uvIn(vec2 uv) { return uv.x>=0.0&&uv.x<=1.0&&uv.y>=0.0&&uv.y<=1.0; }
vec3 sampleS(vec2 uv, vec3 fb) { if(!uvIn(uv))return fb; vec2 t=0.5/screenSize; return texture(MainColorSampler, clamp(uv,t,1.0-t)).rgb; }
vec2 raySph(vec3 ro, vec3 rd, vec3 c, float r) { vec3 oc=ro-c; float b=dot(oc,rd),cc=dot(oc,oc)-r*r,h=b*b-cc; if(h<0.0)return vec2(-1.0); float sh=sqrt(h); return vec2(-b-sh,-b+sh); }

void warp(inout vec3 ev, inout vec3 rp, float inv) {
    float d2=dot(rp,rp); float w=1.0/(d2+0.000001);
    ev=normalize(ev - rp*inversesqrt(d2+0.000001)*w*5.0*inv*intensity);
}

vec3 traceLensed(vec3 lro, vec3 lrd, float dte, out bool sw) {
    mat3 tilt=rotX(tiltAngle); lro=tilt*lro; lrd=tilt*lrd;
    vec3 ep=lro*ES, ev=normalize(lrd);
    float f=sat((dte-scale*0.5)/(scale*8.0));
    int it=max(int(mix(float(MAX_ITER)*0.5,float(MIN_ITER)*0.5,f)*renderQuality),30);
    float inv=1.0/float(it), sm=dte<=90.0?0.3:(dte>=300.0?1.0:(dte-90.0)/210.0*0.7+0.3);
    sw=false; vec3 rp=ep;
    for(int i=0;i<MAX_ITER&&i<it;i++) { warp(ev,rp,inv); rp+=ev*sm; if(dot(rp,rp)<CORE_RADIUS*CORE_RADIUS*0.64){sw=true;return vec3(0.0);} }
    return normalize(rotX(-tiltAngle)*ev);
}

void gasDisc(inout vec3 col, inout float al, vec3 p, float lod) {
    float dr=3.2*accretionDiskRadiusScale, dw=5.3*accretionDiskRadiusScale;
    float di=dr-dw*0.5, dd=dr+dw*0.5, dc=length(p.xz), dy=p.y;
    if(dc<di*0.3||dc>dd*1.3)return; if(abs(dy)>0.5*accretionDiskThicknessScale)return;
    float g=1.0-sat((dc-di)/dw*0.8), cov=pcurve(g,4.0,0.9);
    float th=0.1*g*accretionDiskThicknessScale; cov*=sat(1.0-abs(dy)/max(th,0.0001));
    float bl=1.0/(pow(1.0-g,2.0)*290.0+0.002); vec3 dust=accretionDiskColor*bl*8.2;
    float fa=pow(abs(dc-di)+0.4,4.0)*0.04, bb=1.0/(dy*dy*40.0+fa+0.00002);
    vec3 b=accretionDiskColor*pow(bb,1.5); b*=mix(accretionDiskInnerColor,accretionDiskOuterColor,vec3(pow(g,2.0)));
    dust=mix(dust,b*150.0,sat(1.0-cov)); cov=sat(cov+bb*bb*0.1); if(cov<0.01)return;
    float ang=atan(-p.x,-p.z)/(2.0*PI)+0.5, sp=0.12;
    vec3 rc; rc.x=dc*1.5+0.55;rc.y=ang*2.0*PI*1.5;rc.z=dy*1.5;rc*=0.95;
    float n=1.0; vec3 tmp=rc;tmp.y+=time*sp;n*=noise(tmp*4.0)*0.5+0.5;tmp.y=rc.y-time*sp*1.414;n*=noise(tmp*8.0)*0.5+0.5;
    if(lod<0.35){tmp.y=rc.y+time*sp;n*=noise(tmp*16.0)*0.5+0.5;}
    dust*=mix(1.0,n*0.998+0.002,diskNoiseStrength); cov*=mix(1.0,mix(accretionDiskDensity,1.0,sat(n)),diskNoiseStrength);
    cov=sat(cov*2000.0/float(MAX_ITER)); dust=max(vec3(0),dust); cov*=pcurve(g,5.0,0.9);
    col=(1.0-al)*dust*cov+col; al=(1.0-al)*cov+al;
}

void main() {
    vec2 uv=texCoord;
    vec3 sc=texture(MainColorSampler, uv).rgb;

    vec4 ev4=modelViewMatrix*vec4(entityPos-cameraPos,1.0);
    vec4 cp4=projectionMatrix*ev4;
    if(cp4.w<=0.0){fragColor=vec4(sc,1.0);return;}
    vec2 eUV=cp4.xy/cp4.w*0.5+0.5;
    float pxDist=length((uv-eUV)*screenSize);
    float entityZ=-ev4.z;
    float projScale=screenSize.y/max(entityZ,0.01);
    float maxInfluence=scale*projScale*6.0;
    if(pxDist>maxInfluence*1.5){fragColor=vec4(sc,1.0);return;}

    vec3 ro=clipToWorld(uv,0.0), rd=normalize(clipToWorld(uv,1.0)-ro);
    float dte=distance(ro,entityPos);

    bool sw; vec3 ld=traceLensed((ro-entityPos)/scale, rd, dte, sw);
    vec3 lbg=sc;
    if(!sw&&length(ld)>0.001){vec2 luv=wd2uv(ro,ld); lbg=sampleS(luv,sc);}

    float corePx=scale*projScale*coreRadiusScale;
    float ssCore=cp4.w>0.0?1.0-smoothstep(corePx*0.3,corePx*0.8,pxDist):0.0;
    vec2 st=raySph(ro,rd,entityPos,scale);
    float et=st.y>=0.0?max(st.x,0.0):0.0;

    mat3 tilt=rotX(tiltAngle), invT=rotX(-tiltAngle);
    vec3 vro=tilt*(ro+rd*et-entityPos)/scale*ES, vrd=normalize(tilt*rd);
    float far=15.0, qf=sat((dte-scale*0.5)/(scale*8.0));
    int qi=max(int(mix(float(MAX_ITER),float(MIN_ITER),qf)*renderQuality),MIN_ITER);
    float inv=1.0/float(qi), sl=far*inv, dith=(rnd(uv*127.1+time*0.618)-0.5)*sl*ditherStrength;
    vec3 rp=vro+vrd*dith, ev=normalize(vrd), vc=vec3(0); float va=0;

    for(int i=0;i<MAX_ITER&&i<qi;i++){warp(ev,rp,inv); gasDisc(vc,va,rp,float(i)/float(qi)); rp+=ev*sl; if(va>0.99||length(rp)>far*1.5)break;}
    float cd=length(vro), ca=1.0-smoothstep(CORE_RADIUS-0.02,CORE_RADIUS+0.02,cd);
    float edgeSoft = max(0.001, lensBoundarySoftness*0.02);
    float coreAlpha = 1.0 - smoothstep(CORE_RADIUS-edgeSoft, CORE_RADIUS+edgeSoft, cd);
    if(coreAlpha>0.0){va=mix(va,1.0,coreAlpha);vc=mix(vc,vec3(0.0),(1.0-va)*coreAlpha);}
    vc=vc/(1.0+vc);

    float finalAlpha = max(va, ssCore);
    vec3 darkCore = vec3(0.0);
    vec3 finalColor = mix(lbg, darkCore, ssCore);
    if(va>0.001){finalColor=mix(finalColor,vc,va);}
    fragColor=vec4(finalColor,1.0);
}
