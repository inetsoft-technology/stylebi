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
package inetsoft.report.composition;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.DataSerializable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;

/**
 * Table assembly entry locates an table assembly.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class TableAssemblyEntry implements DataSerializable, AssetObject {
   /**
    * The plain type.
    */
   public static final int PLAIN = 0x1;
   /**
    * The mergejoin type.
    */
   public static final int MERGEJOIN = 0x2;
   /**
    * The crossjoin type.
    */
   public static final int CROSSJOIN = 0x4;
   /**
    * The union type.
    */
   public static final int UNION = 0x8;
   /**
    * The minus type.
    */
   public static final int MINUS = 0x10;
   /**
    * The intersect type.
    */
   public static final int INTERSECT = 0x20;
   /**
    * The mirror type.
    */
   public static final int MIRROR = 0x40;
   /**
    * The rotated type.
    */
   public static final int ROTATED = 0x80;
   /**
    * The embeded type.
    */
   public static final int EMBEDED = 0x100;
   /**
    * The subquery type.
    */
   public static final int SUBQUERY = 0x200;
   /**
    * The join type.
    */
   public static final int JOIN = 0x400;
   /**
    * The unpivot type.
    */
   public static final int UNPIVOT = 0x800;

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableAssemblyEntry)) {
         return false;
      }

      String entryName = ((TableAssemblyEntry) obj).getName();
      ToolTipContainer tipContainer =
         ((TableAssemblyEntry) obj).getTipContainer();
      boolean isaggr = ((TableAssemblyEntry) obj).isAggregateDefined();
      boolean iscond = ((TableAssemblyEntry) obj).isConditionDefined();
      String objPath = ((TableAssemblyEntry) obj).getPath();
      Point objpos = ((TableAssemblyEntry) obj).getPosition();
      int objtype = ((TableAssemblyEntry) obj).getType();

      return Tool.equals(entryName, name) &&
         Tool.equals(tipContainer, container) &&
         isaggr == isAggregate && iscond == isCondition &&
         Tool.equals(objPath, path) && Tool.equals(objpos, pos) &&
         objtype == type;
   }

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   public String getName() {
      return name;
   }

   /**
    * Set a name to the table assembly entry.
    * @param name the name of the property.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the ToolTipContainer.
    * @return the ToolTipContainer of the assembly.
    */
   public ToolTipContainer getTipContainer() {
      return container;
   }

   public void setTipContainer(ToolTipContainer container) {
      this.container = container;
   }

   /**
    * Check if is a aggregate table assembly.
    * @return <tt>true</tt> is a aggregate table assembly,
    * <tt>false</tt> otherwise.
    */
   public boolean isAggregateDefined() {
      return isAggregate;
   }

   public void setAggregate(boolean isAggregate) {
      this.isAggregate = isAggregate;
   }

   /**
    * Check if is a condition table assembly.
    * @return <tt>true</tt> is a condition table assembly,
    * <tt>false</tt> otherwise.
    */
   public boolean isConditionDefined() {
      return isCondition;
   }

   public void setCondition(boolean isCondition) {
      this.isCondition = isCondition;
   }

   /**
    * Check if is a subquery table assembly.
    * @return <tt>true</tt> is a condition table assembly,
    * <tt>false</tt> otherwise.
    */
   public boolean isSubqueryDefined() {
      return isSubquery;
   }

   public void setSubquery(boolean isSubquery) {
      this.isSubquery = isSubquery;
   }

   /**
    * Get the path.
    * @return the path of the assembly.
    */
   public String getPath() {
      return path;
   }

   /**
    * Set a path to the table assembly entry.
    * @param path the path of the property.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Get the position.
    * @return the position of the assembly.
    */
   public Point getPosition() {
      return pos;
   }

   /**
    * Set a position to the table assembly entry.
    * @param pos the position of the property.
    */
   public void setPosition(Point pos) {
      this.pos = pos;
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   public int getType() {
      return type;
   }

   /**
    * Set a type to the table assembly entry.
    * @param type the type of the property.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream output) {
      try{
         output.writeInt(type);
         output.writeBoolean(pos == null);

         if(pos != null) {
            output.writeInt(pos.x);
            output.writeInt(pos.y);
         }

         output.writeBoolean(name == null);

         if(name != null) {
            output.writeUTF(name);
         }

         if(path != null) {
            output.writeUTF(path);
         }

         output.writeBoolean(isAggregate);
         output.writeBoolean(isCondition);
         output.writeBoolean(isSubquery);
         output.writeBoolean(container == null);

         if(container != null) {
            container.writeData(output);
         }
      }
      catch (IOException e) {
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      parseAttributes(elem);
      this.path = Tool.getChildValueByTagName(elem, "path");
      this.name = Tool.getChildValueByTagName(elem, "name");

      if(Tool.getChildNodeByTagName(elem, "ToolTipContainer") != null) {
         this.container = new ToolTipContainer();
         container.parseXML(
            Tool.getChildNodeByTagName(elem, "ToolTipContainer"));
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      this.type = Integer.parseInt(Tool.getAttribute(elem, "type"));

      if(Tool.getAttribute(elem, "x") != null &&
         Tool.getAttribute(elem, "y") != null)
      {
         int x = Integer.parseInt(Tool.getAttribute(elem, "x"));
         int y = Integer.parseInt(Tool.getAttribute(elem, "y"));
         this.pos = new Point(x, y);
      }

      this.isAggregate = "true".equals(Tool.getAttribute(elem, "isAggregate"));
      this.isCondition = "true".equals(Tool.getAttribute(elem, "isCondition"));
      this.isSubquery = "true".equals(Tool.getAttribute(elem, "isSubquery"));
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println(
         "<TableAssemblyEntry class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");

      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.print("</name>");
      }

      if(path != null) {
         writer.print("<path>");
         writer.print("<![CDATA[" + path + "]]>");
         writer.print("</path>");
      }

      if(container != null) {
         container.writeXML(writer);
      }

      writer.println("</TableAssemblyEntry>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      if(pos != null) {
         writer.print(" x=\"" + pos.x + "\"");
         writer.print(" y=\"" + pos.y + "\"");
      }

      writer.print(" type=\"" + type + "\"");
      writer.print(" isAggregate=\"" + isAggregate + "\"");
      writer.print(" isCondition=\"" + isCondition + "\"");
      writer.print(" isSubquery=\"" + isSubquery + "\"");
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         return this;
      }
   }

   private String name;
   private ToolTipContainer container;
   private boolean isAggregate;
   private boolean isCondition;
   private boolean isSubquery;
   private String path;
   private Point pos;
   private int type;
}
