package dshell.main;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import dshell.lang.GenericPair;

public class ArgsParser {
	protected final Map<String, Option> optionMap = new LinkedHashMap<>();

	public ArgsParser addOption(String option, OptionListener listener) {
		return this.addOption(option, false, listener);
	}

	/**
	 * add option.
	 * @param option
	 * - option name, contains option prefix(--, -).
	 * overwrite option if has already defined.
	 * @param hasArg
	 * - if true, has additional argument.
	 * @param listener
	 * - call back for option handling
	 * @return
	 * - this.
	 */
	public ArgsParser addOption(String option, boolean hasArg, OptionListener listener) {
		if(this.optionMap.containsKey(option)) {
			throw new RuntimeException("already defined option: " + option);
		}
		this.optionMap.put(option, new Option(option, hasArg, listener));
		return this;
	}

	/**
	 * parse command line options.
	 * @param args
	 * @return
	 * @throws IllegalArgumentException
	 * - when parsing error happened
	 */
	public CommandLine parse(String[] args) throws IllegalArgumentException {
		String[] restArgs = null;
		final List<GenericPair<OptionListener, String>> listenerPairs = new ArrayList<>();
		final int size = args.length;
		Set<String> foundOptionSet = new HashSet<>();
		for(int i = 0; i < size; i++) {
			String optionSymbol = args[i];
			if(foundOptionSet.contains(optionSymbol)) {
				throw new IllegalArgumentException("duplicated option: " + optionSymbol);
			}
			foundOptionSet.add(optionSymbol);
			Option option = this.optionMap.get(optionSymbol);
			if(option == null) {
				if(optionSymbol.startsWith("-")) {
					throw new IllegalArgumentException("invalid option: " + optionSymbol);
				}
				int restArgSize = size - i;
				restArgs = new String[restArgSize];
				System.arraycopy(args, i, restArgs, 0, restArgSize);
				break;
			}
			String arg = null;
			if(option.requireArg()) {
				if(i + 1 < size && !args[i + 1].startsWith("-")) {
					arg = args[++i];
				} else {
					throw new IllegalArgumentException("expected for " + optionSymbol);
				}
			}
			listenerPairs.add(new GenericPair<ArgsParser.OptionListener, String>(option.getListener(), arg));
		}
		return new CommandLine(restArgs, listenerPairs);
	}

	public void printHelp(PrintStream stream) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("Options:\n");
		for(Entry<String, Option> entry : this.optionMap.entrySet()) {
			sBuilder.append("    ");
			sBuilder.append(entry.getKey());
			if(entry.getValue().hasArg) {
				sBuilder.append(" arg");
			}
			sBuilder.append('\n');
		}
		stream.println(sBuilder.toString());
	}

	protected static class Option {
		protected final String optionSymbol;
		protected final boolean hasArg;
		protected final OptionListener listener;

		protected Option(String optionSymbol, boolean hasArg, OptionListener listener) {
			this.optionSymbol = optionSymbol;
			this.hasArg = hasArg;
			this.listener = listener;
		}

		protected String getOptionSymbol() {
			return this.optionSymbol;
		}

		protected boolean requireArg() {
			return this.hasArg;
		}

		protected OptionListener getListener() {
			return this.listener;
		}
	}

	/**
	 * contains parsed options.
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	public static class CommandLine {
		private final String[] restArgs;
		private final List<GenericPair<OptionListener, String>> listenerPairs;

		protected CommandLine(String[] restArgs, List<GenericPair<OptionListener, String>> listenerPairs) {
			this.restArgs = restArgs;
			this.listenerPairs = listenerPairs;
		}

		/**
		 * call OptionListener#invoke
		 */
		public void notifiyListeners() {
			for(GenericPair<OptionListener, String> pair : this.listenerPairs) {
				pair.getLeft().invoke(pair.getRight());
			}
		}

		/**
		 * 
		 * @return
		 * - return null if has no rest arguments
		 */
		public String[] getRestArgs() {
			return this.restArgs;
		}
	}

	public static interface OptionListener {
		/**
		 * invoke action corresponds to option.
		 * @param arg
		 * - if has no argument, is null.
		 */
		public void invoke(String arg);
	}
}
