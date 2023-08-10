package co.purevanilla.mcplugins.kungfu.listener;

import co.purevanilla.mcplugins.kungfu.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GenericListener implements org.bukkit.event.Listener {

    Plugin plugin;

    public GenericListener(Plugin plugin){
        this.plugin=plugin;
    }

    @EventHandler()
    public void onMessage(AsyncChatEvent event){
        try {
            Main.getAPI().log(event.getPlayer().getName(), ((TextComponent) event.originalMessage()).content(), null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler
    public void onPrivateMessage(PlayerCommandPreprocessEvent event) throws IOException {
        Set<String> commands = new HashSet<>();
        commands.add("msg");
        commands.add("tell");
        commands.add("w");

        String[] parts = event.getMessage().split("\\s+");
        if(parts.length>=3){
            for(String command: commands){
                if(parts[0].toLowerCase().equals("/"+command)){
                    @Nullable Player recipient = this.plugin.getServer().getPlayer(parts[1]);
                    if(recipient!=null){
                        String message = String.join(" ", Arrays.stream(parts).toList().subList(2, parts.length));
                        Set<Player> recipients = new HashSet<>();
                        recipients.add(recipient);
                        Main.getAPI().log(event.getPlayer().getName(), message, recipients);
                    }
                    break;
                }
            }
        }
    }

}
