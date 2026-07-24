#version 150

uniform float SunX, SunY, MoonX, MoonY, BhX, BhY, BhDist, Time, AspectRatio;
uniform float RingN;
uniform float RX[30];
uniform float RY[30];

in vec2 texCoord;
out vec4 fragColor;

float circ(vec2 uv, vec2 c, float r) { vec2 d=uv-c; d.x*=AspectRatio; return 1.0-smoothstep(0.0,r,length(d)); }

void main() {
    vec2 uv = texCoord;
    fragColor = vec4(0.0);

    float distScale = 20.0 / max(BhDist, 0.5);
    float fade = clamp(1.0 - (BhDist - 50.0) / 40.0, 0.0, 1.0);

    int rn = int(RingN);
    for (int i=0; i<rn; i++) {
        if (RX[i] > -90.0) fragColor += vec4(0.8,0.8,0.25,0.2*fade)*circ(uv,vec2(RX[i],RY[i]),0.012*distScale);
    }

    if (SunX >= 0.0) {
        vec2 sc=vec2(SunX,SunY); float p=sin(Time*2.5)*0.1+1.0;
        fragColor += vec4(1.0,0.35,0.05,0.9*fade)*circ(uv,sc,0.08*distScale)*p;
        fragColor += vec4(1.0,0.25,0.02,0.5*fade)*circ(uv,sc,0.16*distScale)*p;
        fragColor += vec4(1.0,0.2,0.0,0.22*fade)*circ(uv,sc,0.28*distScale)*p;
        fragColor += vec4(1.0,0.12,0.0,0.08*fade)*circ(uv,sc,0.44*distScale)*p;
        fragColor += vec4(1.0,0.06,0.0,0.03*fade)*circ(uv,sc,0.64*distScale)*p;
    }

    if (MoonX >= 0.0) {
        vec2 mc=vec2(MoonX,MoonY);
        float moonBaseR=0.04*distScale;
        float mb=circ(uv,mc,moonBaseR),cr=0.0;
        vec2 mv=(uv-mc)/max(moonBaseR,0.001);
        float rot=Time*0.5;
        for (int ci=0;ci<8;ci++){
            float cd=sin(float(ci)*7.3)*0.6, sd=cos(float(ci)*5.1)*0.6;
            float cx=cd*cos(rot)-sd*sin(rot), cy=cd*sin(rot)+sd*cos(rot);
            float crSz=0.06+(float(ci%3)*0.16);
            cr+=0.45*(1.0-smoothstep(0.0,crSz,length(mv-vec2(cx,cy))));
        }
        fragColor += vec4(0.9,0.9,0.92,0.85*fade)*mb*(1.0-cr);
        fragColor += vec4(0.6,0.6,0.65,0.15*fade)*circ(uv,mc,0.10*distScale);
        fragColor += vec4(0.4,0.4,0.45,0.05*fade)*circ(uv,mc,0.18*distScale);
    }
}
