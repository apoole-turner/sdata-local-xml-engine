package com.turner.sdata.helpers;

public class DistributorBuilder {
	private String jmsDistrubutorStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><action name=\"%%NAME%%\" class=\"com.turner.loki.plugins.JmsDistributor\">\n"
			+
			// " threaded=\"true\" threadPoolName=\"gpmsDistributor\" poolMax=\"3\"\n" +
			// " poolMaxIdle=\"8\" poolMaxWait=\"15000\">\n" +
			"		<initialize>\n" + "			<param name=\"queueManager\" value=\"%%QUEUE_MANAGER%%\" />\n"
			+ "			<param name=\"queue\" value=\"%%QUEUE%%\" />\n"
			+ "			<param name=\"msgObjectType\" value=\"bytes\" />\n" + "		</initialize>\n"
			+ "		<monitor />\n" + "		<success action=\"stopFlow\" />\n"
			+ "		<success action=\"showMessage\" />\n" + "	</action>";

	private String queueManager;
	private String name;
	private String queue;
	
	
	public String build() {
		String msg=jmsDistrubutorStr;
		
		msg=msg.replaceAll("%%NAME%%", this.name);
		msg=msg.replace("%%QUEUE_MANAGER%%", this.queueManager);
		msg=msg.replace("%%QUEUE%%", this.queue);
		return msg;
	}
	public DistributorBuilder setJmsDistrubutorStr(String jmsDistrubutorStr) {
		this.jmsDistrubutorStr = jmsDistrubutorStr;
		return this;
	}


	public DistributorBuilder setQueueManager(String queueManager) {
		this.queueManager = queueManager;
		return this;
	}

	public DistributorBuilder setName(String name) {
		this.name = name;
		return this;
	}

	public DistributorBuilder setQueue(String queue) {
		this.queue = queue;
		return this;
	}

}
