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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.report.ReportSheet;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * VSAssemblyLayout stores assembly information for viewsheet layout.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class VSAssemblyLayout implements AssetObject {
   /**
    * Constructor.
    */
   public VSAssemblyLayout() {
      position = new Point(-1, -1);
      size = new Dimension(-1, -1);
   }

   /**
    * Constructor.
    */
   public VSAssemblyLayout(String name, Point position, Dimension size) {
      this.name = name;
      this.position = position;
      this.size = size;
   }

   /**
    * Get name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get position.
    */
   public Point getPosition() {
      return position;
   }

   /**
    * Set position.
    */
   public void setPosition(Point position) {
      this.position = position;
   }

   /**
    * Get size.
    */
   public Dimension getSize() {
      return size;
   }

   /**
    * Set size.
    */
   public void setSize(Dimension size) {
      this.size = size;
   }

   /**
    * Get whether if the assembly is double calendar.
    */
   public Boolean isDoubleCalendar() {
      return doubleCalendar;
   }

   /**
    * Set whether if the assembly is double calendar.
    */
   public void setDoubleCalendar(boolean doubleCalendar) {
      this.doubleCalendar = doubleCalendar;
   }

   public int getTableLayout() {
      return tableLayout;
   }

   public void setTableLayout(int tableLayout) {
      this.tableLayout = tableLayout;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<vsAssemblyLayout class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</vsAssemblyLayout>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      Point position = getPosition();

      if(position.x != -1) {
         writer.print(" x=\"" + position.x + "\"");
      }

      if(position.y != -1) {
         writer.print(" y=\"" + position.y + "\"");
      }

      Dimension size = getSize();

      if(size.width != -1) {
         writer.print(" width=\"" + size.width + "\"");
      }

      if(size.height != -1) {
         writer.print(" height=\"" + size.height + "\"");
      }

      writer.print(" doubleCalendar=\"" + doubleCalendar + "\"");
      writer.print(" tableLayout=\"" + tableLayout + "\"");
   }

   /**
    * Write contents.
    */
   protected void writeContents(PrintWriter writer) {
      writer.print("<name><![CDATA[" + name + "]]></name>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   private void parseAttributes(Element elem) {
      String val = Tool.getAttribute(elem, "x");
      val = val == null ? "-1" : val;
      int x = (int) Double.parseDouble(val);
      val = Tool.getAttribute(elem, "y");
      val = val == null ? "-1" : val;
      int y = (int) Double.parseDouble(val);
      this.position = new Point(x, y);
      val = Tool.getAttribute(elem, "width");
      val = val == null ? "-1" : val;
      int width = (int) Double.parseDouble(val);
      val = Tool.getAttribute(elem, "height");
      val = val == null ? "-1" : val;
      int height = (int) Double.parseDouble(val);
      this.size = new Dimension(width, height);
      this.doubleCalendar =
         "true".equals(Tool.getAttribute(elem, "doubleCalendar"));

      String layoutVal = Tool.getAttribute(elem, "tableLayout");

      if(layoutVal != null) {
         try {
            tableLayout = Integer.parseInt(layoutVal);
         }
         catch(Exception ignore) {
         }
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      this.name = Tool.getChildValueByTagName(elem, "name");
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         VSAssemblyLayout layout = (VSAssemblyLayout) super.clone();
         layout.position = (Point) position.clone();
         layout.size = (Dimension) size.clone();
         layout.tableLayout = this.tableLayout;

         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public String toString() {
      return "VSAssemblyLayout{" +
         "name='" + name + '\'' +
         ", position=" + position +
         ", size=" + size +
         ", doubleCalendar=" + doubleCalendar +
         '}';
   }

   private String name;
   private Point position;
   private Dimension size;
   private int tableLayout = ReportSheet.TABLE_FIT_PAGE;
   private Boolean doubleCalendar = false;
   private static final Logger LOG = LoggerFactory.getLogger(VSAssemblyLayout.class);
}
