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
package inetsoft.uql.viewsheet;

import inetsoft.report.*;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.CalcGroup;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Utility for converting between table modes.
 */
public class VSTableConverter extends TableConverter {
   /**
    * Change table mode.
    */
   public static TableLayout changeTableMode(TableDataVSAssembly assembly,
                                             TableLayout olayout, int nmode)
   {
      int omode = olayout == null ? TableLayout.NORMAL : olayout.getMode();

      if(omode == nmode || nmode != TableLayout.CALC) {
         return new TableLayout();
      }

      return changeToCalc(assembly, olayout);
   }

   /**
    * Sync from other table layout to calc layout.
    */
   private static TableLayout changeToCalc(TableDataVSAssembly assembly,
                                           TableLayout olayout)
   {
      TableLayout nlayout = null;

      if(olayout == null) {
         nlayout = VSLayoutTool.createLayout(new CalcTableLens(2, 1));
         nlayout.setMode(TableLayout.CALC);
         return nlayout;
      }

      nlayout = new TableLayout();
      nlayout.setMode(TableLayout.CALC);
      nlayout.setColCount(olayout.getColCount());

      if(olayout.isCrosstab()) {
         changeFromCrosstabToCalc(assembly, olayout, nlayout);
      }
      else {
         changeFromNormalToCalc(assembly, olayout, nlayout);
      }

      // copy row height, column width and span
      for(int r = 0; r < olayout.getRowCount(); r++) {
         nlayout.setRowHeight(r, olayout.getRowHeight(r));

         for(int c = 0; c < olayout.getColCount(); c++) {
            if(r == 0) {
               nlayout.setColWidth(c, olayout.getColWidth(c));
            }

            Dimension span = olayout.getSpan(r, c);
            nlayout.setSpan(r, c, span);
         }
      }

      // for calc, clear all cell binding in the span, otherwise runtime data
      // may be error, bug1279608019099 is caused by this problem, split span
      // will more clear to see the problem
      clearSpanCellBinding(nlayout);

      return nlayout;
   }

   /**
    * Change from normal table to calc.
    */
   private static void changeFromNormalToCalc(TableDataVSAssembly assembly,
                                              TableLayout olayout,
                                              TableLayout nlayout)
   {
      BaseLayout.Region[] headers = olayout.getRegions(HEADER);

      // all headers convert to one header
      if(headers.length > 0) {
         BaseLayout.Region header = nlayout. new Region();
         header.setRowCount(LayoutTool.getRowCount(headers));
         TableDataPath hpath = new TableDataPath(-1, HEADER, 0);
         nlayout.addRegion(hpath, header);
         convertHeaderFromNormalToCalc(olayout, nlayout);
      }

      // vs simple table only have detail region
      BaseLayout.Region[] details = olayout.getRegions(DETAIL);

      if(details.length > 0) {
         BaseLayout.Region detail = nlayout. new Region();
         detail.setRowCount(LayoutTool.getRowCount(details));
         TableDataPath dpath = new TableDataPath(-1, DETAIL, 0);
         nlayout.addRegion(dpath, detail);
         convertRegionFromNormalToCalc(assembly, olayout, nlayout, DETAIL);
      }
   }

   private static void convertRegionFromNormalToCalc(TableDataVSAssembly assembly,
                                                     TableLayout olayout,
                                                     TableLayout nlayout,
                                                     int regionType)
   {
      BaseLayout.Region[] ogheaders = olayout.getRegions(regionType);
      XSourceInfo source = assembly.getSourceInfo();

      for(int i = 0; i < ogheaders.length; i++) {
         BaseLayout.Region oheader = ogheaders[i];
         int level = oheader.getPath().getLevel();

         for(int r = 0; r < oheader.getRowCount(); r++) {
            int gr = olayout.convertToGlobalRow(oheader, r);
            TableLayout.RegionIndex ridx = nlayout.getRegionIndex(gr);

            for(int c = 0; c < oheader.getColCount(); c++) {
               TableCellBinding obinding = (TableCellBinding)
                  oheader.getCellBinding(r, c);

               if(obinding == null) {
                  continue;
               }

               TableCellBinding nbinding = (TableCellBinding) obinding.clone();

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
               ridx.getRegion().setCellBinding(ridx.getRow(), c, nbinding);
            }
         }
      }
   }

   /**
    * Change from crosstab to calc.
    */
   private static void changeFromCrosstabToCalc(TableDataVSAssembly assembly,
                                                TableLayout olayout,
                                                TableLayout nlayout)
   {
      changeFromCrosstabToCalc(olayout, nlayout);
      convertFromCrosstabToCalc(assembly, olayout, nlayout);
   }

