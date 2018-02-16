package com.turner.sdata;

import java.io.*;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//Put here by Austin Poole because I don't want all those transitive dependencies from the loki-core plugins :/. Sue me 
/** 
 * Created by Stephen McConnell on 1/27/16.
 *
 * Perl is SOOO much easier than this. But the libraries one has to load in a Docker image
 * make it too big.
 *
 * This just runs in the JVM.
 *
 * Arguments:
 *   [Absolute path to source erb file] [Absolute path to target file]
 *
 */
public class EnvironmentVariableReplace {
	public static String myPat = "<%= *@([a-zA-Z0-9_]*) *%>";

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Useage: 2 arguments: [ Absolute path of erb file ] [ Absolute path target file ]");
			return;
		}

		System.out.println("Performing Variable Replacement on: [" + args[0] + "].");
		System.out.println("Writing to                          [" + args[1] + "].");

		File erbFile = new File(args[0]);
		File tgtFile = new File(args[1]);

		BufferedReader reader = null;
		BufferedWriter writer = null;

		try {
			reader = new BufferedReader(new FileReader(erbFile));
			writer = new BufferedWriter(new FileWriter(tgtFile, false));
		} catch (FileNotFoundException e) {
			System.out.println("***** File: [" + erbFile.getAbsolutePath() + "] does not exist.");
			return;
		} catch (Throwable t) {
			t.printStackTrace();
		}

		// Get a map of the enviornment variables.
		Map<String, String> env = System.getenv();
		System.out.println("Looking for following enviornment variables:");

		for (String envName: env.keySet()) {
			System.out.format("   %s=%s%n", envName, env.get(envName));
		}

		System.out.println("-----------------------------------------------------------------------");

		Pattern pattern = Pattern.compile(myPat);

		try {
			String line;

			while ((line = reader.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);

				if (matcher.find()) {
					int start;

					while ((start = line.indexOf("<%=")) != -1) {
						int end = line.indexOf("%>") + 2;
						String patStr = line.substring(start, end);
						String patName = patStr.substring(3, patStr.length() - 2).trim().substring(1);

						boolean foundMatch = false;

						for (String envName: env.keySet()) {
							// There are env variables with name "_"
							if (patName.equals(envName)) {
								line = line.replace(patStr, env.get(envName));
								foundMatch = true;

								break;
							}
						}

						if (!foundMatch) {
							System.out.println("ERROR:  Failed to find an environment variable match for " + patName);

							System.exit(-1);
						}
					}
				}

				System.out.println("****** " + line);
				writer.write(line);
				writer.newLine();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			System.out.println("Closing files.");
			// Like in Kindergarden: when you make a mess clean it up. :)
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (writer != null) {
				try {
					writer.flush();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		System.out.println("Return for variable replacement.");
	}
}
