package com.angel.macroplay;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "macroplay", name = "MacroPlay", version = "0.1", clientSideOnly = true)
public class MacroPlay {
    public static KeyBinding recordKey;
    public static KeyBinding stopKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        recordKey = new KeyBinding("Grabar Macro", Keyboard.KEY_R, "MacroPlay");
        stopKey = new KeyBinding("Parar Macro (Panico)", Keyboard.KEY_K, "MacroPlay");

        ClientRegistry.registerKeyBinding(recordKey);
        ClientRegistry.registerKeyBinding(stopKey);

        MinecraftForge.EVENT_BUS.register(MacroEngine.INSTANCE);
        ClientCommandHandler.instance.registerCommand(new CommandMacro());
    }
}