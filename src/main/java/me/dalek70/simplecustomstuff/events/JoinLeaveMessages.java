package me.dalek70.simplecustomstuff.events;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.mineacademy.fo.annotation.AutoRegister;

@AutoRegister
public final class JoinLeaveMessages implements Listener {
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		event.joinMessage(Component.text(""));
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		event.quitMessage(Component.text(""));
	}
}
