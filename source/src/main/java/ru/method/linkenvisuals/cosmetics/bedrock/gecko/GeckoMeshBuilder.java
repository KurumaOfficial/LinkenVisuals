package ru.method.linkenvisuals.cosmetics.bedrock.gecko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds GeckoLib-compatible mesh data from Bedrock geometry JSON,
 * matching {@code GeoBuilder} + {@code GeoCube.createFromPojoCube}.
 */
public final class GeckoMeshBuilder {
    private GeckoMeshBuilder() {
    }

    public static List<GeckoBuiltBone> build(JsonObject geometry, int textureWidth, int textureHeight) {
        if (geometry == null || !geometry.has("bones") || !geometry.get("bones").isJsonArray()) {
            return List.of();
        }

        JsonArray bonesArray = geometry.getAsJsonArray("bones");
        Map<String, RawBone> bonesByName = new HashMap<>();
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
            Float boneInflate = boneObject.has("inflate") ? boneObject.get("inflate").getAsFloat() : null;
            Boolean boneMirror = boneObject.has("mirror") && boneObject.get("mirror").getAsBoolean() ? Boolean.TRUE : null;

            List<GeckoBuiltCube> cubes = buildCubes(boneObject, textureWidth, textureHeight, boneInflate, boneMirror);
            bonesByName.put(name, new RawBone(name, pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, cubes));
            insertionOrder.add(name);

            if (boneObject.has("parent")) {
                parentByName.put(name, boneObject.get("parent").getAsString());
            }
        }

        Map<String, List<String>> childrenByParent = new HashMap<>();
        List<String> rootNames = new ArrayList<>();
        for (String name : insertionOrder) {
            String parentName = parentByName.get(name);
            if (parentName == null || parentName.isBlank() || !bonesByName.containsKey(parentName)) {
                rootNames.add(name);
            } else {
                childrenByParent.computeIfAbsent(parentName, ignored -> new ArrayList<>()).add(name);
            }
        }

