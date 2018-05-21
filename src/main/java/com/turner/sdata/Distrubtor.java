package com.turner.sdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.turner.sdata.helpers.DistributorBuilder;
import com.turner.sdata.helpers.ReceiverBuilder;
import com.turner.sdata.helpers.ToBytesBuilder;
import com.turner.sdata.helpers.ToObjectBuilder;

public class Distrubtor {

	private String workflowFile;
	private String propertyName;
	
	public static void main(String[] args) {
		Distrubtor d = new Distrubtor();
		d.workflowFile = "workflow2.xml";
		d.propertyName ="Test";
		d.execute();
		
		
	}

	public void execute() {

		try {
			ImportXiIncludeFiles include = new ImportXiIncludeFiles();
			include.createCombinedWorkflowFile(this.workflowFile);
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

			DocumentBuilder db = dbf.newDocumentBuilder();
			InputStream stream = ImportXiIncludeFiles.class.getClassLoader().getResourceAsStream(this.workflowFile);
			Document doc = db.parse(stream);
			List<ActionRoot> actionRoots=getActionRoots(doc);
			Map<String, ActionRoot> actionRootsWithDist = getActionsWithDist(actionRoots);
			addSuccessFailNodes(actionRoots);
			List<ActionChain> successFailActionsNotReplaced=findSuccessFailDist(actionRootsWithDist,actionRoots);//Do not add ones who have a third level
			createDefaultJMSActions(doc);
			createCustomJMSActions(doc,actionRoots);
			changeNameAndCreatenewSubSuccess(successFailActionsNotReplaced,actionRootsWithDist);
			System.out.println(getStringOfDocument(doc));
		} catch (Exception e) {

			e.printStackTrace();
		}

	}
	private void createDefaultJMSActions(Document doc) throws SAXException, IOException, ParserConfigurationException, TransformerException {
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		DistributorBuilder dbr=new DistributorBuilder();
		dbr.setName(this.createJMSDistName(null));
		dbr.setQueue(this.createQueueName(null));
		dbr.setQueueManager(this.createQueueManagerName(null));
		addDocToDoc(db, doc, dbr.build());
		
		ReceiverBuilder rbr=new ReceiverBuilder();
		rbr.setName(this.createJMSReceiverName(null));
		rbr.setQueue(this.createQueueName(null));
		rbr.setQueueManager(this.createQueueManagerName(null));
		rbr.setSuccessAction(this.createMessageToObjectName(null));
		addDocToDoc(db, doc, rbr.build());
		
		ToBytesBuilder tbb=new ToBytesBuilder();
		tbb.setName(this.createMessageToBytesName(null));
		tbb.setSuccessAction(this.createJMSDistName(null));
		addDocToDoc(db, doc, tbb.build());
		
		ToObjectBuilder tob=new ToObjectBuilder();
		tob.setName(this.createMessageToObjectName(null));
		addDocToDoc(db, doc, tob.build());
	}

