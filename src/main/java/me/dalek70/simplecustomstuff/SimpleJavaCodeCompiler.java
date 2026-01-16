package me.dalek70.simplecustomstuff;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static me.dalek70.simplecustomstuff.InMemoryJavaCompiler.compile;

public class SimpleJavaCodeCompiler {

	/**
	 * Compiles user-written Java code from a book.
	 * User writes the full class; plugin only adds the package.
	 */
	public static Runnable getRunnableFromBookCode(String bookCode, Player player, JavaPlugin plugin) {
		if (bookCode == null) return null;

		// Prepend package
		String codeToCompile = "package me.dalek70.codetoexecute;\n" + bookCode;

		try {
			// Compile class
			Class<?> clazz = compile("me.dalek70.codetoexecute.Code", codeToCompile, plugin);

			// Must implement Runnable
			if (!Runnable.class.isAssignableFrom(clazz)) {
				player.sendMessage("Your class must implement Runnable!");
				return null;
			}

			// Try to find a constructor with Player + Plugin for convenience
			try {
				return (Runnable) clazz.getDeclaredConstructor(Player.class, JavaPlugin.class).newInstance(player, plugin);
			} catch (NoSuchMethodException ignored) {
				// If they didn't define that constructor, try no-arg constructor
				return (Runnable) clazz.getDeclaredConstructor().newInstance();
			}

		} catch (Exception e) {
			player.sendMessage("Error compiling/executing code: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}
