package dev.linkenvisuals.ui.linkmenu;

import dev.linkenvisuals.audio.LinkenMenuMusic;
import dev.linkenvisuals.i18n.I18n;
import dev.linkenvisuals.render.Pipelines;
import dev.linkenvisuals.ui.Spring;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * LINKEN main menu, ported 1:1 from the donor {@code CustomMainMenuScreen}.
 *
 * <p>The composition is anchored to the donor 1920x1080 canvas. It is
 * independent from the customizable Beta menu and never touches
 * {@code MainMenuConfig}; switching styles therefore preserves the Beta and
 * Vanilla layouts.
 */
public final class LinkenMainMenuScreen extends Screen {
    private static final float INTRO_MS = 450.0f;
    private static final long WELCOME_MS = 3000L;
    private static final long QUIT_CONFIRM_MS = 3000L;
    private static boolean welcomePlayed;

    private static final String DISCORD_URL = "https://dsc.gg/linkenvisuals";
    private static final String TELEGRAM_URL = "https://t.me/linkenvisuals";
    private static final String SITE_URL = "https://linkenvisuals.ru";
    private static final String DEV_URL = "https://t.me/linkenvisualsdev";

    private final LinkenAnimation multiplayerHover = LinkenAnimation.hover();
    private final LinkenAnimation singleplayerHover = LinkenAnimation.hover();
    private final LinkenAnimation settingsHover = LinkenAnimation.hover();
    private final LinkenAnimation quitHover = LinkenAnimation.hover();
    private final LinkenAnimation accountHover = LinkenAnimation.hover();
    private final LinkenAnimation musicCoverHover = LinkenAnimation.hover();
    private final LinkenAnimation[] socialHover = {
        LinkenAnimation.hover(), LinkenAnimation.hover(),
        LinkenAnimation.hover(), LinkenAnimation.hover()
    };

    private LinkenMenuLayout.Metrics layout;
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    private long openedAt;
    private long lastFrame = System.nanoTime();
    private long quitArmedUntil;
    private long welcomeStart;
    private boolean welcomeActive;
    private float parallaxX;
    private float parallaxY;

    public LinkenMainMenuScreen() {
        super(Text.empty());
    }

    @Override
    protected void init() {
        LinkenFonts.body();
        openedAt = Util.getMeasuringTimeMs();
        if (!welcomePlayed) {
            welcomeActive = true;
            welcomeStart = 0L;
        } else {
            welcomeActive = false;
            LinkenMenuMusic.autoStartOnce();
        }
        lastFrame = System.nanoTime();
        quitArmedUntil = 0L;
        layout = null;
        layoutWidth = -1;
        layoutHeight = -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        if (!Pipelines.resourcesReady()) {
            // The initial resource reload still owns the screen. Drawing the
            // LINKEN menu before the shader sources exist would fail.
            lastFrame = now;
            return;
        }
        if (client != null && client.getOverlay() != null) {
            // Splash overlay is still active on top of this screen; do not start welcome countdown yet
            if (!welcomePlayed) {
                welcomeStart = 0L;
                welcomeActive = true;
            }
            lastFrame = now;
            return;
        }
        // Once resources are ready the menu may render underneath the fading
        // splash, so its first frame is already in the framebuffer when the
        // overlay is removed.
        Pipelines.warmUp();
        double deltaSeconds = Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
        lastFrame = now;

        LinkenMenuLayout.Metrics metrics = layout();
        long millis = Util.getMeasuringTimeMs();

        float virtualMouseX = (float) mouseX / metrics.scale();
        float virtualMouseY = (float) mouseY / metrics.scale();
        updateParallax(virtualMouseX, virtualMouseY, metrics);

        if (welcomeActive) {
            if (welcomeStart == 0L) {
                welcomeStart = millis;
            }
            if (welcomePlayed || millis - welcomeStart >= WELCOME_MS) {
                finishWelcome(millis);
            } else {
                renderWelcomeIntro(context, metrics, (millis - welcomeStart) / (float) WELCOME_MS);
                return;
            }
        }
        float intro = Math.min(1.0f, (millis - openedAt) / INTRO_MS);
        intro = 1.0f - (1.0f - intro) * (1.0f - intro) * (1.0f - intro);

        float introSlideY = (1.0f - intro) * 22.0f;
        float contentMouseY = virtualMouseY - introSlideY;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());

