package ru.method.linkenvisuals.screen.multiplayer;

import net.minecraft.client.MinecraftClient;

/** Форма «По адресу» — прямое подключение, модалка в материале окна. */
final class DirectConnectFormScreen extends MultiplayerModal {

    /** Последний введённый адрес — на сессию; между перезапусками живёт в options.lastServer (ваниль). */
    private static String lastAddress = "";

    DirectConnectFormScreen(CustomMultiplayerScreen owner) {
        super(owner, "Подключение по адресу", "Подключиться", false);
        if (lastAddress.isEmpty()) {
            String saved = MinecraftClient.getInstance().options.lastServer;
            if (saved != null) {
                lastAddress = saved;
            }
        }
        fields.add(new Field("Адрес сервера", "mc.artygrief.su", lastAddress));
    }

    @Override
    protected boolean onPrimary() {
        String address = fields.get(0).value.trim();
        if (address.isEmpty()) return false;
        remember(address);
        owner.connectDirect(address);
        return false; // экран подключения открывает сам connectDirect
    }

    @Override
    protected void dismiss() {
        // Сохраняем введённый адрес и при закрытии БЕЗ подключения (Отмена/Esc) — иначе текст терялся,
        // и при следующем открытии поле было пустым.
        remember(fields.get(0).value.trim());
        super.dismiss();
    }

    /** Запоминает адрес на сессию и в options.txt (lastServer) — переживает перезапуск игры. */
    private static void remember(String address) {
        lastAddress = address;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!address.isEmpty() && !address.equals(client.options.lastServer)) {
            client.options.lastServer = address;
            client.options.write();
        }
    }
}
