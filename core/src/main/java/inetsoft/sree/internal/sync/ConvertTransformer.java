/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.sync;

import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.IOException;
import java.io.StringReader;

public interface ConvertTransformer extends Transformer {
   default void updateProperties(Element assemblyInfo, Element queryNode) {
      NodeList list = UpdateDependencyHandler.getChildNodes(queryNode, "//query");

      if(list.getLength() == 0) {
         return;
      }

      Element xquery = (Element) list.item(0);
      Node node = xquery.getFirstChild();
      node = node == null ? null : node.getNextSibling();

      if(node == null) {
         return;
      }

      Element query = (Element) node;
      String clazz = query.getAttribute("class");

      // tabular query
      if(!StringUtils.isEmpty(clazz)) {
         xquery.setAttribute("class", clazz);
      }

      String desc = DependencyTool.getString(query, "./description/text()");

      if(!StringUtils.isEmpty(desc)) {
         Element elem = assemblyInfo.getOwnerDocument().createElement("description");
         elem.setTextContent(desc);
         assemblyInfo.appendChild(elem);
      }

      Element maxRowElem = Tool.getChildNodeByTagName(query, "maxrows");
      String maxrows = maxRowElem.getTextContent();

      if(!StringUtils.isEmpty(maxrows) && !"0".equals(maxrows)) {
         assemblyInfo.setAttribute("maxRow", maxrows);
         maxRowElem.setTextContent("0");
      }

      Element timeoutElem = Tool.getChildNodeByTagName(query, "timeout");

      // remove timeout setting since ws table not suppport.
      if(timeoutElem != null) {
         timeoutElem.setTextContent("0");
      }

      list = UpdateDependencyHandler.getChildNodes(queryNode,
         "//query/query_jdbc/uniform_sql");

      if(list.getLength() > 0) {
         Element uniform_sql = (Element) list.item(0);
         Element distinct = Tool.getChildNodeByTagName(uniform_sql, "distinct");

         if(distinct != null) {
            assemblyInfo.setAttribute("distinct", "true");
            uniform_sql.removeChild(distinct);
         }
      }
   }

   default void updateSqlEditValue(Element assembly, Element queryNode) {
      NodeList list = UpdateDependencyHandler.getChildNodes(queryNode,
         "//query/query_jdbc/uniform_sql/sqlstring");

      if(list.getLength() == 0) {
         return;
      }

      Element elem = (Element) list.item(0);
      String parseResult = elem.getAttribute("parseResult");

      if(!(UniformSQL.PARSE_FAILED + "").equals(parseResult)) {
         return;
      }

      Element sqlEdit = Tool.getChildNodeByTagName(assembly, "sqlEdited");

      if(sqlEdit == null) {
         sqlEdit = assembly.getOwnerDocument().createElement("sqlEdited");
         assembly.appendChild(sqlEdit);
      }

      sqlEdit.setAttribute("val", "true");
   }

