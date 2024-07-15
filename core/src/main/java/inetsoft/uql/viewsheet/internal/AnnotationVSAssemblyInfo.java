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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.DimensionD;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;

/**
 * AnnotationVSAssemblyInfo stores basic annotation assembly information.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationVSAssemblyInfo extends VSAssemblyInfo {
   /**
    * Viewsheet.
    */
   public static final int VIEWSHEET = 0;
   /**
    * Assembly.
    */
   public static final int ASSEMBLY = 1;
   /**
    * Data point.
    */
   public static final int DATA = 2;

   /**
    * Set the sub line assembly name.
    * @param line the line assembly name.
    */
   public void setLine(String line) {
      this.line = line;
   }

   /**
    * Get the sub line assembly name.
    */
   public String getLine() {
      return line;
   }

   /**
    * Set the sub rectangle assembly name.
    * @param rectangle the rectangle assembly name.
    */
   public void setRectangle(String rectangle) {
      this.rectangle = rectangle;
   }

   /**
    * Get the sub rectangle assembly name.
    */
   public String getRectangle() {
      return rectangle;
   }

   /**
    * Set the annotation type.
    * @param type the annotation type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the annotation type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set data row index.
    * @param row the data row index.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Get the data row index.
    */
   public int getRow() {
      return row;
   }

   /**
    * Set data col index.
    * @param col the data col index.
    */
   public void setCol(int col) {
      this.col = col;
   }

   /**
    * Get the data col index.
    */
   public int getCol() {
      return col;
   }

   /**
    * Set the last display time of this object.
    */
   public void setLastDisplay(long lastDisplay) {
      this.lastDisplay = lastDisplay;
   }

   /**
    * Get the last display time of this object.
    */
   public long getLastDisplay() {
      return lastDisplay;
   }

   /**
    * Set the values for data position.
    * @param cellVal the values for data position.
    */
   public void setValue(AnnotationCellValue cellVal) {
      this.cellVal = cellVal;
   }

   /**
    * Get the values.
    */
   public AnnotationCellValue getValue() {
      return cellVal;
   }

   /**
    * Get relative offset from the base assembly.
    */
   public Point getRelativeOffset() {
      return relativeOffset;
   }

   /**
    * Set relative offset from the base assembly.
    */
   public void setRelativeOffset(Point relativeOffset) {
      this.relativeOffset = relativeOffset;
   }

   /**
    * Set the scaling ratio.
    */
   public void setScalingRatio(double width, double height) {
      scalingRatio.setSize(width, height);
   }

   /**
    * Get the scaling ratio.
    */
   public DimensionD getScalingRatio() {
      return scalingRatio;
   }

   /**
    * Set the annotation whether is available.
    */
   public void setAvailable(boolean available) {
      this.available = available;
   }

   /**
    * Check the annotation whether is available.
    * @return available the annotation is available.
    */
   public boolean isAvailable() {
      return available;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) info;

      if(!Tool.equals(isVisible(), ainfo.isVisible())) {
         setVisible(ainfo.isVisible());
         result = true;
      }

      if(!Tool.equals(line, ainfo.line)) {
         line = ainfo.line;
         result = true;
      }

      if(!Tool.equals(rectangle, ainfo.rectangle)) {
         rectangle = ainfo.rectangle;
         result = true;
      }

      if(!Tool.equals(cellVal, ainfo.cellVal)) {
         cellVal = ainfo.cellVal;
         result = true;
      }

      if(!Tool.equals(type, ainfo.type)) {
         type = ainfo.type;
         result = true;
      }

      if(!Tool.equals(row, ainfo.row)) {
         row = ainfo.row;
         result = true;
      }

      if(!Tool.equals(col, ainfo.col)) {
         col = ainfo.col;
         result = true;
      }

      if(!Tool.equals(lastDisplay, ainfo.lastDisplay)) {
         lastDisplay = ainfo.lastDisplay;
         result = true;
      }

      if(!Tool.equals(relativeOffset, ainfo.relativeOffset)) {
         relativeOffset = ainfo.relativeOffset;
         result = true;
      }

      return result;
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String val = null;

      if((val = Tool.getAttribute(elem, "line")) != null) {
         line = val;
      }

      if((val = Tool.getAttribute(elem, "rectangle")) != null) {
         rectangle = val;
      }

      if((val = Tool.getAttribute(elem, "type")) != null) {
         type = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(elem, "row")) != null) {
         row = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(elem, "col")) != null) {
         col = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(elem, "lastDisplay")) != null) {
         lastDisplay = Long.parseLong(val);
      }

      available = "true".equals(Tool.getAttribute(elem, "available"));

      String xstr = Tool.getAttribute(elem, "relativeOffX");
      String ystr = Tool.getAttribute(elem, "relativeOffY");

      if(xstr != null && ystr != null) {
         relativeOffset = new Point(Integer.parseInt(xstr),
                                    Integer.parseInt(ystr));
      }

      String scaleX = Tool.getAttribute(elem, "scaleX");
      String scaleY = Tool.getAttribute(elem, "scaleY");

      if(scaleX != null && scaleY != null) {
         scalingRatio = new DimensionD(Double.parseDouble(scaleX),
                                       Double.parseDouble(scaleY));
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(line != null) {
         writer.print(" line=\"" + line + "\"");
      }

      if(rectangle != null) {
         writer.print(" rectangle=\"" + rectangle + "\"");
      }

      writer.print(" type=\"" + type + "\"");
      writer.print(" row=\"" + row + "\"");
      writer.print(" col=\"" + col + "\"");
      writer.print(" lastDisplay=\"" + lastDisplay + "\"");
      writer.print(" available=\"" + available + "\"");

      if(relativeOffset != null) {
         writer.print(" relativeOffX=\"" + relativeOffset.x + "\"");
         writer.print(" relativeOffY=\"" + relativeOffset.y + "\"");
      }

      writer.print(" scaleX=\"" + scalingRatio.getWidth() + "\"");
      writer.print(" scaleY=\"" + scalingRatio.getHeight() + "\"");


   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(cellVal != null) {
         writer.print("<cellValue type=\"" + cellVal.getType() + "\">");
         cellVal.writeXML(writer);
         writer.print("Calcc</cellValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element vnode = Tool.getChildNodeByTagName(elem, "cellValue");

      if(vnode != null) {
         String val = null;

         if((val = Tool.getAttribute(vnode, "type")) != null) {
            int type = Integer.parseInt(val);
            cellVal = AnnotationCellValue.create(type);
            cellVal.parseXML((Element) vnode.getFirstChild());
         }
      }

      Element snode = Tool.getChildNodeByTagName(elem, "ovisible");

      if(snode != null) {
         setVisible(Boolean.parseBoolean(Tool.getValue(snode)));
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public AnnotationVSAssemblyInfo clone(boolean shallow) {
      return (AnnotationVSAssemblyInfo) super.clone(shallow);
   }

   /**
    * override.
    * Get position scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getPositionScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(1, 1);
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return new Point2D.Double(1, 1);
   }

   private String line;
   private String rectangle;
   private int type;
   private int row = -1;
   private int col = -1;
   private long lastDisplay;
   private AnnotationCellValue cellVal;
   private boolean available = true;
   // offset relative with the base assembly
   private Point relativeOffset = new Point(0, 0);
   private DimensionD scalingRatio = new DimensionD(1.0, 1.0);
}
