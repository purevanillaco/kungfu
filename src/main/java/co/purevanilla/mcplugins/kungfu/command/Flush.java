package co.purevanilla.mcplugins.kungfu.command;

import co.purevanilla.mcplugins.kungfu.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Flush implements CommandExecutor {

    Plugin plugin;

    public Flush(Plugin plugin){
        this.plugin=plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        long lines = 0;
        if(args.length>0){
            try {
                lines = Long.parseLong(args[0]);
            } catch (NumberFormatException err){
                return false;
            }
        }

        long finalLines = lines;
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            Main.getAPI().flush(0, finalLines);
        });

        return true;
    }

}
