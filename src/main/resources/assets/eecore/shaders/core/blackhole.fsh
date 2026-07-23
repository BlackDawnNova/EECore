#version 150

uniform sampler2D SceneSampler;
uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform vec3 cameraPos;
uniform vec3 entityPos;
uniform float scale;
uniform float time;
uniform vec2 screenSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 clipToWorld(vec2 uv, float d) {
    vec4 cp = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 wp = inverse(projectionMatrix * modelViewMatrix) * cp;
    return wp.xyz / wp.w + cameraPos;
}

void main() {
    vec3 sc = texture(SceneSampler, texCoord).rgb;

    vec4 ev4 = modelViewMatrix * vec4(entityPos - cameraPos, 1.0);
    vec4 cp4 = projectionMatrix * ev4;
    if (cp4.w <= 0.0) { fragColor = vec4(sc, 1.0); return; }
    vec2 eUV = cp4.xy / cp4.w * 0.5 + 0.5;
    if (eUV.x < 0.0 || eUV.x > 1.0 || eUV.y < 0.0 || eUV.y > 1.0) { fragColor = vec4(sc, 1.0); return; }

    float pxDist = length((texCoord - eUV) * screenSize);
    float entityZ = -ev4.z;
    float projScale = screenSize.y / max(entityZ, 0.01);
    float corePx = scale * projScale * 0.35;

    // ── World-space ray ──
    vec3 ro = clipToWorld(texCoord, 0.0);
    vec3 rd = normalize(clipToWorld(texCoord, 1.0) - ro);

    // ── Accretion disk: ray-march ──
    vec3 diskCol = vec3(0.0);
    float diskAlpha = 0.0;
    float toBh = distance(ro, entityPos);
    int steps = int(min(toBh / 0.2, 200.0));
    float stepLen = toBh / float(max(steps, 1));
    vec3 pos = ro;

    for (int i = 0; i < 200 && i < steps && diskAlpha < 0.95; i++) {
        vec3 d = pos - entityPos;
        float dc = length(d.xz);
        float dy = abs(d.y);
        if (dy < 0.5 && dc > 0.3 && dc < 6.0) {
            float dens = sat(1.0 - dy / 0.5) * exp(-sat((dc - 1.0) / 4.0) * 2.0) * 0.3;
            if (dens > 0.001) {
                float g = sat((dc - 1.0) / 4.0);
                vec3 col = mix(vec3(1.0, 0.95, 0.7), vec3(0.9, 0.2, 0.03), g) * dens;
                diskCol = mix(diskCol, col, dens);
                diskAlpha = mix(diskAlpha, 1.0, dens);
            }
        }
        pos += rd * stepLen;
    }
    diskCol = diskCol / max(diskAlpha, 0.001);

    // ── Lensing ──
    vec3 bg = sc;
    if (pxDist > corePx * 0.4) {
        float warpR = corePx * 2.0;
        float strength = 1.0 / (pxDist / warpR + 0.15) * 0.5;
        vec2 warpUV = eUV + (texCoord - eUV) * (1.0 - strength);
        if (warpUV.x > 0.0 && warpUV.x < 1.0 && warpUV.y > 0.0 && warpUV.y < 1.0)
            bg = texture(SceneSampler, warpUV).rgb;
    }

    float glow = (1.0 - smoothstep(corePx * 0.8, corePx * 2.5, pxDist))
               * smoothstep(corePx * 2.5, corePx * 3.5, pxDist);

    // ── Composite ──
    vec3 color = bg;
    if (diskAlpha > 0.01) color = mix(color, diskCol, diskAlpha);
    color = mix(color, vec3(1.0, 0.4, 0.1), glow * 0.4);
    color *= vec3(1.0, 0.3, 0.3);
    fragColor = vec4(color, 1.0);
}
