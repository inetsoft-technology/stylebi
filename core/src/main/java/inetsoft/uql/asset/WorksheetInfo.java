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
package inetsoft.uql.asset;

import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * WorksheetInfo stores worksheet properties.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class WorksheetInfo implements AssetObject {
   /**
    * Constructor.
    */
   public WorksheetInfo() {
      String prop = SreeEnv.getProperty("asset.sample.maxrows");

      if(prop.length() > 0) {
         try {
            inputmax = Integer.parseInt(prop);
         }
         catch(Exception ex) {
            LOG.error("Invalid integer value for the maximum " +
               "number of sample rows property (asset.sample.maxrows): " + prop,
               ex);
         }
      }

      previewMaxRow = Util.getQueryPreviewMaxrow();
   }

   /**
    * Get the maximum rows of detail table for design mode.
    */
   public int getDesignMaxRows() {
      return inputmax;
   }

   /**
    * Set the maximum rows of detail table for design mode. The max rows is
    * applied to the detail table to limit the data used for queries.
    */
   public void setDesignMaxRows(int max) {
      this.inputmax = max;
   }

   /**
    * Get the maximum rows for preview.
    */
   public int getPreviewMaxRow() {
      return Util.getQueryLocalPreviewMaxrow(previewMaxRow);
   }

   /**
    * Set the maximum rows for preview.
    */
   public void setPreviewMaxRow(int max) {
      this.previewMaxRow = max;
   }

   /**
    * Get the alias of the worksheet entry.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set the alias of the worksheet entry.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the description of the worksheet entry.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Set the description of the worksheet entry.
    * @param description
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Get the selected message levels of the worksheet entry.
    */
   public String[] getMessageLevels() {
      return messageLevels;
   }

   /**
    * Set the selected message levels of the worksheet entry.
    */
   public void setMessageLevels(String[] messageLevels) {
      this.messageLevels = messageLevels;
   }

   /**
    * Whether ws is default mode.
    */
   public boolean isDefaultMode() {
      return this.worksheetMode == WorksheetMode.DEFAULT;
   }

   /**
    * Whether ws is single query mode.
    */
   public boolean isSingleQueryMode() {
      return this.worksheetMode == WorksheetMode.SINGLE_QUERY;
   }

   /**
    * Set ws to single query mode.
    */
   public void setSingleQueryMode() {
      this.worksheetMode = WorksheetMode.SINGLE_QUERY;
   }

   /**
    * Whether ws is mashup mode.
    */
   public boolean isMashupMode() {
      return this.worksheetMode == WorksheetMode.MASHUP;
   }

   /**
    * Set ws to mashup mode.
    */
   public void setMashupMode() {
      this.worksheetMode = WorksheetMode.MASHUP;
   }

   /**
    * Copy the properties.
    * @return true if the property change should trigger a worksheet
    * re-execution.
    */
   public boolean copyInfo(WorksheetInfo info) {
      boolean rc = inputmax != info.inputmax || previewMaxRow != info.previewMaxRow;
      this.inputmax = info.inputmax;
      this.previewMaxRow = info.previewMaxRow;
      this.alias = info.getAlias();
      this.description = info.getDescription();
      return rc;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<worksheetInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</worksheetInfo>");
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" inputmax=\"" + inputmax + "\"");

      StringBuilder levels = new StringBuilder();

      for(int i = 0; i < messageLevels.length; i++) {
         levels.append(i != messageLevels.length - 1 ? messageLevels[i] + "," : messageLevels[i]);
      }

      writer.print(" messageLevels=\"" + levels + "\"");
      writer.print(" worksheetMode=\"" + worksheetMode + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      if(Tool.getAttribute(elem, "inputmax") != null) {
         this.inputmax = Integer.parseInt(Tool.getAttribute(elem, "inputmax"));
      }

      if(Tool.getAttribute(elem, "messageLevels") != null) {
         this.messageLevels = Tool.getAttribute(elem, "messageLevels").split(",");
      }

      if(Tool.getAttribute(elem, "worksheetMode") != null) {
         this.worksheetMode = Integer.parseInt(Tool.getAttribute(elem, "worksheetMode"));
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
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
    * Get the string representation.
    * @return the string representation of this assembly info.
    */
   public String toString() {
      return super.toString() + "[" + inputmax + "]";
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private static class WorksheetMode {
      public static final int DEFAULT = 0;
      public static final int SINGLE_QUERY = 1;
      public static final int MASHUP = 2;
   }

   private int inputmax;
   private int previewMaxRow;
   private String alias;
   private String description;
   private String[] messageLevels = new String[] {"Error", "Warning", "Info"};
   private int worksheetMode = WorksheetMode.DEFAULT;

   private static final Logger LOG =
      LoggerFactory.getLogger(WorksheetInfo.class);
}
