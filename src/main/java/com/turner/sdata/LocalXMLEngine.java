package com.turner.sdata;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.turner.loki.XmlWorkflowEngine;

public class LocalXMLEngine {
	private static String workflowFilename;
	private static String threadPropertiesFilename;
	private static String envPropertiesFileName;
	private static List<String> ignoredErbFileNames;
	private static LocalXMLEngine localXMLEngine;

	public static void main(String[] args) throws IOException {
		LocalXMLEngine.start();
	}

	private LocalXMLEngine() {
		
	}

	public static void start() {
		if (LocalXMLEngine.localXMLEngine == null) {
			LocalXMLEngine.localXMLEngine = new LocalXMLEngine();
			if (envPropertiesFileName != null) {
				try {
					Map<String, String> map = localXMLEngine.getEnvProperties();
					localXMLEngine.setEnv(map);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			localXMLEngine.replaceERB();
			
			localXMLEngine.startXmlWorkflowEngine();
		}
	} 

	private void startXmlWorkflowEngine() {
		String workflow = null;
		String threadProperties = null;
		if (workflowFilename != null) {
			workflow = workflowFilename;
		} else {
			workflow = "workflow.xml";
		}
		if (threadPropertiesFilename != null) {
			threadProperties = threadPropertiesFilename;
		} else {
			threadProperties = "loki-threads.properties";
		}
		
		ImportXiIncludeFiles horrid=new ImportXiIncludeFiles();
		horrid.createCombinedWorkflowFile(workflow);
		String[] args = new String[2];
		args[0] = getAbsClassPathFileName(workflow);
		args[1] = getAbsClassPathFileName(threadProperties);
		XmlWorkflowEngine.main(args);
	}

	private String getAbsClassPathFileName(String strPath) {
		return LocalXMLEngine.class.getClassLoader().getResource(strPath).getFile();

	}

	private void replaceERB() {
		Path resourcePath = Paths.get(this.getClass().getClassLoader().getResource("").getFile());

		List<Path> list = erbFiles(resourcePath);
		for (Path path : list) {
			//System.out.println(path);
			String erbFile = path.toAbsolutePath().toString();
			
			String newFile ="";
			
			if(erbFile.endsWith(".erb"))
				newFile=erbFile.substring(0, erbFile.indexOf(".erb"));
			else {
				Path parentFolder=path.getParent();
				newFile=parentFolder.toAbsolutePath()+"/"+path.getFileName().toString().substring(4);
			}
			String[] arr = new String[2];
			arr[0] = erbFile;
			arr[1] = newFile;
			EnvironmentVariableReplace.main(arr);
			;
		}
	}

	private List<Path> erbFiles(Path path) {
		try {
			//Files.walk(path).forEach(System.out::println);;
			Stream<Path> stream = Files.walk(path).sorted(Collections.reverseOrder())
					.filter((pathz) -> pathz.toString().toLowerCase().endsWith(".erb") ||  pathz.getFileName().toString().toLowerCase().startsWith("erb.") );
			if (ignoredErbFileNames != null) {
				for (String ignored : ignoredErbFileNames)
					stream = stream.filter((pathz) -> !pathz.toString().equalsIgnoreCase(ignored));
			}
			return stream.collect(Collectors.toList());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	private void setEnv(Map<String, String> newenv) throws Exception {
		try {

			Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
			Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
			theEnvironmentField.setAccessible(true);
			Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
			env.putAll(newenv);
			Field theCaseInsensitiveEnvironmentField = processEnvironmentClass
					.getDeclaredField("theCaseInsensitiveEnvironment");
			theCaseInsensitiveEnvironmentField.setAccessible(true);
			Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
			cienv.putAll(newenv);
		} catch (NoSuchFieldException e) {
			Class[] classes = Collections.class.getDeclaredClasses();
			Map<String, String> env = System.getenv();
			for (Class cl : classes) {
				if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
					Field field = cl.getDeclaredField("m");
					field.setAccessible(true);
					Object obj = field.get(env);
					Map<String, String> map = (Map<String, String>) obj;
					map.clear();
					map.putAll(newenv);
				}
			}
		}
	}

	private Map<String, String> getEnvProperties() throws FileNotFoundException, IOException {
		Map<String, String> map = new HashMap<>();
		Properties props = new Properties();
		Path path = Paths.get(envPropertiesFileName);
		if (path.isAbsolute())
			props.load(new FileInputStream(new File(envPropertiesFileName)));
		else
			props.load(LocalXMLEngine.class.getClassLoader().getResourceAsStream(envPropertiesFileName));
		for (Object key : props.keySet()) {

			map.put(key.toString(), props.get(key).toString());
		}
		return map;
	}

	public static String getWorkflowFilename() {
		return workflowFilename;
	}

	public static void setWorkflowFilename(String workflowFilename) {
		LocalXMLEngine.workflowFilename = workflowFilename;
	}

	public static String getThreadPropertiesFilename() {
		return threadPropertiesFilename;
	}

	public static void setThreadPropertiesFilename(String threadPropertiesFilename) {
		LocalXMLEngine.threadPropertiesFilename = threadPropertiesFilename;
	}

	public static String getEnvPropertiesFileName() {
		return envPropertiesFileName;
	}

	public static void setEnvPropertiesFileName(String envPropertiesFileName) {
		LocalXMLEngine.envPropertiesFileName = envPropertiesFileName;
	}

	public static List<String> getIgnoredErbFileNames() {
		return ignoredErbFileNames;
	}

	public static void setIgnoredErbFileNames(List<String> ignoredErbFileNames) {
		LocalXMLEngine.ignoredErbFileNames = ignoredErbFileNames;
	}

	

}
