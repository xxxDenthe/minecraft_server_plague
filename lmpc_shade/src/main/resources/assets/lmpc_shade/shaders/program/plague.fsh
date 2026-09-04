#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;   // глубина сцены: 1.0 = ничего не нарисовано (небо)

uniform float SkyR;          // цвет тумана этого кадра, покомпонентно —
uniform float SkyG;          // тот же, что показывает Fabulous в дырке неба
uniform float SkyB;
uniform float Overcast;      // 1 — заливать дырку от SkyType.NONE, 0 — не трогать

uniform float Time;          // 0..1, оборот за 20 тиков (ставит PostPass сам) — зерно

uniform float Saturation;    // 0 — серо, 1 — как есть
uniform float Brightness;    // общий множитель яркости
uniform float TintR;         // цвет тона, покомпонентно
uniform float TintG;         // (uniform-вектор в PostChain 1.21.1 не пробросить)
uniform float TintB;
uniform float TintStrength;  // 0..1 — сколько тона подмешать
uniform float Vignette;      // 0..1 — затемнение краёв

uniform float NightFactor;   // 0 день .. 1 глухая ночь
uniform float NightDarkness; // до какой доли яркости проваливается неосвещённое ночью
uniform float HealthFactor;  // 0 здоров .. 1 при смерти (квадратичный)
uniform float Pulse;         // 0..1 удар сердца, темп задаётся в Java
uniform float Grain;         // сила плёночного зерна
uniform float Posterize;     // число уровней на канал, <2 — выключено

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    vec3 rgb = c.rgb;
    float alpha = c.a;

    // Пасмурное небо (overcast) убирает купол через SkyType.NONE. В графике
    // Fabulous дырку закрывает цвет тумана, в Fancy — ничем (чёрное). Здесь
    // заливаем её тем же цветом тумана и дальше грейдим как весь кадр —
    // получается то же, что в Fabulous, в любом режиме графики.
    if (Overcast > 0.5 && texture(DepthSampler, texCoord).r >= 1.0) {
        rgb = vec3(SkyR, SkyG, SkyB);
        alpha = 1.0;
    }

    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    // «свет — это жизнь»: доля освещённости пикселя.
    // ponytail: по яркости кадра, не по настоящему lightmap. Тёмный
    // материал в свете тоже частично сереет; на глаз ок. Апгрейд — миксин.
    float lit = smoothstep(0.04, 0.34, luma);

    // тёплое пятно у источников света ночью
    float warm = lit * NightFactor;

    // обесцвечивание: в темноте глубже, но у самих источников цвет живёт
    float sat = Saturation * mix(0.25, 1.0, lit);
    sat = max(sat, warm);                       // факел — единственный цвет в сером мире
    rgb = mix(vec3(luma), rgb, sat);

    // тон: сдвиг цветового баланса (нейтрально-тёплый серый, без лиловости)
    vec3 tint = vec3(TintR, TintG, TintB);
    float avg = max((TintR + TintG + TintB) / 3.0, 1e-3);
    rgb = mix(rgb, rgb * (tint / avg), TintStrength);

    // ночная тьма вне освещённых пятен: жёсткий провал по кривой
    float dark = NightFactor * pow(1.0 - lit, 1.5);
    rgb *= mix(1.0, NightDarkness, dark);

    // уют у огня: теплее и чуть ярче
    rgb *= mix(1.0, 1.14, warm);
    rgb *= vec3(1.0 + 0.16 * warm, 1.0 + 0.03 * warm, 1.0 - 0.10 * warm);

    // общее затемнение
    rgb *= Brightness;

    // --- реакция на низкое HP ---
    if (HealthFactor > 0.0) {
        float g = dot(rgb, vec3(0.299, 0.587, 0.114));
        rgb = mix(rgb, vec3(g), 0.75 * HealthFactor);        // почти обесцветить
        rgb *= 1.0 - 0.28 * HealthFactor * Pulse;            // тук темноты
        rgb *= 1.0 - 0.12 * HealthFactor;                    // мир меркнет
    }

    // --- виньетка (у смерти — уже и с красным) ---
    vec2 d = texCoord - 0.5;
    float vig = Vignette + 0.9 * HealthFactor;
    float fall = clamp(1.0 - dot(d, d) * vig * 4.0, 0.0, 1.0);
    float crit = smoothstep(0.55, 1.0, HealthFactor);
    vec3 edge = mix(vec3(0.0), vec3(0.11, 0.01, 0.01), crit) * (0.4 + 0.6 * Pulse);
    rgb = mix(edge, rgb, fall);

    // --- плёночное зерно ---
    if (Grain > 0.0) {
        float n = hash12(gl_FragCoord.xy + fract(Time) * 431.0);
        rgb += (n - 0.5) * Grain;
    }

    // --- постеризация ---
    if (Posterize >= 2.0) {
        rgb = floor(rgb * Posterize + 0.5) / Posterize;
    }

    fragColor = vec4(clamp(rgb, 0.0, 1.0), alpha);
}