   default void convertVariables(Element wsRoot) {
      NodeList list = UpdateDependencyHandler.getChildNodes(wsRoot, "//assemblies");

      if(list.getLength() == 0) {
         throw new RuntimeException("Worksheet template error.");
      }

      Element assemblies = (Element) list.item(0);
      list = UpdateDependencyHandler.getChildNodes(wsRoot, "//query/*/variable");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         elem.getParentNode().removeChild(elem);
         String type = elem.getAttribute("type");

         // remove query variable, since ws not support, maybe support in the future
         // if proven necessary.
         if("query".equalsIgnoreCase(type)) {
            elem.removeAttribute("source");
            elem.removeAttribute("query");
            elem.removeAttribute("aggregate");
            elem.removeAttribute("local");
            elem.setTextContent("");
            elem.setAttribute("type", "user");
         }

         Element choiceQuery = Tool.getChildNodeByTagName(elem, "ChoiceQuery");

         if(choiceQuery != null) {
            elem.removeChild(choiceQuery);
         }

         String name = elem.getAttribute("name");
         Element choices = Tool.getChildNodeByTagName(elem, "choices");

         // ws variable only depends on the data block in the current ws.
         if(choices == null && StringUtils.isEmpty(elem.getAttribute("source"))) {
            elem.removeAttribute("source");
         }

         int style = choices != null ? UserVariable.LIST : UserVariable.NONE;
         elem.setAttribute("style", style + "");

         try {
            Element oneAssembly = createVariableAssembly(name, style);
            oneAssembly = (Element) wsRoot.getOwnerDocument().importNode(oneAssembly, true);
            assemblies.appendChild(oneAssembly);
            Element variableAssembly = Tool.getChildNodeByTagName(oneAssembly, "assembly");
            variableAssembly.appendChild(elem);
         }
         catch(Exception ex) {
            LOG.error("Failed to create ws variable assembly for variable from query", ex);
         }
      }
   }

   private Element createVariableAssembly(String assemblyName, int style)
      throws IOException, SAXException, ParserConfigurationException
   {
      int pixelOffX = getProperPixelOffX();
      int pixelOffY = getProperPixelOffY();

      StringBuilder xmlBuilder = new StringBuilder();
      xmlBuilder.append("<oneAssembly>\n")
         .append("<assembly class=\"inetsoft.uql.asset.DefaultVariableAssembly\">\n")
         .append("<assemblyInfo class=\"inetsoft.uql.asset.internal.WSAssemblyInfo\" pixelOffX=\"%d\" pixelOffY=\"%d\" pixelWidth=\"100\" pixelHeight=\"40\" primary=\"false\">\n")
         .append("<name>\n")
         .append("<![CDATA[%s]]>\n")
         .append("</name>\n")
         .append("<class>\n")
         .append("<![CDATA[ inetsoft.uql.asset.DefaultVariableAssembly ]]>\n")
         .append("</class>\n")
         .append("<message>\n")
         .append("<![CDATA[%s]]>\n")
         .append("</message>\n")
         .append("</assemblyInfo>\n")
         .append("<attachedAssembly type=\"0\"> </attachedAssembly>\n")
         .append("</assembly>\n")
         .append("</oneAssembly>");

      String xmlString = xmlBuilder.toString();
      xmlString = String.format(xmlString, pixelOffX, pixelOffY, assemblyName,
         getVariableMessage(assemblyName, style));
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = factory.newDocumentBuilder();
      Document newDocument = docBuilder.parse(new InputSource(new StringReader(xmlString)));
      return newDocument.getDocumentElement();
   }

   private String getVariableMessage(String varName, int style) {
      String str;
      Catalog catalog = Catalog.getCatalog();

      str = switch(style) {
         case UserVariable.NONE -> catalog.getString("Text Input");
         case UserVariable.COMBOBOX -> catalog.getString("Combo Box");
         case UserVariable.LIST -> catalog.getString("List");
         case UserVariable.RADIO_BUTTONS -> catalog.getString("Radio Buttons");
         case UserVariable.CHECKBOXES -> catalog.getString("Checkboxes");
         case UserVariable.DATE_COMBOBOX -> catalog.getString("Date Combo Box");
         default -> throw new RuntimeException("Unsupported style found: " + style);
      };

      return varName + "[" + str + "]";
   }

   default int getStartPixelOffX() {
      return 0;
   }

   default int getStartPixelOffY() {
      return 0;
   }

   default int getProperPixelOffX() {
      return getStartPixelOffX() + ADDED_ASSEMBLY_COUNT * PIXEL_WIDTH + GAP;
   }

   default int getProperPixelOffY() {
      return getStartPixelOffY() + ADDED_ASSEMBLY_COUNT * PIXEL_HEIGHT + GAP;
   }

   int ADDED_ASSEMBLY_COUNT = 1;
   int X_START = 25;
   int Y_START = 30;
   int GAP = 10;
   int PIXEL_WIDTH = 100;
   int PIXEL_HEIGHT = 100;
   Logger LOG = LoggerFactory.getLogger(ConvertTransformer.class);
}
