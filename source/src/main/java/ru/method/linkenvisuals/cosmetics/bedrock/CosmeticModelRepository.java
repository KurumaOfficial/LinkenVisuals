package ru.method.linkenvisuals.cosmetics.bedrock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticModelRepository {
    private static final Map<Identifier, BedrockModel> CACHE = new ConcurrentHashMap<>();

    private CosmeticModelRepository() {
    }

    public static BedrockModel get(Identifier modelAsset) {
        if (modelAsset == null) {
            return null;
        }
        return CACHE.computeIfAbsent(modelAsset, CosmeticModelRepository::loadModel);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static BedrockModel loadModel(Identifier assetId) {
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        try {
            Resource resource = resources.getResource(assetId).orElseThrow();
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            return BedrockModelParser.parse(root);
        } catch (Exception e) {
            LinkenVisuals.LOGGER.warn("[Cosmetics] Failed to load model asset {}", assetId, e);
            return null;
        }
    }
}
