package me.dalek70.simplecustomstuff;

import org.bukkit.plugin.java.JavaPlugin;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class InMemoryJavaCompiler {

	public static Class<?> compile(String className, String javaCode, JavaPlugin plugin) throws Exception {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null)
			throw new IllegalStateException("No Java compiler found! Make sure the server runs on a JDK.");

		StandardJavaFileManager stdFileManager = compiler.getStandardFileManager(null, null, null);
		ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
		MemoryJavaFileManager fileManager = new MemoryJavaFileManager(stdFileManager, pluginClassLoader);

		JavaFileObject source = new MemorySourceJavaFileObject(className, javaCode);

		// Build proper classpath
		Set<String> cp = new LinkedHashSet<>();
		String javaClassPath = System.getProperty("java.class.path");
		if (javaClassPath != null) cp.addAll(Arrays.asList(javaClassPath.split(File.pathSeparator)));

		// Add plugin jar explicitly
		File pluginJar = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		cp.add(pluginJar.getAbsolutePath());

		// Add all JVM runtime classpath entries
		String runtimeClasspath = ManagementFactory.getRuntimeMXBean().getClassPath();
		if (runtimeClasspath != null) cp.addAll(Arrays.asList(runtimeClasspath.split(File.pathSeparator)));

		// Extract classpath from plugin's classloader (contains Bukkit/Paper API)
		addClassLoaderUrls(pluginClassLoader, cp);

		// Also check the server's main classloader
		addClassLoaderUrls(plugin.getServer().getClass().getClassLoader(), cp);

		// Find Paper's bundled libraries (extracted to cache)
		findPaperLibraries(plugin, cp);

		List<String> options = new ArrayList<>();
		options.add("-classpath");
		options.add(String.join(File.pathSeparator, cp));

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		JavaCompiler.CompilationTask task = compiler.getTask(
				null,
				fileManager,
				diagnostics,
				options,
				null,
				Collections.singletonList(source)
		);

		if (!task.call()) {
			StringBuilder errorMsg = new StringBuilder("Compilation failed:\n");
			for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
				errorMsg.append(diagnostic.getMessage(null)).append("\n");
			}
			throw new IllegalStateException(errorMsg.toString());
		}

		return fileManager.getClassLoader(null).loadClass(className);
	}

	private static void findPaperLibraries(JavaPlugin plugin, Set<String> cp) {
		// Paper extracts bundled libraries to cache directories
		// Common locations: .paper-remapped, libraries, cache

		// getWorldContainer() returns the world folder, but we need the server root
		File worldContainer = plugin.getServer().getWorldContainer();
		File serverRoot = worldContainer != null ? worldContainer.getParentFile() : new File(".");
		if (serverRoot == null) serverRoot = new File(".");

		// Also check from current working directory
		File cwd = new File(System.getProperty("user.dir"));

		String[] libDirs = {"libraries", ".paper-remapped", "cache", "bundler"};
		for (String dirName : libDirs) {
			// Check from server root
			File libDir = new File(serverRoot, dirName);
			if (libDir.exists() && libDir.isDirectory()) {
				findJarsRecursively(libDir, cp, 5); // limit depth
			}
			// Check from current working directory
			File libDirCwd = new File(cwd, dirName);
			if (libDirCwd.exists() && libDirCwd.isDirectory()) {
				findJarsRecursively(libDirCwd, cp, 5);
			}
		}

		// Also check for Paper's versions folder
		File versionsDir = new File(serverRoot, "versions");
		if (versionsDir.exists() && versionsDir.isDirectory()) {
			findJarsRecursively(versionsDir, cp, 3);
		}
		File versionsDirCwd = new File(cwd, "versions");
		if (versionsDirCwd.exists() && versionsDirCwd.isDirectory()) {
			findJarsRecursively(versionsDirCwd, cp, 3);
		}
	}

	private static void findJarsRecursively(File dir, Set<String> cp, int maxDepth) {
		if (maxDepth <= 0 || dir == null || !dir.exists()) return;

		File[] files = dir.listFiles();
		if (files == null) return;

		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".jar")) {
				cp.add(file.getAbsolutePath());
			} else if (file.isDirectory()) {
				findJarsRecursively(file, cp, maxDepth - 1);
			}
		}
	}

	private static void addClassLoaderUrls(ClassLoader classLoader, Set<String> cp) {
		if (classLoader == null) return;

		// Handle standard URLClassLoader
		if (classLoader instanceof URLClassLoader urlClassLoader) {
			for (URL url : urlClassLoader.getURLs()) {
				if ("file".equals(url.getProtocol())) {
					cp.add(new File(url.getPath()).getAbsolutePath());
				}
			}
		}

		// Try reflection to get URLs from Paper's custom classloaders (PaperPluginClassLoader, etc.)
		try {
			// Try to find a getURLs method via reflection
			java.lang.reflect.Method getUrlsMethod = null;
			for (java.lang.reflect.Method m : classLoader.getClass().getMethods()) {
				if (m.getName().equals("getURLs") && m.getParameterCount() == 0) {
					getUrlsMethod = m;
					break;
				}
			}
			if (getUrlsMethod != null) {
				URL[] urls = (URL[]) getUrlsMethod.invoke(classLoader);
				if (urls != null) {
					for (URL url : urls) {
						if ("file".equals(url.getProtocol())) {
							cp.add(new File(url.getPath()).getAbsolutePath());
						}
					}
				}
			}
		} catch (Exception ignored) {
			// Reflection failed, continue with other methods
		}

		// Try to find jar files from the classloader's loaded classes (especially org.bukkit classes)
		try {
			Class<?> bukkitClass = classLoader.loadClass("org.bukkit.Bukkit");
			URL bukkitLocation = bukkitClass.getProtectionDomain().getCodeSource().getLocation();
			if (bukkitLocation != null && "file".equals(bukkitLocation.getProtocol())) {
				cp.add(new File(bukkitLocation.toURI()).getAbsolutePath());
			}
		} catch (Exception ignored) {
			// Class not found or other error
		}

		// Try to find Adventure API classes (used by Paper)
		String[] adventureClasses = {
			"net.kyori.adventure.audience.Audience",
			"net.kyori.adventure.key.Namespaced",
			"net.kyori.adventure.text.event.HoverEventSource",
			"net.kyori.adventure.text.Component",
			"net.kyori.examination.Examinable"
		};
		for (String className : adventureClasses) {
			try {
				Class<?> adventureClass = classLoader.loadClass(className);
				URL location = adventureClass.getProtectionDomain().getCodeSource().getLocation();
				if (location != null && "file".equals(location.getProtocol())) {
					cp.add(new File(location.toURI()).getAbsolutePath());
				}
			} catch (Exception ignored) {
				// Class not found
			}
		}

		// Traverse parent classloaders
		if (classLoader.getParent() != null) {
			addClassLoaderUrls(classLoader.getParent(), cp);
		}
	}

	private static class MemorySourceJavaFileObject extends SimpleJavaFileObject {
		private final String code;

		protected MemorySourceJavaFileObject(String className, String code) {
			super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.code = code;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return code;
		}
	}

	private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
		private final Map<String, ByteArrayOutputJavaFileObject> classBytes = new HashMap<>();
		private final ClassLoader parentClassLoader;

		protected MemoryJavaFileManager(StandardJavaFileManager fileManager, ClassLoader parentClassLoader) {
			super(fileManager);
			this.parentClassLoader = parentClassLoader;
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
			ByteArrayOutputJavaFileObject out = new ByteArrayOutputJavaFileObject(className, kind);
			classBytes.put(className, out);
			return out;
		}

		@Override
		public ClassLoader getClassLoader(Location location) {
			return new ClassLoader(parentClassLoader) {
				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException {
					ByteArrayOutputJavaFileObject out = classBytes.get(name);
					if (out == null) throw new ClassNotFoundException(name);
					return defineClass(name, out.getBytes(), 0, out.getBytes().length);
				}
			};
		}
	}

	private static class ByteArrayOutputJavaFileObject extends SimpleJavaFileObject {
		private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

		protected ByteArrayOutputJavaFileObject(String name, Kind kind) {
			super(URI.create("byte:///" + name + kind.extension), kind);
		}

		@Override
		public ByteArrayOutputStream openOutputStream() {
			return baos;
		}

		public byte[] getBytes() {
			return baos.toByteArray();
		}
	}
}