   /**
    * Convert from crosstab to calc.
    */
   private static void convertFromCrosstabToCalc(TableDataVSAssembly assembly,
                                                 TableLayout olayout,
                                                 TableLayout nlayout) {
      if(assembly == null) {
         return;
      }

      AggregateInfo ainfo = null;

      if(assembly instanceof CrosstabVSAssembly) {
         CrosstabVSAssembly cassembly = (CrosstabVSAssembly) assembly;
         ainfo = cassembly.getVSCrosstabInfo().getAggregateInfo();
         fixNamedGroups(ainfo.getGroups());
      }

      if(ainfo == null || ainfo.isEmpty()) {
         convertAsPlainToCalc(assembly.getSourceInfo(), olayout, nlayout);
      }
      else {
         convertCrosstabToCalc(assembly, olayout, nlayout);
      }
   }

   /**
    * Convert from crosstab to calc.
    */
   private static void convertCrosstabToCalc(TableDataVSAssembly assembly,
                                             TableLayout olayout,
                                             TableLayout nlayout)
   {
      CrosstabVSAssembly cassembly = (CrosstabVSAssembly) assembly;
      VSCrosstabInfo cinfo = cassembly.getVSCrosstabInfo();
      AggregateInfo ainfo = cinfo.getAggregateInfo();
      SourceInfo source = cassembly.getSourceInfo();
      GroupRef[] grefs = ainfo.getGroups();
      int perBy = cinfo.getPercentageByOption();

      DataRef[] rows = cinfo.getPeriodRuntimeRowHeaders();
      GroupRef[] rgrefs = VSLayoutTool.getGroupRefs(cinfo, rows, true);
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      GroupRef[] cgrefs = VSLayoutTool.getGroupRefs(cinfo, cols, true);
      AggregateRef[] arefs = VSLayoutTool.getAggregateRefs(cinfo,
         cinfo.getRuntimeAggregates(), true);
      List<String> fakes = new ArrayList<>();

      for(GroupRef ref : grefs) {
         if(VSUtil.isFake(ref)) {
            fakes.add(ref.getName());
         }
      }

      for(AggregateRef ref : arefs) {
         if(VSUtil.isFake(ref)) {
            fakes.add(ref.getName());
         }
      }

      // column header cell names
      String[] colnames = new String[cgrefs.length];
      // row header cell names
      String[] rownames = new String[rgrefs.length];
      Map<Point, Rectangle> gdetails = new HashMap<>();
      // each cell position -> row/col group name index
      Map<Point, Point> cell2rc = new HashMap<>();
      List<Rectangle> spans = new ArrayList<>();

      for(int r = 0; r < olayout.getRegionCount(); r++) {
         BaseLayout.Region region = olayout.getRegion(r);
         TableDataPath rpath = olayout.getRegion(r).getPath();
         int rtype = rpath.getType();
         int rlvl = rpath.getLevel();

         for(int c = 0; c < olayout.getVRegionCount(); c++) {
            if(processed(spans, new Point(c, r))) {
               continue;
            }

            Dimension dim = olayout.getSpan(r, c);

            if(dim != null) {
               spans.add(new Rectangle(c, r, dim.width, dim.height));
            }

            TableDataPath cpath = olayout.getVRegion(c).getPath();
            int ctype = cpath.getType();
            int clvl = cpath.getLevel();
            TableDataPath cellPath = olayout.getCellDataPath(r, c);
            int celltype = cellPath.getType();
            TableCellBinding binding = (TableCellBinding) region.getCellBinding(0, c);

            if(binding == null) {
               continue;
            }

            if(rtype == TableDataPath.SUMMARY_HEADER ||
               ctype == TableDataPath.SUMMARY_HEADER ||
               rtype == TableDataPath.HEADER || ctype == TableDataPath.HEADER)
            {
               binding.setType(CellBinding.BIND_TEXT);
            }

            // take care, it is not absolutely correct, so we use the gdetails
            // map to modify it second times to make sure it is correct
            TableCellBinding nbinding = (TableCellBinding) binding.clone();
            nlayout.setCellBinding(r, c, nbinding);

            nbinding.setRowGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setColGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setMergeRowGroup(TableCellBinding.DEFAULT_GROUP);
            nbinding.setMergeColGroup(TableCellBinding.DEFAULT_GROUP);

            if(nbinding.getType() != CellBinding.BIND_COLUMN) {
               continue;
            }

            String value = nbinding.getValue();

            if(fakes.contains(value)) {
               nbinding.setType(CellBinding.BIND_TEXT);

               continue;
            }

            if(source != null) {
               nbinding.setSource(source.getSource());
               nbinding.setSourcePrefix(source.getPrefix());
               nbinding.setSourceType(source.getType());
            }

            // expansion
            if(rtype == G_HEADER) {
               nbinding.setExpansion(TableCellBinding.EXPAND_H);
            }
            else if(ctype == G_HEADER) {
               nbinding.setExpansion(TableCellBinding.EXPAND_V);
            }

            // column header, column header
            if(rtype == G_HEADER || ctype == G_HEADER) {
               int index = rtype == G_HEADER ? r : c;
               String[] cellnames = rtype == G_HEADER ? colnames : rownames;

               GroupRef[] refs = rtype == G_HEADER ? cgrefs : rgrefs;
               DataRef[] headers = rtype == G_HEADER ? cols : rows;

               if(cellnames[index] == null) {
                  String cellname = nbinding.getCellName();

                  if(cellname == null) {
                     cellname = getDefaultCellName(nlayout, olayout, refs[index]);
                  }

                  cellnames[index] = cellname;
                  nbinding.setCellName(cellname);
               }
               else if(nbinding.getCellName() == null) {
                  if(rtype == G_HEADER) {
                     nbinding.setColGroup(cellnames[index]);
                  }
                  else {
                     nbinding.setRowGroup(cellnames[index]);
                  }
               }

               if(rtype == G_HEADER) {
                  cell2rc.put(new Point(c, r), new Point(index - 1, -1));
               }
               else {
                  cell2rc.put(new Point(c, r), new Point(-1, index - 1));
               }

               nbinding.setMergeCells(cinfo.isMergeSpan());
               nbinding.setMergeRowGroup(nbinding.getCellName());
               nbinding.setMergeColGroup(nbinding.getCellName());
               nbinding.setBType(TableCellBinding.GROUP);
               nbinding.setOrderInfo(fixOrderInfo(refs[index], headers[index]));
               nbinding.setTopN(fixTopN(refs[index], cinfo));

               if(ainfo != null && ainfo.getGroup(refs[index].getName()) == null) {
                  nbinding.setValue(VSLayoutTool.getOriginalColumn(nbinding.getValue(), refs[index]));
               }

               nbinding.setTimeSeries(VSLayoutTool.isTimeSeries(refs[index]));

               if(rtype == DETAIL || ctype == DETAIL) {
                  int gr = olayout.convertToGlobalRow(region, 0);
                  Rectangle span = olayout.findSpan(gr, c);

                  if(span != null) {
                     gdetails.put(new Point(c, gr), span);
                  }
               }

               continue;
            }

            // aggregate cell
            // row group index
            int ridx = -1;
            // column group index
            int cidx = -1;
            String aggname = nbinding.getValue();

            if(rtype == DETAIL) {
               ridx = rgrefs.length;
            }
            else if(rtype == G_FOOTER) {
               ridx = rlvl + 1;
            }
            else if(rtype == FOOTER) {
               ridx = 0;
            }

            // column
            if(ctype == DETAIL) {
               cidx = cgrefs.length;
            }
            else if(ctype == G_FOOTER) {
               cidx = clvl + 1;
            }
            else if(ctype == FOOTER) {
               cidx = 0;
            }

            cell2rc.put(new Point(c, r), new Point(cidx - 1, ridx - 1));
            nbinding.setExpansion(TableCellBinding.EXPAND_NONE);
            String realName = nbinding.getValue0();
            realName = realName == null ? aggname : realName;
            AggregateRef aref = (AggregateRef) VSLayoutTool.findAttribute(arefs, realName);

            if(aref == null) {
               aref = VSLayoutTool.findAggregateRef(arefs, realName, perBy);
            }

            if(arefs == null || arefs.length == 0) {
               nbinding.setExpansion(TableCellBinding.EXPAND_V);
               continue;
            }

            int percent = aref.getPercentageOption();

            if(perBy == XConstants.PERCENTAGE_BY_COL) {
               if(percent == XConstants.PERCENTAGE_OF_GROUP) {
                  aref.setPercentageOption(XConstants.PERCENTAGE_OF_COL_GROUP);
               }
               else if(percent == XConstants.PERCENTAGE_OF_GRANDTOTAL) {
                  aref.setPercentageOption(XConstants.PERCENTAGE_OF_COL_GRANDTOTAL);
               }
            }
            else if(perBy == XConstants.PERCENTAGE_BY_ROW) {
               if(percent == XConstants.PERCENTAGE_OF_GROUP) {
                  aref.setPercentageOption(XConstants.PERCENTAGE_OF_ROW_GROUP);
               }
               else if(percent == XConstants.PERCENTAGE_OF_GRANDTOTAL) {
                  aref.setPercentageOption(XConstants.PERCENTAGE_OF_ROW_GRANDTOTAL);
               }
            }

            String formula = aref.getFormulaName();
            nbinding.setBType(TableCellBinding.SUMMARY);
            nbinding.setFormula(formula);
            nbinding.setValue(VSLayoutTool.getAggregateColumn(nbinding.getValue(), aref.getName(),
               aref.getFormula()));
         }
      }

      // copy detail group header binding to all cell which is in the same span
      Iterator<Point> gdkeys = gdetails.keySet().iterator();
      List<Point> processed = new ArrayList<>();

      while(gdkeys.hasNext()) {
         Point p = gdkeys.next();
         Rectangle rect = gdetails.get(p);
         TableCellBinding binding =
            (TableCellBinding) nlayout.getCellBinding(p.y, p.x);

         if(binding == null || binding.getCellName() == null) {
            continue;
         }

         // ** take care of rect, see BaseLayout.findSpan
         for(int r = rect.y; r < rect.height; r++) {
            for(int c = rect.x; c < rect.width; c++) {
               boolean processName = r == rect.y && c == rect.x;
               Point cp = new Point(p.y + r, p.x + c);

               if(processed.contains(cp)) {
                  continue;
               }

               processed.add(cp);

               if(r == 0 && c == 0) {
                  if(!processName) {
                     binding.setCellName(null);
                  }

                  continue;
               }

               TableCellBinding obinding = (TableCellBinding)
                  nlayout.getCellBinding(p.y + r, p.x + c);
               TableCellBinding nbinding = binding == null ?
                  null : (TableCellBinding) binding.clone();

               if(nbinding != null && obinding != null) {
                  nbinding.setCellName(processName ? binding.getCellName() : null);
               }

               nlayout.setCellBinding(p.y + r, p.x + c, nbinding);
            }
         }
      }

      for(Point p : cell2rc.keySet()) {
         Point rc = cell2rc.get(p);
         TableCellBinding binding = (TableCellBinding) nlayout.getCellBinding(p.y, p.x);

         if(rc.y >= 0) {
            binding.setRowGroup(rownames[rc.y]);
         }

         if(rc.x >= 0) {
            binding.setColGroup(colnames[rc.x]);
         }
      }
   }

