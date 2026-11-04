from pathlib import Path

p = Path("src/main/java/ru/method/linkenvisuals/api/mixins/CharacterPlayerModelMixin.java")
t = p.read_text(encoding="utf-8")
old = "            float armBack = -(1.25F + 0.45F * pose + swing);"
new = "            float armBack = 1.25F + 0.45F * pose + swing;"
assert t.count(old) == 1
t = t.replace(old, new, 1)
old2 = "            float run = MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);"
new2 = "            float run = Boolean.getBoolean(\"linkenvisuals.naruto.force\")\n                    ? 1.0F : MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);"
assert t.count(old2) == 1
t = t.replace(old2, new2, 1)
p.write_text(t, encoding="utf-8", newline="\n")

p = Path("src/main/java/ru/method/linkenvisuals/api/mixins/LivingEntityRendererMixin.java")
t = p.read_text(encoding="utf-8")
old3 = "                float pose = MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);"
new3 = "                float pose = Boolean.getBoolean(\"linkenvisuals.naruto.force\")\n                        ? 1.0F : MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);"
assert t.count(old3) == 1
t = t.replace(old3, new3, 1)
p.write_text(t, encoding="utf-8", newline="\n")
print("ok")
