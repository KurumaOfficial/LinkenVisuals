package ru.method.linkenvisuals.cosmetics.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoBuiltBone;
import ru.method.linkenvisuals.cosmetics.bedrock.gecko.GeckoMeshBuilder;
import ru.method.linkenvisuals.cosmetics.model.CosmeticAnchor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BedrockModelParser {
    private BedrockModelParser() {
    }

    public static BedrockModel parse(JsonObject root) {
        if (root == null) {
            return emptyModel();
        }

        Identifier texture = parseIdentifier(root, "texture");
        CosmeticAnchor anchor = parseAnchor(root);
        float offsetX = parseMoreCosmeticsFloat(root, "offsetX", "x", 0f);
        float offsetY = parseMoreCosmeticsFloat(root, "offsetY", "y", 0f);
        float offsetZ = parseMoreCosmeticsFloat(root, "offsetZ", "z", 0f);
        float scale = parseFloat(root, "scale", 1f);
        float yaw = parseFloat(root, "yaw", 0f);
        float pitch = parseFloat(root, "pitch", 0f);
        float roll = parseFloat(root, "roll", 0f);
        float previewY = parseFloat(root, "previewY", 0.5f);
        float previewScale = parseFloat(root, "previewScale", 1f);
        MoreCosmeticsAnimationSet animations = root.has("animation") && root.get("animation").isJsonObject()
                ? MoreCosmeticsAnimationSet.parse(root.getAsJsonObject("animation"))
                : MoreCosmeticsAnimationSet.empty();

        JsonObject geometryRoot = root.has("geometry") && root.get("geometry").isJsonObject()
                ? root.getAsJsonObject("geometry")
                : root;

        JsonArray geometries = geometryRoot.has("minecraft:geometry")
                ? geometryRoot.getAsJsonArray("minecraft:geometry")
                : null;
        if (geometries == null || geometries.isEmpty()) {
            return new BedrockModel(texture, anchor, offsetX, offsetY, offsetZ, scale, yaw, pitch, roll, previewY, previewScale, 64, 64, List.of(), List.of(), animations);
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
        int textureWidth = parseInt(description, "texture_width", 64);
        int textureHeight = parseInt(description, "texture_height", 64);
        List<BedrockBone> roots = parseBones(geometry);
        List<GeckoBuiltBone> geckoRoots = GeckoMeshBuilder.build(geometry, textureWidth, textureHeight);
        return new BedrockModel(texture, anchor, offsetX, offsetY, offsetZ, scale, yaw, pitch, roll, previewY, previewScale, textureWidth, textureHeight, roots, geckoRoots, animations);
    }

    private static float parseMoreCosmeticsFloat(JsonObject root, String primaryKey, String fallbackKey, float fallback) {
        if (root.has(primaryKey)) {
            return parseFloat(root, primaryKey, fallback);
        }
        if (root.has(fallbackKey)) {
            return parseFloat(root, fallbackKey, fallback);
        }
        return fallback;
    }

    private static List<BedrockBone> parseBones(JsonObject geometry) {
        if (!geometry.has("bones") || !geometry.get("bones").isJsonArray()) {
            return List.of();
        }

        JsonArray bonesArray = geometry.getAsJsonArray("bones");
        Map<String, BedrockBone> bonesByName = new HashMap<>();
        Map<String, String> parentByName = new HashMap<>();
        List<String> insertionOrder = new ArrayList<>();

        for (JsonElement element : bonesArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject boneObject = element.getAsJsonObject();
            String name = boneObject.has("name") ? boneObject.get("name").getAsString() : "";
            if (name.isBlank()) {
                continue;
            }

            float pivotX = parseFloat(boneObject, "pivot", 0, 0f);
            float pivotY = parseFloat(boneObject, "pivot", 1, 0f);
            float pivotZ = parseFloat(boneObject, "pivot", 2, 0f);
            float rotationX = parseFloat(boneObject, "rotation", 0, 0f);
            float rotationY = parseFloat(boneObject, "rotation", 1, 0f);
            float rotationZ = parseFloat(boneObject, "rotation", 2, 0f);
            List<BedrockCube> cubes = parseCubes(boneObject);
            bonesByName.put(name, new BedrockBone(name, pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, cubes));
            insertionOrder.add(name);

            if (boneObject.has("parent")) {
                parentByName.put(name, boneObject.get("parent").getAsString());
            }
        }

        List<BedrockBone> roots = new ArrayList<>();
        for (String name : insertionOrder) {
            BedrockBone bone = bonesByName.get(name);
            if (bone == null) {
                continue;
            }
            String parentName = parentByName.get(name);
            if (parentName == null || parentName.isBlank()) {
                roots.add(bone);
                continue;
            }
            BedrockBone parent = bonesByName.get(parentName);
            if (parent != null) {
                parent.addChild(bone);
            } else {
                roots.add(bone);
            }
        }
        return roots;
    }

    private static List<BedrockCube> parseCubes(JsonObject boneObject) {
        if (!boneObject.has("cubes") || !boneObject.get("cubes").isJsonArray()) {
            return List.of();
        }

        List<BedrockCube> cubes = new ArrayList<>();
        for (JsonElement element : boneObject.getAsJsonArray("cubes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject cubeObject = element.getAsJsonObject();
            float originX = parseFloat(cubeObject, "origin", 0, 0f);
            float originY = parseFloat(cubeObject, "origin", 1, 0f);
            float originZ = parseFloat(cubeObject, "origin", 2, 0f);
            float sizeX = parseFloat(cubeObject, "size", 0, 0f);
            float sizeY = parseFloat(cubeObject, "size", 1, 0f);
            float sizeZ = parseFloat(cubeObject, "size", 2, 0f);
            float pivotX = parseFloat(cubeObject, "pivot", 0, originX + sizeX * 0.5f);
            float pivotY = parseFloat(cubeObject, "pivot", 1, originY + sizeY * 0.5f);
            float pivotZ = parseFloat(cubeObject, "pivot", 2, originZ + sizeZ * 0.5f);
            float rotationX = parseFloat(cubeObject, "rotation", 0, 0f);
            float rotationY = parseFloat(cubeObject, "rotation", 1, 0f);
            float rotationZ = parseFloat(cubeObject, "rotation", 2, 0f);
            boolean mirror = cubeObject.has("mirror") && cubeObject.get("mirror").getAsBoolean();
            float inflate = parseFloat(cubeObject, "inflate", 0f);
            if (inflate != 0f) {
                originX -= inflate;
                originY -= inflate;
                originZ -= inflate;
                sizeX += inflate * 2f;
                sizeY += inflate * 2f;
                sizeZ += inflate * 2f;
            }

            JsonObject uvObject = cubeObject.has("uv") && cubeObject.get("uv").isJsonObject()
                    ? cubeObject.getAsJsonObject("uv")
                    : null;

            BedrockFaceUv north;
            BedrockFaceUv east;
            BedrockFaceUv south;
            BedrockFaceUv west;
            BedrockFaceUv up;
            BedrockFaceUv down;

            if (uvObject != null) {
                north = parseFace(uvObject, "north");
                east = parseFace(uvObject, "east");
                south = parseFace(uvObject, "south");
                west = parseFace(uvObject, "west");
                up = parseFace(uvObject, "up");
                down = parseFace(uvObject, "down");
            } else if (cubeObject.has("uv") && cubeObject.get("uv").isJsonArray()) {
                float uvU = parseFloat(cubeObject, "uv", 0, 0f);
                float uvV = parseFloat(cubeObject, "uv", 1, 0f);
                north = boxFace(uvU + sizeZ, uvV + sizeZ, sizeX, sizeY);
                east = boxFace(uvU + sizeZ + sizeX, uvV + sizeZ, sizeZ, sizeY);
                south = boxFace(uvU + sizeZ + sizeX + sizeZ, uvV + sizeZ, sizeX, sizeY);
                west = boxFace(uvU, uvV + sizeZ, sizeZ, sizeY);
                up = boxFace(uvU + sizeZ, uvV, sizeX, sizeZ);
                down = boxFace(uvU + sizeZ + sizeX, uvV, sizeX, sizeZ);
            } else {
                north = BedrockFaceUv.EMPTY;
                east = BedrockFaceUv.EMPTY;
                south = BedrockFaceUv.EMPTY;
                west = BedrockFaceUv.EMPTY;
                up = BedrockFaceUv.EMPTY;
                down = BedrockFaceUv.EMPTY;
            }

            cubes.add(new BedrockCube(
                    originX,
                    originY,
                    originZ,
                    sizeX,
                    sizeY,
                    sizeZ,
                    pivotX,
                    pivotY,
                    pivotZ,
                    rotationX,
                    rotationY,
                    rotationZ,
                    mirror,
                    north,
                    east,
                    south,
                    west,
                    up,
                    down
            ));
        }
        return cubes;
    }

    private static BedrockFaceUv boxFace(float u, float v, float width, float height) {
        if (width == 0f || height == 0f) {
            return BedrockFaceUv.EMPTY;
        }
        return new BedrockFaceUv(u, v, width, height);
    }

    private static BedrockFaceUv parseFace(JsonObject uvObject, String face) {
        if (!uvObject.has(face) || !uvObject.get(face).isJsonObject()) {
            return BedrockFaceUv.EMPTY;
        }
        JsonObject faceObject = uvObject.getAsJsonObject(face);
        float u = parseFloat(faceObject, "uv", 0, 0f);
        float v = parseFloat(faceObject, "uv", 1, 0f);
        float width = parseFloat(faceObject, "uv_size", 0, 0f);
        float height = parseFloat(faceObject, "uv_size", 1, 0f);
        return new BedrockFaceUv(u, v, width, height);
    }

    private static CosmeticAnchor parseAnchor(JsonObject root) {
        if (root.has("anchor")) {
            String raw = root.get("anchor").getAsString();
            if (raw != null && "body".equalsIgnoreCase(raw.trim())) {
                return CosmeticAnchor.BODY;
            }
            if (raw != null && "head".equalsIgnoreCase(raw.trim())) {
                return CosmeticAnchor.HEAD;
            }
            if (raw != null && "left_arm".equalsIgnoreCase(raw.trim())) {
                return CosmeticAnchor.LEFT_ARM;
            }
            if (raw != null && "right_arm".equalsIgnoreCase(raw.trim())) {
                return CosmeticAnchor.RIGHT_ARM;
            }
            if (raw != null && "pet".equalsIgnoreCase(raw.trim())) {
                return CosmeticAnchor.PET;
            }
        }
        if (root.has("category")) {
            return switch (root.get("category").getAsInt()) {
                case 3, 5 -> CosmeticAnchor.BODY;
                case 4 -> CosmeticAnchor.LEFT_ARM;
                case 6 -> CosmeticAnchor.RIGHT_ARM;
                case 7 -> CosmeticAnchor.PET;
                default -> CosmeticAnchor.HEAD;
            };
        }
        return CosmeticAnchor.HEAD;
    }

    private static Identifier parseIdentifier(JsonObject root, String key) {
        if (!root.has(key)) {
            return null;
        }
        String raw = root.get(key).getAsString().trim();
        // Resource paths emitted by Blockbench/import tools are commonly namespace-less.
        // They belong to this mod, not to minecraft; treating them as minecraft:* produces
        // the black/purple missing-texture checkerboard.
        if (!raw.contains(":")) {
            return normalizeTextureIdentifier(LinkenVisuals.id(raw));
        }
        Identifier parsed = Identifier.tryParse(raw);
        if (parsed != null) {
            return normalizeTextureIdentifier(parsed);
        }
        return normalizeTextureIdentifier(LinkenVisuals.id(raw));
    }

    private static Identifier normalizeTextureIdentifier(Identifier identifier) {
        if (identifier == null) {
            return null;
        }
        String path = identifier.getPath();
        if (path.startsWith("textures/")) {
            return Identifier.of(identifier.getNamespace(), path.substring("textures/".length()));
        }
        return identifier;
    }

    private static float parseFloat(JsonObject object, String key, float fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(JsonObject object, String arrayKey, int index, float fallback) {
        if (object == null || !object.has(arrayKey) || !object.get(arrayKey).isJsonArray()) {
            return fallback;
        }
        JsonArray array = object.getAsJsonArray(arrayKey);
        if (index < 0 || index >= array.size()) {
            return fallback;
        }
        try {
            return array.get(index).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static BedrockModel emptyModel() {
        return new BedrockModel(null, CosmeticAnchor.HEAD, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0.5f, 1f, 64, 64, List.of(), List.of(), MoreCosmeticsAnimationSet.empty());
    }
}
