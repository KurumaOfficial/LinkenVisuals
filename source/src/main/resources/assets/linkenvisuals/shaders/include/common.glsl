float rdist(vec2 pos, vec2 size, vec4 radius) {
    // Выбор углового радиуса БЕЗ записи в свизлы параметра: gl4es/GLES2 не поддерживает свизл-l-value
    // (radius.xy = ...). Логика та же: по знаку pos.x берём пару (xy|wz), по знаку pos.y — компонент.
    vec2 rr = (pos.x > 0.0) ? radius.xy : radius.wz;
    float r  = (pos.y > 0.0) ? rr.x : rr.y;

    vec2 v = abs(pos) - size + r;
    return min(max(v.x, v.y), 0.0) + length(max(v, 0.0)) - r;
}

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 center = size * 0.5;
    float dist = rdist(center - (coord * size), center - 1.0, radius);
    return 1.0 - smoothstep(1.0 - smoothness, 1.0, dist);
}

vec2 rvertexcoord(int id) {
    // Без const-массива с динамическим индексом: в GLES2 индексация массива допустима только
    // константой/индексом цикла, иначе ошибка компиляции. If-цепочка эквивалентна и безопасна везде.
    int k = id - (id / 4) * 4; // id % 4
    if (k == 0) return vec2(0.0, 0.0);
    if (k == 1) return vec2(0.0, 1.0);
    if (k == 2) return vec2(1.0, 1.0);
    return vec2(1.0, 0.0);
}
