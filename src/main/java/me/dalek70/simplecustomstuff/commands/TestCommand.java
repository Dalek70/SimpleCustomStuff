package me.dalek70.simplecustomstuff.commands;
import static me.dalek70.simplecustomstuff.SimpleCustomStuff.getInstance;
import me.dalek70.simplecustomstuff.AnnotatedCommand;
import me.dalek70.simplecustomstuff.SimpleJavaCodeCompiler;
import me.dalek70.simplecustomstuff.annotation.Parameter;
import me.dalek70.simplecustomstuff.util.BookUtil;
import org.mineacademy.fo.annotation.AutoRegister;

@AutoRegister
public final class TestCommand extends AnnotatedCommand {
	public TestCommand() {
		super("testcommand");
	}

	@Parameter("run_book_code")
	public void runBook() {
		String code = BookUtil.getBookText(getPlayer());
		if (code == null) {
			tell("You must hold a book containing Java code!");
			return;
		}

		Runnable runnable = SimpleJavaCodeCompiler.getRunnableFromBookCode(code, getPlayer(), getInstance());
		if (runnable == null) {
			tell("Failed to compile book code!");
			return;
		}
		getInstance().getServer().getScheduler().runTask(getInstance(), runnable);
	}
}
