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

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.Margin;
import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * PrintInfo stores print information.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class PrintInfo implements AssetObject {
   /**
    * Constructor.
    */
   public PrintInfo() {
   }

   /**
    * Constructor.
    */
   public PrintInfo(String paperType, DimensionD size, float marginTop,
      float marginLeft, float marginBottom, float marginRight, String unit)
   {
      this.paperType = paperType;
      this.size = size;
      this.marginTop = marginTop;
      this.marginLeft = marginLeft;
      this.marginBottom = marginBottom;
      this.marginRight = marginRight;
      this.unit = unit;
   }

   /**
    * Get paper type for print.
    */
   public String getPaperType() {
      return paperType;
   }

   /**
    * Set paper type for print.
    */
   public void setPaperType(String paperType) {
      this.paperType = paperType;
   }

   /**
    * Get size for print.
    */
   public DimensionD getSize() {
      double ratio = 1 / getUnitRatio(); //Convert inches to current unit
      return new DimensionD(size.getWidth() * ratio, size.getHeight() * ratio);
   }

   /**
    * Set size for print.
    */
   public void setSize(DimensionD size) {
      this.size = size;
   }

   /**
    * Get paper margin.
    */
   public Margin getMargin() {
      return new Margin(marginTop, marginLeft, marginBottom, marginRight);
   }

   /**
    * Sets the page margin.
    *
    * @param margin the margin.
    */
   public void setMargin(Margin margin) {
      if(margin != null) {
         marginTop = (float) margin.top;
         marginLeft = (float) margin.left;
         marginBottom = (float) margin.bottom;
         marginRight = (float) margin.right;
      }
   }

   /**
    * Get margin right of the print layout.
    */
   public float getHeaderFromEdge() {
      return headerFromEdge;
   }

   public float getInchHeaderFromEdge() {
      return headerFromEdge * (float) getUnitRatio();
   }

   /**
    * Set margin right to the print layout.
    */
   public void setHeaderFromEdge(float headerFromEdge) {
      this.headerFromEdge = headerFromEdge;
   }

   /**
    * Get margin right of the print layout.
    */
   public float getFooterFromEdge() {
      return footerFromEdge;
   }

   public float getInchFooterFromEdge() {
      return footerFromEdge * (float) getUnitRatio();
   }

   /**
    * Set margin right to the print layout.
    */
   public void setFooterFromEdge(float footerFromEdge) {
      this.footerFromEdge = footerFromEdge;
   }

   /**
    * Get margin right of the print layout.
    */
   public int getPageNumberingStart() {
      return pgStart;
   }

   /**
    * Set margin right to the print layout.
    */
   public void setPageNumberingStart(int pgStart) {
      this.pgStart = pgStart;
   }

   /**
    * Get unit of the size.
    */
   public String getUnit() {
      return unit;
   }

   /**
    * Set unit of the size.
    */
   public void setUnit(String unit) {
      this.unit = unit;
   }

   private double getUnitRatio() {
      return getUnitRatio(unit);
   }

   public static double getUnitRatio(String unit) {
      double ratio = 1;

      if("mm".equals(unit)) {
         ratio = 1 / 25.4;
      }
      else if("points".equals(unit)) {
         ratio = 1 / 72.0;
      }

      return ratio;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<printInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</printInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      if(size != null) {
         writer.print(" width=\"" + size.getWidth() + "\"");
         writer.print(" height=\"" + size.getHeight() + "\"");
      }

      writer.print(" marginTop=\"" + marginTop + "\"");
      writer.print(" marginLeft=\"" + marginLeft + "\"");
      writer.print(" marginBottom=\"" + marginBottom + "\"");
      writer.print(" marginRight=\"" + marginRight + "\"");
      writer.print(" headerFromEdge=\"" + headerFromEdge + "\"");
      writer.print(" footerFromEdge=\"" + footerFromEdge + "\"");
      writer.print(" pgStart=\"" + pgStart + "\"");
   }

   /**
    * Write contents.
    */
   private void writeContents(PrintWriter writer) {
      writer.print("<paperType><![CDATA[" + paperType + "]]></paperType>");
      writer.print("<unit><![CDATA[" + unit + "]]></unit>");
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
      String width = Tool.getAttribute(elem, "width");
      String height = Tool.getAttribute(elem, "height");

      if(width != null && height != null) {
         this.size = new DimensionD(Double.parseDouble(width),
            Double.parseDouble(height));
      }

      this.marginTop = Float.parseFloat(Tool.getAttribute(elem, "marginTop"));
      this.marginLeft = Float.parseFloat(Tool.getAttribute(elem, "marginLeft"));
      this.marginBottom =
         Float.parseFloat(Tool.getAttribute(elem, "marginBottom"));
      this.marginRight =
         Float.parseFloat(Tool.getAttribute(elem, "marginRight"));

      if(Tool.getAttribute(elem, "headerFromEdge") != null) {
         this.headerFromEdge =
            Float.parseFloat(Tool.getAttribute(elem, "headerFromEdge"));
      }

      if(Tool.getAttribute(elem, "footerFromEdge") != null) {
         this.footerFromEdge =
            Float.parseFloat(Tool.getAttribute(elem, "footerFromEdge"));
      }

      if(Tool.getAttribute(elem, "pgStart") != null) {
         this.pgStart =
            Integer.parseInt(Tool.getAttribute(elem, "pgStart"));
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   private void parseContents(Element elem) throws Exception {
      this.paperType = Tool.getChildValueByTagName(elem, "paperType");
      this.unit = Tool.getChildValueByTagName(elem, "unit");
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         PrintInfo info2 = (PrintInfo) super.clone();
         info2.size = (DimensionD) size.clone();

         return info2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private String paperType;
   private DimensionD size;
   private float marginTop;
   private float marginLeft;
   private float marginBottom;
   private float marginRight;
   private float headerFromEdge = 0.5f;
   private float footerFromEdge = 0.75f;
   private int pgStart = 0;
   private String unit;

   private static final Logger LOG =
      LoggerFactory.getLogger(PrintInfo.class);
}