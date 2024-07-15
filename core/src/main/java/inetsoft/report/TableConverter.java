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
package inetsoft.report;

import inetsoft.report.internal.binding.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XSourceInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for converting between table modes.
 */
public class TableConverter {
   /**
    * Clear cell binding inner of span.
    */
   protected static void clearSpanCellBinding(TableLayout layout) {
      BaseLayout.Region[] regions = layout.getRegions();

      for(int c = 0; c < layout.getColCount(); c++) {
         for(BaseLayout.Region region : regions) {
            for(int r = 0; r < region.getRowCount(); r++) {
               Rectangle rect = region.findSpan(r, c);

               if(rect != null && (rect.x != 0 || rect.y != 0)) {
                  region.setCellBinding(r, c, null);
               }
            }
         }
      }
   }

   /**
    * Change from crosstab to calc.
    */
   protected static void changeFromCrosstabToCalc(TableLayout olayout,
                                                  TableLayout nlayout)
   {
      List<BaseLayout.Region> details = new ArrayList<>();
      List<BaseLayout.Region> headers = new ArrayList<>();

      for(int i = 0; i < olayout.getRegionCount(); i++) {
         BaseLayout.Region region = olayout.getRegion(i);
         int type = region.getPath().getType();

         // header, summary header and group header
         // convert to calc header region
         if(type == HEADER || type == G_HEADER ||
            type == TableDataPath.SUMMARY_HEADER)
         {
            headers.add(region);
         }
         else {
            details.add(region);
         }
      }

      BaseLayout.Region[] headerR = new BaseLayout.Region[headers.size()];
      headers.toArray(headerR);
      BaseLayout.Region header = nlayout.new Region();
      nlayout.addRegion(new TableDataPath(-1, HEADER), header);
      header.setRowCount(LayoutTool.getRowCount(headerR));

      BaseLayout.Region[] detailR = new BaseLayout.Region[details.size()];
      details.toArray(detailR);
      BaseLayout.Region detail = nlayout. new Region();
      nlayout.addRegion(new TableDataPath(-1, DETAIL), detail);
      detail.setRowCount(LayoutTool.getRowCount(detailR));
   }

   /**
    * Process the named group when table convert to a calc table, the named
    * group with complex operation can not support to calc.
    */
   protected static void fixNamedGroups(CalcGroup[] groups) {
      for(int i = 0; i < groups.length; i++) {
         CalcGroup group = groups[i];

         OrderInfo order = group.getOrderInfo();
         fixNamedGroup(order);
      }
   }

   /**
    * Process the named group when table convert to a calc table, the named
    * group with complex operation can not support to calc.
    */
   private static void fixNamedGroup(OrderInfo order) {
      if(order == null) {
         return;
      }

      XNamedGroupInfo group = order.getNamedGroupInfo();

      if(!LayoutTool.isSimpleNamedGroup(group)) {
         order.setNamedGroupInfo(null);
      }
   }

