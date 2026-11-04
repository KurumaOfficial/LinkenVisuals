package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.method.linkenvisuals.screen.mainmenu.MenuChrome;
import ru.method.linkenvisuals.screen.overlay.LinkenConnectRenderer;

/**
 * 9e — рескин ванильного экрана «Подключение к серверу». Отменяем ванильный render и рисуем нашу карточку
 * ({@link LinkenConnectRenderer}) с реальным этапом (поле {@code status}), именем/адресом сервера (ловим их
 * из статического {@code connect(...)}), «Отмена»/ESC рвут подключение так же, как ванильная кнопка.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow private Text status;
    @Shadow @Final Screen parent;
    @Shadow volatile ClientConnection connection;
    @Shadow volatile boolean connectingCancelled;
    @Shadow io.netty.channel.ChannelFuture future;
    @Unique private long linkenvisuals$stageSince;
    @Unique private String linkenvisuals$lastStage = "";

    @Unique private static String linkenvisuals$name;
    @Unique private static String linkenvisuals$addr;

    protected ConnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD")
    )
    private static void linkenvisuals$captureTarget(Screen screen, MinecraftClient client, ServerAddress address,
                                                    ServerInfo info, boolean bl, CookieStorage cookieStorage, CallbackInfo ci) {
        linkenvisuals$name = info != null ? info.name : null;
        linkenvisuals$addr = address != null ? address.getAddress() + ":" + address.getPort()
                : (info != null ? info.address : "");
        // Отдаём цель общему держателю: следующие экраны цепочки (загрузка территории и т.д.)
        // рисуются уже не этим миксином, а перехватом на базовом Screen.
        LinkenConnectRenderer.beginJoin(linkenvisuals$name, linkenvisuals$addr);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();
        LinkenConnectRenderer.render(context, mouseX, mouseY, this.status, linkenvisuals$name, linkenvisuals$addr);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void linkenvisuals$connectionTimeout(CallbackInfo ci) {
        if (connectingCancelled || client == null || client.currentScreen != (Object) this) return;
        String stage = status == null ? "" : status.getString();
        long now = System.nanoTime();
        if (linkenvisuals$stageSince == 0L || !stage.equals(linkenvisuals$lastStage)) {
            linkenvisuals$lastStage = stage;
            linkenvisuals$stageSince = now;
        } else if (now - linkenvisuals$stageSince > java.util.concurrent.TimeUnit.SECONDS.toNanos(60)) {
            connectingCancelled = true;
            if (future != null) future.cancel(true);
            Text reason = Text.translatable("disconnect.timeout");
            if (connection != null) connection.disconnect(reason);
            ru.method.linkenvisuals.LinkenVisuals.LOGGER.warn("[Connection] No progress for 60s at stage: {}", stage);
            client.setScreen(new net.minecraft.client.gui.screen.DisconnectedScreen(parent,
                    Text.translatable("connect.failed"), reason));
            ci.cancel();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == 0) {
            float dx = (float) (click.x() / LinkenConnectRenderer.uiScale);
            float dy = (float) (click.y() / LinkenConnectRenderer.uiScale);
            if (MenuChrome.in(LinkenConnectRenderer.CANCEL_RECT, dx, dy)) linkenvisuals$cancel();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            linkenvisuals$cancel();
            return true;
        }
        return false;
    }

    @Unique
    private void linkenvisuals$cancel() {
        this.connectingCancelled = true;
        if (this.future != null) this.future.cancel(true);
        if (this.connection != null) this.connection.disconnect(ConnectScreen.ABORTED_TEXT);
        this.client.setScreen(this.parent);
    }
}
