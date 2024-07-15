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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.ViewsheetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * This class defines the viewsheet dashboard.
 *
 * @version 8.5, 6/15/2006
 * @author InetSoft Technology Corp
 */
public class VSDashboard implements Dashboard {
   /**
    * Constructor.
    */
   public VSDashboard() {
      super();
   }

   /**
    * Gets the type of the dashboard.
    */
   @Override
   public String getType() {
      return Dashboard.VSDASHBOARD;
   }

   /**
    * Checks if the dashboard editable.
    */
   @Override
   public boolean isComposable() {
      return false;
   }

   /**
    * Gets description of this registry.
    */
   @Override
   public String getDescription() {
      return description;
   }

   /**
    * Sets description of this registry.
    */
   @Override
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets viewsheet entry of this registry.
    * @return viewsheet entry.
    */
   public ViewsheetEntry getViewsheet() {
      return viewsheet;
   }

   /**
    * Sets viewsheet entry of this registry.
    * @param viewsheet the specified viewsheet entry.
    */
   public void setViewsheet(ViewsheetEntry viewsheet) {
      this.viewsheet = viewsheet;
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
    * Writes the viewsheet dashboard to a xml writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<dashboard class=\"" + getClass().getName() + "\">");

      if(createdBy != null) {
         writer.println("<createdBy><![CDATA[" + createdBy +"]]>");
         writer.println("</createdBy>");
      }

      if(modifiedBy != null) {
         writer.println("<modifiedBy><![CDATA[" + modifiedBy +"]]>");
         writer.println("</modifiedBy>");
      }

      if(created != 0) {
         writer.println("<created><![CDATA[" + created +"]]>");
         writer.println("</created>");
      }

      if(modified != 0) {
         writer.println("<modified><![CDATA[" + modified +"]]>");
         writer.println("</modified>");
      }

      if(description != null) {
         writer.println("<description><![CDATA[" + getDescription() +"]]>");
         writer.println("</description>");
      }

      if(viewsheet != null) {
         viewsheet.writeXML(writer);
      }

      writer.println("</dashboard>");
   }

   /**
    * Builds a viewsheet dashboard from a xml element tag.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element node = Tool.getChildNodeByTagName(tag, "description");

      if(node != null) {
         this.description = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "createdBy");

      if(node != null) {
         this.createdBy = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "modifiedBy");

      if(node != null) {
         this.modifiedBy = Tool.getValue(node);
      }

      node = Tool.getChildNodeByTagName(tag, "created");

      if(node != null) {
         this.created = Long.parseLong(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "modified");

      if(node != null) {
         this.modified = Long.parseLong(Tool.getValue(node));
      }

      node = Tool.getChildNodeByTagName(tag, "entry");

      if(node != null) {
         if(viewsheet == null) {
            viewsheet = new ViewsheetEntry();
         }

         viewsheet.parseXML(node);
      }
   }

   private String description = null;
   private ViewsheetEntry viewsheet = null;
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
}
