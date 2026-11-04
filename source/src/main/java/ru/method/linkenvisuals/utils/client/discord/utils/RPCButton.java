package ru.method.linkenvisuals.utils.client.discord.utils;


import lombok.Getter;

import java.io.Serializable;

@Getter

public class RPCButton implements Serializable {
    private final String url;
    private final String label;

    public static RPCButton create(String label, String url) {
        String safeLabel = label != null ? label : "";
        safeLabel = safeLabel.substring(0, Math.min(safeLabel.length(), 31));
        return new RPCButton(safeLabel, url);
    }

    protected RPCButton(String label, String url) {
        this.label = label;
        this.url = url;
    }
}
