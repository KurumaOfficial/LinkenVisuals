package ru.method.linkenvisuals.screen.multiplayer;

import net.minecraft.client.network.ServerInfo;

/** Подтверждение удаления сервера — модалка в материале окна, primary в danger-цвете. */
final class ConfirmDeleteScreen extends MultiplayerModal {

    private final String name;

    ConfirmDeleteScreen(CustomMultiplayerScreen owner, ServerInfo target) {
        super(owner, "Удалить сервер?", "Удалить", true);
        this.name = target.name == null || target.name.isBlank() ? target.address : target.name;
    }

    @Override
    protected String description() {
        return "«" + name + "» будет удалён из списка. Действие необратимо.";
    }

    @Override
    protected float modalWidth() {
        return 420f;
    }

    @Override
    protected boolean onPrimary() {
        owner.deleteConfirmed();
        return true;
    }
}
