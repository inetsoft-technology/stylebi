/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.sync;

import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.sql.Types;

/**
 * Convert jdbc query to a worksheet.
 */
public class QueryToWsConverter implements ConvertTransformer {
   public QueryToWsConverter() {
      super();
   }

   /**
    * Convert the query file to a worksheet file.
    */
   public void process(String queryName, String qfolder, File qfile) {
      Document wsDoc = loadWsTemplateDoc();
      Document queryDoc = UpdateDependencyHandler.getXmlDocByFile(qfile);
      processDocument(queryName, qfolder, queryDoc, wsDoc);
      updateFile(wsDoc, qfile);
   }

   public Document convertToWorksheetDoc(String queryName, String qfolder, Document queryDoc) {
      Document wsDoc = loadWsTemplateDoc();
      processDocument(queryName, qfolder, queryDoc, wsDoc);
      return wsDoc;
   }

   private void processDocument(String queryName, String qfolder, Document queryDoc, Document wsDoc) {
      updateIdentifier(wsDoc, qfolder, queryName);
      Element queryRoot = queryDoc.getDocumentElement();
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(queryRoot, "//query");
      Element queryNode = nodeList.getLength() == 0 ? null : (Element) nodeList.item(0);

      if(queryNode == null) {
         throw new RuntimeException("Invalid query file!");
      }

      updatePrimaryName(wsDoc, queryName);
      updateAssemblyInfo(wsDoc, queryName, queryNode);
      convertVariables(wsDoc.getDocumentElement());
   }

   private void updateIdentifier(Document wsDoc, String qname, String qfolder) {
      String path = Tool.createPathString(qfolder, qname);
      path = Tool.createPathString("1^2^__NULL__^", path);
      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPath xpath = xpathFactory.newXPath();

      try {
         XPathExpression expr = xpath.compile("//processing-instruction('inetsoft-asset')");
         NodeList nodeList = (NodeList) expr.evaluate(wsDoc, XPathConstants.NODESET);
         ProcessingInstruction elem = (ProcessingInstruction) nodeList.item(0);
         String data = elem.getData();
         data = Tool.replace(data, "1^2^__NULL__^queryName", path);
         elem.setData(data);
      }
      catch(XPathExpressionException ignore) {
      }
   }

   private void updatePrimaryName(Document wsDoc, String queryName) {
      Element root = wsDoc.getDocumentElement();
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//worksheet/primary");
      Element primary = null;

      if(list.getLength() != 0) {
         primary = (Element) list.item(0);
      }
      else {
         primary = wsDoc.createElement("name");
         root.appendChild(primary);
      }

      replaceElementCDATANode(primary,  queryName);
   }

   private void updateAssemblyInfo(Document wsDoc, String queryName, Element queryNode) {
      Element root = wsDoc.getDocumentElement();
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//assemblies/oneAssembly/assembly");

      if(list.getLength() == 0) {
         throw new RuntimeException("Worksheet template error.");
      }

      
      Element assembly = (Element) list.item(0);
      Element assemblyInfo = Tool.getChildNodeByTagName(assembly, "assemblyInfo");
      // update name
      updateAssemblyName(assemblyInfo, queryName);
      // append query node
      queryNode = (Element) wsDoc.importNode(queryNode, true);
      assemblyInfo.appendChild(queryNode);
      // append datasource node.
      String dsName = queryNode.getAttribute("datasource");
      updateDatasource(assemblyInfo, queryNode, dsName);
      // update sourceinfo
      updateSourceInfo(assemblyInfo, dsName);
      // add columns
      updateColumnSelection(assemblyInfo, queryNode);
      updateProperties(assemblyInfo, queryNode);
      updateOtherContent(assembly, queryNode);
   }

   protected void updateOtherContent(Element assembly, Element queryNode) {
      updateSqlEditValue(assembly, queryNode);
   }

   private void updateDatasource(Element assemblyInfo, Element queryNode, String dsName) {
      Element dsElem = Tool.getChildNodeByTagName(assemblyInfo, "datasource");

      if(dsElem == null) {
         Element datasource = assemblyInfo.getOwnerDocument().createElement("datasource");
         datasource.setAttribute("name", dsName);
         assemblyInfo.appendChild(datasource);
      }
      else {
         dsElem.setAttribute("name", dsName);
      }

      // update datasource of query_jdbc
      NodeList dsList = UpdateDependencyHandler.getChildNodes(
         assemblyInfo, ".//query/query_jdbc//datasource");

      if(dsList.getLength() != 0) {
         Element elem = (Element) dsList.item(0);
         replaceElementCDATANode(elem, dsName);
      }
   }

