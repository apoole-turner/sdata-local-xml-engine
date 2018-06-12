package com.turner.sdata.helpers;

public class JMSConnectionStringBuilder {
	private String connectionStr = "\n%%QUEUE%%/ClassName=org.apache.activemq.command.ActiveMQQueue\n"
			+ "%%QUEUE%%/FactoryName=org.apache.activemq.jndi.JNDIReferenceFactory\n"
			+ "%%QUEUE%%/RefAddr/0/Content=%%QUEUE%%\n" 
			+ "%%QUEUE%%/RefAddr/0/Encoding=String\n"
			+ "%%QUEUE%%/RefAddr/0/Type=physicalName\n";


	private String queue;

	public String build() {
		String msg = this.connectionStr;

		msg = msg.replace("%%QUEUE%%", this.queue);
		return msg;
	}



	public JMSConnectionStringBuilder setQueue(String queue) {
		this.queue = queue;
		return this;
	}

}
