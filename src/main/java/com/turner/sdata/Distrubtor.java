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

public class Distrubtor {

	private String workflowFile;
	private String propertyName;
	private String jmsDistrubutorStr="<?xml version=\"1.0\" encoding=\"UTF-8\"?><action name=\"%%NAME%%\" class=\"com.turner.loki.plugins.JmsDistributor\">\n" + 
			//"		threaded=\"true\" threadPoolName=\"gpmsDistributor\" poolMax=\"3\"\n" + 
			//"		poolMaxIdle=\"8\" poolMaxWait=\"15000\">\n" + 
			"		<initialize>\n" + 
			"			<param name=\"queueManager\" value=\"%%QUEUE_MANAGER%%\" />\n" + 
			"			<param name=\"queue\" value=\"%%QUEUE%%\" />\n" + 
			"			<param name=\"msgObjectType\" value=\"bytes\" />\n" + 
			"		</initialize>\n" + 
			"		<monitor />\n" + 
			"		<success action=\"stopFlow\" />\n" + 
			"		<success action=\"showMessage\" />\n" + 
			"	</action>";
	private String jmsReceiverStr="<?xml version=\"1.0\" encoding=\"UTF-8\"?><action name=\"%%NAME%%\" class=\"com.turner.loki.plugins.JmsReceiver\">\n" + 
			"		<initialize>\n" + 
			"			<param name=\"queueManager\" value=\"%%QUEUE_MANAGER%%\" />\n" + 
			"			<param name=\"queue\" value=\"%%QUEUE%%\" />\n" + 
			"			<param name=\"msgObjectType\" value=\"bytes\" />\n" + 
			"		</initialize>\n" + 
			"		<monitor />\n" + 
			"		<success action=\"stopFlow\" />\n" + 
			"		<success action=\"showMessage\" />\n" + 
			"	</action>";
	
