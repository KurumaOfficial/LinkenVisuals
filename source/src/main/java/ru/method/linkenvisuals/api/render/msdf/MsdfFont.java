package ru.method.linkenvisuals.api.render.msdf;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import ru.method.linkenvisuals.LinkenVisuals;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import ru.method.linkenvisuals.api.render.msdf.FontData.AtlasData;
import ru.method.linkenvisuals.api.render.msdf.FontData.GlyphData;
import ru.method.linkenvisuals.api.render.msdf.FontData.MetricsData;
import ru.method.linkenvisuals.api.render.providers.ResourceProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

public final class MsdfFont {

	private final String name;
	private final AbstractTexture texture;
	private final AtlasData atlas;
	private final MetricsData metrics;
	// fastutil: лукап по примитивному int — без автобоксинга Integer на каждый символ (вся кириллица > 127)
	private final Int2ObjectOpenHashMap<MsdfGlyph> glyphs;
	private final Int2ObjectOpenHashMap<Int2FloatOpenHashMap> kernings;
	private volatile boolean linearFilteringApplied;

	// Ширина строки не зависит от кадра (метрики глифов статичны), а GUI меряет одни и те же
	// подписи каждый кадр — кешируем по (size|text). ponytail: грубый сброс при переполнении,
	// LRU не нужен (набор строк почти статичен, динамику вроде таймеров просто пересчитаем).
	private final java.util.Map<Long, java.util.Map<String, Float>> widthCache = new java.util.HashMap<>();

	private MsdfFont(String name, AbstractTexture texture, AtlasData atlas, MetricsData metrics, Int2ObjectOpenHashMap<MsdfGlyph> glyphs, Int2ObjectOpenHashMap<Int2FloatOpenHashMap> kernings) {
		this.name = name;
		this.texture = texture;
		this.atlas = atlas;
		this.metrics = metrics;
		this.glyphs = glyphs;
		this.kernings = kernings;
	}

	public int getTextureId() {
		if (this.texture != null && this.texture.getGlTexture() instanceof net.minecraft.client.texture.GlTexture gl) {
			return gl.getGlId();
		}
		return 0;
	}

	public void ensureLinearFiltering() {
		linearFilteringApplied = true;
	}

	public void applyGlyphs(Matrix4f matrix, VertexConsumer consumer, String text, float size, float thickness, float spacing, float x, float y, float z, int color) {
		int prevChar = -1;
		for (int i = 0; i < text.length(); i++) {
			int _char = text.charAt(i);
			MsdfGlyph glyph = this.glyphs.get(_char);
			
			if (glyph == null) continue;

			Int2FloatOpenHashMap kerning = this.kernings.get(prevChar);
			if (kerning != null) {
				x += kerning.get(_char) * size;
			}

			x += glyph.apply(matrix, consumer, size, x, y, z, color) + thickness + spacing;
			prevChar = _char;
		}
	}
	
	public float getWidth(String text, float size) {
		if (text.isEmpty()) return 0.0f;
		long sizeKey = Float.floatToRawIntBits(size) & 0xFFFFFFFFL;
		java.util.Map<String, Float> bySize = widthCache.get(sizeKey);
		if (bySize == null) {
			bySize = new java.util.HashMap<>();
			widthCache.put(sizeKey, bySize);
		}
		Float cached = bySize.get(text);
		if (cached != null) return cached;
		float computed = computeWidth(text, size);
		if (bySize.size() > 4096) bySize.clear();
		bySize.put(text, computed);
		return computed;
	}

	private float computeWidth(String text, float size) {
		int prevChar = -1;
		float width = 0.0f;
		for (int i = 0; i < text.length(); i++) {
			int _char = text.charAt(i);
			MsdfGlyph glyph = this.glyphs.get(_char);
			if (glyph == null) continue;
			Int2FloatOpenHashMap kerning = this.kernings.get(prevChar);
			if (kerning != null) width += kerning.get(_char) * size;
			width += (glyph.getWidth(size) * 1.05f);
			prevChar = _char;
		}
		
		return width;
	}

	public float getHeight(float size) {
		return ((metrics.ascender() * size) + (Math.abs(metrics.descender() * size))) * 1.05f;
	}
	
	public String getName() {
		return this.name;
	}
	
	public AtlasData getAtlas() {
		return this.atlas;
	}
	
	public MetricsData getMetrics() {
		return this.metrics;
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private String name = "?";
		private Identifier dataIdentifer;
		private Identifier atlasIdentifier;
		
		private Builder() {}
		
		public Builder name(String name) {
			this.name = name;
			return this;
		}
		
		public Builder data(String dataFileName) {
			this.dataIdentifer = LinkenVisuals.id("fonts/" + dataFileName + ".json");
			return this;
		}
		
		public Builder atlas(String atlasFileName) {
			this.atlasIdentifier = LinkenVisuals.id("fonts/" + atlasFileName + ".png");
			return this;
		}
		
		public MsdfFont build() {
			FontData data = ResourceProvider.fromJsonToInstance(this.dataIdentifer, FontData.class);
			AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(this.atlasIdentifier);
			
			if (data == null) {
				throw new RuntimeException("Failed to read font data file: " + this.dataIdentifer.toString() + 
						"; Are you sure this is json file? Try to check the correctness of its syntax.");
			}
			
			float aWidth = data.atlas().width();
			float aHeight = data.atlas().height();
			Int2ObjectOpenHashMap<MsdfGlyph> glyphs = new Int2ObjectOpenHashMap<>();
			for (GlyphData glyphData : data.glyphs()) {
				glyphs.put(glyphData.unicode(), new MsdfGlyph(glyphData, aWidth, aHeight));
			}

			Int2ObjectOpenHashMap<Int2FloatOpenHashMap> kernings = new Int2ObjectOpenHashMap<>();
			data.kernings().forEach((kerning) -> {
				Int2FloatOpenHashMap map = kernings.get(kerning.leftChar());
				if (map == null) {
					map = new Int2FloatOpenHashMap();
					map.defaultReturnValue(0.0f);
					kernings.put(kerning.leftChar(), map);
				}

				map.put(kerning.rightChar(), kerning.advance());
			});

			return new MsdfFont(this.name, texture, data.atlas(), data.metrics(), glyphs, kernings);
		}
	}
}
