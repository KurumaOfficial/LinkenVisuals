#version 330 core

in vec2 uv;
out vec4 outColor;

uniform sampler2D CurrentTexture; // текущий кадр (мир)
uniform sampler2D PrevTexture;    // накопленный предыдущий (шлейф)
uniform float strength;           // 0 = нет блюра, ~0.7 = длинные шлейфы (persistence)

void main() {
    vec3 cur = texture(CurrentTexture, uv).rgb;
    vec3 prev = texture(PrevTexture, uv).rgb;
    // Кадровый бленд в ЛИНЕЙНОМ пространстве (гамма-корректно). Смешение прямо в sRGB даёт тёмные,
    // «грязные» промежуточные тона на границах контраста — шлейф выглядит рвано. Переводим оба кадра
    // в линейный свет, смешиваем, возвращаем в sRGB: смаз получается плавным и естественным.
    vec3 curLin = pow(cur, vec3(2.2));
    vec3 prevLin = pow(prev, vec3(2.2));
    vec3 mixed = mix(curLin, prevLin, strength);
    outColor = vec4(pow(mixed, vec3(1.0 / 2.2)), 1.0);
}
