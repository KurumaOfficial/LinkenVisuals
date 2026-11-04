package ru.method.linkenvisuals.api.mixins;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к приватным данным ванильного экрана отключения — заголовок берём из getTitle(), причину из info. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {

    @Accessor("info")
    DisconnectionInfo linkenvisuals$getInfo();
}
