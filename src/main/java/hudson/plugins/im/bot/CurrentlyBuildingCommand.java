package hudson.plugins.im.bot;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Queue.Executable;
import hudson.model.queue.SubTask;
import hudson.plugins.im.IMChat;
import hudson.plugins.im.IMException;
import hudson.plugins.im.IMMessage;
import hudson.plugins.im.Sender;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

/**
 * CurrentlyBuilding command for instant messaging plugin.
 * 
 * Generates a list of jobs in progress.
 * 
 * @author Bjoern Kasteleiner
 */
@Extension
public class CurrentlyBuildingCommand extends BotCommand {
	private static final String SYNTAX = " [~ regex pattern]";
	private static final String HELP = SYNTAX + " - list jobs which are currently in progress, with optional filter on reported lines";

	@Override
	public Collection<String> getCommandNames() {
		return Arrays.asList("currentlyBuilding", "cb");
	}

	@Override
	public void executeCommand(Bot bot, IMChat chat, IMMessage message,
			Sender sender, String[] args) throws IMException {
		StringBuffer msg = new StringBuffer();
		String filterRegex = null;
		Pattern filterPattern = null;
		if (args.length >= 2) {
			switch (args[1]) {
				case "~":
					if (args.length < 3) {
						msg.append("\n- WARNING: got filtering argument for currentlyBuilding, but no filter value - so none was applied\n");
						break;
					}
					for (int i = 2; i < args.length; i++) {
						if (i>2) {
							// We can not really assume what the user
							// entered if there were e.g. several
							// whitespaces trimmed by line-parser.
							// So if they meant modifiers (brackets,
							// counts), they should spell them out.
							filterRegex += " ";
						}
						filterRegex += args[i];
					}
					msg.append("\n- NOTE: got argument for currentlyBuilding: applying regex filter to reported strings: " + filterRegex);
					filterPattern = Pattern.compile(filterRegex);
					break;
				default:
					msg.append("\n- WARNING: got unsupported argument for currentlyBuilding, no filter was applied\n");
					break;
			}
		}

		int countJobsInProgess = 0;
		int countJobsInPattern = 0;
		for (Computer computer : Jenkins.getInstance().getComputers()) {
			for (Executor executor : computer.getExecutors()) {
				Executable currentExecutable = executor.getCurrentExecutable();
				if (currentExecutable != null) {
					countJobsInProgess++;

					SubTask task = currentExecutable.getParent();
					Item item = null;
					if (task instanceof Item) {
						item = (Item) task;
					}

					StringBuffer msgLine = new StringBuffer();

					msgLine.append(computer.getDisplayName());
					msgLine.append("#");
					msgLine.append(executor.getNumber());
					msgLine.append(": ");
					msgLine.append(item != null ? item.getFullDisplayName() : task.getDisplayName());

					if (filterPattern != null) {
						Matcher matcher = filterPattern.matcher(msgLine);
						if (!matcher.find()) {
							continue;
						}
						// We have a regex hit, report it
						countJobsInPattern++;
					}

					msg.append("\n- ");
					msg.append(msgLine);
					msg.append(" (Elapsed time: ");
					msg.append(Util.getTimeSpanString(executor.getElapsedTime()));
					msg.append(", Estimated remaining time: ");
					msg.append(executor.getEstimatedRemainingTime());
					msg.append(")");
				}
			}
		}

		if (countJobsInProgess == 0) {
			msg.append("\n- No jobs are running.");
		} else if (countJobsInPattern == 0 && filterPattern != null) {
			msg.append("\n- None of the running matched the filter.");
		}

		if (filterPattern != null) {
			msg.insert(0, "Currently building (" + countJobsInProgess +
				" items total, of which " + countJobsInPattern +
				" items matched the filter):");
		} else {
			msg.insert(0, "Currently building (" + countJobsInProgess +
				" items):");
		}

		chat.sendMessage(msg.toString());
	}

	private String giveSyntax(String sender, String cmd) {
		return sender + ": syntax is: '" + cmd +  SYNTAX + "'";
	}

	@Override
	public String getHelp() {
		return HELP;
	}

}
