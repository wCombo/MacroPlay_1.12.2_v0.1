package com.angel.macroplay;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public class CommandMacro extends CommandBase {
    private static final List<String> pendingConfirmations = new ArrayList<>();

    @Override @Nonnull public String getName() { return "macro"; }
    @Override @Nonnull public String getUsage(@Nonnull ICommandSender s) { return "/macro <record|play|stop|list|delete|rename>"; }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length < 1) return;
        String action = args[0].toLowerCase();

        switch (action) {
            case "record":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "Uso: /macro record <nombre>"));
                    return;
                }
                String nombre = args[1];
                if (MacroEngine.INSTANCE.exists(nombre) && !pendingConfirmations.contains(nombre)) {
                    sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "El macro '" + nombre + "' ya existe."));
                    sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Escribe el comando de nuevo para sobreescribirlo."));
                    pendingConfirmations.add(nombre);
                    return;
                }
                if (!MacroEngine.INSTANCE.isBusy()) {
                    MacroEngine.INSTANCE.startRecording(nombre);
                    pendingConfirmations.remove(nombre);
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "Grabando..."));
                }
                break;

            case "stop":
                MacroEngine.INSTANCE.saveRecording();
                MacroEngine.INSTANCE.stop();
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[MacroPlay] - Se ha detenido y guardado."));
                break;

            case "play":
                if (args.length < 2) return;
                int r = 1;
                if (args.length > 2) {
                    try { r = Integer.parseInt(args[args.length - 1]); } catch (Exception e) {}
                }
                if (MacroEngine.INSTANCE.loadAndPlay(args[1], r)) {
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Reproduciendo: " + args[1]));
                }
                break;

            case "list":
                List<String> ms = MacroEngine.INSTANCE.listMacros();
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "--- Lista de Macros ---"));
                for (String m : ms) sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "- " + m));
                break;

            case "delete":
                if (args.length > 1 && MacroEngine.INSTANCE.deleteMacro(args[1]))
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Borrado: " + args[1]));
                break;

            case "rename":
                if (args.length > 2 && MacroEngine.INSTANCE.renameMacro(args[1], args[2]))
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Renombrado exitoso."));
                break;
        }
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }
}