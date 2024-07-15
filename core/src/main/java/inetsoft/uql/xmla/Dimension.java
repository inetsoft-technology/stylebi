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
package inetsoft.uql.xmla;

import inetsoft.uql.XCubeMember;
import inetsoft.uql.XDimension;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dimension represents a dimension in a certain cube.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
public class Dimension implements XDimension {
   /**
    * Get the name of this dimension.
    *
    * @return the name of this dimension.
    */
   @Override
   public String getName() {
      return dimName;
   }

   /**
    * Get the name of this dimension.
    *
    * @return the name of this dimension.
    */
   public String getDimensionName() {
      return dimName;
   }

   /**
    * Set the name of this dimension.
    *
    * @param dimName the name of this dimension.
    */
   public void setDimensionName(String dimName) {
      this.dimName = dimName;
   }

   /**
    * Get the unique name of this dimension.
    *
    * @return the unique name of this dimension.
    */
   public String getUniqueName() {
      return uniqueName;
   }

   /**
    * Set the unique name of this dimension.
    *
    * @param uniqueName the unique name of this dimension.
    */
   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   /**
   * Get the unique name of this dimension.
   *
   * @return the unique name of this dimension.
   */
   public String getIdentifier() {
      return uniqueName;
   }

   /**
    * Get the caption of this dimension.
    *
    * @return the caption of this dimension.
    */
   public String getCaption() {
      return dimCaption == null ? dimName : dimCaption;
   }

   /**
    * Set the caption of this dimension.
    *
    * @param dimCaption the caption of this dimension.
    */
   public void setCaption(String dimCaption) {
      this.dimCaption = dimCaption;
   }

   /**
    * Get the type of this dimension.
    *
    * @return the type of this dimension.
    */
   @Override
   public int getType() {
      return type;
   }

   /**
    * Set the type of this dimension.
    *
    * @param type the type of this dimension.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Check if should keep original order for members.
    *
    * @return <tt>true</tt> if should keep, <tt>false</tt> otherwise.
    */
   public boolean isOriginalOrder() {
      return originalOrder ||
         (type & DataRef.CUBE_TIME_DIMENSION) == DataRef.CUBE_TIME_DIMENSION;
   }

   /**
    * Set to keep original order of members.
    *
    * @param originalOrder <tt>true</tt> to keep, <tt>false</tt> otherwise.
    */
   public void setOriginalOrder(boolean originalOrder) {
      this.originalOrder = originalOrder;
   }

   /**
    * Get the number of levels in this dimension.
    *
    * @return the number of levels in this dimension.
    */
   @Override
   public int getLevelCount() {
      return levels == null ? 0 : levels.size();
   }

   /**
    * Get the specified level value.
    */
   @Override
   public XCubeMember getLevelAt(int level) {
      return levels.get(level);
   }

   /**
    * Set the levels for this dimension.
    *
    * @param levels array list of levels.
    */
   @SuppressWarnings("unchecked")
   public void setLevels(Collection levels) {
      this.levels = new ArrayList<>(levels);
   }

   /**
    * Get level by level name.
    * @param name the specified level name.
    * @return level.
    */
   public XCubeMember getLevel(String name) {
      for(int i = 0; i < getLevelCount(); i++) {
         DimMember member = (DimMember) getLevelAt(i);

         if(Objects.equals(name, member.getUniqueName())) {
            return member;
         }

         if(Objects.equals(name, member.getName())) {
            return member;
         }

         if(Objects.equals(name, member.getCaption())) {
            return member;
         }
      }

      return null;
   }

   /**
    * Get the scope/level number of the level.
    * @param levelName level name.
    * @return level number.
    */
   @Override
   public int getScope(String levelName) {
      DimMember member = (DimMember) getLevel(levelName);

      return member == null ? -1 : member.getNumber();
   }

   /**
    * Get parent dimension .
    * @return parent dimension name if any.
    */
   public String getParentDimension() {
      return null;
   }

   /**
    * Get parent dimension caption.
    * @return parent dimension caption if any.
    */
   public String getParentCaption() {
      return null;
   }

   /**
    * Write an XML element representation of this object.
    *
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<Dimension");
      writeAttributes(writer);
      writer.print(">");
      writer.print("<Levels>");

      if(levels != null) {
         for(DimMember level : levels) {
            level.writeXML(writer);
         }
      }

      writer.print("</Levels>");
      writer.print("</Dimension>");
   }

   /**
    * Read in the definition of this object from an XML tag.
    *
    * @param tag the XML Element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);

      Element levelsNode = Tool.getChildNodeByTagName(tag, "Levels");
      NodeList levelsList = Tool.getChildNodesByTagName(levelsNode, "DimMember");
      levels = new ArrayList<>();

      for(int i = 0; i < levelsList.getLength(); i++) {
         DimMember level = new DimMember();
         level.parseXML((Element) levelsList.item(i));
         levels.add(level);
      }
   }

   /**
    * Create a copy of this object.
    *
    * @return a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         Dimension dim = (Dimension) super.clone();

         if(levels != null) {
            dim.setLevels(levels.stream()
               .map(l -> (DimMember) l.clone())
               .collect(Collectors.toList()));
         }

         return dim;
      }
      catch(CloneNotSupportedException e) {
         LOG.error("Failed to clone Dimension", e);
      }

      return null;
   }

   /**
    * Returns a string representation of the object.
    * @return a string representation of the object.
    */
   public String toString() {
      return "[name: " + dimName + ", uniqueName: " + uniqueName +
         ", caption: " + dimCaption + "]";
   }

   /**
    * Write attributes.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" classname=\"" + getClass().getName() + "\"");
      writer.print(" dimensionName=\"" + Tool.byteEncode(dimName) + "\"");
      writer.print(" uniqueName=\"" + Tool.byteEncode(uniqueName) + "\"");
      writer.print(" dimCaption=\"" + Tool.byteEncode(dimCaption) + "\"");
      writer.print(" type=\"" + type + "\"");
      writer.print(" originalOrder=\"" + originalOrder + "\"");
   }

   /**
    * Parse attributes.
    */
   protected void parseAttributes(Element tag) throws Exception {
      String val = Tool.getAttribute(tag, "dimensionName");

      if(val != null) {
         dimName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "uniqueName");

      if(val != null) {
         uniqueName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "dimCaption");

      if(val != null) {
         dimCaption = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "type");

      if(val != null) {
         type = Integer.parseInt(Tool.byteDecode(val));
      }

      originalOrder = "true".equals(Tool.getAttribute(tag, "originalOrder"));
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof Dimension)) {
         return false;
      }

      return Tool.equals(((Dimension) obj).getIdentifier(), getIdentifier());
   }

   public int hashCode() {
      String identifier = getIdentifier();

      if(identifier != null) {
         return identifier.hashCode();
      }

      return Integer.MIN_VALUE;
   }

   private String dimName = null;
   private String uniqueName = null;
   private String dimCaption = null;
   private int type;
   private List<DimMember> levels = null;
   private boolean originalOrder;

   private static final Logger LOG = LoggerFactory.getLogger(Dimension.class);
}