	public static void main(String[] args) {
		Distrubtor d = new Distrubtor();
		d.workflowFile = "workflow.xml";
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
			NodeList actionNodes = doc.getElementsByTagName("action");
			List<ActionRoot> actions = new ArrayList<>();
			Map<String, Action> actionsWithDist = null;
			for (int i = 0; i < actionNodes.getLength(); i++) {
				Element action = (Element) actionNodes.item(i);
				String name = action.getAttribute("name");
				String clazz = action.getAttribute("class");
				ActionRoot ac = new ActionRoot();
				ac.setName(name);
				ac.setClazz(clazz);
				ac.setElement(action);
				actions.add(ac);
			}
			actionsWithDist = getActionsWithDist(actions);
			addSuccessFailNodes(actions);
			List<Action> listDistSuccessActions=findSuccessFailDist(actionsWithDist,actions);//Do not add ones who have a third level
			listDistSuccessActions.forEach(System.out::println);
			createJMSActions(doc,actionsWithDist);
			changeNameAndCreatenewSubSuccess(actionsWithDist, actions);
			System.out.println(getStringOfDocument(doc));
		} catch (Exception e) {

			e.printStackTrace();
		}

	}
	
	private void createJMSActions(Document doc, Map<String, Action> actionsWithDist) throws ParserConfigurationException, SAXException, IOException, TransformerException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		for(Entry<String, Action> entry:actionsWithDist.entrySet()) {
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			String jmsDist=jmsDistrubutorStr.replace("%%NAME%%", this.propertyName + "_" + entry.getKey() + "jmsDist");
			jmsDist=jmsDist.replaceAll("%%QUEUE_MANAGER%%", this.propertyName + "_" + entry.getKey() +"_Distrubutor");
			jmsDist=jmsDist.replaceAll("%%QUEUE%%", this.propertyName + "_" + entry.getKey() +"_Distrubutor");
			Document subDocDist=db.parse(new InputSource(new StringReader(jmsDist)));
			
			String jmsRec=jmsReceiverStr.replace("%%NAME%%", this.propertyName + "_" + entry.getKey() + "jmsReceiver");
			jmsRec=jmsRec.replaceAll("%%QUEUE_MANAGER%%", this.propertyName + "_" + entry.getKey() +"_Distrubutor");
			jmsRec=jmsRec.replaceAll("%%QUEUE%%", this.propertyName + "_" + entry.getKey() +"_Distrubutor");
			Document subDocRec=db.parse(new InputSource(new StringReader(jmsRec)));
			
			Node nodeDist=doc.importNode(subDocDist.getFirstChild(), true);
			
			Node nodeRec=doc.importNode(subDocRec.getFirstChild(), true);
			
			doc.getFirstChild().appendChild(nodeDist);
			doc.getFirstChild().appendChild(nodeRec);
			
		};
		
	}

	private <T extends Action>List<Action> findSuccessFailDist(Map<String, Action> actionsWithDist, List<T> actions) {
		List<Action> successFailNodes=new ArrayList<>();
		for (Action root : actions) {
			for (ActionChain ac : root.getChildrenActions()) {
				if (actionsWithDist.get(ac.getName()) != null) {
					successFailNodes.add(ac);
					if(ac.getChildrenActions().size()>0) {
						successFailNodes.addAll(findSuccessFailDist(actionsWithDist, ac.getChildrenActions()));
					}
				}
			}
		}
		return successFailNodes;
	}

	private <T extends Action>void changeNameAndCreatenewSubSuccess(Map<String, Action> actionsWithDist,List<T> actions) {
		for (Action root : actions) {
			//System.out.println(root.getName() + ":" + root.getChildrenActions().size());
			for (ActionChain ac : root.getChildrenActions()) {
				if (actionsWithDist.get(ac.getName()) != null) {
					Element el = ac.getElement();
					el.setAttribute("action", this.propertyName+"_MessageBytesPrep");
					Element param=el.getOwnerDocument().createElement("param");
					param.setAttribute("name", "to");
					param.setAttribute("value", "BYTES");
					el.appendChild(param);
					

				}
			}
		}
	}
	
	private Map<String, Action> getActionsWithDist(List<ActionRoot> actions) {
		Map<String, Action> actionsWithDist = new HashMap<>();
		for (ActionRoot root : actions) {
			NodeList list = root.getElement().getElementsByTagName("dist");
			if (list.getLength() > 0) {
				Element action = (Element) list.item(0);
				actionsWithDist.put(root.getName(), root);
			}

		}
		return actionsWithDist;
	}

	private void addSuccessFailNodes(List<ActionRoot> actions) {

		for (ActionRoot action : actions) {

			List<Element> nodes = getShallowSuccessFail(action.getElement());

			for (int i = 0; i < nodes.size(); i++) {

				Element el = nodes.get(i);
				ActionChain ac = commonChain(el, action, actions);
				action.addChild(ac);

				checkSuccessActionsHelper(ac, actions);
			}

		}

	}

	private ActionChain commonChain(Element el, Action parentChain, List<ActionRoot> actionRootList) {
		ActionChain childAc = new ActionChain(parentChain);
		String name = el.getAttribute("action");

		childAc.setName(name);
		childAc.setElement(el);
		childAc.setFailNode(el.getTagName().equalsIgnoreCase("failure"));
		if (!hasAction(actionRootList, name)) {
			String prepend = "";
			if (childAc.isFailNode) {
				prepend = "Failue";
			} else
				prepend = "Success";
			String msg = prepend + " Action is missing " + childAc.getAuditTrail();
		}
		return childAc;
	}

	private void checkSuccessActionsHelper(ActionChain ac, List<ActionRoot> actionRootList) {
		List<Element> list = getShallowSuccessFail(ac.getElement());
		for (Element el : list) {
			ActionChain childAc = commonChain(el, ac, actionRootList);
			checkSuccessActionsHelper(childAc, actionRootList);
		}
	}

	private boolean hasAction(List<ActionRoot> actionRootList, String actionName) {
		for (int i = 0; i < actionRootList.size(); i++) {
			if (actionRootList.get(i).getName().equals(actionName)) {
				return true;
			}
		}
		return false;
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

	private String getSuccessActions(List<ActionChain> actionsList, Node node) {
		Element el = (Element) node;
		NodeList successes = el.getElementsByTagName("success");
		for (int i = 0; i < successes.getLength(); i++) {
			Element success = (Element) successes.item(i);
			String name = success.getAttribute("action");

		}
		return null;
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

}
