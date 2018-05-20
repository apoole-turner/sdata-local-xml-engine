package com.turner.sdata.helpers;

public class ToBytesBuilder {

	String template = "<action name=\"%%NAME%%\" class=\"com.turner.sdata.MessageToBytes\">\n"
			+ "		<success action=\"%%SUCCESS_ACTION%%\"></success>\n" + "	</action>";
	private String name;
	private String successAction;
	
	public String build() {
		String msg=template;
		msg=msg.replaceAll("%%NAME%%", this.name);
		msg=msg.replace("%%SUCCESS_ACTION%%", this.successAction);
		return msg;
	}
	public void setName(String name) {
		this.name = name;
	}

	public void setSuccessAction(String successAction) {
		this.successAction = successAction;
	}
}
