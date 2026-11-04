package ru.method.linkenvisuals.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.method.linkenvisuals.LinkenVisuals;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class GifTexture {
    private final List<Identifier> frameTextures = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private final Identifier gifResource;
    private final String framePrefix;

    private int currentFrame;
    private long lastFrameTime;
    private volatile boolean loaded;

    public GifTexture(Identifier gifResource) {
        this.gifResource = gifResource;
        this.framePrefix = "dynamic/gif_" + Math.abs(gifResource.toString().hashCode());
        CompletableFuture.runAsync(this::loadFrames);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Identifier getCurrentFrameTexture() {
        if (frameTextures.isEmpty()) return null;
        return frameTextures.get(Math.floorMod(currentFrame, frameTextures.size()));
    }

    public void update() {
        if (!loaded || frameTextures.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int idx = Math.floorMod(currentFrame, delays.size());
        if (now - lastFrameTime >= delays.get(idx)) {
            currentFrame = (idx + 1) % frameTextures.size();
            lastFrameTime = now;
        }
    }

    private void loadFrames() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        Optional<Resource> resource = client.getResourceManager().getResource(gifResource);
        if (resource.isEmpty()) {
            LinkenVisuals.LOGGER.warn("GIF resource not found: {}", gifResource);
            return;
        }

        List<int[]> snapshots = new ArrayList<>();
        List<Integer> frameDelays = new ArrayList<>();

        try (InputStream input = resource.get().getInputStream();
             ImageInputStream imageStream = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return;
            }

            ImageReader reader = readers.next();
            reader.setInput(imageStream, false);

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            int frameCount = reader.getNumImages(true);
            int[] canvas = new int[width * height];
            int[] backup = null;

            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(frameIndex).getAsTree("javax_imageio_gif_image_1.0");
                IIOMetadataNode graphicControl = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                IIOMetadataNode descriptor = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);

                String disposal = graphicControl != null ? graphicControl.getAttribute("disposalMethod") : "none";
                int delay = graphicControl != null ? Math.max(Integer.parseInt(graphicControl.getAttribute("delayTime")) * 10, 20) : 100;
                int offsetX = descriptor != null ? Integer.parseInt(descriptor.getAttribute("imageLeftPosition")) : 0;
                int offsetY = descriptor != null ? Integer.parseInt(descriptor.getAttribute("imageTopPosition")) : 0;

                if ("restoreToPrevious".equals(disposal)) {
                    backup = canvas.clone();
                }

                BufferedImage frame = reader.read(frameIndex);
                int frameWidth = frame.getWidth();
                int frameHeight = frame.getHeight();

                for (int y = 0; y < frameHeight; y++) {
                    for (int x = 0; x < frameWidth; x++) {
                        int globalX = x + offsetX;
                        int globalY = y + offsetY;
                        if (globalX < width && globalY < height) {
                            int argb = frame.getRGB(x, y);
                            if (((argb >> 24) & 0xFF) > 0) {
                                canvas[globalY * width + globalX] = argb;
                            }
                        }
                    }
                }

                snapshots.add(canvas.clone());
                frameDelays.add(delay);

                if ("restoreToBackgroundColor".equals(disposal)) {
                    for (int y = 0; y < frameHeight; y++) {
                        for (int x = 0; x < frameWidth; x++) {
                            int globalX = x + offsetX;
                            int globalY = y + offsetY;
                            if (globalX < width && globalY < height) {
                                canvas[globalY * width + globalX] = 0;
                            }
                        }
                    }
                } else if ("restoreToPrevious".equals(disposal) && backup != null) {
                    canvas = backup;
                }
            }

            reader.dispose();

            client.execute(() -> registerFrames(client, width, height, snapshots, frameDelays));
        } catch (Exception exception) {
            LinkenVisuals.LOGGER.error("Failed to load GIF {}", gifResource, exception);
        }
    }

    private void registerFrames(
            MinecraftClient client,
            int width,
            int height,
            List<int[]> snapshots,
            List<Integer> frameDelays
    ) {
        frameTextures.clear();
        delays.clear();

        for (int frameIndex = 0; frameIndex < snapshots.size(); frameIndex++) {
            int[] pixels = snapshots.get(frameIndex);
            NativeImage image = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setColorArgb(x, y, pixels[y * width + x]);
                }
            }

            Identifier frameId = Identifier.of(LinkenVisuals.MOD_ID, framePrefix + "/" + frameIndex);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "gif_frame", image);
            client.getTextureManager().registerTexture(frameId, texture);
            frameTextures.add(frameId);
            delays.add(frameDelays.get(frameIndex));
        }

        currentFrame = 0;
        lastFrameTime = System.currentTimeMillis();
        loaded = !frameTextures.isEmpty();
    }
}
