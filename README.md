# Local-Xml-Engine

This is java application is a wrapper for XMLWorkflowEngine we've all come to love. It always you to run your loki project locally without having to run it in docker. It does the variable replacement for you and everything.

## Getting Started
---
Add to your pom.xml inside the dependencies section
```
<dependency>
	<groupId>com.turner.sdata</groupId>
	<artifactId>local-xml-engine</artifactId>
	<version>1.1.0</version>
	<scope>compile</scope>
</dependency>     
```
## Prerequisites
---
* Maven 3.x.x
* A Loki Project
* Have in the classpath your env.properties and workflow.xml

## Example
By default loki will look for your xml that contains your loki workflow as "workflow.xml." You can specify it to look for another name if you want. 
Also by default loki doesn't require you have an environment properties file. This is because the environment you could be executing in could already have all the environment variables set. No  need to have repeats.
> ** Make sure that all of these files are compiled into your classpath. LocalXMlengine will look for it there. So if you have if you workflow.xml is in ${project.basedir}/target/cfg/workflow.xml then you need to set your workflow file location as 	LocalXMLEngine.setWorkflowFilename("cfg/workflow.xml");

```
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
```

### EnviromentRules Interface
From the example above you'll notice a method called LocalXMLEngine.setRules(...). This accepts an implementation of the EnviromentRules interface. It has to methods to it excludeList and customFileTransformation. The excludeList works as expected. It will exclude **ERB FILES** that you don't want transformed (No need to specify erb. or .erb) in the name. The customFileTransformation will rename a **ERB FILE** to another name after it does its variable replacement.(No need to specify erb. or .erb)

```
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
```

## Generating Environment Properties
See [Sdata Resource Plugins](https://github.com/apoole-turner/sdata-resource-plugin) on generating all the environment variables in your project

## Authors
---
* **Austin Poole** - *Initial work* 

See also the list of [contributors](https://github.com/apoole-turner/sdata-resource-plugin/graphs/contributors) who participated in this project.