   private static OrderInfo fixOrderInfo(CalcGroup gfield, DataRef dim) {
      OrderInfo orderInfo = gfield.getOrderInfo();

      if(orderInfo != null) {
         orderInfo = (OrderInfo) orderInfo.clone();
         VSDimensionRef dim2 = (VSDimensionRef) dim;
         String baseType = null;

         // crosstab period comparison dim's dataType is explicitly set to base type
         // (not the date-range type). that seemed to be one to avoid a P_column to
         // be added like in chart period comparison. changing that has a high impact.
         // this baseType check makes sure the real type (DateRangeRef) is used to
         // check whether the column is date/time.
         if(dim2.getDataRef() instanceof ColumnRef &&
            ((ColumnRef) dim2.getDataRef()).getDataRef() != null)
         {
            baseType = ((ColumnRef) dim2.getDataRef()).getDataRef().getDataType();
         }

         if(!dim2.isDateTime() && !XSchema.isDateType(baseType)) {
            orderInfo.setInterval(orderInfo.getInterval(), 0);
         }
      }

      return orderInfo;
   }

   /**
    * Fix TopN info.
    */
   private static TopNInfo fixTopN(GroupRef dim, VSCrosstabInfo info) {
      TopNInfo topN = dim.getTopN();

      if(topN != null) {
         topN = (TopNInfo) topN.clone();

         if(isNotInnerDimRef(dim, info)) {
            topN.setOthers(false);
         }
      }

      return topN;
   }

   /**
    * Judge whether the data type dimension is an outer dimension.
    */
   private static boolean isNotInnerDimRef(GroupRef dim, VSCrosstabInfo info) {
      DataRef[] dims = info.getRowHeaders();
      DataRef ref = null;

      for(int i = 0; i < dims.length; i++) {
         ref = dims[i];

         if(!(ref instanceof VSDimensionRef)) {
            break;
         }

         if(!((VSDimensionRef) ref).getFullName().equals(dim.getName())) {
            continue;
         }

         return i != dims.length - 1;
      }

      dims = info.getColHeaders();

      for(int i = 0; i < dims.length; i++) {
         ref = dims[i];

         if(!(ref instanceof VSDimensionRef)) {
            break;
         }

         if(!((VSDimensionRef) ref).getFullName().equals(dim.getName())) {
            continue;
         }

         return i != dims.length - 1;
      }

      return false;
   }
}
