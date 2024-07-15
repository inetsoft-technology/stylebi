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
package inetsoft.report.io.csv;

import inetsoft.sree.internal.HttpXMLSerializable;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.CSVConfigModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * Data transfer object that represents the {@link CSVConfig}
 */
public class CSVConfig implements Cloneable, Serializable, HttpXMLSerializable {
   /*
    * Create an empty object
    */
   public CSVConfig() {
   }

   public CSVConfig(String delimiter, String quote, boolean keepHeader, boolean tabDelimited) {
      this.delimiter = delimiter;
      this.quote = quote;
      this.keepHeader = keepHeader;
      this.tabDelimited = tabDelimited;
   }

   public CSVConfig(CSVConfigModel model) {
      if(model == null) {
         return;
      }

      this.delimiter = model.delimiter();
      this.quote = model.quote();
      this.keepHeader = model.keepHeader();
      this.tabDelimited = model.tabDelimited() != null && model.tabDelimited();

      if(model.selectedAssemblies() != null) {
         if(exportAssemblies == null) {
            exportAssemblies = new ArrayList<>();
         }

         for(String selectedAssembly : model.selectedAssemblies()) {
            if(selectedAssembly != null && !exportAssemblies.contains(selectedAssembly)) {
               exportAssemblies.add(selectedAssembly);
            }
         }
      }
   }
   /*
    * Get the delimiter
    */
   public String getDelimiter() {
      return delimiter;
   }

   /*
    * Set the delimiter
    */
   public void setDelimiter(String delimiter) {
      this.delimiter = delimiter;
   }

   /*
    * Get the quote string
    */
   public String getQuote() {
      return "".equals(quote) ? null : quote;
   }

   /*
    * Set the quote string
    */
   public void setQuote(String quote) {
      this.quote = quote;
   }

   /*
    * Check if the header should be kept
    */
   public boolean isKeepHeader() {
      return keepHeader;
   }

   /*
   * Set if the header should be kept
   */
   public void setKeepHeader(boolean keepHeader) {
      this.keepHeader = keepHeader;
   }

   /*
    * Check if the header should be kept
    */
   public boolean isTabDelimited() {
      return tabDelimited;
   }

   /*
   * Set if the header should be kept
   */
   public void setTabDelimited(boolean tabDelimited) {
      this.tabDelimited = tabDelimited;
   }

   public List<String> getExportAssemblies() {
      return exportAssemblies == null ? null : new ArrayList<>(exportAssemblies);
   }

   public void setExportAssemblies(List<String> exportAssemblies) {
      this.exportAssemblies = exportAssemblies;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
      }

      return new CSVConfig();
   }

   /**
    * Parse the replet action definition from xml.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      delimiter = tag.getAttribute("delimiter");
      delimiter = byteDecode(delimiter);
      quote = tag.getAttribute("quote");
      quote = byteDecode(quote);

      keepHeader = "true".equals(tag.getAttribute("keepHeader"));
      tabDelimited = "true".equals(tag.getAttribute("tabDelimited"));

      NodeList assembliesTag = Tool.getChildNodesByTagName(tag, "exportAssemblies");

      if(assembliesTag != null && assembliesTag.getLength() > 0) {
         if(exportAssemblies == null) {
            exportAssemblies = new ArrayList<>();
         }

         NodeList assemblies =
            Tool.getChildNodesByTagName(assembliesTag.item(0), "assembly");

         for(int i = 0; assemblies != null && i < assemblies.getLength(); i++) {
            Node assembly = assemblies.item(i);
            String assemblyName = Tool.getAttribute((Element) assembly, "name");

            if(assemblyName != null) {
               exportAssemblies.add(assemblyName);
            }
         }
      }
   }

   /**
    * Write itself to a xml file
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<CSVConfig");
      writer.print(" delimiter=\"" +
         Optional.of(Tool.encodeHTMLAttribute(byteEncode(delimiter))).orElse("") + "\"");
      writer.print(" quote=\"" +
         Optional.of(Tool.encodeHTMLAttribute(byteEncode(quote))).orElse("") + "\"");
      writer.print(" keepHeader=\"" + Boolean.toString(keepHeader) + "\"");
      writer.print(" tabDelimited=\"" + Boolean.toString(tabDelimited) + "\"");
      writer.println(">");

      if(exportAssemblies != null && exportAssemblies.size() > 0) {
         writer.print("<exportAssemblies>");

         for(String key : exportAssemblies) {
            writer.print("<assembly name=\"" + key + "\"/>");
         }

         writer.print("</exportAssemblies>");
      }
      writer.print("</CSVConfig>");
   }

   /**
    * Encode non-ascii characters to unicode enclosed in '[]'.
    * @param source source string.
    * @return encoded string.
    */
   @Override
   public String byteEncode(String source) {
      return encoding ? Tool.byteEncode2(source) : source;
   }

   /**
    * Convert the encoded string to the original unencoded string.
    * @param encString a string encoded using the byteEncode method.
    * @return original string.
    */
   @Override
   public String byteDecode(String encString) {
      return encoding ? Tool.byteDecode(encString) : encString;
   }

   /**
    * Check if this object should encoded when writing.
    * @return <code>true</code> if should encoded, <code>false</code> otherwise.
    */
   @Override
   public boolean isEncoding() {
      return encoding;
   }

   /**
    * Set encoding flag.
    * @param encoding true to encode.
    */
   @Override
   public void setEncoding(boolean encoding) {
      this.encoding = encoding;
   }

   private String delimiter = ",";
   private String quote = null;
   private boolean keepHeader = true;
   private boolean tabDelimited = false;
   private transient boolean encoding = false;
   private List<String> exportAssemblies;

   private static final Logger LOG = LoggerFactory.getLogger(CSVConfig.class);
}
