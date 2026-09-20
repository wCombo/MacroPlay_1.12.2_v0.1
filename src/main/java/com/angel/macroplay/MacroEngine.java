package com.angel.macroplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.CPacketCloseWindow;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MacroEngine {
    public static final MacroEngine INSTANCE = new MacroEngine();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File macroDir = new File(Minecraft.getMinecraft().mcDataDir, "macros");

    private ArrayList<MacroData> recordedActions = new ArrayList<>();
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private int playIndex = 0;
    private int remainingReplays = 0;
    private String currentName = "";

    // Memoria
    private boolean prevLeft = false;
    private boolean prevRight = false;
    private HashSet<Integer> prevKeys = new HashSet<>();
    private int rightClickDelay = 0; // Emula el temporizador interno de Minecraft

    public MacroEngine() {
        if (!macroDir.exists()) macroDir.mkdirs();
    }

    public void startRecording(String nombre) {
        this.currentName = nombre;
        this.recordedActions.clear();
        this.prevKeys.clear();
        this.isRecording = true;
        this.isPlaying = false;
        this.playIndex = 0;
    }

    public void saveRecording() {
        if (currentName.isEmpty() || recordedActions.isEmpty()) return;
        try (Writer writer = new FileWriter(new File(macroDir, currentName + ".json"))) {
            GSON.toJson(recordedActions, writer);
        } catch (IOException e) {}
    }

    public boolean loadAndPlay(String nombre, int repeticiones) {
        File file = new File(macroDir, nombre + ".json");
        if (!file.exists()) return false;
        try (Reader reader = new FileReader(file)) {
            recordedActions = GSON.fromJson(reader, new TypeToken<ArrayList<MacroData>>(){}.getType());
            this.remainingReplays = repeticiones;
            this.playIndex = 0;
            this.isPlaying = true;
            this.isRecording = false;
            this.prevKeys.clear();
            this.rightClickDelay = 0;
            return true;
        } catch (IOException e) { return false; }
    }

    public void stop() {
        this.isRecording = false;
        this.isPlaying = false;
        this.playIndex = 0;
        KeyBinding.unPressAllKeys();
        for (int i = 0; i < 256; i++) KeyBinding.setKeyBindState(i, false);
    }

    public boolean exists(String nombre) { return new File(macroDir, nombre + ".json").exists(); }
    public boolean isBusy() { return isRecording || isPlaying; }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || Minecraft.getMinecraft().player == null) return;
        Minecraft mc = Minecraft.getMinecraft();

        // Pánico
        if (Keyboard.isKeyDown(MacroPlay.stopKey.getKeyCode())) {
            if (isRecording || isPlaying) {
                if (isRecording) saveRecording();
                stop();
                mc.player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[MacroPlay] - Se ha detenido."));
            }
        }

        // GRABAR
        if (isRecording) {
            HashSet<Integer> pressed = new HashSet<>();
            for (int i = 0; i < 256; i++) {
                if (Keyboard.isKeyDown(i)) pressed.add(i);
            }
            recordedActions.add(new MacroData(
                mc.player.posX, mc.player.posY, mc.player.posZ,
                mc.player.rotationYaw, mc.player.rotationPitch,
                mc.player.motionX, mc.player.motionY, mc.player.motionZ,
                mc.player.inventory.currentItem, pressed,
                Mouse.isButtonDown(0), Mouse.isButtonDown(1),
                Mouse.getX(), Mouse.getY(), mc.currentScreen != null
            ));
        }

        // REPRODUCIR
        if (isPlaying && playIndex < recordedActions.size()) {
            MacroData data = recordedActions.get(playIndex);
            
            // Sincronización de físicas y teclado general
            mc.player.setPosition(data.x, data.y, data.z);
            mc.player.motionX = data.mX; mc.player.motionY = data.mY; mc.player.motionZ = data.mZ;
            mc.player.rotationYaw += (data.yaw - mc.player.rotationYaw) * 0.7f;
            mc.player.rotationPitch += (data.pitch - mc.player.rotationPitch) * 0.7f;
            
            for (int i = 0; i < 256; i++) KeyBinding.setKeyBindState(i, data.keys.contains(i));
            mc.player.inventory.currentItem = data.hotbarSlot;

            // --- LÓGICA DE APERTURA DE INVENTARIO (E) ---
            int invKey = mc.gameSettings.keyBindInventory.getKeyCode();
            if (data.keys.contains(invKey) && !prevKeys.contains(invKey)) {
                if (mc.currentScreen == null) {
                    mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));
                } else {
                    mc.player.closeScreen();
                    mc.getConnection().sendPacket(new CPacketCloseWindow(mc.player.openContainer.windowId));
                }
            }

            // --- SEPARACIÓN: MUNDO vs GUI ---
            if (data.hasGuiOpen && mc.currentScreen != null) {
                handleGuiLogic(mc, data);
            } else {
                // Si en el macro NO hay GUI abierto, pero en el juego sí, lo cerramos.
                if (mc.currentScreen != null) {
                    mc.player.closeScreen();
                    mc.getConnection().sendPacket(new CPacketCloseWindow(mc.player.openContainer.windowId));
                }

                // Clic Izquierdo (Romper / Atacar)
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), data.leftClick);
                if (data.leftClick && !prevLeft) invokeClick(mc, 0);

                // Clic Derecho (Poner Bloques / Usar items / Abrir cofres)
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), data.rightClick);
                if (data.rightClick) {
                    if (!prevRight || rightClickDelay == 0) {
                        invokeClick(mc, 1);
                        rightClickDelay = 4; // Resetear temporizador de 4 ticks
                    } else if (rightClickDelay > 0) {
                        rightClickDelay--;
                    }
                } else {
                    rightClickDelay = 0;
                }
            }

            // Actualizar memorias
            prevLeft = data.leftClick;
            prevRight = data.rightClick;
            prevKeys = new HashSet<>(data.keys);
            playIndex++;
            
        } else if (isPlaying) {
            if (remainingReplays > 1) { remainingReplays--; playIndex = 0; }
            else { stop(); mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[MacroPlay] - Finalizado.")); }
        }
    }

    private void handleGuiLogic(Minecraft mc, MacroData data) {
        if (!(mc.currentScreen instanceof GuiContainer)) return;
        try {
            GuiContainer gui = (GuiContainer) mc.currentScreen;
            Mouse.setCursorPosition(data.mouseX, data.mouseY);

            // Obtener el Slot
            int x = data.mouseX * gui.width / mc.displayWidth;
            int y = gui.height - data.mouseY * gui.height / mc.displayHeight - 1;
            Slot slot = null;
            try {
                Method m = ReflectionHelper.findMethod(GuiContainer.class, "getSlotAtPosition", "func_146975_c", int.class, int.class);
                slot = (Slot) m.invoke(gui, x, y);
            } catch (Exception e) {}

            int slotId = (slot != null) ? ReflectionHelper.getPrivateValue(Slot.class, slot, "slotNumber", "field_75222_d") : -999;

            // Teclas Numéricas (1-9) para mover items
            for (int i = 0; i < 9; i++) {
                int keyCode = mc.gameSettings.keyBindsHotbar[i].getKeyCode();
                if (data.keys.contains(keyCode) && !prevKeys.contains(keyCode)) {
                    if (slotId != -999) mc.playerController.windowClick(mc.player.openContainer.windowId, slotId, i, ClickType.SWAP, mc.player);
                    return;
                }
            }

            // Clics con Ratón
            if ((data.leftClick && !prevLeft) || (data.rightClick && !prevRight)) {
                int button = data.leftClick ? 0 : 1;
                ClickType type = ClickType.PICKUP;

                // Detectar si está apretando Shift (Ya sea el bindeo oficial o el LSHIFT físico)
                if (data.keys.contains(mc.gameSettings.keyBindSneak.getKeyCode()) || data.keys.contains(42) || data.keys.contains(54)) {
                    type = ClickType.QUICK_MOVE;
                }

                // Clic fuera del GUI para tirar ítems
                if (slotId == -999) {
                    type = ClickType.PICKUP; 
                    // button 0 = tirar todo el stack | button 1 = tirar uno solo
                }

                mc.playerController.windowClick(mc.player.openContainer.windowId, slotId, button, type, mc.player);
            }
        } catch (Exception e) {}
    }

    private void invokeClick(Minecraft mc, int button) {
        try {
            String name = (button == 0) ? "func_147116_af" : "func_147121_ag";
            Method m = ReflectionHelper.findMethod(Minecraft.class, (button == 0) ? "clickMouse" : "rightClickMouse", name);
            m.setAccessible(true);
            m.invoke(mc);
        } catch (Exception e) {}
    }

    public List<String> listMacros() {
        File[] files = macroDir.listFiles((dir, name) -> name.endsWith(".json"));
        List<String> names = new ArrayList<>();
        if (files != null) for (File f : files) names.add(f.getName().replace(".json", ""));
        return names;
    }

    public boolean deleteMacro(String nombre) { return new File(macroDir, nombre + ".json").delete(); }
    public boolean renameMacro(String viejo, String nuevo) { return new File(macroDir, viejo + ".json").renameTo(new File(macroDir, nuevo + ".json")); }

    public static class MacroData {
        public double x, y, z; 
        public float yaw, pitch;
        public double mX, mY, mZ;
        public int hotbarSlot;
        public HashSet<Integer> keys;
        public boolean leftClick, rightClick, hasGuiOpen;
        public int mouseX, mouseY;

        public MacroData(double x, double y, double z, float yaw, float pitch, double mx, double my, double mz, int slot, HashSet<Integer> k, boolean lc, boolean rc, int mouseX, int mouseY, boolean gui) {
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch; this.mX = mx; this.mY = my; this.mZ = mz;
            this.hotbarSlot = slot; this.keys = k; this.leftClick = lc; this.rightClick = rc;
            this.mouseX = mouseX; this.mouseY = mouseY; this.hasGuiOpen = gui;
        }
    }
}