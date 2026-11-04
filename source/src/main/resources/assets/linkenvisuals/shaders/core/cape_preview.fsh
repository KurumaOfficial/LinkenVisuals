#version 150

#moj_import <linkenvisuals:common.glsl>

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Size;      // размер карточки в пикселях
uniform vec4 Radius;    // скругление углов
uniform float Smoothness;
uniform vec4 UvRect;    // подпрямоугольник текстуры: видимая грань плаща (u0,v0,u1,v1)
uniform float Time;     // секунды
uniform float Amount;   // 0 — плоско, 1 — полная волна

out vec4 OutColor;

const float WAVE_LEN = 1.45;   // сколько волн умещается по высоте
const float WAVE_SPEED = 1.7;  // как быстро волна бежит вниз
const float WAVE_UV = 0.055;   // максимальный сдвиг UV по горизонтали
const float SHADE = 0.34;      // глубина светотени на складках

void main() {
    vec2 local = clamp(TexCoord, 0.0, 1.0);

    // Верх плаща закреплён на плечах, низ свободен — амплитуда растёт вниз.
    float hang = local.y * local.y * (3.0 - 2.0 * local.y);
    float phase = local.y * 6.2831853 * WAVE_LEN - Time * WAVE_SPEED;

    // Складки: горизонтальный сдвиг ткани + светотень по наклону складки.
    float offset = sin(phase) * WAVE_UV * hang * Amount;
    float slope = cos(phase) * hang * Amount;

    vec2 uv = clamp(vec2(local.x + offset, local.y), 0.0, 1.0);
    vec4 tex = texture(Sampler0, mix(UvRect.xy, UvRect.zw, uv));

    // Объём: гребни складок ловят свет, впадины уходят в тень; плюс мягкое затемнение к краям.
    float light = 1.0 + slope * SHADE;
    float edge = 1.0 - 0.22 * pow(abs(local.x * 2.0 - 1.0), 3.0);
    float top = 1.0 + 0.10 * (1.0 - local.y);
    tex.rgb *= light * edge * top;

    float alpha = ralpha(Size, FragCoord, Radius, Smoothness);
    vec4 color = tex * FragColor;
    color.a *= alpha;

    if (color.a == 0.0) {
        discard;
    }

    OutColor = color;
}
