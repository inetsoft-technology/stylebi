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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.AssetObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * File format info contains format information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class FileFormatInfo implements AssetObject {
   /**
    * Set export type of Excel.
    */
   public static final int EXPORT_TYPE_EXCEL = 0;
   /**
    * Set export type of PowerPoint.
    */
   public static final int EXPORT_TYPE_POWERPOINT = 1;
   /**
    * Set export type of Excel.
    */
   public static final int EXPORT_TYPE_PDF = 2;
   /**
    * Set export type of Excel.
    */
   public static final int EXPORT_TYPE_SNAPSHOT = 3;
   /**
    * Set export type of PNG.
    */
   public static final int EXPORT_TYPE_PNG = 4;
   /**
    * Set export type of HTML.
    */
   public static final int EXPORT_TYPE_HTML = 5;
   /**
    * Set export type of HTML.
    */
   public static final int EXPORT_TYPE_CSV = 6;
   /**
    * Set export name of Excel.
    */
   public static final String EXPORT_NAME_EXCEL = "Excel";
   /**
    * Set export name of PowerPoint.
    */
   public static final String EXPORT_NAME_POWERPOINT = "PowerPoint";
   /**
    * Set export name of Excel.
    */
   public static final String EXPORT_NAME_PDF = "PDF";
   /**
    * Set export name of Excel.
    */
   public static final String EXPORT_NAME_SNAPSHOT = "Snapshot";
   /**
    * Set export name of PNG.
    */
   public static final String EXPORT_NAME_PNG = "PNG";
   /**
    * Set export name of HTML.
    */
   public static final String EXPORT_NAME_HTML = "HTML";
   /**
    * Set export name of HTML.
    */
   public static final String EXPORT_NAME_CSV = "CSV";
   /**
    * Set export name of all.
    */
   public static final String[] EXPORT_ALL_NAMES = {
      EXPORT_NAME_EXCEL, EXPORT_NAME_POWERPOINT, EXPORT_NAME_PDF, EXPORT_NAME_SNAPSHOT,
      EXPORT_NAME_PNG, EXPORT_NAME_HTML, EXPORT_NAME_CSV
   };
   /**
    * Constructor.
    */
   public FileFormatInfo() {
      super();
   }

   /**
    * Get the format type.
    * @return the format type of the file.
    */
   public int getFormat() {
      return formatType;
   }

   /**
    * Set the format type.
    * @param type the specified format type.
    */
   public void setFormat(int type) {
      this.formatType = type;
   }

   /**
    * If including the current view.
    * @return selection of current value.
    */
   public boolean isIncludingCurrent() {
      return includingCurrent;
   }

   /**
    * Set the selection of current view.
    * @param selected the selection of current view.
    */
   public void setIncludingCurrent(boolean selected) {
      this.includingCurrent = selected;
   }

   /**
    * If match the layout.
    * @return selection of match layout.
    */
   public boolean isMatchLayout() {
      return match;
   }

   /**
    * Set the selection of match layout.
    * @param matched the selection of match layout.
    */
   public void setMatchLayout(boolean matched) {
      this.match = matched;
   }

   /**
    * Get all the bookmarks.
    * @return the bookmarks of the viewsheet.
    */
   public String[] getSelectedBookmarks() {
      return bookmarks;
   }

   /**
    * Set the bookmarks.
    * @param marks the specified bookmarks.
    */
   public void setSelectedBookmarks(String[] marks) {
      this.bookmarks = marks;
   }

   /**
    * Get the string representation.
    * @return the string representaion of this assembly info.
    */
   public String toString() {
      return super.toString() + "[" + formatType + ", " + includingCurrent +
         ", " + match + ", " + Tool.arrayToString(bookmarks) + "]";
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         FileFormatInfo info =
            (FileFormatInfo) super.clone();

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone FileFormatInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print(" formatType=\"" + formatType + "\"");
      writer.print(" includingCurrent=\"" + includingCurrent + "\"");
      writer.print(" match=\"" + match + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseAttributes(Element elem) {
      formatType = Integer.parseInt(Tool.getAttribute(elem, "formatType"));
      includingCurrent =
         "true".equals(Tool.getAttribute(elem, "includingCurrent"));
      match =
         "true".equals(Tool.getAttribute(elem, "match"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(bookmarks != null && bookmarks.length > 0) {
         writer.print("<bookmarks>");

         for(int i = 0; i < bookmarks.length; i++) {
            writer.print("<bookmark>");
            writer.print("<![CDATA[" + bookmarks[i] + "]]>");
            writer.print("</bookmark>");
         }

         writer.println("</bookmarks>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      Element bookmarksNode =
         Tool.getChildNodeByTagName(elem, "bookmarks");

      if(bookmarksNode != null) {
         NodeList bookmarksList =
            Tool.getChildNodesByTagName(bookmarksNode, "bookmark");

         if(bookmarksList != null && bookmarksList.getLength() > 0) {
            bookmarks = new String[bookmarksList.getLength()];

            for(int i = 0; i < bookmarksList.getLength(); i++) {
               bookmarks[i] = Tool.getValue(bookmarksList.item(i));
            }
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<fileFormatInfo class=\"" + getClass().getName()+ "\" ");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.print("</fileFormatInfo>");
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

   private int formatType = -1;
   private boolean includingCurrent = true;
   private boolean match = true;
   private String[] bookmarks;

   private static final Logger LOG =
      LoggerFactory.getLogger(FileFormatInfo.class);
}
