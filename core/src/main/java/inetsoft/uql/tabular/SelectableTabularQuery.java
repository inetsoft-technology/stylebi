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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Base class for tabular query implementations that support customization of
 * the columns returned in the source data.
 *
 * @since 12.2
 */
public abstract class SelectableTabularQuery extends TabularQuery {
   /**
    * Creates a new instance of <tt>SelectableTabularQuery</tt>.
    *
    * @param type the data source type.
    */
   public SelectableTabularQuery(String type) {
      super(type);
   }

   /**
    * Loads the column definitions for the currently configured properties of
    * this query. If the current configuration is insufficient to load the
    * columns, this method should return <tt>null</tt>.
    *
    * @return the column definitions.
    *
    * @throws Exception if an error prevented the columns from being loaded.
    */
   protected abstract ColumnDefinition[] loadColumns() throws Exception;

   /**
    * Gets the column definitions that have been modified from the source data.
    *
    * @return the column definitions.
    */
   public ColumnDefinition[] getColumns() {
      if(columns == null) {
         try {
            columns = loadColumns();
         }
         catch(Exception e) {
            LOG.warn("Failed to load columns", e);
         }
      }

      return columns;
   }

   /**
    * Sets the column definitions that have been modified from the source data.
    *
    * @param columns the column definitions.
    */
   public void setColumns(ColumnDefinition[] columns) {
      this.columns = columns;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(columns != null && columns.length > 0) {
         writer.println("<columns>");

         for(ColumnDefinition column : columns) {
            column.writeXML(writer);
         }

         writer.println("</columns>");
      }
   }

   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element element = Tool.getChildNodeByTagName(tag, "columns");

      if(element != null) {
         NodeList nodes = Tool.getChildNodesByTagName(element, "column");
         columns = new ColumnDefinition[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            columns[i] = new ColumnDefinition();
            columns[i].parseXML((Element) nodes.item(i));
         }
      }
   }

   private ColumnDefinition[] columns;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectableTabularQuery.class);
}
