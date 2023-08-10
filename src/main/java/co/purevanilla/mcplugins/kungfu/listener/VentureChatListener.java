package co.purevanilla.mcplugins.kungfu.listener;

import co.purevanilla.mcplugins.kungfu.Main;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VentureChatListener implements org.bukkit.event.Listener {

    Plugin plugin;

    public VentureChatListener(Plugin plugin){
        this.plugin=plugin;
    }

    @EventHandler
    public void onPrivateMessage(PlayerCommandPreprocessEvent event) throws IOException {
        Set<String> commands = new HashSet<>();
        commands.add("r");
        commands.add("reply");

        String[] parts = event.getMessage().split("\\s+");
        if(parts.length>=2){
            for(String command: commands){
                if(parts[0].toLowerCase().equals("/"+command)){
                    MineverseChatPlayer sender = MineverseChatAPI.getMineverseChatPlayer(event.getPlayer());
                    if(sender!=null){
                        UUID replyUUID = sender.getReplyPlayer();
                        if(replyUUID!=null){
                            Player recipient = this.plugin.getServer().getPlayer(replyUUID);
                            if(recipient!=null){
                                String message = String.join(" ", Arrays.stream(parts).toList().subList(1, parts.length));
                                Set<Player> recipients = new HashSet<>();
                                recipients.add(recipient);
                                Main.getAPI().log(event.getPlayer().getName(), message, recipients);
                            }
                        }

                    }
                    break;
                }
            }
        }
    }

}
