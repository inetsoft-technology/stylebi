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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.List;

/**
 * BindingInfo contains binding information. The information will be executed
 * to fill the data consumer with data.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class BindingInfo implements AssetObject {
   /**
    * Constructor.
    */
   public BindingInfo() {
      super();
   }

   /**
    * Get the name of the source table.
    * @return the name of the source table.
    */
   public String getTableName() {
      return table;
   }

   /**
    * Set the name of the source table.
    * @param name the specified name of the source table.
    */
   public void setTableName(String name) {
      this.table = name;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<bindingInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</bindingInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      // do nothing
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(table != null) {
         writer.print("<table>");
         writer.print("<![CDATA[" + table + "]]>");
         writer.println("</table>");
      }

      if(type != null) {
         writer.print("<type>");
         writer.print("<![CDATA[" + type + "]]>");
         writer.println("</type>");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      // do nothing
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      table = Tool.getChildValueByTagName(elem, "table");
      String type = Tool.getChildValueByTagName(elem, "type");

      if(!StringUtils.isEmpty(type)) {
         this.type = Integer.parseInt(type);
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public final void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone BindingInfo", ex);
      }

      return null;
   }

   /**
    * Get the dynamic values.
    * @return the dynamic values.
    */
   public abstract List<DynamicValue> getDynamicValues();

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public abstract void renameDepended(String oname, String nname,
                                       Viewsheet vs);

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof BindingInfo)) {
         return false;
      }

      BindingInfo info = (BindingInfo) obj;
      return Tool.equals(table, info.table);
   }

   /**
    * Check if this binding is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return table == null || table.length() == 0;
   }

   public Integer getType() {
      return type;
   }

   public void setType(Integer type) {
      this.type = type;
   }

   private String table;
   private Integer type; // source type

   private static final Logger LOG =
      LoggerFactory.getLogger(BindingInfo.class);
}
