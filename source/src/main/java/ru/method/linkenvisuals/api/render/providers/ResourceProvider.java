package ru.method.linkenvisuals.api.render.providers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.method.linkenvisuals.LinkenVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class ResourceProvider {

	private static final Gson GSON = new Gson();

    private static ResourceManager resourceManager() {
        return MinecraftClient.getInstance().getResourceManager();
    }
	
	public static Identifier getShaderIdentifier(String name) {
		return LinkenVisuals.id("core/" + name);
	}

	public static JsonObject toJson(Identifier identifier) {
		return JsonParser.parseString(toString(identifier)).getAsJsonObject();
	}

	public static <T> T fromJsonToInstance(Identifier identifier, Class<T> clazz) {
		return GSON.fromJson(toString(identifier), clazz);
	}

	public static String toString(Identifier identifier) {
		return toString(identifier, "\n");
	}
	
	public static String toString(Identifier identifier, String delimiter) {
		try(InputStream inputStream = resourceManager().open(identifier);
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining(delimiter));
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
