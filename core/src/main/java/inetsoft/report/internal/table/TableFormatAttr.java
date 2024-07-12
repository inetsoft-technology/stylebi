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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.*;
import inetsoft.uql.XConstants;
import inetsoft.util.IteratorEnumeration;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * Table format attr contains table format attributes.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class TableFormatAttr extends TableAttr {
   /**
    * Check if a data path in a table data descriptor supports table format.
    * @param table the specified table lens
    * @param dpath the specified data path
    * @return true if supports, false otherwise
    */
   public static boolean supportsFormat(TableLens table, TableDataPath dpath) {
      // is cell data path?
      if(!dpath.isRow() && !dpath.isCol()) {
         return true;
      }
      // is row data path and is not crosstab?
      else if(dpath.isRow() && table.getDescriptor().getType() !=
              TableDataDescriptor.CROSSTAB_TABLE)
      {
         return true;
      }
      // is col data path and is not crosstab?
      else if(dpath.isCol() && table.getDescriptor().getType() !=
              TableDataDescriptor.CROSSTAB_TABLE)
      {
         return true;
      }

      return false;
   }

   /**
    * Create a table format attr.
    */
   public TableFormatAttr() {
      fmtmap = new Hashtable();
      rfmtmap = new Hashtable();
   }

   /**
    * Create filter from a table lens. TableAttr will apply the attributes
    * to the created filter.
    * @param table the base table lens
    */
   @Override
   public TableLens createFilter(TableLens table) {
      return fmtmap.size() != 0 || rfmtmap.size() != 0 || table.containsFormat() ?
         new FormatTableLens2(table) : table;
   }

   /**
    * Check if is null.
    */
   public boolean isNull() {
      return fmtmap.size() == 0 && rfmtmap.size() == 0;
   }

   /**
    * Set table format.
    * @param tpath the specified table data path
    * @param tfmt the specified table format
    */
   public void setFormat(TableDataPath tpath, TableFormat tfmt) {
      setFormat(tpath, tfmt, false);
   }

   /**
    * Set table format.
    * @param tpath the specified table data path
    * @param tfmt the specified table format
    * @param runtime true if is a runtime format, which will not be stored
    */
   public void setFormat(TableDataPath tpath, TableFormat tfmt, boolean runtime) {
      if(tfmt != null) {
         tfmt = tfmt.isDefault() ? null : tfmt;
      }

      Map map = runtime ? rfmtmap : fmtmap;

      if(tfmt == null) {
         map.remove(tpath);
      }
      else {
         map.put(tpath, tfmt);
      }

      // the design time format reset?
      // clear runtime format, it is out of sync
      // fix bug1294643629423
      if(!runtime) {
         rfmtmap.remove(tpath);
      }
   }

   /**
    * Check if is runtime format.
    * @param tpath the format data path.
    * @return <tt>true</tt> if is runtime format, <tt>false</tt> otherwise.
    */
   public boolean isRuntimeFormat(TableDataPath tpath) {
      return rfmtmap.containsKey(tpath);
   }

   /**
    * Get table format.
    */
   public TableFormat getFormat(TableDataPath tpath) {
      TableFormat fmt = rfmtmap.get(tpath);

      if(fmt != null) {
         return fmt;
      }

      return fmtmap.get(tpath);
   }

   /**
    * Get table format, this is not same as getForamt(TableDataPath tpath),
    * this function will return the specified format only, for runtime is
    * true, return runtime only even the runtime is null, if runtime is false,
    * return non-runtime format only even the runtime is not null.
    */
   public TableFormat getFormat(TableDataPath tpath, boolean runtime) {
      Map map = runtime ? rfmtmap : fmtmap;
      return (TableFormat) map.get(tpath);
   }

   public Set<TableDataPath> getRuntimeDataPaths() {
      return rfmtmap.keySet();
   }

   public Set<TableDataPath> getDataPaths() {
      return fmtmap.keySet();
   }

   /**
    * Get all the keys.
    */
   public Enumeration getAllDataPaths() {
      Set set = new HashSet();
      set.addAll(rfmtmap.keySet());
      set.addAll(fmtmap.keySet());
      return new IteratorEnumeration(set.iterator());
   }

   /**
    * Get all the values.
    */
   public Enumeration getAllFormats() {
      List list = new ArrayList();
      list.addAll(rfmtmap.values());
      list.addAll(fmtmap.values());
      return new IteratorEnumeration(list.iterator());
   }

   /**
    * Clear the table format attr.
    */
   public void clear() {
      clear(false);
   }

   /**
    * Clear the table format attr.
    */
   public void clear(boolean runtimeOnly) {
      rfmtmap.clear();

      if(!runtimeOnly) {
         fmtmap.clear();
      }
   }

   /**
    * Set locale for format.
    * @param loc the specified locale
    */
   public void setLocale(Locale loc) {
      this.loc = loc;
   }

   /**
    * Get locale for format.
    * @return the locale
    */
   public Locale getLocale() {
      return loc;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tableFormatAttr>");

      for(TableDataPath tpath : fmtmap.keySet()) {
         TableFormat tfmt = fmtmap.get(tpath);
         writer.println("<aTableFormat>");
         tpath.writeXML(writer);
         tfmt.writeXML(writer);
         writer.println("</aTableFormat>");
      }

      writer.println("</tableFormatAttr>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(tag, "aTableFormat");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         Element pathnode = Tool.getChildNodeByTagName(node, "tableDataPath");
         Element fmtnode = Tool.getChildNodeByTagName(node, "tableFormat");

         TableDataPath path = new TableDataPath();
         TableFormat fmt = new TableFormat();
         path.parseXML(pathnode);
         fmt.parseXML(fmtnode);
         setFormat(path, fmt);
      }
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      TableFormatAttr attr2 = new TableFormatAttr();
      attr2.fmtmap = Tool.deepCloneMap(fmtmap);
      attr2.rfmtmap = Tool.deepCloneMap(rfmtmap);
      return attr2;
   }

   /**
    * FormatTableLens is used to apply table format.
    */
   public class FormatTableLens2 extends FormatTableLens {
      /**
       * Create a format table lens.
       */
      public FormatTableLens2(TableLens table) {
         super(table);
          loc = loc == null ? Locale.getDefault() : loc;
      }

      @Override
      public Format getCellFormat(int r, int c) {
         return getCellFormat(r, c, false);
      }

      @Override
      public Format getCellFormat(int r, int c, boolean cellOnly) {
         return super.getCellFormat(r, c, cellOnly);
      }

      /**
       * Get the locale.
       * @return the locale.
       */
      @Override
      protected final Locale getLocale() {
         return loc;
      }

      /**
       * Get the format map.
       * @return the format map.
       */
      @Override
      protected final Map<TableDataPath, TableFormat> getFormatMap() {
         if(map != null) {
            return map;
         }

         int rsize = rfmtmap.size();

         if(rsize == 0) {
            map = Tool.deepCloneMap(fmtmap);
         }
         else {
            map = Tool.deepCloneMap(rfmtmap);
            Iterator keys = fmtmap.keySet().iterator();

            while(keys.hasNext()) {
               Object key = keys.next();

               if(!map.containsKey(key)) {
                  TableFormat format = fmtmap.get(key);

                  if(format != null) {
                     format = (TableFormat) format.clone();
                  }

                  map.put(key, format);
               }
            }
         }

         return map;
      }

      @Override
      public synchronized void invalidate() {
         map = null;
         super.invalidate();
      }

      private Map map;
   }

   private Hashtable<TableDataPath, TableFormat> fmtmap; // format map
   private Hashtable<TableDataPath, TableFormat> rfmtmap; // runtime format map
   private Locale loc; // locale
}