	private List<ActionRoot> getActionRoots(Document doc){
		NodeList actionNodes = doc.getElementsByTagName("action");
		List<ActionRoot> actions = new ArrayList<>();
		
		for (int i = 0; i < actionNodes.getLength(); i++) {
			Element action = (Element) actionNodes.item(i);
			String name = action.getAttribute("name");
			String clazz = action.getAttribute("class");
			boolean ownQueue=false;
			boolean hasDist=false;
			NodeList distList=action.getElementsByTagName("dist");
			if(distList.getLength()>0) {
				hasDist=true;
				Element dist=(Element)distList.item(0);
				String ownQueueStr=dist.getAttribute("ownQueue");
				if(ownQueueStr!=null && ownQueueStr.equalsIgnoreCase("true")) {
					ownQueue=true;
				}
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
	private void createCustomJMSActions(Document doc, List<ActionRoot> actions) throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		for(ActionRoot ac:actions) {
			if(!ac.ownQueue)
				continue;
			
			
			DistributorBuilder dbr=new DistributorBuilder();
			dbr.setName(this.createJMSDistName(ac.getName()));
			dbr.setQueue(this.createQueueName(ac.getName()));
			dbr.setQueueManager(this.createQueueManagerName(ac.getName()));
			addDocToDoc(db, doc, dbr.build());
			
			ReceiverBuilder rbr=new ReceiverBuilder();
			rbr.setName(this.createJMSReceiverName(ac.getName()));
			rbr.setQueue(this.createQueueName(ac.getName()));
			rbr.setQueueManager(this.createQueueManagerName(ac.getName()));
			rbr.setSuccessAction(this.createMessageToObjectName(ac.getName()));
			addDocToDoc(db, doc, rbr.build());
			
			ToBytesBuilder tbb=new ToBytesBuilder();
			tbb.setName(this.createMessageToBytesName(ac.getName()));
			tbb.setSuccessAction(this.createJMSDistName(ac.getName()));
			addDocToDoc(db, doc, tbb.build());
			
			ToObjectBuilder tob=new ToObjectBuilder();
			tob.setName(this.createMessageToObjectName(ac.getName()));
			addDocToDoc(db, doc, tob.build());
			
		};
		
	}

	private <T extends Action>List<ActionChain> findSuccessFailDist(Map<String, ActionRoot> actionsWithDist, List<ActionRoot> actions) {
		List<ActionChain> successFailNodes=new ArrayList<>();
		for (Action root : actions) {
			for (ActionChain ac : root.getChildrenActions()) {
				boolean is3LevelsDeep=false;
				if (ac.getChildrenActions().isEmpty() && actionsWithDist.get(ac.getName()) != null) {
					successFailNodes.add(ac);
				}
				
			}
		}
		return successFailNodes;
	}

	private void changeNameAndCreatenewSubSuccess(List<ActionChain> actions,Map<String, ActionRoot> actionRootsWithDist) {
		for (ActionChain ac : actions) {
			//System.out.println(root.getName() + ":" + root.getChildrenActions().size());
			String actionhasQueue=null;
			if (actionRootsWithDist.get(ac.getName()).ownQueue) {
				actionhasQueue=ac.getName();
			}
			Element el = ac.getElement();
			el.setAttribute("action", this.createMessageToBytesName(actionhasQueue));
			Element param=el.getOwnerDocument().createElement("param");
			param.setAttribute("name", "nextAction");
			param.setAttribute("value", ac.getName());
			el.appendChild(param);
				

			
		}
	}
	
	private Map<String, ActionRoot> getActionsWithDist(List<ActionRoot> actions) {
		Map<String, ActionRoot> actionsWithDist = new HashMap<>();
		for (ActionRoot root : actions) {
			if(root.hasDist)
				actionsWithDist.put(root.getName(), root);
		}
		return actionsWithDist;
	}

	private List<ActionChain> addSuccessFailNodes(List<ActionRoot> actions) {
		List<ActionChain> acList=new ArrayList<>();
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

	private ActionChain commonChain(Element el, Action parentChain, List<ActionRoot> actionRootList) {
		ActionChain childAc = new ActionChain(parentChain);
		String name = el.getAttribute("action");

		childAc.setName(name);
		childAc.setElement(el);
		childAc.setFailNode(el.getTagName().equalsIgnoreCase("failure"));
		
		return childAc;
	}

	private void checkSuccessActionsHelper(ActionChain ac, List<ActionRoot> actionRootList) {
		List<Element> list = getShallowSuccessFail(ac.getElement());
		for (Element el : list) {
			ActionChain childAc = commonChain(el, ac, actionRootList);
			checkSuccessActionsHelper(childAc, actionRootList);
		}
	}

	
	private List<Element> getShallowSuccessFail(Element el) {
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

	
	private String getStringOfDocument(Node doc) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(doc);
		transformer.transform(source, result);

		String xmlString = result.getWriter().toString();
		return xmlString;
	}
	public String getWorkflowFile() {
		return workflowFile;
	}

	public void setWorkflowFile(String workflowFile) {
		this.workflowFile = workflowFile;
	}
	private String createJMSDistName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_jmsDist";
		}else {
			return this.propertyName + "_" + actionName + "_jmsDist";
		}
	}
	private void addDocToDoc(DocumentBuilder db,Document doc,String xmlStr) throws SAXException, IOException {
		Document subDocRec=db.parse(new InputSource(new StringReader(xmlStr)));
		Node node=doc.importNode(subDocRec.getFirstChild(), true);
		doc.getFirstChild().appendChild(node);
	}
	private String createJMSReceiverName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_jmsReceiver";
		}else {
			return this.propertyName + "_" + actionName + "_jmsReceiver";
		}
	}
	private String createQueueName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_Distrubutor";
		}else {
			return this.propertyName + "_" + actionName + "_Distrubutor";
		}
	}
	private String createQueueManagerName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_Distrubutor";
		}else {
			return this.propertyName + "_" + actionName + "_Distrubutor";
		}
	}
	
	private String createMessageToBytesName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_MessageBytesPluginToDistributor";
		}else {
			return this.propertyName + "_" + actionName + "_MessageBytesPluginToDistributor";
		}
	}
	private String createMessageToObjectName(String actionName) {
		if(actionName==null) {
			return this.propertyName +"_MessageBytesPluginFromReceiver";
		}else {
			return this.propertyName + "_" + actionName + "_BytesToObjectPluginFromReceiver";
		}
	}
	private static abstract class Action {
		private List<ActionChain> childrenActions;
		private String name;
		private Element element;

		public Action() {
			childrenActions = new ArrayList<>();
		}

		public void addChild(ActionChain ac) {
			childrenActions.add(ac);
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
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
		private boolean ownQueue;
		private boolean hasDist;
		public String getClazz() {
			return clazz;
		}

		public void setClazz(String clazz) {
			this.clazz = clazz;
		}

		public boolean isOwnQueue() {
			return ownQueue;
		}

		public void setOwnQueue(boolean ownQueue) {
			this.ownQueue = ownQueue;
		}

		@Override
		public String getAuditTrail() {
			return this.getName();
		}

		public boolean isHasDist() {
			return hasDist;
		}

		public void setHasDist(boolean hasDist) {
			this.hasDist = hasDist;
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

}
