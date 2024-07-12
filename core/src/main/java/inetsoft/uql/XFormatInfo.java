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
package inetsoft.uql;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.Format;
import java.time.Duration;
import java.util.Date;

/**
 * XFormatInfo represents a column's format information.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XFormatInfo implements XMLSerializable, Serializable, Cloneable {
   /**
    * Constructor.
    */
   public XFormatInfo() {
   }

   /**
    * Constructor.
    */
   public XFormatInfo(String format, String formatSpec) {
      this.format = format;
      this.formatSpec = formatSpec;
   }

   public XFormatInfo(Format fmt) {
      TableFormat parser = new TableFormat();
      parser.setFormat(fmt);
      setFormat(parser.format);
      setFormatSpec(parser.format_spec);
   }

   /**
    * Format setter.
    */
   public void setFormat(String format) {
      this.format = format;
   }

   /**
    * Format getter.
    */
   public String getFormat() {
      return format;
   }

   /**
    * FormatSpec setter.
    */
   public void setFormatSpec(String formatSpec) {
      this.formatSpec = formatSpec;
   }

   /**
    * FormatSpec getter.
    */
   public String getFormatSpec() {
      return formatSpec;
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      format = Tool.getChildValueByTagName(tag, "format");
      formatSpec = Tool.getChildValueByTagName(tag, "formatSpec");

      if("null".equals(formatSpec)) {
         formatSpec = null;
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<XFormatInfo>");

      if(format != null) {
         writer.print("<format>");
         writer.print("<![CDATA[" + format + "]]>");
         writer.print("</format>");
         writer.print("<formatSpec>");
         writer.print("<![CDATA[" + formatSpec + "]]>");
         writer.print("</formatSpec>");
      }

      writer.println("</XFormatInfo>");
   }

   /**
    * Check if equals another object.
    * @param obj the specified opject to compare.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XFormatInfo)) {
         return false;
      }

      XFormatInfo info = (XFormatInfo) obj;

      return Tool.equals(this.format, info.format) &&
             Tool.equals(this.formatSpec, info.formatSpec);
   }

   /**
    * Get hash code.
    * @return hash code.
    */
   public int hashCode() {
      int hash = format == null ? 0 : format.hashCode();
      hash += formatSpec == null ? 0 : formatSpec.hashCode();
      return hash;
   }

   /**
    * Get a cloned object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      return new XFormatInfo(format, formatSpec);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      String result = null;
      Format fmt = TableFormat.getFormat(format, formatSpec);

      if(format == null) {
         return Catalog.getCatalog().getString("none");
      }
      else {
         if(format.equals(TableFormat.DATE_FORMAT)) {
            result = fmt.format(new Date());
         }
         else if(format.equals(TableFormat.DURATION_FORMAT) ||
            format.equals(TableFormat.DURATION_FORMAT_PAD_NON))
         {
            Duration duration = Duration.parse("P1DT12H30M30.5S");
            result = fmt.format(duration.toMillis());
         }
         else if(format.equals(TableFormat.MESSAGE_FORMAT)) {
            if(formatSpec != null) {
               result = format + ": " + formatSpec;
            }
            else {
               result = format;
            }
         }
         else if(format.equals(TableFormat.DECIMAL_FORMAT)) {
            result = "##.00%".equals(formatSpec) || "##.##\"%\"".equals(formatSpec) ?
               fmt.format(1) : fmt.format(100000);
         }
         else if(format.equals(TableFormat.CURRENCY_FORMAT)) {
            result = fmt.format(100);
         }
         else if(format.equals(TableFormat.PERCENT_FORMAT)) {
            result = fmt.format(0.10);
         }
      }

      return result == null ? Catalog.getCatalog().getString("none") : result;
   }

   /**
    * Check if this format info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return format == null;
   }

   private String format;
   private String formatSpec;
}
