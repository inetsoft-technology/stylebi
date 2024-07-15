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
package inetsoft.uql.asset.internal;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * WSAssemblyInfo stores basic worksheet assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class WSAssemblyInfo extends AssemblyInfo {
   /**
    * Constructor.
    */
   public WSAssemblyInfo() {
      super();
   }

   /**
    * Get the class.
    * @return the class of the assembly.
    */
   public String getClassName() {
      return cls;
   }

   /**
    * Set the class.
    * @param cls the specified class.
    */
   public void setClassName(String cls) {
      this.cls = cls;
   }

   /**
    * Get the description.
    * @return the description of the assembly.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set the description.
    * @param desc the specified description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the message.
    * @return the message of the assembly.
    */
   public String getMessage() {
      return msg;
   }

   /**
    * Set the message.
    * @param msg the specified message.
    */
   public void setMessage(String msg) {
      this.msg = msg;
   }

   /**
    * Check if the assembly is iconized.
    * @return <tt>true</tt> if iconized, <tt>false</tt> otherwise.
    */
   public boolean isIconized() {
      return iconized;
   }

   /**
    * Set iconized option.
    * @param iconized <tt>true</tt> indicated iconized.
    */
   public void setIconized(boolean iconized) {
      this.iconized = iconized;
   }

   /**
    * Check if the assembly is outer.
    * @return <tt>true</tt> if outer, <tt>false</tt> otherwise.
    */
   public boolean isOuter() {
      return outer;
   }

   /**
    * Set outer option.
    * @param outer <tt>true</tt> indicated outer.
    */
   public void setOuter(boolean outer) {
      this.outer = outer;
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   public boolean isEditable2() {
      return super.isEditable() && !outer;
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isVisible() {
      return super.isVisible() && !outer;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(iconized) {
         writer.print(" iconized=\"" + iconized + "\"");
      }

      if(outer) {
         writer.print(" outer=\"" + outer + "\"");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      this.iconized = "true".equals(Tool.getAttribute(elem, "iconized"));
      this.outer = "true".equals(Tool.getAttribute(elem, "outer"));
   }

   /**
    * Get the class name.
    */
   @Override
   protected String getClassName(boolean compact) {
      String name = getClass().getName();

      if(compact) {
         int idx = name.lastIndexOf(".");
         name = name.substring(idx + 1);
      }

      return name;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      boolean compact = Tool.isCompact();

      if(!compact) {
         writer.print("<class>");
         writer.print("<![CDATA[" + cls + "]]>");
         writer.println("</class>");
      }

      if(!compact && desc != null) {
         writer.print("<description>");
         writer.print("<![CDATA[" + desc + "]]>");
         writer.println("</description>");
      }

      if(!compact && msg != null) {
         writer.print("<message>");
         writer.print("<![CDATA[" + msg + "]]>");
         writer.println("</message>");
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   public Object clone(boolean recursive) {
      return clone();
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      cls = Tool.getChildValueByTagName(elem, "class");
      desc = Tool.getChildValueByTagName(elem, "description");
      msg = Tool.getChildValueByTagName(elem, "message");
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return super.toString() + "[" + getName() + "]";
   }

   private String cls;
   private String desc;
   private String msg;
   private boolean iconized;
   private boolean outer;
}
