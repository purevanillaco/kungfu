package co.purevanilla.mcplugins.kungfu.listener;

import co.purevanilla.mcplugins.kungfu.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;

import java.io.IOException;

public class GenericListener implements org.bukkit.event.Listener {

    @EventHandler()
    public void onMessage(AsyncChatEvent event){
        try {
            Main.getAPI().log(event.getPlayer().getName(), event.originalMessage().toString(), null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
