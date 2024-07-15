/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.xmla;

import inetsoft.uql.*;
import inetsoft.uql.table.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.xml.XMLPParser;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Vector;

/**
 * This class unions different tables.
 *
 * @version 10.1, 3/18/2009
 * @author InetSoft Technology Corp
 */
public class UnionTableNode extends XTableNode {
   /**
    * Append a table.
    * @param root the xml element could be parsed to a table.
    */
   public void appendTable(XMLPParser parser) throws Exception{
      parseXML(parser);
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      row++;

      return swappable.moreRows(row);
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return swappable.getColCount();
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      return (String) swappable.getObject(0, col);
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      return swappable.getColType(col);
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public Object getObject(int col) {
      return swappable.getObject(row, col);
   }

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      return null;
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public boolean rewind() {
      row = -1;

      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }

   /**
    * Complete this table. The method MUST be called after all rows added.
    */
   public final void complete() {
      swappable.complete();
   }

   /**
    * Get the table column creator.
    * @param col the specified column index.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getColumnCreator(int col) {
      return creators[col];
   }

   /**
    * Get the table column creators.
    * @return the table column creators.
    */
   @Override
   public XTableColumnCreator[] getColumnCreators() {
      return creators;
   }

   /**
    * Get coresponding type in XShema of olap data type.
    */
   private String getSchemaType(String type) {
      if(type == null) {
         return Tool.STRING;
      }

      type = type.substring(4);

      if(type.equals("int")) {
         return Tool.INTEGER;
      }
      else if(type.equals("dateTime")) {
         return Tool.TIME_INSTANT;
      }

      return type;
   }

   /**
    * Get the table column creator.
    * @param type the specified sql type.
    * @param tname the specified sql type name.
    * @return the table column creator.
    */
   private XTableColumnCreator getXTableColumnCreator(String type) {
      if(type == null) {
         return XStringColumn.getCreator();
      }

      type = type.substring(4);

      if(type.equals("int")) {
         return XIntegerColumn.getCreator();
      }
      else if(type.equals("dateTime")) {
         return XTimestampColumn.getCreator();
      }
      else if(type.equals("boolean")) {
         return XBooleanColumn.getCreator();
      }
      else if(type.equals("float") || type.equals("double")) {
         return XDoubleColumn.getCreator();
      }
      else if(type.equals("short")) {
         return XShortColumn.getCreator();
      }
      else if(type.equals("long")) {
         return XLongColumn.getCreator();
      }
      else if(type.equals("date")) {
         return XDateColumn.getCreator();
      }
      else if(type.equals("time")) {
         return XTimeColumn.getCreator();
      }
      else if(type.equals("dateTime")) {
         return XTimestampColumn.getCreator();
      }
      else if(type.equals("string")) {
         return XStringColumn.getCreator();
      }

      return XObjectColumn.getCreator();
   }

   /**
    * Parse xml.
    */
   private void parseXML(XMLPParser parser) throws Exception {
      int state = XMLPParser.END_DOCUMENT;
      UnionHandler handler = new UnionHandler();
      handler.setXMLPParser(parser);

      while((state = parser.next()) != XMLPParser.END_DOCUMENT) {
         switch(state) {
         case XMLPParser.START_TAG:
            handler.startElement();
            break;
         case XMLPParser.TEXT:
            handler.characters();
            break;
         case XMLPParser.END_TAG:
            handler.endElement();
            break;
         }
      }
   }

   /**
    * Union handler.
    */
   private class UnionHandler extends SAXHandler {
      @Override
      public void startElement() throws SAXException {
         super.startElement();

         if("xsd:element".equals(lastTag)) {
            if(swappable.getColCount() > 0) {
               return;
            }

            String fields = pparser.getAttributeValue("sql:field");
            String name = pparser.getAttributeValue("name");
            String type = pparser.getAttributeValue("type");

            if(fields == null || name == null) {
               return;
            }

            int start = fields.lastIndexOf("[");
            int end = fields.lastIndexOf("]");
            fields = start < 0 || end < 0 ?
               fields : fields.substring(start + 1, end);

            colHeaders.add(fields);
            colType.put(name, getSchemaType(type));
            colIndex.put(name, Integer.valueOf(colHeaders.size() - 1));
            colCreators.add(getXTableColumnCreator(type));
         }

         if("row".equals(lastTag)) {
            if(XCube.MONDRIAN.equals(getCubeType())) {
               if(firstRow) {
                  firstRow = false;
                  return;
               }
            }

            rows = new Object[colHeaders.size()];
            processingRow = true;
         }
      }

      @Override
      public void endElement() {
         if("xsd:schema".equals(pparser.getName())) {
            if(swappable.getColCount() == 0 && colHeaders.size() > 0) {
               creators = new XTableColumnCreator[colCreators.size()];
               colCreators.toArray(creators);
               swappable.init(creators);
               swappable.addRow(colHeaders.toArray());
            }
         }

         if("row".equals(pparser.getName())) {
            if(rows != null) {
               swappable.addRow(rows);
            }

            processingRow = false;
         }
      }

      @Override
      public void characters() {
         super.characters();
         String txt = getText();

         if(txt == null || txt.trim().length() <= 0 || txt.equals("\n")) {
            return;
         }

         if(!processingRow) {
            return;
         }

         if(colIndex.containsKey(lastTag)) {
            int col = ((Integer) colIndex.get(lastTag)).intValue();
            rows[col] = Tool.getData((String) colType.get(lastTag), txt);
         }
         else {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "viewer.viewsheet.unionErr"));
         }
      }

      private Object[] rows;
      private boolean processingRow;
      private boolean firstRow = true;
   }

   private XSwappableTable swappable = new XSwappableTable();
   private Vector colHeaders = new Vector();
   private Vector colCreators = new Vector();
   private HashMap colType = new HashMap();
   private HashMap colIndex = new HashMap();
   private int row;
   private XTableColumnCreator[] creators;
}