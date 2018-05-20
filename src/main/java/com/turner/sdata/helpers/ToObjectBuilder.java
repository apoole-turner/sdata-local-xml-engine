package com.turner.sdata.helpers;

public class ToObjectBuilder {

	String template = "<action name=\"%%NAME%%\" class=\"com.turner.sdata.MessageToObject\">\n"
			+ "		<success action=\"stopFlow\"></success>\n" + "	</action>";
	private String name;

	public String build() {
		String msg = template;
		msg=msg.replaceAll("%%NAME%%", this.name);
		return msg;
	}

	public void setName(String name) {
		this.name = name;
	}


}
