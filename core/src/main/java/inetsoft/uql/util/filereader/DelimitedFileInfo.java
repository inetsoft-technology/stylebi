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
package inetsoft.uql.util.filereader;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Descriptor for a delimited text file.
 *
 * @author InetSoft Technology
 * @since  11.0
 */
public class DelimitedFileInfo implements TextFileInfo {
   /**
    * Creates a new instance of <tt>DelimitedFileInfo</tt>.
    */
   public DelimitedFileInfo() {
      // default constructor
   }

   /**
    * Gets the sequence of characters that separate fields in the file.
    *
    * @return the delimiter.
    */
   public final String getDelimiter() {
      return delimiter;
   }

   /**
    * Sets the sequence of characters that separate fields in the file.
    *
    * @param delimiter the delimiter.
    */
   public final void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   /**
    * Checks whether to remove quotations from the file.
    *
    * @return the delimiter.
    */
   public final boolean isRemoveQuotation() {
      return this.removeQuote;
   }

   /**
    * Sets the whether to remove quotations from the file.
    *
    * @param removeQuote remove quotation marks.
    */
   public final void setRemoveQuotation(boolean removeQuote) {
      this.removeQuote = removeQuote;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      delimiter = parseDelimiter(Tool.getChildValueByTagName(tag, "delimiter"));
      removeQuote = "true".equals(Tool.getChildValueByTagName(tag, "removeQuote"));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<info>");
      writer.format("<delimiter><![CDATA[%s]]></delimiter>%n", writeDelimiter(delimiter));
      writer.format("<removeQuote><![CDATA[%s]]></removeQuote>%n", this.removeQuote);
      writer.println("</info>");
   }

   /**
    * Parses a delimiter from its XML representation.
    *
    * @param xml the XML to parse.
    *
    * @return the delimiter.
    */
   public static String parseDelimiter(String xml) {
      StringBuilder delimiter = new StringBuilder();

      if(xml == null || xml.length() == 0) {
         delimiter.append(',');
      }
      else if("tab".equals(xml)) {
         delimiter.append('\t');
      }
      else {
         String[] chars = xml.split(",", 0);
         byte[] bytes = new byte[chars.length];

         for(int i = 0; i < chars.length; i++) {
            bytes[i] = chars[i] == null || chars[i].trim().length() == 0 ?
               0 : (byte) Integer.parseInt(chars[i].trim());
         }

         delimiter.append(new String(bytes));
      }

      return delimiter.toString();
   }

   /**
    * Gets the XML representation of a delimiter.
    *
    * @param delimiter the delimiter to write.
    *
    * @return the XML representation.
    */
   public static String writeDelimiter(String delimiter) {
      StringBuilder xml = new StringBuilder();
      byte[] bytes = null;

      if(delimiter == null || delimiter.length() == 0) {
         bytes = ",".getBytes();
      }
      else if(delimiter.charAt(0) == '\t') {
         xml.append("tab");
      }
      else {
         bytes = delimiter.getBytes();
      }

      if(bytes != null) {
         for(int i = 0; i < bytes.length; i++) {
            if(i > 0) {
               xml.append(',');
            }

            xml.append(((int) bytes[i]) & 0xff);
         }
      }

      return xml.toString();
   }

   private String delimiter = ",";
   private boolean removeQuote = true;
}
