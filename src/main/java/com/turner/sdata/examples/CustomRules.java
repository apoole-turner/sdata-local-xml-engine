package com.turner.sdata.examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.turner.sdata.EnvironmentRules;

public class CustomRules implements EnvironmentRules {

	@Override
	public List<String> excludeList() {
		List<String> list = new ArrayList<>();
		if (System.getenv("CLUSTER_QUARTZ") == null || System.getenv("CLUSTER_QUARTZ").equals("false"))
			list.add("quartz.properties");
		return list;
	}

	@Override
	public Map<String, String> customFileTransformation() {
		Map<String, String> map = new HashMap<>();
		map.put("bindings", ".bindings");
		return map;
	}

}
