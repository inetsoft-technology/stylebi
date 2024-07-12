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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Query bound table assembly, bound to a query.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class QueryBoundTableAssembly extends BoundTableAssembly {
   /**
    * Constructor.
    */
   public QueryBoundTableAssembly() {
      super();

      qselection = new ColumnSelection();
   }

   /**
    * Constructor.
    */
   public QueryBoundTableAssembly(Worksheet ws, String name) {
      super(ws, name);

      qselection = new ColumnSelection();
   }

   /**
    * Get the query column selection.
    * @return the query column selection.
    */
   public ColumnSelection getQueryColumnSelection() {
      return qselection;
   }

   /**
    * Set the query column selection.
    * @param qselection the specified query column selection.
    */
   public void setQueryColumnSeletion(ColumnSelection qselection) {
      this.qselection = qselection;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<queryColumnSelection>");
      qselection.writeXML(writer);
      writer.println("</queryColumnSelection>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element qnode = Tool.getChildNodeByTagName(elem, "queryColumnSelection");
      qnode = Tool.getFirstChildNode(qnode);
      qselection.parseXML(qnode);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         QueryBoundTableAssembly assembly =
            (QueryBoundTableAssembly) super.clone();
         assembly.qselection = (ColumnSelection) qselection.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private ColumnSelection qselection;

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryBoundTableAssembly.class);
}
