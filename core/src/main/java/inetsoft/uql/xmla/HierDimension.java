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
package inetsoft.uql.xmla;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * HierDimension represents a hierarchy in a dimension.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
public class HierDimension extends Dimension {
   /**
    * Get the name of this dimension.
    *
    * @return the name of this dimension.
    */
   @Override
   public String getName() {
      return hierUniqueName;
   }

   /**
    * Get the hierarchy name if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @return the hierarchy name.
    */
   public String getHierarchyName() {
      return hierName;
   }

   /**
    * Set the hierarchy name if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @param hierName the hierarchy name.
    */
   public void setHierarchyName(String hierName) {
      this.hierName = hierName;
   }

   /**
    * Set the hierarchy caption if this dimension
    * represents a hierarchy/dimension combination.
    *
    */
   public String getHierCaption() {
      return hierCaption;
   }

   /**
    * Get the hierarchy caption if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @return the hierarchy caption.
    */
   public void setHierCaption(String hierCaption) {
      this.hierCaption = hierCaption;
   }

   /**
    * Get the hierarchy unique name if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @return the hierarchy unqiue name.
    */
   public String getHierarchyUniqueName() {
      return hierUniqueName;
   }

   /**
    * Set the hierarchy unique name if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @param hierUniqueName the hierarchy unique name.
    */
   public void setHierarchyUniqueName(String hierUniqueName) {
      this.hierUniqueName = hierUniqueName;
   }
   
   /**
    * Get the hierarchy unique name if this dimension
    * represents a hierarchy/dimension combination.
    *
    * @return the hierarchy unqiue name.
    */
   @Override
   public String getIdentifier() {
      return hierUniqueName;
   }
   
   /**
    * Get parent dimension.
    * @return parent dimension name if any.
    */
   @Override
   public String getParentDimension() {
      return parentCaption == null ? getDimensionName() : parentCaption;
   }

   /**
    * Get parent dimension caption.
    * @return parent dimension caption if any.
    */
   @Override
   public String getParentCaption() {
      return parentCaption;
   }

   /**
    * Set the parent dimension caption.
    *
    * @param parentCaption the dimension caption.
    */
   public void setParentCaption(String parentCaption) {
      this.parentCaption = parentCaption;
   }

   /**
    * Set if is user defined hierarchy.
    * @param userDefined <tt>true</tt> if is user defined,
    * <tt>false</tt> otherwise.
    */
   public void setUserDefined(boolean userDefined) {
      this.userDefined = userDefined;
   }

   /**
    * Check if is user defined hierarchy.
    * @return <tt>true</tt> if is user defined, <tt>false</tt> otherwise.
    */
   public boolean isUserDefined() {
      return userDefined;
   }

   /**
    * Parse attributes.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String val = Tool.getAttribute(tag, "hierarchyName");

      if(val != null) {
         hierName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "hierarchyUniqueName");

      if(val != null) {
         hierUniqueName = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "hierCaption");

      if(val != null) {
         hierCaption = Tool.byteDecode(val);
      }

      val = Tool.getAttribute(tag, "parentCaption");

      if(val != null) {
         parentCaption = Tool.byteDecode(val);
      }

      userDefined = "true".equals(Tool.getAttribute(tag, "userDefined"));
   }

   /**
    * Write attributes.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" userDefined=\"" + userDefined + "\"");

      if(hierName != null) {
         writer.print(" hierarchyName=\"" + Tool.byteEncode(hierName) + "\"");
      }

      if(hierUniqueName != null) {
         writer.print(" hierarchyUniqueName=\"" +
            Tool.byteEncode(hierUniqueName) + "\"");
      }

      if(hierCaption != null) {
         writer.print(" hierCaption=\"" + Tool.byteEncode(hierCaption) + "\"");
      }

      if(parentCaption != null) {
         writer.print(" parentCaption=\"" + 
            Tool.byteEncode(parentCaption) + "\"");
      }
   }

   private String hierName = null;
   private String hierUniqueName = null;
   private String hierCaption = null;
   private String parentCaption = null;
   private boolean userDefined = false;
}