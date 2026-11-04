package ru.method.linkenvisuals.cosmetics.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.bedrock.CosmeticModelRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class CosmeticTextureRegistry {
    private static final List<String> TEXTURE_IDS = List.of(
            "cosmetics/icons/crown.png",
            "cosmetics/icons/halo.png",
            "cosmetics/icons/horns.png",
            "cosmetics/icons/cap.png",
            "cosmetics/icons/angel.png",
            "cosmetics/icons/demon.png",
            "cosmetics/icons/effect_halo.png",
            "cosmetics/icons/effect_spheres.png",
            "cosmetics/icons/effect_trail.png",
            "cosmetics/models/hat_crown.png",
            "cosmetics/models/hat_halo.png",
            "cosmetics/models/hat_horns.png",
            "cosmetics/models/hat_cap.png",
            "cosmetics/models/hat_caramel_frappuccino.png",
            "cosmetics/models/hat_straw_hat.png",
            "cosmetics/models/hat_top_hat.png",
            "cosmetics/models/head_gas_mask.png",
            "cosmetics/models/head_satellite_dish.png",
            "cosmetics/models/head_headlamp.png",
            "cosmetics/models/head_headphones.png",
            "cosmetics/models/head_ski_goggles.png",
            "cosmetics/models/head_bunny_ears.png",
            "cosmetics/models/body_safety_vest.png",
            "cosmetics/models/body_kevler_vest.png",
            "cosmetics/models/body_tactical_belt.png",
            "cosmetics/models/body_quiver.png",
            "cosmetics/models/body_backpack.png",
            "cosmetics/models/body_shark_fin.png",
            "cosmetics/models/shield_knight_shield.png",
            "cosmetics/models/shield_edgy_shield.png",
            "cosmetics/models/shield_rounded_shield.png",
            "cosmetics/models/shield_tactical_shield.png",
            "cosmetics/models/wings_angel.png",
            "cosmetics/models/wings_demon.png"
    );

    private static final Map<String, String> MORECOSMETICS_JSON = Map.of(
            "cosmetics/models/hat_crown.png", "1.json",
            "cosmetics/models/hat_halo.png", "21.json",
            "cosmetics/models/hat_horns.png", "7.json",
            "cosmetics/models/hat_cap.png", "3.json",
            "cosmetics/models/wings_angel.png", "24.json",
            "cosmetics/models/wings_demon.png", "23.json"
    );

    private static final Map<String, String> ICON_MODEL_FALLBACK = Map.of(
            "cosmetics/icons/crown.png", "cosmetics/models/hat_crown.png",
            "cosmetics/icons/halo.png", "cosmetics/models/hat_halo.png",
            "cosmetics/icons/horns.png", "cosmetics/models/hat_horns.png",
            "cosmetics/icons/cap.png", "cosmetics/models/hat_cap.png",
            "cosmetics/icons/angel.png", "cosmetics/models/wings_angel.png",
            "cosmetics/icons/demon.png", "cosmetics/models/wings_demon.png"
    );

    private CosmeticTextureRegistry() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LinkenVisuals.id("cosmetics/texture_registry");
            }

            @Override
            public void reload(ResourceManager manager) {
                CosmeticTextureRegistry.reload(manager);
            }
        });
    }

    public static void reload(ResourceManager manager) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        int loaded = 0;
        for (String path : TEXTURE_IDS) {
            if (loadTexture(manager, client, LinkenVisuals.id(path))) {
                loaded++;
            }
        }

        CosmeticModelRepository.clearCache();
        ru.method.linkenvisuals.cosmetics.character.RudyCharacter.reset();
        ru.method.linkenvisuals.cosmetics.character.RudyCharacterIcon.reset();
        LinkenVisuals.LOGGER.info("[Cosmetics] Registered {}/{} textures", loaded, TEXTURE_IDS.size());
    }

    private static boolean loadTexture(ResourceManager manager, MinecraftClient client, Identifier textureId) {
        NativeImage image = resolveImage(manager, textureId);
        if (image == null) {
            LinkenVisuals.LOGGER.warn("[Cosmetics] Missing texture {}", textureId);
            return false;
        }

        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "cosmetic", image);
            client.getTextureManager().registerTexture(textureId, texture);
            return true;
        } catch (Exception e) {
            image.close();
            LinkenVisuals.LOGGER.warn("[Cosmetics] Failed to register texture {}", textureId, e);
            return false;
        }
    }

    private static NativeImage resolveImage(ResourceManager manager, Identifier textureId) {
        NativeImage image = readFromPack(manager, textureId);
        if (image != null) {
            return image;
        }

        image = readFromEmbedded(textureId);
        if (image != null) {
            return image;
        }

        image = readFromMoreCosmetics(textureId);
        if (image != null) {
            return image;
        }

        String modelPath = ICON_MODEL_FALLBACK.get(textureId.getPath());
        if (modelPath != null) {
            image = resolveImage(manager, LinkenVisuals.id(modelPath));
            if (image != null) {
                return image;
            }
        }

        return null;
    }

    private static NativeImage readFromPack(ResourceManager manager, Identifier textureId) {
        NativeImage fromClasspath = readFromClasspath(textureId);
        if (fromClasspath != null) {
            return fromClasspath;
        }

        for (Identifier resourceId : resourceCandidates(textureId)) {
            try (InputStream stream = manager.open(resourceId)) {
                return NativeImage.read(stream);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static NativeImage readFromClasspath(Identifier textureId) {
        String path = "/assets/" + textureId.getNamespace() + "/textures/" + textureId.getPath();
        try (InputStream stream = CosmeticTextureRegistry.class.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            NativeImage image = NativeImage.read(stream);
            LinkenVisuals.LOGGER.debug("[Cosmetics] Loaded texture {} from classpath {}", textureId, path);
            return image;
        } catch (Exception e) {
            LinkenVisuals.LOGGER.debug("[Cosmetics] Classpath miss for {}", textureId);
            return null;
        }
    }

    private static List<Identifier> resourceCandidates(Identifier textureId) {
        String path = textureId.getPath();
        String namespace = textureId.getNamespace();
        String withoutExt = path.endsWith(".png") ? path.substring(0, path.length() - 4) : path;
        return List.of(
                Identifier.of(namespace, "textures/" + path),
                Identifier.of(namespace, "textures/" + withoutExt + ".png"),
                Identifier.of(namespace, path),
                Identifier.of(namespace, withoutExt + ".png")
        );
    }

    private static NativeImage readFromEmbedded(Identifier textureId) {
        byte[] bytes = EmbeddedCosmeticTextures.decode(textureId.getPath());
        if (bytes == null) {
            return null;
        }
        try (InputStream stream = new java.io.ByteArrayInputStream(bytes)) {
            return NativeImage.read(stream);
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[Cosmetics] Failed to decode embedded texture {}", textureId, e);
            return null;
        }
    }

    private static NativeImage readFromMoreCosmetics(Identifier textureId) {
        String jsonName = MORECOSMETICS_JSON.get(textureId.getPath());
        if (jsonName == null) {
            return null;
        }

        Path jsonPath = FabricLoader.getInstance().getGameDir()
                .resolve("MoreCosmetics/data/models")
                .resolve(jsonName);
        if (!Files.isRegularFile(jsonPath)) {
            return null;
        }

        try {
            String raw = Files.readString(jsonPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("texture") || !root.get("texture").isJsonPrimitive()) {
                return null;
            }
            String base64 = root.get("texture").getAsString();
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (InputStream stream = new java.io.ByteArrayInputStream(bytes)) {
                NativeImage image = NativeImage.read(stream);
                LinkenVisuals.LOGGER.info("[Cosmetics] Loaded fallback texture {} from {}", textureId, jsonPath.getFileName());
                return image;
            }
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[Cosmetics] Failed to read MoreCosmetics fallback for {}", textureId, e);
            return null;
        }
    }

    private static int argb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return argb(
                Math.round(r * factor),
                Math.round(g * factor),
                Math.round(b * factor)
        );
    }

    private static int blend(int fromRgb, int toRgb, float t) {
        float clamped = MathHelper.clamp(t, 0f, 1f);
        int fr = (fromRgb >> 16) & 0xFF;
        int fg = (fromRgb >> 8) & 0xFF;
        int fb = fromRgb & 0xFF;
        int tr = (toRgb >> 16) & 0xFF;
        int tg = (toRgb >> 8) & 0xFF;
        int tb = toRgb & 0xFF;
        return argb(
                Math.round(fr + (tr - fr) * clamped),
                Math.round(fg + (tg - fg) * clamped),
                Math.round(fb + (tb - fb) * clamped)
        );
    }
}
