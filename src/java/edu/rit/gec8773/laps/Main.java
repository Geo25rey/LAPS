package edu.rit.gec8773.laps;

import edu.rit.gec8773.laps.parser.Parser;
import edu.rit.gec8773.laps.parser.topDown.TopDownParser;
import edu.rit.gec8773.laps.resources.Resources;
import edu.rit.gec8773.laps.scanner.CustomScanner;
import edu.rit.gec8773.laps.util.BNFWriter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * The main class which a user of the LAPS library could use
 */
public class Main {
	private static final Integer COMMAND_LINE_ARGS_FAIL = -5;
	private static Resources r;
	private static CustomScanner sc;
	private static Parser p;
	private static BNFWriter bnfWriter = null;

	/**
	 * Prints out the usage message
	 */
	private static void usage() {
		System.out.println("laps [options]\n");
		System.out.println("Options:");
		System.out.println(
				"-s [filename]: Saves the language to the " +
				"specified filename or a filename set by the current time " +
				"by default\n" +

				"-b [filename]: Write the language grammar to the specified " +
				"filename or stdout by default\n" +

				"-l [filename]: Loads the language with the specified " +
				"filename or the newest language file in the working " +
				"directory by default (same format as the default save " +
				"filename)\n" +

				"-c <class-name>: Sets the grammatical entry point for the " +
				"language (unnecessary if the language is loaded from a " +
				"file)\n" +

				"-d: Enables debugging output\n" +

				"-f <filename>: executes source code from the file with the " +
				"given filename in the described language\n" +

				"-h: Prints this usage message and exits normally\n" +

				"\nNote: If you ever encounter a StackOverflowError during " +
				"parsing of a large input, adding \"-Xss4m\" to the JVM " +
				"arguments may fix your issue.");
	}

	/**
	 * Initializes the methods for commandline argument parser
	 */
	private static void initArgParseMap() {
		argParseMap = new HashMap<>();
		argParseMap.put("l", (args, i) -> {
			if (i + 1 == args.length || args[i + 1].startsWith("-"))
				Resources.load();
			else
				Resources.load(args[++i]);
			return i;
		});
		argParseMap.put("c", (args, i) -> {
			if (i + 1 == args.length || args[i + 1].startsWith("-")) {
				return COMMAND_LINE_ARGS_FAIL;
			}
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			r.setParserHead(TopDownParser.grammarRule(
				loader.loadClass(args[++i])));
			return i;
		});
		argParseMap.put("s", (args, i) -> {
			if (i + 1 == args.length || args[i + 1].startsWith("-"))
				setSave();
			else
				setSave(args[++i]);
			return i;
		});
		argParseMap.put("b", (args, i) -> {
			if (i + 1 == args.length || args[i + 1].startsWith("-"))
				bnfWriter = new BNFWriter();
			else
				bnfWriter = new BNFWriter(args[++i]);
			return i;
		});
		argParseMap.put("d", (args, i) -> {
			r.enableDebugOutput();
			return i;
		});
		argParseMap.put("f", (args, i) -> {
			if (i + 1 == args.length || args[i + 1].startsWith("-"))
				throw new IllegalArgumentException("the -f option requires a " +
						"file argument");
			else
				sc = new CustomScanner(args[++i]);
			return i;
		});
		argParseMap.put("h", (args, i) -> {
			usage();
			System.exit(0);
			return i;
		});
	}

	/**
	 * Necessary to throw exceptions from lambda functions
	 * @see java.util.function.BiFunction
	 */
	private interface MyBiFunction<A,B,R> {
		R apply(A a, B b) throws Exception;
	}

	/**
	 * Container for methods of each commandline argument
	 */
	private static HashMap<String,
			MyBiFunction<String[], Integer, Integer>> argParseMap;

	/**
	 * Parses the commandline arguments
	 *
	 * @param args the arguments to parse
	 * @throws Exception when an argument fails to parse
	 */
	private static void parseArgs(String[] args) throws Exception {
		if (argParseMap == null)
			initArgParseMap();
		int i;
		for (i = 0; i < args.length; ++i) {
			if (i < 0 || !args[i].startsWith("-")) {
				usage();
				System.exit(-5);
			}
			String option = args[i].substring(1)
					       .toLowerCase();
			i = argParseMap.get(option).apply(args, i);
		}
	}

	/**
	 * Indicates if the language should be saved or not
	 */
	private static boolean save = false;

	/**
	 * The optional name of the file to save to
	 */
	private static String saveName = null;

	/**
	 * Sets {@link Main#save} to true, indicating that the language should be
	 * saved to a file
	 */
	private static void setSave() {
		save = true;
	}

	/**
	 * Sets {@link Main#save} to true, indicating that the language should be
	 * saved to a file with the given name
	 *
	 * @param name the name of the file to print to
	 */
	private static void setSave(String name) {
		save = true;
		saveName = name;
	}

	/**
	 * Saves the language if {@link Main#save} is set to true
	 *
	 * @throws IOException when there's an error saving to the specified file
	 */
	private static void saveIfSet() throws IOException {
		if (save) {
			if (saveName == null)
				Resources.save();
			else
				Resources.save(saveName);
		}
	}

	/**
	 * Parses commandline arguments and executes the language description
	 * passed in. If no description is given, the program exits normally
	 *
	 * @param args the commandline arguments
	 * @see Main#usage()
	 * @throws IOException when there is an error closing the token scanner
	 * @see TopDownParser
	 */
	public static void main(String[] args) throws IOException {
		int result = 0;
		try {
			r = Resources.instance;
			parseArgs(args);
			saveIfSet();
			p = r.getParserHead();
			if (bnfWriter != null && p instanceof BNFWriter.Writable) {
				bnfWriter.writeGrammar((BNFWriter.Writable)p);
				bnfWriter.close();
			}
			if (sc == null)
				sc = new CustomScanner(System.in);
			Object AST = p.parse(sc);
			sc.skip();
			if (AST == null) {
				System.out.print("\nCould not parse input: \"");
				System.out.print(sc.getBufferString());
				System.out.println("\" on line " + sc.getLineNumber());
			} else {
				System.out.print("\nUnparsed input: \"");
				System.out.print(sc.getBufferString());
				System.out.println("\" got to line " + sc.getLineNumber());
			}
		} catch (InvocationTargetException e) { // for user exceptions
			result = -3;
			Throwable t = e.getTargetException();
			printStackTrace(t);
		} catch (InstantiationException e) { // for user exceptions
			result = -4;
			Throwable t = e.getCause();
			printStackTrace(t);
		} catch (IOException ioe) {
			System.err.println("\nI/O Error: " + ioe.getMessage());
			if (r.debugEnabled())
				ioe.printStackTrace();
			result = -2;
		} catch (Exception e) {
			System.err.println();
			e.printStackTrace();
			result = -1;
		} finally {
			if (sc != null)
				sc.close();
			System.exit(result);
		}
	}

	private static void printStackTrace(Throwable throwable) {
		StackTraceElement[] elements = throwable.getStackTrace();
		Pattern nativeConstructorPattern = Pattern.compile("java\\.base/jdk" +
				"\\.internal\\.reflect\\.Native(Constructor|Method)AccessorImpl\\." +
				"(newInstance|invoke)0\\(Native Method\\)");
		System.err.println();
		System.err.println(throwable.toString());
		for (StackTraceElement element : elements)
			if (!r.debugEnabled() &&
					nativeConstructorPattern.matcher(element.toString())
											.matches())
				break;
			else
				System.err.println("    at " + element);
	}
}
