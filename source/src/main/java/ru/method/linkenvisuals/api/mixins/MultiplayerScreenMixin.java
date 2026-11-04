package ru.method.linkenvisuals.api.mixins;

import ru.method.linkenvisuals.LinkenVisuals;
import ru.method.linkenvisuals.api.mixins.accessors.IServerList;
import ru.method.linkenvisuals.utils.server.ServerManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {

    @Shadow
    protected MultiplayerServerListWidget serverListWidget;

    @Shadow
    private ServerList serverList;

    private static final int ORDER_CHECK_INTERVAL = 10;

    @Unique
    private List<ServerInfo> linkenvisuals$protectedBuffer;
    @Unique
    private List<ServerInfo> linkenvisuals$orderedProtected;
    @Unique
    private List<ServerInfo> linkenvisuals$otherBuffer;

    private int linkenvisuals$ticksUntilReorderCheck = 0;
    private int lastServerListSize = 0;
    private int lastProtectedHash = 0;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void linkenvisuals$initServerBuffers(Screen parent, CallbackInfo ci) {
        linkenvisuals$protectedBuffer = new ArrayList<>();
        linkenvisuals$orderedProtected = new ArrayList<>();
        linkenvisuals$otherBuffer = new ArrayList<>();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        refreshProtectedServers(true);
        linkenvisuals$ticksUntilReorderCheck = ORDER_CHECK_INTERVAL;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (linkenvisuals$ticksUntilReorderCheck > 0) {
            linkenvisuals$ticksUntilReorderCheck--;
            return;
        }

        linkenvisuals$ticksUntilReorderCheck = ORDER_CHECK_INTERVAL;
        refreshProtectedServers(false);
    }

    private void refreshProtectedServers(boolean force) {
        ensureProtectedServersExist();

        int currentSize = serverList.size();
        int currentHash = computeProtectedHeaderHash();

        if (!force && currentSize == lastServerListSize && currentHash == lastProtectedHash) {
            return;
        }

        boolean reordered = moveProtectedToTop();
        boolean changed = force || reordered || currentSize != lastServerListSize;

        if (changed) {
            serverListWidget.setServers(serverList);
            serverList.saveFile();
        }

        lastServerListSize = serverList.size();
        lastProtectedHash = computeProtectedHeaderHash();
    }

    private void ensureProtectedServersExist() {
        Set<String> knownAddresses = new HashSet<>();
        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo existing = serverList.get(i);
            if (existing != null && existing.address != null) {
                knownAddresses.add(existing.address.toLowerCase(Locale.ROOT));
            }
        }

        for (ServerInfo template : ServerManager.getProtectedServers()) {
            String key = template.address.toLowerCase(Locale.ROOT);
            if (knownAddresses.add(key)) {
                serverList.add(template, false);
            }
        }
    }

    private boolean moveProtectedToTop() {
        List<ServerInfo> backing = getBackingServers();
        if (backing == null || backing.isEmpty()) {
            return false;
        }

        linkenvisuals$protectedBuffer.clear();
        linkenvisuals$otherBuffer.clear();

        for (ServerInfo server : backing) {
            if (ServerManager.isProtectedServer(server)) {
                linkenvisuals$protectedBuffer.add(server);
            } else {
                linkenvisuals$otherBuffer.add(server);
            }
        }

        if (linkenvisuals$protectedBuffer.isEmpty()) {
            return false;
        }

        List<ServerInfo> ordered = orderProtectedServers();
        if (ordered.isEmpty()) {
            return false;
        }

        boolean alreadyOrdered = true;
        for (int i = 0; i < ordered.size(); i++) {
            if (backing.size() <= i || backing.get(i) != ordered.get(i)) {
                alreadyOrdered = false;
                break;
            }
        }

        if (alreadyOrdered) {
            return false;
        }

        backing.clear();
        backing.addAll(ordered);
        backing.addAll(linkenvisuals$otherBuffer);
        return true;
    }

    private List<ServerInfo> orderProtectedServers() {
        linkenvisuals$orderedProtected.clear();
        if (linkenvisuals$protectedBuffer.isEmpty()) {
            return linkenvisuals$orderedProtected;
        }

        List<ServerInfo> desiredOrder = ServerManager.getProtectedServers();
        for (ServerInfo template : desiredOrder) {
            ServerInfo match = findByAddress(template.address);
            if (match != null) {
                linkenvisuals$orderedProtected.add(match);
            }
        }

        for (ServerInfo server : linkenvisuals$protectedBuffer) {
            if (!linkenvisuals$orderedProtected.contains(server)) {
                linkenvisuals$orderedProtected.add(server);
            }
        }

        return linkenvisuals$orderedProtected;
    }

    private ServerInfo findByAddress(String address) {
        if (address == null) {
            return null;
        }
        for (ServerInfo server : linkenvisuals$protectedBuffer) {
            if (address.equals(server.address)) {
                return server;
            }
        }
        return null;
    }

    private int computeProtectedHeaderHash() {
        int limit = Math.min(ServerManager.protectedCount(), serverList.size());
        int hash = 1;
        for (int i = 0; i < limit; i++) {
            ServerInfo info = serverList.get(i);
            hash = 31 * hash + hashServer(info);
        }
        hash = 31 * hash + serverList.size();
        return hash;
    }

    private static int hashServer(ServerInfo info) {
        return info == null ? 0 : Objects.hash(info.address, info.name);
    }

    private List<ServerInfo> getBackingServers() {
        return ((IServerList) serverList).linkenvisuals$getServers();
    }
}
