package me.dalek70.simplecustomstuff.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class BookUtil {

	/**
	 * Returns the full text of a book in the player's main hand.
	 * Works for WRITTEN_BOOK and BOOK_AND_QUILL.
	 * Returns null if the player isn't holding a book.
	 */
	public static String getBookText(Player player) {
		ItemStack item = player.getInventory().getItemInMainHand();

		if (item == null) return null;

		Material type = item.getType();
		if (type != Material.WRITTEN_BOOK && type != Material.WRITABLE_BOOK) return null;

		if (item.hasItemMeta() && item.getItemMeta() instanceof BookMeta meta) {
			return String.join("\n", meta.getPages());
		}

		return null;
	}
}