        LinkenMenuChrome.drawBackground(context, metrics, parallaxX, parallaxY, 1.0f);

        context.getMatrices().translate(0.0f, introSlideY);
        LinkenMenuChrome.drawWordmark(context, metrics, intro);
        LinkenMenuChrome.drawClock(context, metrics, intro);
        renderWelcome(context, metrics, intro);
        renderAccount(context, metrics, virtualMouseX, contentMouseY, intro);

        float multiHover = multiplayerHover.updateAndGet(metrics.multiplayer().contains(virtualMouseX, contentMouseY));
        float singleHover = singleplayerHover.updateAndGet(metrics.singleplayer().contains(virtualMouseX, contentMouseY));
        float settingsHoverValue = settingsHover.updateAndGet(metrics.settings().contains(virtualMouseX, contentMouseY));
        float quitHoverValue = quitHover.updateAndGet(metrics.quit().contains(virtualMouseX, contentMouseY));

        LinkenMenuChrome.drawButton(context, metrics.multiplayer(), true, LinkenMenuChrome.MENU_ICON_GLOBE,
            getI18nOrDefault("linkenvisuals.menu.multiplayer", "Сетевая игра"), multiHover, false, false, intro, metrics.scale());
        LinkenMenuChrome.drawButton(context, metrics.singleplayer(), false, LinkenMenuChrome.MENU_ICON_PERSON,
            getI18nOrDefault("linkenvisuals.menu.singleplayer", "Одиночная игра"), singleHover, false, false, intro, metrics.scale());
        LinkenMenuChrome.drawButton(context, metrics.settings(), false, LinkenMenuChrome.MENU_ICON_GEAR,
            getI18nOrDefault("linkenvisuals.menu.settings", "Настройки"), settingsHoverValue, false, false, intro, metrics.scale());

        boolean quitArmed = millis < quitArmedUntil;
        LinkenMenuChrome.drawButton(context, metrics.quit(), false, LinkenMenuChrome.MENU_ICON_POWER,
            quitArmed ? getI18nOrDefault("linkenvisuals.linkmenu.quit_confirm", "Точно?")
                      : getI18nOrDefault("linkenvisuals.linkmenu.quit_short", "Выход"),
            quitHoverValue, quitArmed, true, intro, metrics.scale());

        renderSocials(context, metrics, virtualMouseX, contentMouseY, intro);
        float coverHover = musicCoverHover.updateAndGet(musicCover(metrics).contains(virtualMouseX, contentMouseY));
        LinkenMenuChrome.drawMusic(context, metrics.music(), LinkenMenuMusic.track(), intro, metrics.scale(), coverHover);
        LinkenMenuChrome.drawFooter(context, metrics, intro);
        context.getMatrices().popMatrix();

