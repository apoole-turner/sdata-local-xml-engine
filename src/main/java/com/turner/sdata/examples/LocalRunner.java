package com.turner.sdata.examples;

import com.turner.sdata.LocalXMLEngine;

public class LocalRunner {
	public static void main(String[] args) {
		LocalXMLEngine.setEnvPropertiesFileName("env.properties");
		LocalXMLEngine.setWorkflowFilename("workflow.xml");
		LocalXMLEngine.setThreadPropertiesFilename("loki-threads.properties");
		LocalXMLEngine.setRules(new CustomRules());
		LocalXMLEngine.start();

	}
}