   private void updateAssemblyName(Element assemblyInfo, String queryName) {
      Element nameElem = Tool.getChildNodeByTagName(assemblyInfo, "name");

      if(nameElem == null) {
         nameElem = assemblyInfo.getOwnerDocument().createElement("name");
         assemblyInfo.appendChild(nameElem);
      }

      replaceElementCDATANode(nameElem,  queryName);
   }

   private void updateColumnSelection(Element assemblyInfo, Element queryElem) {
      Document targetDoc = assemblyInfo.getOwnerDocument();
      NodeList list = UpdateDependencyHandler.getChildNodes(assemblyInfo, "./normalColumnSelection");
      Element normalCols = list.getLength() == 0 ? null : (Element) list.item(0);

      if(normalCols == null) {
         normalCols = assemblyInfo.getOwnerDocument().createElement("normalColumnSelection");
         assemblyInfo.appendChild(normalCols);
      }

      list = UpdateDependencyHandler.getChildNodes(assemblyInfo, "./crosstabColumnSelection");
      Element crosstabCols = list.getLength() == 0 ? null : (Element) list.item(0);

      if(crosstabCols == null) {
         crosstabCols = assemblyInfo.getOwnerDocument().createElement("crosstabColumnSelection");
         assemblyInfo.appendChild(crosstabCols);
      }

      Element columns = createColumnSelection(targetDoc, queryElem);

      if(columns == null) {
         return;
      }
      
      normalCols.appendChild(columns);
      crosstabCols.appendChild(columns.cloneNode(true));
   }

   protected Element createColumnSelection(Document targetDoc, Element queryElem) {
      Element columns = targetDoc.createElement("columnSelection");
      NodeList list = UpdateDependencyHandler.getChildNodes(queryElem, "//query/query_jdbc/uniform_sql/column");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         Element aliasNode = Tool.getChildNodeByTagName(elem, "alias");
         String alias = null;

         if(aliasNode != null) {
            alias = Tool.getValue(aliasNode);
         }

         if(alias == null) {
            String name = Tool.getValue(elem);
            int idx = name == null ? -1 : name.lastIndexOf(".");

            if(idx != -1) {
               alias = name.substring(idx + 1);

               if(aliasNode != null) {
                  replaceElementCDATANode(aliasNode, alias);
               }
            }
         }

         if(alias == null) {
            alias = Tool.getValue(elem);
         }

         Element typeNode = Tool.getChildNodeByTagName(elem, "type");
         String type = Tool.getValue(typeNode);

         Element descNode = Tool.getChildNodeByTagName(elem, "description");
         String desc = Tool.getValue(descNode);

         if(desc == null) {
            desc = Tool.getValue(elem);
         }

         try {
            Element column = createColumnRef(alias, alias, type, desc);
            columns.appendChild(targetDoc.importNode(column, true));
         }
         catch(Exception ex) {
            LOG.error("Failed to create column when convert query to worksheet", ex);
         }
      }

