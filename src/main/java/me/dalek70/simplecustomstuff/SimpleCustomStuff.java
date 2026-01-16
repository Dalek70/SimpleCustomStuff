package me.dalek70.simplecustomstuff;
import org.mineacademy.fo.plugin.SimplePlugin;

public final class SimpleCustomStuff extends SimplePlugin {
	@Override
	protected void onPluginStart() {

	}

	public static SimpleCustomStuff getInstance() {
		return (SimpleCustomStuff) SimplePlugin.getInstance();
	}
}
