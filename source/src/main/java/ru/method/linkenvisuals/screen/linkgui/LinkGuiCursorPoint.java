package ru.method.linkenvisuals.screen.linkgui;

/**
 * One sampled point of the LinkGUI cursor trail.
 *
 * <p>Kept as a top-level record on purpose: dev Fabric launches can fail to
 * expose nested classes (e.g. {@code LinkGuiScreen$CursorPoint}) to the game
 * class loader, which crashed the cursor-trail render with
 * {@code NoClassDefFoundError}.
 */
public record LinkGuiCursorPoint(float x, float y, long t) {
}
