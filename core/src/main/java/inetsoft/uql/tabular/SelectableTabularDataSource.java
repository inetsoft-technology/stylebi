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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Base class for tabular data source implementations that support customization
 * of the columns returned in the source data.
 *
 * @since 12.2
 */
public abstract class SelectableTabularDataSource<SELF extends SelectableTabularDataSource<SELF>>
   extends TabularDataSource<SELF>
{
   /**
    * Creates a new instance of <tt>SelectableTabularDataSource</tt>.
    *
    * @param type the data source type.
    */
   public SelectableTabularDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   /**
    * Loads the column definitions for the currently configured properties of
    * this data source. If the current configuration is insufficient to load the
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
         NodeList nodes = Tool.getChildNodesByTagName(tag, "column");
         columns = new ColumnDefinition[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            columns[i] = new ColumnDefinition();
            columns[i].parseXML((Element) nodes.item(i));
         }
      }
   }

   private ColumnDefinition[] columns;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectableTabularDataSource.class);
}
