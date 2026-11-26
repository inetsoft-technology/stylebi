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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.SourceInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * BoundTableAssemblyInfo stores bound table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class BoundTableAssemblyInfo extends TableAssemblyInfo {
   /**
    * Constructor.
    */
   public BoundTableAssemblyInfo() {
      super();

      source = new SourceInfo();
   }

   /**
    * Get the source info.
    * @return the source info of the bound table assembly.
    */
   public SourceInfo getSourceInfo() {
      return source;
   }

   /**
    * Set the source info.
    * @param source the specified source info.
    */
   public void setSourceInfo(SourceInfo source) {
      this.source = source;
   }

   /**
    * Get the logical model entities whose rows are all included (outer joined) when the table
    * is queried from a logical model.
    *
    * @return the entity names, never null.
    */
   public List<String> getAllRows() {
      return allRows == null ? new ArrayList<>() : new ArrayList<>(allRows);
   }

   /**
    * Set the logical model entities whose rows are all included.
    *
    * @param allRows the entity names, null or empty for none.
    */
   public void setAllRows(List<String> allRows) {
      this.allRows = allRows == null || allRows.isEmpty() ? null : new ArrayList<>(allRows);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<source>");
      source.writeXML(writer);
      writer.println("</source>");

      // part of the xml so that the query cache tells a table with and without it apart
      if(allRows != null) {
         writer.println("<allRows>");

         for(String entity : allRows) {
            writer.print("<entity><![CDATA[" + entity + "]]></entity>");
         }

         writer.println("</allRows>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    * @param isSiteAdminImport flag to force into current organization if site admin.
    */
   @Override
   protected void parseContents(Element elem, boolean isSiteAdminImport) throws Exception {
      super.parseContents(elem, isSiteAdminImport);

      Element snode = Tool.getChildNodeByTagName(elem, "source");
      snode = Tool.getFirstChildNode(snode);
      source.parseXML(snode);

      Element anode = Tool.getChildNodeByTagName(elem, "allRows");
      allRows = null;

      if(anode != null) {
         allRows = new ArrayList<>();
         NodeList nodes = Tool.getChildNodesByTagName(anode, "entity");

         for(int i = 0; i < nodes.getLength(); i++) {
            allRows.add(Tool.getValue(nodes.item(i)));
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         BoundTableAssemblyInfo info = (BoundTableAssemblyInfo) super.clone();
         info.source = (SourceInfo) source.clone();
         info.allRows = allRows == null ? null : new ArrayList<>(allRows);
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private SourceInfo source;
   private List<String> allRows;

   private static final Logger LOG =
      LoggerFactory.getLogger(BoundTableAssemblyInfo.class);
}
