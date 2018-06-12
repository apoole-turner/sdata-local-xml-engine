package com.turner.sdata;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.turner.sdata.helpers.DistributorBuilder;
import com.turner.sdata.helpers.JMSConnectionStringBuilder;
import com.turner.sdata.helpers.ReceiverBuilder;
import com.turner.sdata.helpers.ToBytesBuilder;
import com.turner.sdata.helpers.ToObjectBuilder;
import com.turner.sdata.helpers.Transformers;

class Distrubtor {
	static Logger logger = LoggerFactory.getLogger(Distrubtor.class);
	private static String workflowFile;
	private static String propertyName;
	private static String bindingsFile;
	private static String queueManager;
	public static void main(String[] args) {
		Distrubtor.workflowFile = args[0];
		Distrubtor.propertyName = args[1];
		Distrubtor.bindingsFile = args[2];
		Distrubtor.execute();

	}

	public static void execute() {

		try {
			ImportXiIncludeFiles include = new ImportXiIncludeFiles();
			include.createCombinedWorkflowFile(Distrubtor.workflowFile);
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			setQueueManager();
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputStream stream = ImportXiIncludeFiles.class.getClassLoader().getResourceAsStream(Distrubtor.workflowFile);
			String workfilePath=ImportXiIncludeFiles.class.getClassLoader().getResource(Distrubtor.workflowFile).getPath();
			Document doc = db.parse(stream);
			List<ActionRoot> actionRoots = getActionRoots(doc);
			addSuccessFailNodes(actionRoots);
			Map<String, ActionRoot> actionRootsWithDist = getActionRootsWithDist(actionRoots);
			Set<ActionChain> actionChainsWithDist=getActionChainsWithDist(actionRoots); 
			Set<Transformers> setOfActionsThatAreDist=new HashSet<>();
			
			setOfActionsThatAreDist.addAll(getSetFromActionRootDistMap(actionRootsWithDist));
			setOfActionsThatAreDist.addAll(getSetFromActionChainSet(actionChainsWithDist));

			List<ActionChain> successFailActionstobeReplaced = findSuccessFailDist(setOfActionsThatAreDist, actionRoots);
			
			
			createDefaultJMSActions(doc);
			createCustomJMSActions(doc, setOfActionsThatAreDist);
			List<ActionChain> leftOvers=changeNameAndCreatenewSubSuccessWithRootContext(successFailActionstobeReplaced, actionRootsWithDist);
			changeNameAndCreatenewSubSuccessWithChainContext(leftOvers);

			appendBindings(setOfActionsThatAreDist);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(workfilePath)))) {
				writer.write(getStringOfDocument(doc));
				
			}
		} catch (Exception e) {

			e.printStackTrace();
		}

	}

	

	private static void setQueueManager() throws IOException {
		InputStream bindingsStream = Distrubtor.class.getClassLoader().getResourceAsStream(Distrubtor.bindingsFile);
		Properties bindings=new Properties();
		bindings.load(bindingsStream);
		bindingsStream.close();
		for(Object str:bindings.keySet()) {
			String key=(String)str;
			String value=bindings.getProperty(key);
			if(value.equals("org.apache.activemq.ActiveMQConnectionFactory")) {
				int firstSlash=key.indexOf("/");
				Distrubtor.queueManager=str.toString().substring(0,firstSlash);
			}
		}
		
	}

	private static Set<Transformers> getSetFromActionChainSet(Set<ActionChain> actionChainsWithDist) {
		Set<Transformers> set=new HashSet<>();
		actionChainsWithDist.forEach(s->{
			Transformers t=new Transformers();
			t.setName(s.getName());
			t.setOwnQueue(s.isOwnQueue());
			set.add(t);
		});
		return set;
	}

	private static Set<Transformers> getSetFromActionRootDistMap(Map<String, ActionRoot> actionRootsWithDist) {
		Set<Transformers> set=new HashSet<>();
		actionRootsWithDist.forEach((k,v)->{
			Transformers t=new Transformers();
			t.setName(k);
			t.setOwnQueue(v.isOwnQueue());
			set.add(t);
		});
		return set;
	}

	private static Set<ActionChain> getActionChainsWithDist(List<ActionRoot> actionRoots) {
		Set<ActionChain> actionsWithDist = new HashSet<>();
		for (ActionRoot root : actionRoots) {
			for(ActionChain ac: root.getChildrenActions()) {
				if(ac.isHasDist()) {
					actionsWithDist.add(ac);
				}
			}
		}
		return actionsWithDist;
	}
	
	private static Map<String, ActionRoot> getActionRootsWithDist(List<ActionRoot> actions) {
		Map<String, ActionRoot> actionsWithDist = new HashMap<>();
		for (ActionRoot root : actions) {
			if (root.isHasDist())
				actionsWithDist.put(root.getName(), root);
		}
		return actionsWithDist;
	}
	private static void appendBindings(Set<Transformers> setOfActionsThatAreDist) throws IOException {
		InputStream bindingsStream = Distrubtor.class.getClassLoader().getResourceAsStream(Distrubtor.bindingsFile);
		String pathStr = Distrubtor.class.getClassLoader().getResource(Distrubtor.bindingsFile).getPath();
		Set<String> excludeQueueList = getExcludeListForBindings(bindingsStream);
		BufferedWriter bw = null;
		try {
			// APPEND MODE SET HERE
			bw = new BufferedWriter(new FileWriter(pathStr, true));
			addDefaultsToBindings(excludeQueueList,bw);
			addCustomToBindings(excludeQueueList,bw, setOfActionsThatAreDist);

			bw.newLine();
			bw.flush();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally { // always close the file
			if (bw != null)
				try {
					bw.close();
				} catch (IOException ioe2) {
					// just ignore it
				}
		}
	}

	private static Set<String> getExcludeListForBindings(InputStream bindingsStream) throws IOException {
		Set<String> excludeSet = new HashSet<>();
		Properties props = new Properties();
		props.load(bindingsStream);
		bindingsStream.close();
		for (Object str : props.keySet()) {
			int firstSlash=str.toString().indexOf("/");
			String name=str.toString().substring(0,firstSlash);
			excludeSet.add(name);
		}
		return excludeSet;
	}

	private static void addDefaultsToBindings(Set<String> excludeQueueList, BufferedWriter bw) throws IOException {
		if(excludeQueueList.contains(Distrubtor.createQueueName(null)))
				return;
		JMSConnectionStringBuilder jb = new JMSConnectionStringBuilder();
		jb.setQueue(Distrubtor.createQueueName(null));
		bw.write(jb.build());
	}

	private static void addCustomToBindings(Set<String> excludeQueueList, BufferedWriter bw, Set<Transformers> setOfActionsThatAreDist)
			throws IOException {
		for (Transformers t : setOfActionsThatAreDist) {
			if (t.isOwnQueue()) {
				if(excludeQueueList.contains(Distrubtor.createQueueName(t.getName())))
						return;
				JMSConnectionStringBuilder jb = new JMSConnectionStringBuilder();
				jb.setQueue(Distrubtor.createQueueName(t.getName()));
				bw.write(jb.build());
			}
		}
	}

	private static void createDefaultJMSActions(Document doc)
			throws SAXException, IOException, ParserConfigurationException, TransformerException {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		DistributorBuilder dbr = new DistributorBuilder();
		dbr.setName(Distrubtor.createJMSDistName(null));
		dbr.setQueue(Distrubtor.createQueueName(null));
		dbr.setQueueManager(Distrubtor.createQueueManagerName(null));
		addDocToDoc(db, doc, dbr.build());

		ReceiverBuilder rbr = new ReceiverBuilder();
		rbr.setName(Distrubtor.createJMSReceiverName(null));
		rbr.setQueue(Distrubtor.createQueueName(null));
		rbr.setQueueManager(Distrubtor.createQueueManagerName(null));
		rbr.setSuccessAction(Distrubtor.createMessageToObjectName(null));
		addDocToDoc(db, doc, rbr.build());

		ToBytesBuilder tbb = new ToBytesBuilder();
		tbb.setName(Distrubtor.createMessageToBytesName(null));
		tbb.setSuccessAction(Distrubtor.createJMSDistName(null));
		addDocToDoc(db, doc, tbb.build());

		ToObjectBuilder tob = new ToObjectBuilder();
		tob.setName(Distrubtor.createMessageToObjectName(null));
		addDocToDoc(db, doc, tob.build());
	}

	private static List<ActionRoot> getActionRoots(Document doc) {
		NodeList actionNodes = doc.getElementsByTagName("action");
		List<ActionRoot> actions = new ArrayList<>();

		for (int i = 0; i < actionNodes.getLength(); i++) {
			Element action = (Element) actionNodes.item(i);
			String name = action.getAttribute("name");
			String clazz = action.getAttribute("class");
			boolean ownQueue = false;
			boolean hasDist = false;
			NodeList distList = action.getElementsByTagName("dist");
			if (distList.getLength() > 0) {
				hasDist = true;
				Element dist = (Element) distList.item(0);
				String ownQueueStr = dist.getAttribute("ownQueue");
				if (ownQueueStr != null && ownQueueStr.equalsIgnoreCase("true")) {
					ownQueue = true;
				}
			}
			for(int j=0;j<distList.getLength();j++) {
				action.removeChild(distList.item(j));
			}
			ActionRoot ac = new ActionRoot();
			ac.setName(name);
			ac.setClazz(clazz);
			ac.setElement(action);
			ac.setOwnQueue(ownQueue);
			ac.setHasDist(hasDist);
			actions.add(ac);
		}
		return actions;
	}

	private static void createCustomJMSActions(Document doc, Set<Transformers> setOfActionsThatAreDist)
			throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		for (Transformers ac : setOfActionsThatAreDist) {
			if (!ac.isOwnQueue())
				continue;

			DistributorBuilder dbr = new DistributorBuilder();
			dbr.setName(Distrubtor.createJMSDistName(ac.getName()));
			dbr.setQueue(Distrubtor.createQueueName(ac.getName()));
			dbr.setQueueManager(Distrubtor.createQueueManagerName(ac.getName()));
			addDocToDoc(db, doc, dbr.build());

			ReceiverBuilder rbr = new ReceiverBuilder();
			rbr.setName(Distrubtor.createJMSReceiverName(ac.getName()));
			rbr.setQueue(Distrubtor.createQueueName(ac.getName()));
			rbr.setQueueManager(Distrubtor.createQueueManagerName(ac.getName()));
			rbr.setSuccessAction(Distrubtor.createMessageToObjectName(ac.getName()));
			addDocToDoc(db, doc, rbr.build());

			ToBytesBuilder tbb = new ToBytesBuilder();
			tbb.setName(Distrubtor.createMessageToBytesName(ac.getName()));
			tbb.setSuccessAction(Distrubtor.createJMSDistName(ac.getName()));
			addDocToDoc(db, doc, tbb.build());

			ToObjectBuilder tob = new ToObjectBuilder();
			tob.setName(Distrubtor.createMessageToObjectName(ac.getName()));
			addDocToDoc(db, doc, tob.build());

		}
		;

	}

	private static <T extends Action> List<ActionChain> findSuccessFailDist(Set<Transformers> setOfActionsThatAreDist,
			List<ActionRoot> actions) {
		List<ActionChain> successFailNodes = new ArrayList<>();
		List<String> names=setOfActionsThatAreDist.stream().map((Transformers td)->td.getName()).collect(Collectors.toList());
		for (Action root : actions) {
			for (ActionChain ac : root.getChildrenActions()) {
				if (ac.getChildrenActions().isEmpty() && names.contains(ac.getName())) {
					successFailNodes.add(ac);
				} else {
					logger.warn(
							"There exists a success chain that is 3 levels deep. It will not be added to distribution mode");
				}

			}
		}
		return successFailNodes;
	}

	private static List<ActionChain> changeNameAndCreatenewSubSuccessWithRootContext(List<ActionChain> actions,
			Map<String, ActionRoot> actionRootsWithDist) {
		List<ActionChain> leftOverActions=new ArrayList<>();
		for (ActionChain ac : actions) {
			if(actionRootsWithDist.get(ac.getName())==null) {
				leftOverActions.add(ac);
				
			}else {
				String actionhasQueue = null;
				if (actionRootsWithDist.get(ac.getName()).isOwnQueue()) {
					actionhasQueue = ac.getName();
				}
				Element el = ac.getElement();
				el.setAttribute("action", Distrubtor.createMessageToBytesName(actionhasQueue));
				el.removeAttribute("dist");
				el.removeAttribute("ownQueue");
				Element param = el.getOwnerDocument().createElement("param");
				param.setAttribute("name", "nextActionToBeCalled");
				param.setAttribute("value", ac.getName());
				el.appendChild(param);
			}
		}
		return leftOverActions;
	}
	private static void changeNameAndCreatenewSubSuccessWithChainContext(List<ActionChain> leftOvers) {
		for (ActionChain ac : leftOvers) {
			
				String actionhasQueue = null;
				if (ac.isOwnQueue()) {
					actionhasQueue = ac.getName();
				}
				Element el = ac.getElement();
				el.setAttribute("action", Distrubtor.createMessageToBytesName(actionhasQueue));
				el.removeAttribute("dist");
				el.removeAttribute("ownQueue");
				Element param = el.getOwnerDocument().createElement("param");
				param.setAttribute("name", "nextActionToBeCalled");
				param.setAttribute("value", ac.getName());
				el.appendChild(param);
			
		}
		
	}


	private static List<ActionChain> addSuccessFailNodes(List<ActionRoot> actions) {
		List<ActionChain> acList = new ArrayList<>();
		for (ActionRoot action : actions) {

			List<Element> nodes = getShallowSuccessFail(action.getElement());

			for (int i = 0; i < nodes.size(); i++) {

				Element el = nodes.get(i);
				ActionChain ac = commonChain(el, action, actions);
				action.addChild(ac);

				checkSuccessActionsHelper(ac, actions);
			}

		}
		return acList;

	}

	private static ActionChain commonChain(Element el, Action parentChain, List<ActionRoot> actionRootList) {
		ActionChain childAc = new ActionChain(parentChain);
		String name = el.getAttribute("action");
		String hasOwnQueue=el.getAttribute("ownQueue");
		String hasDist=el.getAttribute("dist");
		if(hasOwnQueue!=null && hasOwnQueue.equalsIgnoreCase("true")) {
			childAc.setOwnQueue(true);	
		}
		if(hasDist!=null && hasDist.equalsIgnoreCase("true")) {
			childAc.setHasDist(true);
		}
		
		childAc.setName(name);
		childAc.setElement(el);
		childAc.setFailNode(el.getTagName().equalsIgnoreCase("failure"));

		return childAc;
	}

	private static void checkSuccessActionsHelper(ActionChain ac, List<ActionRoot> actionRootList) {
		List<Element> list = getShallowSuccessFail(ac.getElement());
		for (Element el : list) {
			ActionChain childAc = commonChain(el, ac, actionRootList);
			ac.addChild(childAc);
			checkSuccessActionsHelper(childAc, actionRootList);
		}
	}

	private static List<Element> getShallowSuccessFail(Element el) {
		NodeList list = el.getChildNodes();
		List<Element> newList = new ArrayList<>();
		for (int i = 0; i < list.getLength(); i++) {
			if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
				Element child = (Element) list.item(i);
				if (child.getTagName().equalsIgnoreCase("success") || child.getTagName().equalsIgnoreCase("failure"))
					newList.add(child);
			}
		}
		return newList;
	}

	private static String getStringOfDocument(Node doc) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(doc);
		transformer.transform(source, result);

		String xmlString = result.getWriter().toString();
		return xmlString;
	}

	public static String getWorkflowFile() {
		return workflowFile;
	}

	public static void setWorkflowFile(String workflowFile) {
		Distrubtor.workflowFile = workflowFile;
	}

	private static String createJMSDistName(String actionName) {
		if (actionName == null) {
			return Distrubtor.propertyName + "_jmsDist";
		} else {
			return Distrubtor.propertyName + "_" + actionName + "_jmsDist";
		}
	}

	private static void addDocToDoc(DocumentBuilder db, Document doc, String xmlStr) throws SAXException, IOException {
		Document subDocRec = db.parse(new InputSource(new StringReader(xmlStr)));
		Node node = doc.importNode(subDocRec.getFirstChild(), true);
		doc.getFirstChild().appendChild(node);
	}

	private static String createJMSReceiverName(String actionName) {
		if (actionName == null) {
			return Distrubtor.propertyName + "_jmsReceiver";
		} else {
			return Distrubtor.propertyName + "_" + actionName + "_jmsReceiver";
		}
	}

	private static String createQueueName(String actionName) {
		if (actionName == null) {
			return Distrubtor.propertyName + "_Distrubutor";
		} else {
			return Distrubtor.propertyName + "_" + actionName + "_Distrubutor";
		}
	}

	private static  String createQueueManagerName(String actionName) {
			return Distrubtor.queueManager;
		
	}

	private static String createMessageToBytesName(String actionName) {
		if (actionName == null) {
			return Distrubtor.propertyName + "_MessageBytesPluginToDistributor";
		} else {
			return Distrubtor.propertyName + "_" + actionName + "_MessageBytesPluginToDistributor";
		}
	}

	private static String createMessageToObjectName(String actionName) {
		if (actionName == null) {
			return Distrubtor.propertyName + "_MessageBytesPluginFromReceiver";
		} else {
			return Distrubtor.propertyName + "_" + actionName + "_BytesToObjectPluginFromReceiver";
		}
	}

	private static abstract class Action {
		private List<ActionChain> childrenActions;
		private String name;
		private Element element;
		private boolean ownQueue;
		private boolean hasDist;

		public Action() {
			childrenActions = new ArrayList<>();
		}

		public void addChild(ActionChain ac) {
			childrenActions.add(ac);
		}
		public boolean isHasDist() {
			return hasDist;
		}

		public void setHasDist(boolean hasDist) {
			this.hasDist = hasDist;
		}
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
		public boolean isOwnQueue() {
			return ownQueue;
		}

		public void setOwnQueue(boolean ownQueue) {
			this.ownQueue = ownQueue;
		}
		public abstract String getAuditTrail();

		public Element getElement() {
			return element;
		}

		public void setElement(Element element) {
			this.element = element;
		}

		public List<ActionChain> getChildrenActions() {
			return childrenActions;
		}

		public void setChildrenActions(List<ActionChain> childrenActions) {
			this.childrenActions = childrenActions;
		}

	}

	private static class ActionRoot extends Action {
		private String clazz;
		

		public String getClazz() {
			return clazz;
		}

		public void setClazz(String clazz) {
			this.clazz = clazz;
		}

		

		@Override
		public String getAuditTrail() {
			return this.getName();
		}

		

	}

	private static class ActionChain extends Action {
		private Action parent;
		private boolean isFailNode;

		public ActionChain() {
			super();

		}

		public ActionChain(Action parent) {
			super();
			if (parent == null)
				throw new RuntimeException("You'd better pass in a parent");
			this.parent = parent;
		}

		public Action getParent() {
			return parent;
		}

		public void setParent(Action parent) {
			this.parent = parent;
		}

		public boolean isFailNode() {
			return isFailNode;
		}

		public void setFailNode(boolean isFailNode) {
			this.isFailNode = isFailNode;
		}

		public String getAuditTrail() {
			if (parent != null)
				return this.parent.getAuditTrail() + ":" + this.getName();
			else
				return this.getName();

		}

	}

	public static String getPropertyName() {
		return propertyName;
	}

	public static void setPropertyName(String propertyName) {
		Distrubtor.propertyName = propertyName;
	}

	public static String getBindingsFile() {
		return bindingsFile;
	}

	public static void setBindingsFile(String bindingsFile) {
		Distrubtor.bindingsFile = bindingsFile;
	}

}
