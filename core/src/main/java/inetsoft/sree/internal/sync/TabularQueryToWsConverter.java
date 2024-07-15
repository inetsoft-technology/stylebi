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
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

public class TabularQueryToWsConverter extends QueryToWsConverter {

   @Override
   protected String getWsTemplateName() {
      return "tabular_query_ws_template.xml";
   }

   @Override
   protected Element createColumnSelection(Document targetDoc, Element queryElem) {
      Element columns = fetchColumnsByColumnsNodes(targetDoc, queryElem);

      if(columns != null) {
         return columns;
      }

      return fetchColumnsByOutputColumnsNodes(targetDoc, queryElem);
   }

   private Element fetchColumnsByColumnsNodes(Document targetDoc, Element queryElem) {
      NodeList list = UpdateDependencyHandler.getChildNodes(
         queryElem, "//query/*/columns/column");

      if(list.getLength() == 0) {
         return null;
      }

      Element columns = targetDoc.createElement("columnSelection");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         Element selectedElem = Tool.getChildNodeByTagName(elem, "alias");
         String selected = selectedElem == null ? null : Tool.getValue(selectedElem);

         if("false".equalsIgnoreCase(selected)) {
            continue;
         }

         Element aliasElem = Tool.getChildNodeByTagName(elem, "alias");
         String alias = aliasElem == null ? null : Tool.getValue(aliasElem);

         if(StringUtils.isEmpty(alias)) {
            aliasElem = Tool.getChildNodeByTagName(elem, "name");
            alias = aliasElem == null ? null : Tool.getValue(aliasElem);
         }

         Element typeElem = Tool.getChildNodeByTagName(elem, "type");
         String type = Tool.getValue(typeElem);

         try {
            Element column = createColumnRef(alias, alias, type, null);
            columns.appendChild(targetDoc.importNode(column, true));
         }
         catch(Exception ex) {
            LOG.error("Failed to create column when convert query to worksheet", ex);
         }
      }

      return columns;
   }

   private Element fetchColumnsByOutputColumnsNodes(Document targetDoc, Element queryElem) {
      NodeList list = UpdateDependencyHandler.getChildNodes(
         queryElem, "//query/*/outputColumns/element");

      if(list.getLength() == 0) {
         return null;
      }

      Element columns = targetDoc.createElement("columnSelection");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String name = elem.getAttribute("name");
         String type = elem.getAttribute("type");

         try {
            Element column = createColumnRef(name, name, type, null);
            columns.appendChild(targetDoc.importNode(column, true));
         }
         catch(Exception ex) {
            LOG.error("Failed to create column when convert query to worksheet", ex);
         }
      }

      return columns;
   }

   @Override
   protected void updateOtherContent(Element root, Element queryNode) {
      // do nothing
   }

   private static final Logger LOG = LoggerFactory.getLogger(TabularQueryToWsConverter.class);
}