package com.turner.sdata;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.w3c.dom.Text;

public class ImportXiIncludeFiles {
	public static void main(String[] args) throws Exception {
		ImportXiIncludeFiles horrid = new ImportXiIncludeFiles();
		horrid.createCombinedWorkflowFile("cfg/workflow.xml");
		
	}
	private DocLink rootLink;
	public void print() {
		this.rootLink.print();
	}
	public void createCombinedWorkflowFile(String classPathWorkflow) {
		this.rootLink = new DocLink();
		this.rootLink.setLink(classPathWorkflow);
		try {
			findXiIncludes(this.rootLink);
			String sb = this.rootLink.createEmbbeddedDoc();
			URL l = ImportXiIncludeFiles.class.getClassLoader().getResource(classPathWorkflow);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(l.getPath())))) {
				writer.write(sb);
				
			}
		} catch (Exception e) {
			throw new RuntimeException("Couldn't create a combined workflowFile",e);
		}

	}

	private void findXiIncludes(DocLink docParent) throws Exception {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		InputStream stream = ImportXiIncludeFiles.class.getClassLoader().getResourceAsStream(docParent.link);
		Document doc = db.parse(stream);
		NodeList nodelist = doc.getElementsByTagName("xi:include");
		docParent.setDocRef(doc);
		for (int i = 0; i < nodelist.getLength(); i++) {
			Element el = (Element) nodelist.item(i);
			String s = el.getAttribute("href");
			System.out.println(s);
			DocLink docLink = new DocLink();

			docLink.setParent(docParent);
			docLink.setLink(s);
			docParent.addChildDoc(docLink);
			findXiIncludes(docLink);
		}

	}

	protected static class DocLink {
		private List<DocLink> childDocLinkList;
		private Document docRef;
		private DocLink parent;
		private String link;

		public void print() {
			Pusher pusher = new Pusher();
			this.print(pusher);
		}

		public String createEmbbeddedDoc() throws Exception {
			List<DocLink> refs = new ArrayList<>();
			this.getFlattenReferences(refs);
			Document root = this.docRef;
			deleteXiIncludes(root);
			if(refs.size()>0)
				refs.remove(0);//remove parent
			for (DocLink dockLink : refs) {
				readFileAndDeleteXiIncludesAndAppend(root, dockLink);
			}
			return getStringOfDocument(root); 
		}

		private void deleteXiIncludes(Document doc) {
			NodeList nodelistOfIncludeTags = doc.getElementsByTagName("xi:include");
			int size = nodelistOfIncludeTags.getLength();
			List<Element> xiIncludeList=new ArrayList<>();
			for (int i = 0; i < size; i++) {
				Element el = (Element) nodelistOfIncludeTags.item(i);
				xiIncludeList.add(el);
			}
			xiIncludeList.forEach((element)->{element.getParentNode().removeChild(element);});
		}

		private Document readFileAndDeleteXiIncludesAndAppend(Document rootDocument, DocLink docLink) throws Exception {
			Document doc = docLink.getDocRef();
			Element rootElement = (Element) doc.getElementsByTagName("workflow").item(0);
			deleteXiIncludes(doc);
			NodeList nodeList = rootElement.getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node el = (Node) rootDocument.importNode(nodeList.item(i), true);
				rootDocument.getDocumentElement().appendChild(el);
			}
			return rootDocument;
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

		private void getFlattenReferences(List<DocLink> refs) {
			if (refs == null)
				refs = new ArrayList<>();
			for (DocLink doc : this.childDocLinkList) {
				refs.add(doc);
				doc.getFlattenReferences(refs);
			}

		}

		private void print(Pusher pusher) {

			String output = "";
			if (pusher.getColumns() > 0)
				output += "|";
			for (int i = 0; i < pusher.getColumns(); i++) {

				output += "-";
			}
			Pusher child = pusher.incrementedChild();
			System.out.println(output + this.link);
			for (DocLink doc : this.childDocLinkList) {
				doc.print(child);
			}
		}

		public DocLink() {
			this.childDocLinkList = new ArrayList<>();
		}

		public void addChildDoc(DocLink childDoc) {
			if (childDoc != null)
				this.childDocLinkList.add(childDoc);
		}

		public DocLink getParent() {
			return parent;
		}

		public void setParent(DocLink parent) {
			this.parent = parent;
		}

		public String getLink() {
			return link;
		}

		public void setLink(String link) {
			this.link = link;
		}

		public Document getDocRef() {
			return docRef;
		}

		public void setDocRef(Document docRef) {
			this.docRef = docRef;
		}

	}

	private static class Pusher {
		private int columns;
		private int rows;

		public Pusher() {
			this.columns = 0;
			this.rows = 0;
		}

		private Pusher(Pusher pusher) {
			this.columns = 1 + pusher.columns;
			this.rows = 1 + pusher.rows;
		}

		public Pusher incrementedChild() {
			Pusher child = new Pusher(this);
			return child;
		}

		public int getColumns() {
			return columns;
		}

		public int getRows() {
			return rows;
		}

	}
}
