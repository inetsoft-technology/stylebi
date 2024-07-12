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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.filter.CrossFilter;
import inetsoft.report.filter.DCMergeDatePartFilter.MergePartCell;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.internal.table.TableTool;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.uql.XMetaInfo;
import inetsoft.uql.viewsheet.XDimensionRef;

import java.sql.Time;
import java.util.*;

/**
 * VSHyperlinkTableLens apply hyperlinks.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class VSHyperlinkTableLens extends AttributeTableLens {
   /**
    * Create a format viewsheet table lens.
    */
   public VSHyperlinkTableLens(TableLens table, TableHyperlinkAttr tattr) {
      super(table);
      this.tattr = tattr;

      if(getDescriptor().getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         crosstab = Util.getCrossFilter(getTable());
      }
   }

   /**
    * Check if contains hyperlink definitation.
    */
   @Override
   public boolean containsLink() {
      if(tattr != null && !tattr.isNull()) {
         return true;
      }

      return super.containsLink();
   }

   /**
    * Get hyperlink of a table cell.
    * @param r the specified row.
    * @param c the specified col.
    */
   @Override
   public Hyperlink.Ref getHyperlink(int r, int c) {
      Hyperlink.Ref ref = null;

      TableDataDescriptor desc = getDescriptor();
      TableDataPath dpath = desc.getCellDataPath(r, c);
      Hyperlink link = tattr != null ? tattr.getHyperlink(dpath) : null;

      if(link != null) {
         if(crosstab != null) {
            int row = TableTool.getBaseRowIndex(getTable(), crosstab, r);
            int col = TableTool.getBaseColIndex(getTable(), crosstab, c);

            if(row < 0) {
               row = 0;
            }

            if(col < 0) {
               col = 0;
            }

            Map map = crosstab.getKeyValuePairs(row, col, null);
            map = expandPartMergeCell(map);
            fixTimeTypeValue(map, dpath, desc);
            ref = new Hyperlink.Ref(link, map);
         }
         else {
            ref = new Hyperlink.Ref(link, getTable(), r, c);
         }
      }

      return ref == null ? super.getHyperlink(r, c) : ref;
   }

   /**
    * Convert the date value to time value when column type is time type.
    *
    * @param map crosstab type KeyValuePairs map.
    * @param dpath table data path.
    * @param desc table descriptor.
    */
   private void fixTimeTypeValue(Map map, TableDataPath dpath, TableDataDescriptor desc) {
      if(dpath == null || desc == null || dpath.getType() != TableDataPath.GROUP_HEADER) {
         return;
      }

      XMetaInfo xMetaInfo = desc.getXMetaInfo(dpath);

      if(xMetaInfo == null || !"time".equals(xMetaInfo.getProperty("columnDataType"))) {
         return;
      }

      String[] path = dpath.getPath();

      if(path == null || path.length == 0) {
         return;
      }

      String field = path[path.length - 1];

      if(field == null) {
         return;
      }

      Object fieldValue = map.get(field);

      if(!(fieldValue instanceof Date)) {
         return;
      }

      map.put(field, new Time(((Date) fieldValue).getTime()));
   }

   /**
    * Expand the merge cell and add to the map.
    *
    * @param map parameters map.
    * @return
    */
   private Map expandPartMergeCell(Map map) {
      if(map == null || map.size() == 0) {
         return map;
      }

      Map nMap = new HashMap();

      map.entrySet().forEach(entry -> {
         Map.Entry mapEntry = (Map.Entry) entry;

         if(mapEntry.getValue() instanceof MergePartCell) {
            MergePartCell cell = (MergePartCell) mapEntry.getValue();
            List<XDimensionRef> mergedRefs = cell.getMergedRefs();

            for(int i = 0; i < mergedRefs.size(); i++) {
               XDimensionRef dimensionRef = mergedRefs.get(i);

               if(dimensionRef == null) {
                  continue;
               }

               if(!map.containsKey(dimensionRef.getFullName())) {
                  nMap.put(dimensionRef.getFullName(), cell.getValue(i));
               }
            }
         }

         nMap.put(mapEntry.getKey(), mapEntry.getValue());
      });


      return nMap;
   }

   private TableHyperlinkAttr tattr;
   private CrossFilter crosstab;
}