      return columns;
   }

   protected Element createColumnRef(String attr, String view, String type, String desc)
      throws IOException, SAXException, ParserConfigurationException
   {
      String sqlType = getSqlType(type) + "";

      if(columnRef == null) {
         StringBuilder builder = new StringBuilder();
         builder.append("<dataRef class=\"inetsoft.uql.asset.ColumnRef\" dataType=\"%s\" sqlType=\"%s\">");
         builder.append("    <dataRef class=\"inetsoft.uql.erm.AttributeRef\" attribute=\"%s\" sqlType=\"%s\">");
         builder.append("        <view>");
         builder.append("            <![CDATA[%s]]>");
         builder.append("        </view>");
         builder.append("    </dataRef>");
         builder.append("    <description>");
         builder.append("        <![CDATA[%s]]>");
         builder.append("    </description>");
         builder.append("</dataRef>");
         String xmlString = builder.toString();
         xmlString = String.format(xmlString, type, sqlType, attr, sqlType, view, desc);
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = factory.newDocumentBuilder();
         Document newDocument = docBuilder.parse(new InputSource(new StringReader(xmlString)));
         columnRef = newDocument.getDocumentElement();

         return columnRef;
      }

      columnRef.setAttribute("dataType", type);
      columnRef.setAttribute("sqlType", sqlType);
      Element dataRef = Tool.getChildNodeByTagName(columnRef, "dataRef");
      dataRef.setAttribute("attribute", attr);
      dataRef.setAttribute("sqlType", sqlType);
      Element viewNode = Tool.getChildNodeByTagName(dataRef, "view");
      replaceElementCDATANode(viewNode, view);
      Element descNode = Tool.getChildNodeByTagName(columnRef, "description");
      replaceElementCDATANode(descNode, desc);

      return columnRef;
   }

   private int getSqlType(String type) {
      if(type == null) {
         return Types.VARCHAR;
      }

      switch(type) {
         case XSchema.STRING:
         case XSchema.CHARACTER:
            return Types.VARCHAR;
         case XSchema.INTEGER:
            return Types.INTEGER;
         case XSchema.DOUBLE:
            return Types.DOUBLE;
         case XSchema.FLOAT:
            return Types.FLOAT;
         case XSchema.LONG:
            return Types.BIGINT;
         case XSchema.SHORT:
            return Types.SMALLINT;
         case XSchema.DATE:
            return Types.DATE;
         case XSchema.TIME:
            return Types.TIME;
         case XSchema.TIME_INSTANT:
            return Types.TIMESTAMP;
         case XSchema.BYTE:
         case XSchema.CHAR:
            return Types.CHAR;
         case XSchema.DECIMAL:
            return Types.DECIMAL;
         case XSchema.BOOLEAN:
            return Types.BOOLEAN;
         default:
            return Types.VARCHAR;
      }
   }

   private void updateSourceInfo(Element assemblyInfo, String datasource) {
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(assemblyInfo, "./source/sourceInfo");

      if(nodeList.getLength() != 0) {
         Element sourceInfo = (Element) nodeList.item(0);
         NodeList prefixList = UpdateDependencyHandler.getChildNodes(sourceInfo, "./prefix");

         if(prefixList.getLength() != 0) {
            replaceElementCDATANode(prefixList.item(0), datasource);
         }

         NodeList sourceList = UpdateDependencyHandler.getChildNodes(sourceInfo, "./source");

         if(sourceList.getLength() != 0) {
            Element sourceElem = (Element) sourceList.item(0);
            replaceElementCDATANode(sourceElem, datasource);
         }
      }
   }

   private Document loadWsTemplateDoc() {
      try(InputStream input = getClass().getResourceAsStream(getWsTemplateName())) {
         return Tool.parseXML(input);
      }
      catch(Exception ex) {
         throw new RuntimeException("Failed to load worksheet template", ex);
      }
   }

   public static boolean isIgnoredQuery(File file) {
      int type = getQueryType(file);
      return type != QueryToWsConverter.UNIFORM_SQL_QUERY && type != QueryToWsConverter.TABULAR_QUERY;
   }

   public static int getQueryType(File file) {
      Document doc = UpdateDependencyHandler.getXmlDocByFile(file);
      return getQueryType(doc);
   }

   public static int getQueryType(Document doc) {
      Element root = doc.getDocumentElement();
      NodeList nodeList = UpdateDependencyHandler.getChildNodes(root, "//query/query_jdbc/uniform_sql");

      if(nodeList.getLength() > 0) {
         return UNIFORM_SQL_QUERY;
      }

      nodeList = UpdateDependencyHandler.getChildNodes(root, "//query/query_jdbc/procedure_sql");

      if(nodeList.getLength() > 0) {
         return PROCEDURE_SQL_QUERY;
      }

      nodeList = UpdateDependencyHandler.getChildNodes(root, "//query/*/outputColumns");

      if(nodeList.getLength() > 0) {
         return TABULAR_QUERY;
      }

      return -1;
   }

   protected String getWsTemplateName() {
      return "jdbc_query_ws_template.xml";
   }

   @Override
   public int getStartPixelOffX() {
      return X_START + PIXEL_WIDTH;
   }

   @Override
   public int getStartPixelOffY() {
      return Y_START;
   }

   public static final int JDBC_QUERY = 1;
   public static final int UNIFORM_SQL_QUERY = JDBC_QUERY | 16;
   public static final int PROCEDURE_SQL_QUERY = JDBC_QUERY | 32;
   public static final int TABULAR_QUERY = 2;
   private Element columnRef;
   private static final Logger LOG = LoggerFactory.getLogger(QueryToWsConverter.class);
}