        List<GeckoBuiltBone> roots = new ArrayList<>();
        for (String rootName : rootNames) {
            RawBone raw = bonesByName.get(rootName);
            if (raw != null) {
                roots.add(buildBoneTree(raw, childrenByParent, bonesByName));
            }
        }
        return roots;
    }

    private static GeckoBuiltBone buildBoneTree(
            RawBone raw,
            Map<String, List<String>> childrenByParent,
            Map<String, RawBone> bonesByName
    ) {
        List<String> childNames = childrenByParent.getOrDefault(raw.name, List.of());
        List<GeckoBuiltBone> children = new ArrayList<>();
        for (String childName : childNames) {
            RawBone childRaw = bonesByName.get(childName);
            if (childRaw != null) {
                children.add(buildBoneTree(childRaw, childrenByParent, bonesByName));
            }
        }
        float rotX = (float) Math.toRadians(-raw.rotationX);
        float rotY = (float) Math.toRadians(-raw.rotationY);
        float rotZ = (float) Math.toRadians(raw.rotationZ);
        return new GeckoBuiltBone(
                raw.name,
                -raw.pivotX,
                raw.pivotY,
                raw.pivotZ,
                rotX,
                rotY,
                rotZ,
                raw.cubes,
                children
        );
    }

    private static List<GeckoBuiltCube> buildCubes(
            JsonObject boneObject,
            int textureWidth,
            int textureHeight,
            Float boneInflate,
            Boolean boneMirror
    ) {
        if (!boneObject.has("cubes") || !boneObject.get("cubes").isJsonArray()) {
            return List.of();
        }

        List<GeckoBuiltCube> cubes = new ArrayList<>();
        for (JsonElement element : boneObject.getAsJsonArray("cubes")) {
            if (!element.isJsonObject()) {
                continue;
            }
            GeckoBuiltCube cube = buildCube(element.getAsJsonObject(), textureWidth, textureHeight, boneInflate, boneMirror);
            if (cube != null) {
                cubes.add(cube);
            }
        }
        return cubes;
    }

    private static GeckoBuiltCube buildCube(
            JsonObject cubeObject,
            int textureWidth,
            int textureHeight,
            Float boneInflate,
            Boolean boneMirror
    ) {
        float sizeX = parseFloat(cubeObject, "size", 0, 0f);
        float sizeY = parseFloat(cubeObject, "size", 1, 0f);
        float sizeZ = parseFloat(cubeObject, "size", 2, 0f);

        float originX = parseFloat(cubeObject, "origin", 0, 0f);
        float originY = parseFloat(cubeObject, "origin", 1, 0f);
        float originZ = parseFloat(cubeObject, "origin", 2, 0f);

        double inflate = cubeObject.has("inflate")
                ? cubeObject.get("inflate").getAsDouble() / 16.0
                : (boneInflate == null ? 0.0 : boneInflate / 16.0);

        Boolean mirror = cubeObject.has("mirror") && cubeObject.get("mirror").getAsBoolean()
                ? Boolean.TRUE
                : boneMirror;

        float texW = Math.max(1, textureWidth);
        float texH = Math.max(1, textureHeight);

        double ox = -(originX + sizeX) / 16.0;
        double oy = originY / 16.0;
        double oz = originZ / 16.0;
        double sx = sizeX * 0.0625;
        double sy = sizeY * 0.0625;
        double sz = sizeZ * 0.0625;

        float rotX = (float) Math.toRadians(-parseFloat(cubeObject, "rotation", 0, 0f));
        float rotY = (float) Math.toRadians(-parseFloat(cubeObject, "rotation", 1, 0f));
        float rotZ = (float) Math.toRadians(parseFloat(cubeObject, "rotation", 2, 0f));

        float pivotX = parseFloat(cubeObject, "pivot", 0, originX + sizeX * 0.5f) * -1f;
        float pivotY = parseFloat(cubeObject, "pivot", 1, originY + sizeY * 0.5f);
        float pivotZ = parseFloat(cubeObject, "pivot", 2, originZ + sizeZ * 0.5f);

        GeckoVertex p1 = vtx(ox - inflate, oy - inflate, oz - inflate);
        GeckoVertex p2 = vtx(ox - inflate, oy - inflate, oz + sz + inflate);
        GeckoVertex p3 = vtx(ox - inflate, oy + sy + inflate, oz - inflate);
        GeckoVertex p4 = vtx(ox - inflate, oy + sy + inflate, oz + sz + inflate);
        GeckoVertex p5 = vtx(ox + sx + inflate, oy - inflate, oz - inflate);
        GeckoVertex p6 = vtx(ox + sx + inflate, oy - inflate, oz + sz + inflate);
        GeckoVertex p7 = vtx(ox + sx + inflate, oy + sy + inflate, oz - inflate);
        GeckoVertex p8 = vtx(ox + sx + inflate, oy + sy + inflate, oz + sz + inflate);

        List<GeckoQuad> quads = new ArrayList<>(6);
        boolean isBoxUv = cubeObject.has("uv") && cubeObject.get("uv").isJsonArray();

        if (isBoxUv) {
            float uvU = parseFloat(cubeObject, "uv", 0, 0f);
            float uvV = parseFloat(cubeObject, "uv", 1, 0f);
            double uvSizeX = Math.floor(sizeX);
            double uvSizeY = Math.floor(sizeY);
            double uvSizeZ = Math.floor(sizeZ);
            boolean mirrored = Boolean.TRUE.equals(mirror);
            if (mirrored) {
                addQuad(quads, quad(p7, p8, p6, p5, uvU + uvSizeZ + uvSizeX, uvV + uvSizeZ, uvSizeZ, uvSizeY, texW, texH, mirror, -1, 0, 0));
                addQuad(quads, quad(p4, p3, p1, p2, uvU, uvV + uvSizeZ, uvSizeZ, uvSizeY, texW, texH, mirror, 1, 0, 0));
            } else {
                addQuad(quads, quad(p4, p3, p1, p2, uvU + uvSizeZ + uvSizeX, uvV + uvSizeZ, uvSizeZ, uvSizeY, texW, texH, mirror, -1, 0, 0));
                addQuad(quads, quad(p7, p8, p6, p5, uvU, uvV + uvSizeZ, uvSizeZ, uvSizeY, texW, texH, mirror, 1, 0, 0));
            }
            addQuad(quads, quad(p3, p7, p5, p1, uvU + uvSizeZ, uvV + uvSizeZ, uvSizeX, uvSizeY, texW, texH, mirror, 0, 0, -1));
            addQuad(quads, quad(p8, p4, p2, p6, uvU + uvSizeZ + uvSizeX + uvSizeZ, uvV + uvSizeZ, uvSizeX, uvSizeY, texW, texH, mirror, 0, 0, 1));
            addQuad(quads, quad(p4, p8, p7, p3, uvU + uvSizeZ, uvV, uvSizeX, uvSizeZ, texW, texH, mirror, 0, 1, 0));
            addQuad(quads, quad(p2, p6, p5, p1, uvU + uvSizeZ + uvSizeX, uvV, uvSizeX, uvSizeZ, texW, texH, mirror, 0, -1, 0));
        } else if (cubeObject.has("uv") && cubeObject.get("uv").isJsonObject()) {
            JsonObject uvObject = cubeObject.getAsJsonObject("uv");
            boolean mirrored = Boolean.TRUE.equals(mirror);
            if (mirrored) {
                addFaceQuad(quads, uvObject, "west", p7, p8, p6, p5, texW, texH, mirror, -1, 0, 0);
                addFaceQuad(quads, uvObject, "east", p4, p3, p1, p2, texW, texH, mirror, 1, 0, 0);
            } else {
                addFaceQuad(quads, uvObject, "west", p4, p3, p1, p2, texW, texH, mirror, -1, 0, 0);
                addFaceQuad(quads, uvObject, "east", p7, p8, p6, p5, texW, texH, mirror, 1, 0, 0);
            }
            addFaceQuad(quads, uvObject, "north", p3, p7, p5, p1, texW, texH, mirror, 0, 0, -1);
            addFaceQuad(quads, uvObject, "south", p8, p4, p2, p6, texW, texH, mirror, 0, 0, 1);
            addFaceQuad(quads, uvObject, "up", p4, p8, p7, p3, texW, texH, mirror, 0, 1, 0);
            addFaceQuad(quads, uvObject, "down", p1, p5, p6, p2, texW, texH, mirror, 0, -1, 0);
        }

        if (quads.isEmpty()) {
            return null;
        }
        return new GeckoBuiltCube(pivotX, pivotY, pivotZ, rotX, rotY, rotZ, (float) sx, (float) sy, (float) sz, quads);
    }

    private static void addFaceQuad(
            List<GeckoQuad> quads,
            JsonObject uvObject,
            String face,
            GeckoVertex v0,
            GeckoVertex v1,
            GeckoVertex v2,
            GeckoVertex v3,
            float texW,
            float texH,
            Boolean mirror,
            float nx,
            float ny,
            float nz
    ) {
        if (!uvObject.has(face) || !uvObject.get(face).isJsonObject()) {
            return;
        }
        JsonObject faceObject = uvObject.getAsJsonObject(face);
        float u = parseFloat(faceObject, "uv", 0, 0f);
        float v = parseFloat(faceObject, "uv", 1, 0f);
        float uw = parseFloat(faceObject, "uv_size", 0, 0f);
        float vh = parseFloat(faceObject, "uv_size", 1, 0f);
        if (uw == 0f || vh == 0f) {
            return;
        }
        addQuad(quads, quad(v0, v1, v2, v3, u, v, uw, vh, texW, texH, mirror, nx, ny, nz));
    }

    private static void addQuad(List<GeckoQuad> quads, GeckoQuad quad) {
        if (quad != null) {
            quads.add(quad);
        }
    }

    private static GeckoQuad quad(
            GeckoVertex v0,
            GeckoVertex v1,
            GeckoVertex v2,
            GeckoVertex v3,
            double u,
            double v,
            double uSize,
            double vSize,
            float texW,
            float texH,
            Boolean mirror,
            float nx,
            float ny,
            float nz
    ) {
        float uStart = (float) u;
        float vStart = (float) v;
        float uEnd = (float) (u + uSize);
        float vEnd = (float) (v + vSize);
        float tu1 = uStart / texW;
        float tu2 = uEnd / texW;
        float tv1 = vStart / texH;
        float tv2 = vEnd / texH;

        GeckoVertex[] verts = new GeckoVertex[]{
                copy(v0),
                copy(v1),
                copy(v2),
                copy(v3)
        };

        if (mirror != null && mirror) {
            verts[0].withUv(tu1, tv1);
            verts[1].withUv(tu2, tv1);
            verts[2].withUv(tu2, tv2);
            verts[3].withUv(tu1, tv2);
        } else {
            verts[0].withUv(tu2, tv1);
            verts[1].withUv(tu1, tv1);
            verts[2].withUv(tu1, tv2);
            verts[3].withUv(tu2, tv2);
        }
        return new GeckoQuad(verts, nx, ny, nz);
    }

    private static GeckoVertex vtx(double x, double y, double z) {
        return new GeckoVertex((float) x, (float) y, (float) z);
    }

    private static GeckoVertex copy(GeckoVertex source) {
        return new GeckoVertex(source.x, source.y, source.z);
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

    private record RawBone(
            String name,
            float pivotX,
            float pivotY,
            float pivotZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            List<GeckoBuiltCube> cubes
    ) {
    }
}
