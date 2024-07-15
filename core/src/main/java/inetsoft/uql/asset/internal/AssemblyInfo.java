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

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.*;

/**
 * AssemblyInfo stores basic assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AssemblyInfo implements AssetObject, DataSerializable {
   /**
    * Create an assembly info from an xml element.
    * @param elem the specified xml element.
    * @return the created assembly info.
    */
   public static AssemblyInfo createAssemblyInfo(Element elem) throws Exception {
      String cls = Tool.getAttribute(elem, "class");
      assert cls != null;
      int idx = cls.indexOf(".");
      cls = idx < 0 ? "inetsoft.uql.asset.internal." + cls : cls;
      AssemblyInfo info = (AssemblyInfo) Class.forName(cls).newInstance();
      info.parseXML(elem);
      return info;
   }

   /**
    * Create an assembly info.
    */
   public AssemblyInfo() {
      super();

      pixelOffset = new Point(0, 0);
      pixelSize = new Dimension(AssetUtil.defw, AssetUtil.defh);
      visible = true;
      editable = true;
   }

   /**
    * Get the name.
    * @return the name of the assembly.
    */
   public String getName() {
      return name;
   }

   /**
    * Get the absolute name of this assembly.
    * @return the absolute name of this assembly.
    */
   public String getAbsoluteName() {
      return getName();
   }

   /**
    * Set the name.
    * @param name the specified name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Set the pixel offset.
    * @param pixelOffset the pixel offset of the assembly.
    */
   public void setPixelOffset(Point pixelOffset) {
      this.pixelOffset = pixelOffset;
   }

   /**
    * Get the pixel offset.
    * @return the pixel offset of the assembly.
    */
   public Point getPixelOffset() {
      return pixelOffset;
   }

   /**
    * Set the pixel size of the assembly.
    * @param pixelSize the pixel size of the assembly.
    */
   public void setPixelSize(Dimension pixelSize) {
      this.pixelSize = pixelSize;
   }

   /**
    * Get the pixel size of the assembly.
    * @return the pixel size of the assembly.
    */
   public Dimension getPixelSize() {
      return pixelSize;
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   public boolean isEditable() {
      return editable;
   }

   /**
    * Set the editable flag.
    * @param editable <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set the visible flag.
    * @param visible <tt>true</tt> if visible, <tt>false</tt> otherwise.
    */
   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   /**
    * Check if is primary.
    * @return <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   public boolean isPrimary() {
      return primary;
   }

   /**
    * Set the primary option.
    * @param primary <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   public void setPrimary(boolean primary) {
      this.primary = primary;
   }

   /**
    * Get the class name.
    */
   protected String getClassName(boolean compact) {
      return getClass().getName();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      boolean compact = Tool.isCompact();
      String cls = "class=\"" + getClassName(compact) + "\"";
      writer.print("<assemblyInfo " + cls);
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</assemblyInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      if(pixelOffset != null) {
         writer.print(" pixelOffX=\"" + pixelOffset.x + "\"");
         writer.print(" pixelOffY=\"" + pixelOffset.y + "\"");
      }

      if(pixelSize != null) {
         writer.print(" pixelWidth=\"" + pixelSize.width + "\"");
         writer.print(" pixelHeight=\"" + pixelSize.height + "\"");
      }

      if(!editable) {
         writer.print(" editable=\"false\"");
      }

      if(!visible) {
         writer.print(" visible=\"false\"");
      }

      if(primary) {
         writer.print(" primary=\"true\"");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      String pixelOffX = Tool.getAttribute(elem, "pixelOffX");
      String pixelOffY = Tool.getAttribute(elem, "pixelOffY");

      if(pixelOffX != null && pixelOffY != null) {
         pixelOffset =
            new Point(Integer.parseInt(pixelOffX), Integer.parseInt(pixelOffY));
      }
      else {
         pixelOffset = new Point(0, 0);
      }

      String pixelWidth = Tool.getAttribute(elem, "pixelWidth");
      String pixelHeight = Tool.getAttribute(elem, "pixelHeight");

      if(pixelWidth != null && pixelHeight != null) {
         pixelSize =
            new Dimension(Integer.parseInt(pixelWidth), Integer.parseInt(pixelHeight));
      }
      else {
         pixelSize = new Dimension(AssetUtil.defw, AssetUtil.defh);
      }

      this.editable = !"false".equals(Tool.getAttribute(elem, "editable"));
      this.visible = !"false".equals(Tool.getAttribute(elem, "visible"));
      this.primary = "true".equals(Tool.getAttribute(elem, "primary"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      writer.print("<name><![CDATA[" + name + "]]></name>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      name = Tool.getChildValueByTagName(elem, "name");
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
         AssemblyInfo info2 = (AssemblyInfo) super.clone();

         if(pixelSize != null) {
            info2.setPixelSize((Dimension) pixelSize.clone());
         }

         if(pixelOffset != null) {
            info2.setPixelOffset((Point) pixelOffset.clone());
         }

         return info2;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public void writeData(DataOutputStream output) throws IOException {
      XMLTool.writeXMLSerializableAsData(output, this);
   }

   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   private String name;
   private Point pixelOffset;
   private Dimension pixelSize;
   private boolean primary;
   private boolean visible;
   private boolean editable;

   private static final Logger LOG =
      LoggerFactory.getLogger(AssemblyInfo.class);
}
