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

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * DeviceInfo stores device information.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class DeviceInfo implements AssetObject {
   /**
    * Gets the unique identifier of this device.
    *
    * @return the identifier.
    */
   public String getId() {
      return id;
   }

   /**
    * Sets the unique identifier of this device.
    *
    * @param id the identifier.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Get name.
    */
   public String getName() {
      return getName(false);
   }

   /**
    * Set name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get name.
    */
   public String getName(boolean realName) {
      return realName ? name : getDisplayName();
   }

   /**
    * If device name is null, get display name by device media size.
    */
   private String getDisplayName() {
      if(name != null) {
         return name;
      }

      String labelStr = "";

      if(minWidth != -1) {
         labelStr += minWidth;
      }

      if(maxWidth != -1) {
         labelStr = labelStr + "-" + maxWidth;
      }

      return labelStr;
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Gets a description of this device.
    *
    * @return the description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Sets a description of this device.
    *
    * @param description the description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Get min width.
    */
   public int getMinWidth() {
      return minWidth;
   }

   /**
    * Set min width.
    */
   public void setMinWidth(int minWidth) {
      this.minWidth = minWidth;
   }

   /**
    * Get max width.
    */
   public int getMaxWidth() {
      return maxWidth;
   }

   /**
    * Set max width.
    */
   public void setMaxWidth(int maxWidth) {
      this.maxWidth = maxWidth;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @SuppressWarnings("Duplicates")
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<deviceInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</deviceInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   private void writeAttributes(PrintWriter writer) {
      writer.print(" id=\"" + id + "\"");
      writer.print(" minWidth=\"" + minWidth + "\"");
      writer.print(" maxWidth=\"" + maxWidth + "\"");
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   private void writeContents(PrintWriter writer) {
      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>%n", name);
      }

      if(created != 0) {
         writer.format("<created><![CDATA[%d]]></created>%n", created);
      }

      if(modified != 0) {
         writer.format("<modified><![CDATA[%d]]></modified>%n", modified);
      }

      if(createdBy != null) {
         writer.format("<createdBy><![CDATA[%s]]></createdBy>%n", createdBy);
      }

      if(modifiedBy != null) {
         writer.format("<modifiedBy><![CDATA[%s]]></modifiedBy>%n", modifiedBy);
      }

      if(description != null) {
         writer.format("<description><![CDATA[%s]]></description>%n", description);
      }
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
    * Parse attributes.
    * @param elem the specified xml element.
    */
   private void parseAttributes(Element elem) {
      String prop;

      this.id = Tool.getAttribute(elem, "id");

      if((prop = Tool.getAttribute(elem, "minWidth")) != null) {
         this.minWidth = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(elem, "maxWidth")) != null) {
         this.maxWidth = Integer.parseInt(prop);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   private void parseContents(Element elem) throws Exception {
      this.name = Tool.getChildValueByTagName(elem, "name");
      this.description = Tool.getChildValueByTagName(elem, "description");
      this.createdBy = Tool.getChildValueByTagName(elem, "createdBy");
      this.modifiedBy = Tool.getChildValueByTagName(elem, "modifiedBy");
      String val = Tool.getChildValueByTagName(elem, "created");

      if(val != null) {
         created = Long.parseLong(val);
      }

      val = Tool.getChildValueByTagName(elem, "modified");

      if(val != null) {
         modified = Long.parseLong(val);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   @Override
   public String toString() {
      return "DeviceInfo{" +
         "id='" + id + '\'' +
         ", name='" + name + '\'' +
         ", minWidth=" + minWidth +
         ", maxWidth=" + maxWidth +
         ", description='" + description + '\'' +
         '}';
   }

   private String id;
   private String name;
   private String description;
   private int minWidth = -1;
   private int maxWidth = -1;
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;

   private static final Logger LOG =
      LoggerFactory.getLogger(DeviceInfo.class);
}