   /**
    * Convert crosstab like crosstab to calc.
    */
   protected static void convertAsPlainToCalc(XSourceInfo source,
                                              TableLayout olayout,
                                              TableLayout nlayout)
   {
      BaseLayout.Region[] headers = olayout.getRegions(HEADER);
      BaseLayout.Region header = headers.length > 0 ? headers[0] : null;

      if(header != null) {
         // header? text binding
         for(int i = 0; i < header.getColCount(); i++) {
            TableCellBinding binding =
               (TableCellBinding) header.getCellBinding(0, i);

            if(binding == null) {
               continue;
            }

            TableCellBinding nbinding = (TableCellBinding) binding.clone();
            nbinding.setType(CellBinding.BIND_TEXT);
            nlayout.setCellBinding(0, i, nbinding);
         }
      }

      // content
      BaseLayout.Region[] details = olayout.getRegions(DETAIL);
      BaseLayout.Region detail = details.length > 0 ? details[0] : null;

      if(detail != null) {
         for(int i = 0; i < detail.getColCount(); i++) {
            TableCellBinding binding = (TableCellBinding)
               detail.getCellBinding(0, i);

            if(binding == null) {
               continue;
            }

            TableCellBinding nbinding = (TableCellBinding) binding.clone();

            if(source != null) {
               nbinding.setSource(source.getSource());
               nbinding.setSourcePrefix(source.getPrefix());
               nbinding.setSourceType(source.getType());
            }

            nbinding.setRowGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setColGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);

            nbinding.setExpansion(TableCellBinding.EXPAND_V);
            nlayout.setCellBinding(1, i, nbinding);
         }
      }
   }

   /**
    * Convert headers from normal table to calc table.
    */
   protected static void convertHeaderFromNormalToCalc(TableLayout olayout,
                                                       TableLayout nlayout)
   {
      BaseLayout.Region[] oheaders = olayout.getRegions(HEADER);
      BaseLayout.Region[] nheaders = nlayout.getRegions(HEADER);
      BaseLayout.Region nheader = nheaders[0];
      int gr = 0;

      for(int i = 0; i < oheaders.length; i++) {
         BaseLayout.Region oheader = oheaders[i];

         if(i > 0) {
            nheader.setRowCount(nheader.getRowCount() + oheader.getRowCount());
         }

         for(int r = 0; r < oheader.getRowCount(); r++) {
            for(int c = 0; c < oheader.getColCount(); c++) {
               TableCellBinding obinding =
                  (TableCellBinding) oheader.getCellBinding(r, c);

               if(obinding == null) {
                  continue;
               }

               TableCellBinding nbinding = (TableCellBinding) obinding.clone();

               if(obinding.getType() != CellBinding.BIND_FORMULA) {
                  nbinding.setType(CellBinding.BIND_TEXT);
               }

               nheader.setCellBinding(gr, c, nbinding);
            }

            gr++;
         }

         gr++;
      }
   }

   /**
    * Get cell name for a calc cell.
    */
   protected static String getDefaultCellName(TableLayout nlayout,
                                              TableLayout olayout,
                                              DataRef ref)
   {
      String name = BindingTool.getPureField(ref).getName();
      return getDefaultCellName(nlayout, olayout, name);
   }

   protected static String getDefaultCellName(TableLayout nlayout,
                                              TableLayout olayout,
                                              String name)
   {

      name = name.replaceAll("\\.", "_");
      name = name.replaceAll(" ", "_");
      name = name.replaceAll(":", "_");
      name = name.replaceAll("'", "_");

      if(isValidName(olayout, name) && isValidName(nlayout, name)) {
         return name;
      }

      int cnt = 1;
      String name2 = name + "_" + cnt;

      while(!isValidName(olayout, name2) || !isValidName(nlayout, name2)) {
         cnt++;
         name2 = name + "_" + cnt;
      }

      return name2;
   }

   /**
    * Check if a cell name is valid.
    */
   public static boolean isValidName(TableLayout layout, String name) {
      if(layout == null || name == null) {
         return true;
      }

      for(int i = 0; i < layout.getRegionCount(); i++) {
         BaseLayout.Region region = layout.getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding binding =
                  (TableCellBinding) region.getCellBinding(r, c);

               if(binding != null && name.equals(binding.getCellName())) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   protected static boolean processed(List<Rectangle> rects, Point loc) {
      for(Rectangle rect : rects) {
         if(rect.contains(loc)) {
            return true;
         }
      }

      return false;
   }

   protected static final int HEADER = TableDataPath.HEADER;
   protected static final int G_HEADER = TableDataPath.GROUP_HEADER;
   protected static final int DETAIL = TableDataPath.DETAIL;
   protected static final int G_FOOTER = TableDataPath.SUMMARY;
   protected static final int FOOTER = TableDataPath.GRAND_TOTAL;
}
