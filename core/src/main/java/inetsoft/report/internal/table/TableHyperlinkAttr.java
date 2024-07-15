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
package inetsoft.report.internal.table;

import inetsoft.report.*;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.GroupedTable;
import inetsoft.report.internal.BindableElement;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.BindingTool;
import inetsoft.report.lens.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * Table hyperlink attr contains table hyperlink attributes.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class TableHyperlinkAttr extends TableAttr {
   /**
    * Check if a data path in a table data descriptor supports table hyperlink.
    * @param table the specified table lens
    * @param dpath the specified data path
    * @return true if supports, false otherwise
    */
   public static boolean supportsHyperlink(TableLens table,
                                           TableDataPath dpath) {
      // is cell data path?
      if(!dpath.isRow() && !dpath.isCol()) {
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
    * Get available parameters of a table cell.
    * @param table the specified table lens
    * @param row the specified row, only useful for crosstab
    * @param col the specified cell
    */
   public static String[] getAvailableParameters(TableLens table, int row, int col) {
      int type = table.getDescriptor().getType();

      if(type == TableDataDescriptor.CROSSTAB_TABLE) {
         // @by billh, dangerous operation!!! Precondition is base row/col
         // index won't be changed, the condition is satisfied at present, but
         // the precondition is relatively fragile...
         // @by larryl, if header rows are inserted to the crosstab,
         // we need to map the rows to the crosstab row. This means the
         // inserted header rows use the same value as header rows
         CrossFilter crosstab = Util.getCrossFilter(table);
         row = TableTool.getBaseRowIndex(table, crosstab, row);

         if(row < 0) {
            row = 0;
         }

         String[] flds = crosstab.getAvailableFields(row, col);

         for(int i = 0; i < flds.length; i++) {
            int index = flds[i].indexOf("_");
            flds[i] = flds[i].substring(index + 1);
         }

         return flds;
      }
      else if(type == TableDataDescriptor.CALC_TABLE) {
         CalcTableLens calc = (CalcTableLens)
            Util.getNestedTable(table, CalcTableLens.class);

         return calc.getCellNames();
      }
      else {
         Vector<String> params = new Vector<>();

         for(int i = 0; i < table.getColCount(); i++) {
            String header = Util.getHeader(table, i).toString();

            if(!header.startsWith(BindingTool.FAKE_PREFIX)) {
               params.add(header);
            }
         }

         // find hidden columns
         try {
            while(table instanceof TableFilter) {
               TableLens base = ((TableFilter) table).getTable();

               if(((TableFilter) table).getBaseRowIndex(1) != 1) {
                  break;
               }

               table = base;

               for(int i = 0; i < table.getColCount(); i++) {
                  String hdr = Util.getHeader(table, i).toString();

                  if(params.contains(hdr)) {
                     continue;
                  }

                  params.add(hdr);
               }
            }
         } catch(Exception ex) {
            // just in case
            LOG.warn("Failed to get hidden columns", ex);
         }

         return params.toArray(new String[0]);
      }
   }

   /**
    * Create a table hyperlink attr.
    */
   public TableHyperlinkAttr() {
      this.hlmap = new HashMap<>();
   }

   /**
    * Create filter from a table lens. TableAttr will apply the attributes
    * to the created filter.
    * @param table the base table lens
    */
   @Override
   public TableLens createFilter(TableLens table) {
      return hlmap.size() == 0 ? table : new HyperlinkTableLens(table);
   }

   /**
    * Check if is null.
    */
   public boolean isNull() {
      return hlmap.size() == 0;
   }

   /**
    * Get hyperlink at the specified table data path.
    */
   public Hyperlink getHyperlink(TableDataPath dpath) {
      return hlmap.get(dpath);
   }

   /**
    * Set hyperlink at the specified table data path.
    */
   public void setHyperlink(TableDataPath dpath, Hyperlink link) {
      if(link == null) {
         hlmap.remove(dpath);
      }
      else {
         hlmap.put(dpath, link);
      }
   }

   /**
    * Get all the keys.
    */
   public Enumeration<TableDataPath> getAllDataPaths() {
      return new IteratorEnumeration<>(hlmap.keySet().iterator());
   }

   /**
    * Get all the values.
    */
   public Enumeration<Hyperlink> getAllHyperlinks() {
      return new IteratorEnumeration<>(hlmap.values().stream().filter(a -> a != null).iterator());
   }

   /**
    * Get the map.
    */
   public Map<TableDataPath, Hyperlink> getHyperlinkMap() {
      return hlmap;
   }

   /**
    * Sync the table hyperlink attr.
    */
   public void sync(List<TableDataPath> paths, TableLens table) {
      sync(paths, table, true);
   }

   /**
    * Sync the table hyperlink attr.
    */
   public void sync(List<TableDataPath> paths, TableLens table, boolean removal) {
      int type = table.getDescriptor().getType();
      List<TableDataPath> keys = new ArrayList<>(hlmap.keySet());
      List<TableDataPath> rmlist = new ArrayList<>();

      for(TableDataPath path : keys) {
         // @by larryl, for crosstab, keep all header cell setting since the
         // cells are dynamic and can be set in live edit mode
         if(type == TableDataDescriptor.CROSSTAB_TABLE &&
            !path.isRow() && !path.isCol() &&
            path.getType() == TableDataPath.HEADER)
         {
            continue;
         }

         if(!paths.contains(path)) {
            // @by larryl, we allow a detail cell path to remain if there is
            // a group cell of the same path. This is for the detail cell
            // of grouped table with in-place style, where the detail cell
            // may not be on the meta data table
            if(path.getType() == TableDataPath.DETAIL) {
               TableDataPath group = new TableDataPath(
                  0, TableDataPath.GROUP_HEADER, path.getDataType(),
                  path.getPath());

               if(paths.contains(group)) {
                  continue;
               }
            }
            else if(path.getType() == TableDataPath.SUMMARY) {
               TableDataPath path2 = findPathByName(path, paths);

               if(path2 != null && !hlmap.containsKey(path2)) {
                  Hyperlink obj = hlmap.get(path);

                  if(obj != null) {
                     hlmap.put(path2, obj);
                  }
               }
            }

            rmlist.add(path);
         }
      }

      for(TableDataPath rmpath : rmlist) {
         hlmap.remove(rmpath);
      }
   }

   /**
    * Clear the table hyperlink attr.
    */
   public void clear() {
      hlmap.clear();
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tableHyperlinkAttr>");

      for(TableDataPath tpath : hlmap.keySet()) {
         Hyperlink link = hlmap.get(tpath);

         // should not serialize the script set hyperlink.
         if(link != null && link.isScriptCreated()) {
            continue;
         }

         if(link != null) {
            writer.println("<aHyperlink>");
            tpath.writeXML(writer);
            link.writeXML(writer);
            writer.println("</aHyperlink>");
         }
      }

      writer.println("</tableHyperlinkAttr>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList hnodes = Tool.getChildNodesByTagName(tag, "aHyperlink");

      for(int i = 0; i < hnodes.getLength(); i++) {
         Element ahnode = (Element) hnodes.item(i);
         Element pathnode = Tool.getChildNodeByTagName(ahnode, "tableDataPath");
         Element linknode = Tool.getChildNodeByTagName(ahnode, "Hyperlink");
         TableDataPath dpath = new TableDataPath();
         Hyperlink link = new Hyperlink();
         dpath.parseXML(pathnode);
         link.parseXML(linknode);

         hlmap.put(dpath, link);
      }
   }

   /**
    * Clone the object.
    */
   @Override
   public TableHyperlinkAttr clone() {
      TableHyperlinkAttr attr2 = new TableHyperlinkAttr();
      attr2.hlmap = Tool.deepCloneMap(hlmap);
      return attr2;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object to compare.
    * @return <tt>true</tt> if equals the specified object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableHyperlinkAttr)) {
         return false;
      }

      TableHyperlinkAttr tattr2 = (TableHyperlinkAttr) obj;

      return hlmap.equals(tattr2.hlmap);
   }

   public String toString() {
      return hlmap.toString();
   }

   /**
    * HyperlinkTableLens is used to apply hyperlink.
    */
   private class HyperlinkTableLens extends AttributeTableLens
      implements CachedTableLens
   {
      /**
       * Create a hyperlink table lens.
       */
      public HyperlinkTableLens(TableLens table) {
         super(table);
      }

      /**
       * Check if contains hyperlink definitation.
       */
      @Override
      public boolean containsLink() {
         if(!TableHyperlinkAttr.this.isNull()) {
            return true;
         }

         return super.containsLink();
      }

      /**
       * Get table hyperlink at a table cell.
       * @param row the specified row
       * @param col the specified col
       * @return table hyperlink if any, null otherwise
       */
      @Override
      public synchronized Hyperlink.Ref getHyperlink(int row, int col) {
         checkInit();
         Object link = cellcache.get(row, col);

         if(link == SparseMatrix.NULL) {
            link = getHyperlink0(row, col);
            cellcache.set(row, col, link);
         }

         return (Hyperlink.Ref) link;
      }

      /**
       * Invalidate the table filter forcely, and the table filter will perform
       * filtering calculation to validate itself.
       */
      @Override
      public synchronized void invalidate() {
         inited = false;
         crosstab = null;
         super.invalidate();
      }

      /**
       * Set a cell value.
       */
      @Override
      public void setObject(int r, int c, Object val) {
         // @by billh, don't use cache
         table.setObject(r, c, val);
      }

      /**
       * Clear all cached data.
       */
      @Override
      public synchronized void clearCache() {
         colcache = null;
         cellcache = null;
      }

      /**
       * Check if inited.
       */
      private synchronized void checkInit() {
         // @by larryl, the colcache and cellcache may be cleared by clearCache
         // after the init() call to conserve memory. In that case, we need
         // to recreate the cache if it's used again. Since clearCache is only
         // called after the whole report finish processing, the chance of
         // recreating the cache is remote but it is still possible.

         // @by mikec, when invalidated the inited flag be set to false,
         // in this case we should recreate the cache so that the hyperlink
         // will be recalculated.
         if(colcache == null || !inited) {
            colcache = new FixedSizeSparseMatrix();
         }

         if(cellcache == null || !inited) {
            cellcache = new FixedSizeSparseMatrix();
         }

         if(!inited) {
            inited = true;
            descriptor = getDescriptor();

            if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
               crosstab = Util.getCrossFilter(this);
               return;
            }

            paths = new TableDataPath[getTable().getColCount()][];
            final int pathsLength = paths.length;
            List<List<TableDataPath>> plist = new ArrayList<>(pathsLength);
            cpath = new TableDataPath[getTable().getColCount()];

            for(int i = 0; i < pathsLength; i++) {
               plist.add(new ArrayList<>());
            }

            for(TableDataPath path : hlmap.keySet()) {
               // col data path?
               if(path.isCol()) {
                  int[] cols = getColumns(getTable(), path.getPath()[0]);

                  if(cols != null) {
                     for(int col : cols) {
                        if(col < cpath.length) {
                           cpath[col] = path;
                        }
                     }
                  }
               }
               // cell data path?
               else {
                  int[] cols = getColumns(getTable(), path.getPath()[0]);

                  if(cols != null) {
                     for(int col : cols) {
                        if(col < pathsLength) {
                           plist.get(col).add(path);
                        }
                     }
                  }
               }
            }

            for(int i = 0; i < pathsLength; i++) {
               paths[i] = plist.get(i).toArray(new TableDataPath[0]);
            }
         }
      }

      /**
       * Get table hyperlink at a table cell.
       * @param row the specified row
       * @param col the specified col
       * @return table hyperlink if any, null otherwise
       */
      private synchronized Hyperlink.Ref getHyperlink0(int row, int col) {
         TableDataPath path = getCellDataPath(row, col);
         Hyperlink link = path == null ? null : hlmap.get(path);

         // check if col hyperlink exists when cell hyperlink doesn't exist
         link = link != null ? link : getColHyperlink(col);

         if(link == null) {
            return null;
         }

         Hyperlink.Ref ref = null;
         int type = descriptor.getType();

         if(type == TableDataDescriptor.CROSSTAB_TABLE) {
            // @by billh, dangerous operation!!! Precondition is base row/col
            // index won't be changed, the condition is satisfied at present,
            // but the precondition is relatively fragile...
            // @by larryl, if header rows are inserted to the crosstab,
            // we need to map the rows to the crosstab row. This means the
            // inserted header rows use the same value as header rows
            row = TableTool.getBaseRowIndex(getTable(), crosstab, row);

            if(row < 0) {
               row = 0;
            }

            Map<Object, Object> map = crosstab.getKeyValuePairs(row, col, null);

            ref = new Hyperlink.Ref(link, map);
         }
         else if(type == TableDataDescriptor.CALC_TABLE) {
            RuntimeCalcTableLens calc = (RuntimeCalcTableLens)
               Util.getNestedTable(table, RuntimeCalcTableLens.class);

            // design time has no RuntimeCalcTableLens
            if(calc != null) {
               Map map = calc.getKeyValuePairs(row, col, null);

               // @by larryl, if the calc table has not name defined, it is
               // likely an embedded table, so we use the column header
               if(map.size() > 0) {
                  ref = new Hyperlink.Ref(link, map);
               }
               else {
                  ref = new Hyperlink.Ref(link, calc, row, col);
               }
            }
         }
         else {
            // find the data table
            TableLens lens = table;

            // @by larryl, the column header in freehand and header row tables
            // are meaning less for hyperlink, should get the real header
            while(lens instanceof HeaderRowTableLens) {
               TableFilter filter = (TableFilter) lens;
               row = filter.getBaseRowIndex(row);
               lens = filter.getTable();
            }

            ref = new Hyperlink.Ref(link, lens, row, col);
         }

         return ref;
      }

      /**
       * Get table data path stored in table hyperlink map of the table cell.
       * @param row the specified row
       * @param col the specified col
       */
      private synchronized TableDataPath getCellDataPath(int row, int col) {
         if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
            for(TableDataPath path : hlmap.keySet()) {
               if(descriptor.isCellDataPathType(row, col, path)) {
                  return path;
               }
            }

            return null;
         }
         else {
            TableDataPath[] cpath = col >= paths.length ? null : paths[col];

            for(int i = 0; cpath != null && i < cpath.length; i++) {
               if(descriptor.isCellDataPathType(row, col, cpath[i])) {
                  return cpath[i];
               }
            }

            return null;
         }
      }

      /**
       * Get table hyperlink at a table col.
       * @param col the specified col
       * @return table hyperlink if any, null otherwise
       */
      private synchronized Hyperlink getColHyperlink(int col) {
         checkInit();
         Object link = colcache.get(0, col);

         if(link == SparseMatrix.NULL) {
            link = getColHyperlink0(col);
            colcache.set(0, col, link);
         }

         return (Hyperlink) link;
      }

      /**
       * Get table hyperlink at a table col.
       * @param col the specified col
       * @return table hyperlink if any, null otherwise
       */
      private synchronized Hyperlink getColHyperlink0(int col) {
         if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
            return null;
         }

         TableDataPath path = col >= cpath.length ? null : cpath[col];
         return path == null ? null : hlmap.get(path);
      }

      private CrossFilter crosstab;
      private FixedSizeSparseMatrix cellcache;
      private FixedSizeSparseMatrix colcache;
      private TableDataPath[][] paths; // cell data path
      private TableDataPath[] cpath; // col data path
      private TableDataDescriptor descriptor;
      private boolean inited = false;
      private Map<Object, Integer> gcmap = null;
   }

   private Map<TableDataPath, Hyperlink> hlmap;

   private static final Logger LOG = LoggerFactory.getLogger(TableHyperlinkAttr.class);
}
