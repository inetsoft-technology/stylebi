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
package inetsoft.report.io;

import inetsoft.report.*;
import inetsoft.report.internal.paging.ReportCache;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Builder is used to export a report to a file or import a report
 * from a file. A Builder can be created by calling one of the
 * getBuilder() method. To export a report, call the write() method.
 * To import a report, call the read() method.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Builder {
   /**
    * Portable Document Format (PDF) output. The PDF output contains all data
    * in the report. It is generated using PDFGenerator which supports the
    * generation of a Table of Contents in PDF.
    */
   public static final int PDF = 9;

   /**
    * Get the format type from a string. The string can be the file
    * extension or the name of the type.
    */
   public static int getType(String typename) {
      initExportTypeMap();
      Iterator iter = exportTypes.iterator();
      int type = PDF;

      while(iter.hasNext()) {
         ExportType exportType = (ExportType) iter.next();

         if(exportType.getFormatOption().equalsIgnoreCase(typename) ||
            matchTypeByExtension(typename, exportType))
         {
            type = exportType.getFormatId();
            break;
         }
      }

      if(type == PDF && !"pdf".equalsIgnoreCase(typename)) {
         type = -1;
      }

      return type;
   }

   private static boolean matchTypeByExtension(String typename, ExportType exportType) {
      if("xls".equalsIgnoreCase(typename) || "xlsx".equalsIgnoreCase(typename) ||
         "ppt".equalsIgnoreCase(typename) || "pptx".equalsIgnoreCase(typename))
      {
         if(typename.equalsIgnoreCase(exportType.getExtension()) ||
            typename.equalsIgnoreCase(exportType.getOldExtension()))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the base url.
    */
   public static URL getBaseURL() {
      return baseurl;
   }

   /**
    * Get the generator created in the builder. If the builder is created
    * for input, the generator is null.
    */
   public Generator getGenerator() {
      return generator;
   }

   /**
    * Set the embedded styles. This should be called before the
    * export() is performed. The specified styles will be embedded
    * in the report.
    * @param styles vector of XTableStyle.
    */
   public static void setEmbeddedStyles(Vector styles) {
      Builder.styles = styles;
   }

   /**
    * Gets the export type descriptor for the specified format type ID.
    *
    * @param type the format type ID.
    *
    * @return the descriptor for the specified type or <code>null</code> if the
    *         format type for the specified ID is undefined.
    */
   public static ExportType getExportType(int type) {
      initExportTypeMap();
      return exportTypeMap.get(type);
   }

   /**
    * Gets the IDs of the export formats that are supported.
    *
    * @return an array of integers that are the IDs of each supported format.
    */
   public static int[] getSupportedExportTypes() {
      initExportTypeMap();

      return exportTypes.stream()
         .filter(type -> {
            return type.isVisible() && !type.isForVSArchive();
         })
         .mapToInt(ExportType::getFormatId)
         .toArray();
   }

   /**
    * Initializes the export type map from the configuration file.
    */
   private static synchronized void initExportTypeMap() {
      if(exportTypeMap == null) {
         exportTypeMap = new HashMap<>();
         exportTypes = new ArrayList<>();

         try {
            InputStream input = Builder.class.
               getResourceAsStream("/inetsoft/report/io/builder.xml");
            Document doc = Tool.parseXML(input);
            Element root = doc.getDocumentElement();
            NodeList exporterList =
               Tool.getChildNodesByTagName(root, "exporter");

            for(int i = 0; i < exporterList.getLength(); i++) {
               Element exporterNode = (Element) exporterList.item(i);
               ExportType exportType = null;

               try {
                  exportType = new ExportType();

                  String prop = Tool.getAttribute(exporterNode, "formatId");
                  exportType.setFormatId(Integer.parseInt(prop));

                  prop = Tool.getAttribute(exporterNode, "index");
                  exportType.setIndex(Integer.parseInt(prop));

                  prop = Tool.getAttribute(exporterNode, "visible");
                  exportType.setVisible(!"false".equals(prop));

                  prop = Tool.getAttribute(exporterNode, "formatOption");
                  exportType.setFormatOption(prop);

                  prop = Tool.getAttribute(exporterNode, "extension");
                  exportType.setExtension(prop);

                  prop = Tool.getAttribute(exporterNode, "oldExtension");
                  exportType.setOldExtension(prop);

                  prop = Tool.getAttribute(exporterNode, "mimeType");
                  exportType.setMimeType(prop);

                  prop = Tool.getAttribute(exporterNode, "oldMimeType");
                  exportType.setOldMimeType(prop);

                  prop = Tool.getAttribute(exporterNode, "mailSupported");
                  exportType.setMailSupported(!"false".equals(prop));

                  prop = Tool.getAttribute(exporterNode, "exportSupported");
                  exportType.setExportSupported(!"false".equals(prop));

                  Element descNode = Tool.getChildNodeByTagName(exporterNode, "description");
                  prop = Tool.getValue(descNode);
                  exportType.setDescription(prop);

                  Element keyNode = Tool.getChildNodeByTagName(exporterNode, "designerKey");

                  if(keyNode != null) {
                     prop = Tool.getValue(keyNode);
                     exportType.setDesignerKey(prop);
                  }

                  prop = Tool.getAttribute(exporterNode, "actionClass");
                  exportType.setActionClass(prop);

                  prop = Tool.getAttribute(exporterNode, "factoryClass");

                  if(prop != null && prop.length() > 0) {
                     Class cls = Class.forName(prop);
                     exportType.setExportFactory((ExportFactory)
                                                 cls.newInstance());
                  }

                  NodeList sids = Tool.getChildNodesByTagName(
                     exporterNode, "supplementalId");

                  for(int j = 0; j < sids.getLength(); j++) {
                     Element idNode = (Element) sids.item(j);
                     prop = Tool.getAttribute(idNode, "formatId");
                     exportType.addSupplementalId(Integer.parseInt(prop));
                  }
               }
               catch(Exception exc2) {
                  LOG.error("Invalid content in export configuration file", exc2);
               }

               if(exportType != null) {
                  exportTypes.add(exportType);
                  exportTypeMap.put(exportType.getFormatId(), exportType);
                  Enumeration ids = exportType.getSupplementalIds();

                  while(ids.hasMoreElements()) {
                     exportTypeMap.put((Integer) ids.nextElement(), exportType);
                  }
               }
            }
         }
         catch(Exception exc) {
            LOG.error("Failed to parse export configuration file", exc);
         }

         Collections.sort(exportTypes, (t1, t2) -> {
            int result = 0;

            if(t1.getIndex() < t2.getIndex()) {
               result = -1;
            }
            else if(t1.getIndex() > t2.getIndex()) {
               result = 1;
            }

            return result;
         });
      }
   }

   /**
    * Set current thread parse report to build registry.
    */
   public static void setBuildRegistry(Boolean meta) {
      REGISTRY.set(meta);
   }

   /**
    * Check if current thread parse report to build registry.
    */
   public static boolean isBuildRegistry() {
      return REGISTRY.get();
   }

   /**
    * Set whether the current thread is reading an archived report (sro).
    */
   public static void setArchived(Boolean archived) {
      Builder.archived.set(archived);
   }

   /**
    * Check if the current thread is reading an archived report (sro).
    */
   public static boolean isArchived() {
      return archived.get();
   }

   /**
    * Set report cache for generator.
    */
   public void initCache(ReportCache cache, Object repId) {
      if(generator != null) {
         generator.setReportCache(cache);
         generator.setReportId(repId);
      }
   }

   public void cancel() {
      if(generator != null) {
         generator.cancel();
      }
   }

   private Generator generator = null;
   static Vector styles = new Vector(); // embedded styles
   static URL baseurl;
   private static HashMap<Integer, ExportType> exportTypeMap = null;
   private static ArrayList<ExportType> exportTypes = null;
   private static ThreadLocal<Boolean> REGISTRY = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static ThreadLocal<Boolean> archived = ThreadLocal.withInitial(() -> Boolean.FALSE);

   private static final Logger LOG =
      LoggerFactory.getLogger(Builder.class);
}
