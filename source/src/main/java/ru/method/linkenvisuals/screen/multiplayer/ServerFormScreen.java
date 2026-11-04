package ru.method.linkenvisuals.screen.multiplayer;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerInfo.ResourcePackPolicy;

/** Форма «Добавить» / «Настроить» сервер — модалка в материале окна. */
final class ServerFormScreen extends MultiplayerModal {

    private final ServerInfo target;
    private final boolean isNew;
    private final Choice resourcePack;

    // Порядок сегментов = порядок в UI (как в ванильной кнопке ресурспака): Выключен / При запросе / Включён.
    private static final ResourcePackPolicy[] RP_ORDER = {
            ResourcePackPolicy.DISABLED, ResourcePackPolicy.PROMPT, ResourcePackPolicy.ENABLED };

    ServerFormScreen(CustomMultiplayerScreen owner, ServerInfo target, boolean isNew) {
        super(owner, isNew ? "Добавить сервер" : "Настроить сервер", isNew ? "Добавить" : "Готово", false);
        this.target = target;
        this.isNew = isNew;
        fields.add(new Field("Название", "Сервер", target.name));
        fields.add(new Field("Адрес", "mc.artygrief.su", target.address));
        resourcePack = new Choice("Ресурспак сервера", policyIndex(target.getResourcePackPolicy()),
                "Выключен", "При запросе", "Включён");
        choices.add(resourcePack);
    }

    private static int policyIndex(ResourcePackPolicy policy) {
        for (int i = 0; i < RP_ORDER.length; i++) if (RP_ORDER[i] == policy) return i;
        return 1; // PROMPT — ванильное значение по умолчанию
    }

    @Override
    protected boolean onPrimary() {
        String name = fields.get(0).value.trim();
        String address = fields.get(1).value.trim();
        if (address.isEmpty()) return false; // без адреса сохранять нечего
        target.name = name.isEmpty() ? address : name;
        target.address = address;
        target.setResourcePackPolicy(RP_ORDER[resourcePack.selected]);
        owner.applyServer(target, isNew);
        return true;
    }
}
