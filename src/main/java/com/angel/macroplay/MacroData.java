package com.angel.macroplay;

import java.util.HashSet;

public class MacroData {
    public float yaw, pitch;
    public int hotbarSlot;
    public HashSet<Integer> keys;
    public boolean leftClick, rightClick, hasGuiOpen;
    public int mouseX, mouseY;

    public MacroData(float y, float p, int slot, HashSet<Integer> k, boolean lc, boolean rc, int mx, int my, boolean gui) {
        this.yaw = y; this.pitch = p; this.hotbarSlot = slot;
        this.keys = k; this.leftClick = lc; this.rightClick = rc;
        this.mouseX = mx; this.mouseY = my; this.hasGuiOpen = gui;
    }
}