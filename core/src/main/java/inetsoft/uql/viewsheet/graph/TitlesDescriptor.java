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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * TitlesDescriptor is a bean that holds the attributes of the titles area
 * of a chart. For a radar chart style, it needs to keep both y
 * title descriptor in the TitlesDescriptor and for each measure in the
 * ChartAggregateRef. Furthermore, the visibility of y title descriptor
 * should keep in here, and other properties of y title should keep in measure.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class TitlesDescriptor implements AssetObject, ContentObject {
   /**
    * Create a new instance of TitlesDescriptor.
    */
   public TitlesDescriptor() {
      xDes = new TitleDescriptor("x");
      x2Des = new TitleDescriptor("x2");
      yDes = new TitleDescriptor("y");
      y2Des = new TitleDescriptor("y2");
      yDes.getTextFormat().getDefaultFormat().setRotation(90d);
      y2Des.getTextFormat().getDefaultFormat().setRotation(90d);
   }

   /**
    * Get the title descriptor of X axis.
    */
   public TitleDescriptor getXTitleDescriptor() {
      return xDes;
   }

   /**
    * Set the title descriptor of X axis.
    */
   public void setXTitleDescriptor(TitleDescriptor xDes) {
      this.xDes = xDes;
   }

   /**
    * Get the title descriptor of secondary X axis.
    */
   public TitleDescriptor getX2TitleDescriptor() {
      return x2Des;
   }

   /**
    * Set the title descriptor of secondary X axis.
    */
   public void setX2TitleDescriptor(TitleDescriptor xDes) {
      this.x2Des = xDes;
   }

   /**
    * Get the title descriptor of Y axis.
    */
   public TitleDescriptor getYTitleDescriptor() {
      return yDes;
   }

   /**
    * Set the title descriptor of Y axis.
    */
   public void setYTitleDescriptor(TitleDescriptor yDes) {
      this.yDes = yDes;
   }

   /**
    * Get the title descriptor of 2nc Y axis.
    */
   public TitleDescriptor getY2TitleDescriptor() {
      return y2Des;
   }

   /**
    * Set the title descriptor of 2nd Y axis.
    */
   public void setY2TitleDescriptor(TitleDescriptor yDes) {
      this.y2Des = yDes;
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         TitlesDescriptor des = (TitlesDescriptor) super.clone();

         if(xDes != null) {
            des.xDes = (TitleDescriptor) xDes.clone();
         }

         if(x2Des != null) {
            des.x2Des = (TitleDescriptor) x2Des.clone();
         }

         if(yDes != null) {
            des.yDes = (TitleDescriptor) yDes.clone();
         }

         if(y2Des != null) {
            des.y2Des = (TitleDescriptor) y2Des.clone();
         }

         return des;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone TitlesDescriptor", exc);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof TitlesDescriptor)) {
         return false;
      }

      TitlesDescriptor des = (TitlesDescriptor) obj;

      if(!Tool.equalsContent(xDes, des.xDes)) {
         return false;
      }

      if(!Tool.equalsContent(x2Des, des.x2Des)) {
         return false;
      }

      if(!Tool.equalsContent(yDes, des.yDes)) {
         return false;
      }

      if(!Tool.equalsContent(y2Des, des.y2Des)) {
         return false;
      }

      return true;
   }

   /**
    * Write xml representation.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<titlesDescriptor");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</titlesDescriptor>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
      if(xDes != null) {
         writer.println("<XTitleDescriptor>");
         xDes.writeXML(writer);
         writer.println("</XTitleDescriptor>");
      }

      if(x2Des != null) {
         writer.println("<X2TitleDescriptor>");
         x2Des.writeXML(writer);
         writer.println("</X2TitleDescriptor>");
      }

      if(yDes != null) {
         writer.println("<YTitleDescriptor>");
         yDes.writeXML(writer);
         writer.println("</YTitleDescriptor>");
      }

      if(y2Des != null) {
         writer.println("<Y2TitleDescriptor>");
         y2Des.writeXML(writer);
         writer.println("</Y2TitleDescriptor>");
      }
   }

   /**
    * Method to parse an xml segment about parameter element information.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Parse attributes to an XML segment.
    */
   protected void parseAttributes(Element tag) throws Exception {
   }

   /**
    * Parse the content part(child node) of XML segment.
    * @param tag the specified xml element.
    */
   protected void parseContents(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "XTitleDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         xDes = new TitleDescriptor(CSSConstants.X_TITLE);
         xDes.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "X2TitleDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         x2Des = new TitleDescriptor(CSSConstants.X2_TITLE);
         x2Des.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "YTitleDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         yDes = new TitleDescriptor(CSSConstants.Y_TITLE);
         yDes.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "Y2TitleDescriptor");

      if(node != null) {
         node = Tool.getFirstChildNode(node);
         y2Des = new TitleDescriptor(CSSConstants.Y2_TITLE);
         y2Des.parseXML(node);
      }
   }

   /**
    * Get the dynamic property values.
    */
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      list.addAll(xDes.getDynamicValues());
      list.addAll(x2Des.getDynamicValues());
      list.addAll(yDes.getDynamicValues());
      list.addAll(y2Des.getDynamicValues());
      return list;
   }

   private TitleDescriptor xDes;
   private TitleDescriptor x2Des;
   private TitleDescriptor yDes;
   private TitleDescriptor y2Des;

   private static final Logger LOG =
      LoggerFactory.getLogger(TitlesDescriptor.class);
}
