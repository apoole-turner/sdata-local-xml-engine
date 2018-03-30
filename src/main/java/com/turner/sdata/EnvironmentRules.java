package com.turner.sdata;
import java.util.List;
import java.util.Map;

public interface EnvironmentRules {
	/**
	 * If you want to exclude different files
	 * Ex: if you wanted to exclude template.txt.erb then you'd add template.txt to the list
	 * @return
	 */
	List<String> excludeList();

	/**
	 * If you want to change the name of a file after you transform it you can
	 * specifiy it here
	 * Ex: map.put("bindings", ".bindings");
	 * You do not have to have to add the erb in the file name
	 * @return
	 */
	Map<String, String> customFileTransformation();
}
