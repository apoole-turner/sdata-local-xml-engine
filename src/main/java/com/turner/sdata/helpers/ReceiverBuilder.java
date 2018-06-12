package com.turner.sdata.helpers;

public class ReceiverBuilder {
	private String jmsReceiverStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><action name=\"%%NAME%%\" class=\"com.turner.loki.plugins.JmsReceiver\">\n"
			+ "		<initialize>\n" + "			<param name=\"queueManager\" value=\"%%QUEUE_MANAGER%%\" />\n"
			+ "			<param name=\"queue\" value=\"%%QUEUE%%\" />\n"
			+ "			<param name=\"msgObjectType\" value=\"bytes\" />\n" + "		</initialize>\n"
			+ "		<monitor />\n" + "		<success action=\"%%SUCCESS_ACTION%%\" />\n"
			+ "		<success action=\"showMessage\" />\n" + "	</action>";
	private String queueManager;
	private String name;
	private String queue;
	private String successAction;
	
	public String build() {
		String msg =this.jmsReceiverStr;
		
		
		msg=msg.replaceAll("%%NAME%%", this.name);
		msg=msg.replace("%%QUEUE_MANAGER%%", this.queueManager);
		msg=msg.replace("%%QUEUE%%", this.queue);
		msg=msg.replace("%%SUCCESS_ACTION%%", this.successAction);
		return msg;
	}




	public ReceiverBuilder setQueueManager(String queueManager) {
		this.queueManager = queueManager;
		return this;
	}

	public ReceiverBuilder setName(String name) {
		this.name = name;
		return this;
	}

	public ReceiverBuilder setQueue(String queue) {
		this.queue = queue;
		return this;
	}


	public ReceiverBuilder setSuccessAction(String successAction) {
		this.successAction = successAction;
		return this;
	}



}
