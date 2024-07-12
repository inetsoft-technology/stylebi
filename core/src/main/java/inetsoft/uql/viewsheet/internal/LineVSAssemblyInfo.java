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

import inetsoft.report.StyleConstants;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.DynamicValue2;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * LineVSAssemblyInfo stores basic line assembly information.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class LineVSAssemblyInfo extends ShapeVSAssemblyInfo {
   /**
    * North anchor position.
    */
   public static final int NORTH = 1;
   /**
    * East anchor position.
    */
   public static final int EAST = 2;
   /**
    * South anchor position.
    */
   public static final int SOUTH = 4;
   /**
    * West anchor position.
    */
   public static final int WEST = 8;
   /**
    * North-east anchor position.
    */
   public static final int NORTH_EAST = NORTH | EAST;
   /**
    * South-east anchor position.
    */
   public static final int SOUTH_EAST = SOUTH | EAST;
   /**
    * South-west anchor position.
    */
   public static final int SOUTH_WEST = SOUTH | WEST;
   /**
    * North-west anchor position.
    */
   public static final int NORTH_WEST = NORTH | WEST;

   /**
    * Constructor.
    */
   public LineVSAssemblyInfo() {
      super();

      setPixelSize(new Dimension(50, 50));
   }

   /**
    * Set the runtime line begin point arrow style.
    * @param style the line begin point arrow style.
    */
   public void setBeginArrowStyle(int style) {
      beginStyleValue.setRValue(style);
   }

   /**
    * Set the design time line begin point arrow style value.
    * @param style the begin point arrow style value.
    */
   public void setBeginArrowStyleValue(int style) {
      beginStyleValue.setDValue(style + "");
   }

   /**
    * Get the runtime line begin point arrow style.
    * @return the line begin point arrow style.
    */
   public int getBeginArrowStyle() {
      return beginStyleValue.getIntValue(false, StyleConstants.NO_BORDER);
   }

   /**
    * Get the design time line begin point arrow style.
    * @return the line begin point arrow style.
    */
   public int getBeginArrowStyleValue() {
      return beginStyleValue.getIntValue(true, StyleConstants.NO_BORDER);
   }

   /**
    * Set the runtime line end point arrow style.
    * @param style the line end point arrow style.
    */
   public void setEndArrowStyle(int style) {
      endStyleValue.setRValue(style);
   }

   /**
    * Set the design time line end point arrow style value.
    * @param style the line end point arrow style value.
    */
   public void setEndArrowStyleValue(int style) {
      endStyleValue.setDValue(style + "");
   }

   /**
    * Get the runtime line end point arrow style.
    * @return the line end point arrow style.
    */
   public int getEndArrowStyle() {
      return endStyleValue.getIntValue(false, StyleConstants.ARROW_LINE_1);
   }

   /**
    * Get the design time line end point arrow style.
    * @return the line end point arrow style.
    */
   public int getEndArrowStyleValue() {
      return endStyleValue.getIntValue(true, StyleConstants.ARROW_LINE_1);
   }

   /**
    * Get the line start position.
    */
   public Point getStartPos() {
      return startPos;
   }

   /**
    * Set the line start position.
    */
   public void setStartPos(Point pos) {
      this.startPos = pos;
   }

   /**
    * Get the line end position.
    */
   public Point getEndPos() {
      return endPos;
   }

   /**
    * Set the line end position.
    */
   public void setEndPos(Point pos) {
      this.endPos = pos;
   }

   /**
    * Get the line start position.
    */
   public final Point getLayoutStartPos() {
      return getLayoutStartPos(true);
   }

   public final Point getLayoutStartPos(boolean scaled) {
      if(scaled && scaledStartPos != null) {
         return scaledStartPos;
      }

      return layoutStartPos;
   }

   /**
    * Set the line start position.
    */
   public final void setLayoutStartPos(Point layoutStartPos) {
      this.layoutStartPos = layoutStartPos;
      this.scaledStartPos = null;
      this.scaledEndPos = null;
   }

   /**
    * Get the line end position.
    */
   public final Point getLayoutEndPos() {
      return getLayoutEndPos(true);
   }

   public final Point getLayoutEndPos(boolean scaled) {
      if(scaled && scaledEndPos != null) {
         return scaledEndPos;
      }

      return layoutEndPos;
   }

   /**
    * Set the line end position.
    */
   public final void setLayoutEndPos(Point layoutEndPos) {
      this.layoutEndPos = layoutEndPos;
      this.scaledStartPos = null;
      this.scaledEndPos = null;
   }

   public final void setScaledStartPos(Point scaledStartPos) {
      this.scaledStartPos = scaledStartPos;
   }

   public final void setScaledEndPos(Point scaledEndPos) {
      this.scaledEndPos = scaledEndPos;
   }

   /**
    * Get the start anchor element id.
    */
   public String getStartAnchorID() {
      return startID;
   }

   /**
    * Set the start anchor element id.
    */
   public void setStartAnchorID(String id) {
      this.startID = id;
   }

   /**
    * Get the end anchor element id.
    */
   public String getEndAnchorID() {
      return endID;
   }

   /**
    * Set the end anchor element id.
    */
   public void setEndAnchorID(String id) {
      this.endID = id;
   }

   /**
    * Get the start anchor position.
    */
   public int getStartAnchorPos() {
      return startAnchor;
   }

   /**
    * Set the start anchor position.
    */
   public void setStartAnchorPos(int pos) {
      this.startAnchor = pos;
   }

   /**
    * Get the end anchor position.
    */
   public int getEndAnchorPos() {
      return endAnchor;
   }

   /**
    * Set the end anchor position.
    */
   public void setEndAnchorPos(int pos) {
      this.endAnchor = pos;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      LineVSAssemblyInfo cinfo = (LineVSAssemblyInfo) info;

      if(!Tool.equals(beginStyleValue, cinfo.beginStyleValue) ||
         getBeginArrowStyle() != cinfo.getBeginArrowStyle())
      {
         beginStyleValue = cinfo.beginStyleValue;
         result = true;
      }

      if(!Tool.equals(endStyleValue, cinfo.endStyleValue) ||
         getEndArrowStyle() != cinfo.getEndArrowStyle())
      {
         endStyleValue = cinfo.endStyleValue;
         result = true;
      }

      if(startPos != cinfo.startPos) {
         startPos = cinfo.startPos;
         result = true;
      }

      if(endPos != cinfo.endPos) {
         endPos = cinfo.endPos;
         result = true;
      }

      if(layoutStartPos != cinfo.layoutStartPos) {
         layoutStartPos = cinfo.layoutStartPos;
         result = true;
      }

      if(layoutEndPos != cinfo.layoutEndPos) {
         layoutEndPos = cinfo.layoutEndPos;
         result = true;
      }

      if(!Tool.equals(startID, cinfo.startID)) {
         startID = cinfo.startID;
         result = true;
      }

      if(!Tool.equals(endID, cinfo.endID)) {
         endID = cinfo.endID;
         result = true;
      }

      return result;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.LINE;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      beginStyleValue.setRValue(null);
      endStyleValue.setRValue(null);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      beginStyleValue.setDValue(
         getAttributeStr(elem, "beginStyle", StyleConstants.NO_BORDER + ""));
      endStyleValue.setDValue(
         getAttributeStr(elem, "endStyle", StyleConstants.ARROW_LINE_1 + ""));

      String xstr = Tool.getAttribute(elem, "startPosX");
      String ystr = Tool.getAttribute(elem, "startPosY");

      if(xstr != null && ystr != null) {
         startPos = new Point((int) Double.parseDouble(xstr),
                              (int) Double.parseDouble(ystr));
      }

      xstr = Tool.getAttribute(elem, "endPosX");
      ystr = Tool.getAttribute(elem, "endPosY");

      if(xstr != null && ystr != null) {
         endPos = new Point((int) Double.parseDouble(xstr),
                            (int) Double.parseDouble(ystr));
      }

      xstr = Tool.getAttribute(elem, "layoutStartPosX");
      ystr = Tool.getAttribute(elem, "layoutStartPosY");

      if(xstr != null && ystr != null) {
         layoutStartPos = new Point((int) Double.parseDouble(xstr),
                                    (int) Double.parseDouble(ystr));
      }

      xstr = Tool.getAttribute(elem, "layoutEndPosX");
      ystr = Tool.getAttribute(elem, "layoutEndPosY");

      if(xstr != null && ystr != null) {
         layoutEndPos = new Point((int) Double.parseDouble(xstr),
                                  (int) Double.parseDouble(ystr));
      }

      startID = Tool.getAttribute(elem, "startID");
      endID = Tool.getAttribute(elem, "endID");

      String attr;

      if((attr = Tool.getAttribute(elem, "startAnchor")) != null) {
         startAnchor = Integer.parseInt(attr);
      }

      if((attr = Tool.getAttribute(elem, "endAnchor")) != null) {
         endAnchor = Integer.parseInt(attr);
      }
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" beginStyle=\"" + getBeginArrowStyle() + "\"");
      writer.print(" endStyle=\"" + getEndArrowStyle() + "\"");
      writer.print(" beginStyleValue=\"" + beginStyleValue.getDValue() + "\"");
      writer.print(" endStyleValue=\"" + endStyleValue.getDValue() + "\"");

      if(startPos != null) {
         writer.print(" startPosX=\"" + startPos.x + "\"");
         writer.print(" startPosY=\"" + startPos.y + "\"");
      }

      if(endPos != null) {
         writer.print(" endPosX=\"" + endPos.x + "\"");
         writer.print(" endPosY=\"" + endPos.y + "\"");
      }

      if(layoutStartPos != null) {
         writer.print(" layoutStartPosX=\"" + layoutStartPos.x + "\"");
         writer.print(" layoutStartPosY=\"" + layoutStartPos.y + "\"");
      }

      if(layoutEndPos != null) {
         writer.print(" layoutEndPosX=\"" + layoutEndPos.x + "\"");
         writer.print(" layoutEndPosY=\"" + layoutEndPos.y + "\"");
      }

      if(startID != null) {
         writer.print(" startID=\"" + Tool.escape(startID) +
                      "\" startAnchor=\"" + startAnchor + "\"");
      }

      if(endID != null) {
         writer.print(" endID=\"" + Tool.escape(endID) +
                      "\" endAnchor=\"" + endAnchor + "\"");
      }
   }

   /**
    * Rename the anchor element.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      if(oname.equals(startID)) {
         startID = nname;
      }

      if(oname.equals(endID)) {
         endID = nname;
      }
   }

   /**
    * Copy the position/size information.
    */
   @Override
   public void copyLayout(VSAssemblyInfo info) {
      super.copyLayout(info);

      setStartAnchorID(((LineVSAssemblyInfo) info).getStartAnchorID());
      setEndAnchorID(((LineVSAssemblyInfo) info).getEndAnchorID());
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public LineVSAssemblyInfo clone(boolean shallow) {
      LineVSAssemblyInfo info = (LineVSAssemblyInfo) super.clone(shallow);

      if(!shallow) {
         info.beginStyleValue = (DynamicValue2) beginStyleValue.clone();
         info.endStyleValue = (DynamicValue2) endStyleValue.clone();
      }

      return info;
   }

   private Point startPos = new Point(50, 50);
   private Point endPos = new Point(0, 0);
   private Point layoutStartPos;
   private Point layoutEndPos;
   private Point scaledStartPos;
   private Point scaledEndPos;
   private String startID = null; // start point anchored element id
   private int startAnchor = NORTH; // start point anchored position
   private String endID = null; // end point anchored element id
   private int endAnchor = NORTH; // end point anchored position
   private DynamicValue2 beginStyleValue =
      new DynamicValue2(StyleConstants.NO_BORDER + "", XSchema.INTEGER);
   private DynamicValue2 endStyleValue =
      new DynamicValue2(StyleConstants.ARROW_LINE_1 + "", XSchema.INTEGER);
}