        if (intro < 0.999f) {
            context.fill(0, 0, width, height,
                LinkenMenuChrome.rgba(0x060908, Math.round(200.0f * (1.0f - intro)), 1.0f));
        }
    }

    private void finishWelcome(long millis) {
        welcomeActive = false;
        welcomePlayed = true;
        openedAt = millis;
        LinkenMenuMusic.autoStartOnce();
    }

    /** Test hook: skip the first-open intro without changing the normal flow. */
    public static void skipWelcome() {
        welcomePlayed = true;
    }

    /**
     * Short, donor-style first-open scene built from the same palette and
     * MSDF fonts as the menu. The donor Lottie file needs Skija/Skottie,
     * which is intentionally out of scope; this reproduces the welcome beat
     * with a wordmark reveal, a progress rail and the localized greeting.
     */
    private void renderWelcomeIntro(DrawContext context, LinkenMenuLayout.Metrics metrics, float raw) {
        float in = easeOut(Math.min(1.0f, raw / 0.12f));
        float out = easeOut(Math.min(1.0f, (1.0f - raw) / 0.14f));
        float fade = Math.min(in, out);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(metrics.scale(), metrics.scale());
        LinkenMenuChrome.drawBackground(context, metrics, parallaxX, parallaxY, 1.0f);
        context.fill(0, 0, Math.round(metrics.width()), Math.round(metrics.height()),
            LinkenMenuChrome.rgba(0x060908, Math.round(90.0f * fade)));

        // Cursive handwriting progress: draws "welcome" cursive path over ~2 seconds
        float drawProgress = Math.min(1.0f, raw / 0.72f);
        MainMenuWelcomeAnim.render(context, metrics.width(), metrics.height(), drawProgress, fade);

        context.getMatrices().popMatrix();
    }

    private static float easeOut(float value) {
        float inverse = 1.0f - Math.max(0.0f, Math.min(1.0f, value));
        return 1.0f - inverse * inverse * inverse;
    }

    private void renderWelcome(DrawContext context, LinkenMenuLayout.Metrics metrics, float fade) {
        String eyebrow = getI18nOrDefault("linkenvisuals.linkmenu.eyebrow", "ДАВНО НЕ ВИДЕЛИСЬ");
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.headingLight(),
            eyebrow,
            metrics.eyebrow().x(), metrics.eyebrow().y(), 10.0f, 3.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));

        String first = getI18nOrDefault("linkenvisuals.linkmenu.welcome_line1", "Добро пожаловать");
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), first,
            metrics.headline().x(), metrics.headline().y(), 38.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        String secondTemplate = getI18nOrDefault("linkenvisuals.linkmenu.welcome_line2", "вновь, %s");
        String second = secondTemplate.contains("%s") ? String.format(secondTemplate, LinkenMenuChrome.username()) : secondTemplate;
        LinkenMenuChrome.drawText(context, LinkenFonts.bodyMedium(), second,
            metrics.headline().x(), metrics.headline().y() + 43.7f, 38.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        String subtitle = LinkenMenuChrome.ellipsize(LinkenFonts.body(),
            getI18nOrDefault("linkenvisuals.linkmenu.subtitle", "Готов прыгать по блокам?"),
            metrics.subtitle().width(), 14.0f);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), subtitle,
            metrics.subtitle().x(), metrics.subtitle().y(), 14.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
    }

    private void renderAccount(DrawContext context, LinkenMenuLayout.Metrics metrics, float mouseX, float mouseY,
                               float fade) {
        LinkenMenuLayout.Rect rect = metrics.account();
        float avatar = 38.0f;
        dev.linkenvisuals.render.RoundedRectPipeline.drawVerticalGradient(context, rect.x(), rect.y(),
            avatar, avatar, avatar * 0.5f * metrics.scale(),
            LinkenMenuChrome.rgba(0x2A3830, 255, fade),
            LinkenMenuChrome.rgba(0x101613, 255, fade));

        String nick = LinkenMenuChrome.username();
        String initial = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        LinkenMenuChrome.drawCentered(context, LinkenFonts.heading(), initial,
            rect.x() + avatar * 0.5f, rect.y() + avatar * 0.5f, 12.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.ACCENT, fade));

        float textX = rect.x() + avatar + 12.0f;
        String metaTemplate = getI18nOrDefault("linkenvisuals.linkmenu.account.meta", "текущий аккаунт · %s");
        String meta = metaTemplate.contains("%s") ? String.format(metaTemplate, LinkenMenuChrome.licenseWord()) : metaTemplate;
        LinkenMenuChrome.drawSpaced(context, LinkenFonts.body(), meta,
            textX + 10.0f, rect.y() + 1.0f, 10.5f, 0.84f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        if (LinkenFonts.icons2() != null) {
            dev.linkenvisuals.render.IconPipeline.draw(context, LinkenFonts.icons2(),
                LinkenMenuChrome.MENU_ICON_PERSON, textX, rect.y() + 6.5f, 9.0f,
                LinkenMenuChrome.rgba(LinkenMenuChrome.MUTED2, fade));
        }
        LinkenMenuChrome.drawText(context, LinkenFonts.heading(),
            LinkenMenuChrome.ellipsize(LinkenFonts.heading(), nick,
                metrics.column().right() - textX, 13.0f),
            textX, rect.y() + 18.0f, 13.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.TEXT, fade));

        String switchLabel = getI18nOrDefault("linkenvisuals.linkmenu.account.switch", "сменить");
        float switchWidth = LinkenMenuChrome.textWidth(LinkenFonts.body(), switchLabel, 11.0f);
        float switchX = metrics.quit().x() + (metrics.quit().width() - switchWidth) * 0.5f;
        float switchY = rect.y() + (avatar - 11.0f) * 0.5f;
        boolean switchHover = (mouseX >= switchX - 4.0f && mouseX <= switchX + switchWidth + 4.0f
            && mouseY >= rect.y() && mouseY <= rect.bottom())
            || (mouseX >= switchX && mouseX <= switchX + switchWidth
            && mouseY >= switchY && mouseY <= switchY + 11.0f);
        float hover = accountHover.updateAndGet(switchHover);
        LinkenMenuChrome.drawText(context, LinkenFonts.body(), switchLabel,
            switchX, switchY, 11.0f,
            LinkenMenuChrome.rgba(LinkenMenuChrome.mix(LinkenMenuChrome.MUTED,
                LinkenMenuChrome.TEXT, hover), fade));
    }

    private static String getI18nOrDefault(String key, String fallback) {
        if (!I18n.has(key)) {
            return fallback;
        }
        String val = I18n.get(key);
        return (val == null || val.isEmpty() || val.equals(key)) ? fallback : val;
    }

    private void renderSocials(DrawContext context, LinkenMenuLayout.Metrics metrics, float mouseX, float mouseY,
                               float fade) {
        LinkenMenuLayout.Rect socials = metrics.socials();
        int[] icons = {LinkenMenuChrome.MENU_ICON_TELEGRAM, LinkenMenuChrome.MENU_ICON_DISCORD, 0,
            LinkenMenuChrome.MENU_ICON_CODE};
        String[] labels = {null, null, "LV", null};
        for (int i = 0; i < 4; i++) {
            float x = socials.x() + i * (LinkenMenuLayout.SOCIAL_SIZE + LinkenMenuLayout.SOCIAL_GAP);
            LinkenMenuLayout.Rect tile = new LinkenMenuLayout.Rect(
                x, socials.y(), LinkenMenuLayout.SOCIAL_SIZE, LinkenMenuLayout.SOCIAL_SIZE);
            float hover = socialHover[i].updateAndGet(tile.contains(mouseX, mouseY));
            LinkenMenuChrome.drawSocialTile(context, tile, icons[i], labels[i], hover, fade, metrics.scale());
        }
    }

    private void updateParallax(float mouseX, float mouseY, LinkenMenuLayout.Metrics metrics) {
        float targetX = clamp((mouseX - metrics.width() * 0.5f) / (metrics.width() * 0.5f));
        float targetY = clamp((mouseY - metrics.height() * 0.5f) / (metrics.height() * 0.5f));
        parallaxX += (targetX - parallaxX) * 0.08f;
        parallaxY += (targetY - parallaxY) * 0.08f;
    }

    private static float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private LinkenMenuLayout.Metrics layout() {
        if (layout == null || layoutWidth != width || layoutHeight != height) {
            layout = LinkenMenuLayout.compute(width, height);
            layoutWidth = width;
            layoutHeight = height;
        }
        return layout;
    }

    private LinkenMenuLayout.Rect musicCover(LinkenMenuLayout.Metrics metrics) {
        LinkenMenuLayout.Rect music = metrics.music();
        float coverSize = 34.0f;
        float rowY = music.bottom() - 10.5f - 12.0f - coverSize;
        return new LinkenMenuLayout.Rect(music.x(), rowY, coverSize, coverSize);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (welcomeActive) {
            finishWelcome(Util.getMeasuringTimeMs());
            return true;
        }
        if (click.button() != 0) {
            return super.mouseClicked(click, doubleClick);
        }
        LinkenMenuLayout.Metrics metrics = layout();
        float mouseX = (float) click.x() / metrics.scale();
        float mouseY = (float) click.y() / metrics.scale();
        long now = Util.getMeasuringTimeMs();

        float intro = Math.min(1.0f, (now - openedAt) / INTRO_MS);
        intro = 1.0f - (1.0f - intro) * (1.0f - intro) * (1.0f - intro);
        float introSlideY = (1.0f - intro) * 22.0f;
        float contentMouseY = mouseY - introSlideY;

        if (metrics.quit().contains(mouseX, contentMouseY)) {
            if (now < quitArmedUntil) {
                if (client != null) {
                    client.scheduleStop();
                }
            } else {
                quitArmedUntil = now + QUIT_CONFIRM_MS;
            }
            return true;
        }
        if (metrics.multiplayer().contains(mouseX, contentMouseY)) {
            if (client != null) {
                client.setScreen(new LinkenMultiplayerScreen(this));
            }
            return true;
        }
        if (metrics.singleplayer().contains(mouseX, contentMouseY)) {
            if (client != null) {
                client.setScreen(new SelectWorldScreen(this));
            }
            return true;
        }
        if (metrics.settings().contains(mouseX, contentMouseY)) {
            if (client != null) {
                client.setScreen(new LinkenSettingsScreen(this));
            }
            return true;
        }

        LinkenMenuLayout.Rect account = metrics.account();
        String switchLabel = I18n.get("linkenvisuals.linkmenu.account.switch");
        float switchWidth = LinkenMenuChrome.textWidth(LinkenFonts.body(), switchLabel, 11.0f);
        float switchX = metrics.quit().x() + (metrics.quit().width() - switchWidth) * 0.5f;
        float switchY = account.y() + (account.height() - 11.0f) * 0.5f;
        boolean switchHit = (mouseX >= switchX - 4.0f && mouseX <= switchX + switchWidth + 4.0f
            && contentMouseY >= account.y() && contentMouseY <= account.bottom())
            || account.contains(mouseX, contentMouseY);
        if (switchHit) {
            if (client != null) {
                client.setScreen(new LinkenAltManagerScreen(this));
            }
            return true;
        }

        LinkenMenuLayout.Rect socials = metrics.socials();
        for (int i = 0; i < 4; i++) {
            float x = socials.x() + i * (LinkenMenuLayout.SOCIAL_SIZE + LinkenMenuLayout.SOCIAL_GAP);
            LinkenMenuLayout.Rect tile = new LinkenMenuLayout.Rect(
                x, socials.y(), LinkenMenuLayout.SOCIAL_SIZE, LinkenMenuLayout.SOCIAL_SIZE);
            if (tile.contains(mouseX, contentMouseY)) {
                LinkenMenuChrome.openUrl(switch (i) {
                    case 0 -> TELEGRAM_URL;
                    case 1 -> DISCORD_URL;
                    case 2 -> SITE_URL;
                    default -> DEV_URL;
                });
                return true;
            }
        }

        if (musicCover(metrics).contains(mouseX, contentMouseY)) {
            LinkenMenuMusic.toggle();
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (welcomeActive) {
            finishWelcome(Util.getMeasuringTimeMs());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The LINKEN art is drawn explicitly in render(); never fall back to the panorama.
    }
}
