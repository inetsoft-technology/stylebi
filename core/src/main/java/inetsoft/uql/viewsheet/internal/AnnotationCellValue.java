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
package inetsoft.uql.viewsheet.internal;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * Annotation cell values.
 */
public abstract class AnnotationCellValue implements XMLSerializable {
   /**
    * Create specified type annotation value obj.
    */
   public static AnnotationCellValue create(int type) {
      if(type == EMBEDDED_TABLE) {
         return new EmbeddedCellValue();
      }
      else if(type == NORMAL_TABLE) {
         return new TableCellValue();
      }
      else if(type == CROSS_TABLE) {
         return new CrosstabCellValue();
      }
      else if(type == CALC_TABLE) {
         return new CalcTableCellValue();
      }
      else if(type == CHART) {
         return new ChartDataValue();
      }

      return null;
   }

   /**
    * Get annotation values.
    */
   public String[] getValues() {
      return values;
   }

   /**
    * Set annotation values.
    */
   public void setValues(String[] values) {
      this.values = values;
   }

   /**
    * The type of annotation cell.
    */
   public abstract int getType();

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<AnnotationCellValue>");
      writeContents(writer);
      writer.print("</AnnotationCellValue>");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(values != null && values.length > 0) {
         writer.print("<values strictNull=\"true\">");

         for(int i = 0; i < values.length; i++) {
            writer.print("<value>");
            writer.print("<![CDATA[" + Tool.getPersistentDataString(values[i]) + "]]>");
            writer.print("</value>");
         }

         writer.print("</values>");
      }
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseContents(elem);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element vnode = Tool.getChildNodeByTagName(elem, "values");

      if(vnode != null) {
         boolean strictNull = "true".equals(Tool.getAttribute(vnode, "strictNull"));
         NodeList vlist = Tool.getChildNodesByTagName(vnode, "value");

         if(vlist != null && vlist.getLength() > 0) {
            values = new String[vlist.getLength()];

            for(int i = 0; i < vlist.getLength(); i++) {
               values[i] = Tool.getValue(vlist.item(i));

               if(strictNull) {
                  values[i] = (String) Tool.getPersistentData(Tool.STRING, values[i]);
                  values[i] = values[i] == null ? "" : values[i];
                  continue;
               }

               if(values[i] == null) {
                  values[i] = "";
               }
               else if("null".equals(values[i])) {
                  values[i] = null;
               }
            }
         }
      }
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof AnnotationCellValue)) {
         return false;
      }

      AnnotationCellValue value = (AnnotationCellValue) obj;

      return Tool.equals(values, value.values);
   }

   protected static final int EMBEDDED_TABLE = 0;
   protected static final int NORMAL_TABLE = 1;
   protected static final int CROSS_TABLE = 2;
   protected static final int CALC_TABLE = 3;
   protected static final int CHART = 4;
   private String[] values;
}
