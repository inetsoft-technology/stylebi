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
package inetsoft.web.binding.handler;

import inetsoft.report.*;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.script.formula.CellRange;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;

import java.awt.*;

public class CellInfoHandler {
   public CellInfoHandler(CalcTableVSAssemblyInfo table, int row, int col) {
      this(table, row, col, true);
   }

   /**
    * Create a CellInfo.
    * @param row the row index in table layout.
    * @param col the column index in table layout.
    */
   public CellInfoHandler(CalcTableVSAssemblyInfo info, int row, int col, boolean full) {
      this.row = row;
      this.col = col;
      TableLayout layout = info.getTableLayout();
      reg = layout.getRegionIndex(row);
      BaseLayout.Region region = reg.getRegion();

      if(full) {
         binding = region.getCellBinding(reg.getRow(), col);

         if(binding != null && binding.getType() == CellBinding.BIND_COLUMN) {
            field = binding.getValue();
         }

         // @by henryh, fixed bug1132551402191
         // avoid to merge cell span with same datapath.
         span = layout.getSpan(row, col);
         span = span != null ? (Dimension) span.clone() : null;
      }

      TableDataPath path = info.getCellDataPath(row, col);
      path = getRuntimeTableDataPath(path, row, col);

      if(path != null) {
         format = info.getFormatInfo().getFormat(path);
         runtimeFormat = info.getFormatInfo().getFormat(path, true);
         hyperlink = info.getHyperlinkAttr() == null ? null :
            info.getHyperlinkAttr().getHyperlink(path);
         highlight = info.getHighlightAttr() == null ? null :
            info.getHighlightAttr().getHighlight(path);

         // make a copy so the saved values wound not change
         if(format != null) {
            format = (VSCompositeFormat) format.clone();
         }

         if(runtimeFormat != null) {
            runtimeFormat = (VSCompositeFormat) runtimeFormat.clone();
         }

         if(hyperlink != null) {
            hyperlink = (Hyperlink) hyperlink.clone();
         }

         if(highlight != null) {
            highlight = (HighlightGroup) highlight.clone();
         }
      }
   }

   /**
    * Copy the row attributes.
    */
   public void copyRow(CalcTableVSAssemblyInfo info) {
      if(reg == null) {
         return;
      }

      BaseLayout.Region mregion = reg.getRegion();
      TableDataPath path = mregion.getRowDataPath(reg.getRow());
      isRow = true;

      if(path != null) {
         rowFormat = info.getFormatInfo().getFormat(path, false);
         runtimeRowFormat = info.getFormatInfo().getFormat(path, true);
         rowHyperlink = info.getHyperlinkAttr() == null ? null :
            info.getHyperlinkAttr().getHyperlink(path);
         rowHighlight = info.getHighlightAttr() == null ? null :
            info.getHighlightAttr().getHighlight(path);

         if(rowFormat != null) {
            rowFormat = (VSCompositeFormat) rowFormat.clone();
         }

         if(runtimeRowFormat != null) {
            runtimeRowFormat = (VSCompositeFormat) runtimeRowFormat.clone();
         }

         if(rowHyperlink != null) {
            rowHyperlink = (Hyperlink) rowHyperlink.clone();
         }

         if(rowHighlight != null) {
            rowHighlight = (HighlightGroup) rowHighlight.clone();
         }
      }
   }

   /**
    * Copy the column attributes.
    */
   public void copyColumn(CalcTableVSAssemblyInfo info) {
      if(reg == null) {
         return;
      }

      TableLayout layout = info.getTableLayout();
      TableDataPath path = layout.getColDataPath(col);
      isCol = true;

      if(path != null) {
         colFormat = info.getFormatInfo().getFormat(path, false);
         runtimeColFormat = info.getFormatInfo().getFormat(path, true);
         colHyperlink = info.getHyperlinkAttr() == null ? null :
            info.getHyperlinkAttr().getHyperlink(path);
         colHighlight = info.getHighlightAttr() == null ? null :
            info.getHighlightAttr().getHighlight(path);

         if(colFormat != null) {
            colFormat = (VSCompositeFormat) colFormat.clone();
         }

         if(runtimeColFormat != null) {
            runtimeColFormat = (VSCompositeFormat) runtimeColFormat.clone();
         }

         if(colHyperlink != null) {
            colHyperlink = (Hyperlink) colHyperlink.clone();
         }

         if(colHighlight != null) {
            colHighlight = (HighlightGroup) colHighlight.clone();
         }
      }
   }

   /**
    * Set the saved value at the specified cell.
    * @param row the row index in the table layout.
    */
   public void set(CalcTableVSAssemblyInfo info, int row, int col) {
      set(info, row, col, true);
   }

   /**
    * Set the saved value at the specified cell.
    * @param full if false, only copy settings excluding binding and span.
    */
   public void set(CalcTableVSAssemblyInfo info, int row, int col, boolean full) {
      TableLayout modified = info.getTableLayout();
      TableLayout.RegionIndex mreg = modified.getRegionIndex(row);

      if(mreg == null) {
         return;
      }

      BaseLayout.Region mregion = mreg.getRegion();

      if(full) {
         TableCellBinding nbinding = (TableCellBinding) (binding != null ? binding.clone() : null);

         if(modified.getMode() == TableLayout.CALC) {
            if(binding != null) {
               String str = binding.getValue();

               if(binding.getType() == CellBinding.BIND_FORMULA) {
                  if(this.row != row || this.col != col) {
                     str = adjustIndexCell(str, this.row, this.col, row, col, -1, -1);
                  }
               }

               TableCellBinding obinding = (TableCellBinding)
                  mregion.getCellBinding(mreg.getRow(), col);

               // clear out old cell name before checking duplicate name, otherwise
               // merging cell will always be duplicate
               if(obinding != null) {
                  obinding.setCellName(null);
               }

               if(isDuplicateName(info, nbinding.getCellName())) {
                  LayoutTool.fixDuplicateCellBinding(info.getTableLayout(), nbinding);
               }

               nbinding.setValue(str);
               mregion.setCellBinding(mreg.getRow(), col, nbinding);
            }
         }

         if(span != null) {
            modified.setSpan(row, col, span);
         }

         if(nbinding != null) {
            // set the field back, if the cell already contains a field,
            // don't override it
            if(nbinding.getValue() == null) {
               nbinding.setValue(field);
            }

            adjust(info, this.row, row, (TableCellBinding) binding, nbinding);
         }

         mregion.setCellBinding(mreg.getRow(), col, nbinding);
      }

      TableDataPath path = info.getCellDataPath(row, col);
      path = getRuntimeTableDataPath(path, row, col);

      if(path != null) {
         info.getFormatInfo().setFormat(path, format);
         info.getFormatInfo().setFormat(path, runtimeFormat);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, hyperlink);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, highlight);
         }
      }

      TableDataPath rowpath = mregion.getRowDataPath(mreg.getRow());

      if(isRow && rowpath != null) {
         info.getFormatInfo().setFormat(rowpath, rowFormat);
         info.getFormatInfo().setFormat(rowpath, runtimeRowFormat);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, rowHyperlink);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, rowHighlight);
         }
      }

      TableDataPath colpath = modified.getColDataPath(col);

      if(isCol && colpath != null) {
         info.getFormatInfo().setFormat(colpath, colFormat);
         info.getFormatInfo().setFormat(colpath, runtimeColFormat);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(colpath, colHyperlink);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(colpath, colHighlight);
         }
      }
   }

   private static boolean isDuplicateName(CalcTableVSAssemblyInfo info, String name) {
      TableLayout layout = info.getTableLayout();
      return !TableConverter.isValidName(layout, name);
   }

   /**
    * Set the cell binding (e.g. grouping) appropriate for the new location.
    */
   private static void adjust(CalcTableVSAssemblyInfo info, int orow, int nrow,
                              TableCellBinding obinding,
                              TableCellBinding nbinding)
   {
      if(nbinding == null || nbinding.getType() != CellBinding.BIND_COLUMN) {
         return;
      }

      TableLayout layout = info.getTableLayout();
      TableLayout.RegionIndex nidx = layout.getRegionIndex(nrow);
      TableLayout.RegionIndex oidx = layout.getRegionIndex(orow);

      if(nidx == null) {
         return;
      }

      int rtype = nidx.getRegion().getPath().getType();

      if(!layout.isCalc() && rtype == TableDataPath.HEADER && nbinding != null) {
         nbinding.setBType(TableCellBinding.DETAIL);
         nbinding.setExpansion(TableCellBinding.EXPAND_NONE);
      }

      if(!layout.isCalc() && rtype == TableDataPath.GRAND_TOTAL) {
         nbinding.setExpansion(TableCellBinding.EXPAND_NONE);

         if(nbinding.getBType() == TableCellBinding.GROUP) {
            nbinding.setBType(TableDataPath.DETAIL);
         }
      }

      if(!layout.isNormal() || oidx == null || obinding == null ||
         // don't adjust binding if moving in the same region
         oidx.getRegion() == nidx.getRegion() ||
         obinding.getType() != CellBinding.BIND_COLUMN)
      {
         return;
      }

      int type = nidx.getRegion().getPath().getType();
      String value = nbinding.getValue();
   }

   /**
    * Clear all setting at the specified cell.
    */
   public static void clear(CalcTableVSAssemblyInfo info, int row, int col,
                            boolean full, boolean cleanSpan)
   {
      TableLayout modified = info.getTableLayout();
      final TableLayout.RegionIndex mreg = modified.getRegionIndex(row);

      if(mreg == null) {
         return;
      }

      BaseLayout.Region mregion = mreg.getRegion();

      if(modified.getRowCount() <= row || modified.getColCount() <= col) {
         return;
      }

      if(full) {
         mregion.setCellBinding(mreg.getRow(), col, null);
      }

      if(cleanSpan) {
         modified.setSpan(row, col, null);
      }

      // should use the same data path as the CalcTableLens. (51182)
      TableDataPath path = info.getCellDataPath(row, col);
      path = getRuntimeTableDataPath(path, row, col);

      if(path != null) {
         info.getFormatInfo().setFormat(path, null);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, null);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, null);
         }
      }
   }

   /**
    * Clear all setting at the specified row.
    */
   public static void clearRow(CalcTableVSAssemblyInfo info, int row) {
      TableLayout modified = info.getTableLayout();
      TableLayout.RegionIndex mreg = modified.getRegionIndex(row);

      if(mreg == null) {
      }

      BaseLayout.Region mregion = mreg.getRegion();

      if(modified.getRowCount() <= row) {
         return;
      }

      TableDataPath path = mregion.getRowDataPath(mreg.getRow());

      if(path != null) {
         info.getFormatInfo().setFormat(path, null);
         info.getFormatInfo().setFormat(path, null);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, null);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, null);
         }
      }
   }

   /**
    * Clear all setting at the specified column.
    */
   public static void clearColumn(CalcTableVSAssemblyInfo info, int col) {
      TableLayout modified = info.getTableLayout();

      if(modified.getColCount() <= col) {
         return;
      }

      TableDataPath path = modified.getColDataPath(col);

      if(path != null) {
         info.getFormatInfo().setFormat(path, null);
         info.getFormatInfo().setFormat(path, null);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, null);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, null);
         }
      }
   }

   /**
    * Merge the cell attributes into this cell.
    */
   public void merge(CellInfoHandler info) {
      if(binding == null || binding.getValue() == null ||
         "".equals(binding.getValue()))
      {
         binding = (TableCellBinding)
            (info.binding == null ? null : info.binding.clone());

         // make sure name is unique
         if(info.binding != null && !info.binding.isEmpty()) {
            ((TableCellBinding) info.binding).setCellName(null);
         }
      }

      if(span == null) {
         span = info.span;
      }

      if(format == null) {
         format = info.format;
      }
      else if(info.format != null) {
         merge(format, info.format);
      }

      if(runtimeFormat == null) {
         runtimeFormat = info.runtimeFormat;
      }
      else if(info.runtimeFormat != null) {
         merge(runtimeFormat, info.runtimeFormat);
      }

      if(hyperlink == null) {
         hyperlink = info.hyperlink;
      }

      if(highlight == null) {
         highlight = info.highlight;
      }
   }

   public void spreadFormat(CalcTableVSAssemblyInfo info, int row, int col) {
      TableDataPath path = info.getCellDataPath(row, col);
      path = getRuntimeTableDataPath(path, row, col);

      if(path != null) {
         info.getFormatInfo().setFormat(path, format);
         info.getFormatInfo().setFormat(path, runtimeFormat);

         if(info.getHyperlinkAttr() != null) {
            info.getHyperlinkAttr().setHyperlink(path, hyperlink);
         }

         if(info.getHighlightAttr() != null) {
            info.getHighlightAttr().setHighlight(path, colHighlight);
         }
      }
   }

   public void spreadValue(CalcTableVSAssemblyInfo info, int row, int col) {
      TableLayout modified = info.getTableLayout();
      TableLayout.RegionIndex mreg = modified.getRegionIndex(row);

      if(mreg != null) {
         BaseLayout.Region mregion = mreg.getRegion();
         TableCellBinding old = (TableCellBinding)
            mregion.getCellBinding(mreg.getRow(), col);
         TableCellBinding clone = (TableCellBinding)
            (binding == null ? old : binding.clone());

         // make sure name is unique
         if(old != null && clone != null) {
            clone.setCellName(old.getCellName());
         }
         else if(clone != null) {
            clone.setCellName(null);
         }

         mregion.setCellBinding(mreg.getRow(), col, clone);
      }
   }

   private String adjustIndexCell(String expr, int orow, int ocol, int row, int col,
                                  int insertRow, int insertCol)
   {
      if(expr == null) {
         return null;
      }

      int idx = 0;

      // adjust field[row][col] references
      while((idx = expr.indexOf("field[", idx)) >= 0) {
         int lb1 = idx + 5;
         int rb1 = expr.indexOf("]", lb1);

         if(rb1 > 0) {
            int lb2 = expr.indexOf("[", rb1);

            if(lb2 > 0) {
               int rb2 = expr.indexOf("]", lb2);

               if(lb2 > 0) {
                  String rowstr = expr.substring(lb1 + 1, rb1).trim();
                  String colstr = expr.substring(lb2 + 1, rb2).trim();

                  try {
                     int nrow = Integer.parseInt(rowstr) + row - orow;
                     int ncol = Integer.parseInt(colstr) + col - ocol;

                     expr = expr.substring(0, lb1) + "[" + nrow + "][" + ncol +
                         expr.substring(rb2);

                     idx = rb2;
                  } catch(Exception ex) {
                     // ignore if index is not integer
                  }
               }
            }
         }

         idx++;
      }

      idx = 0;

      // adjust cell range [row,col]:[row,col] references
      while((idx = expr.indexOf("]:[", idx)) >= 0) {
         int i1 = expr.lastIndexOf("[", idx);
         int i2 = expr.indexOf("]", idx + 3);
         int nextI = idx + 3;

         if(i1 > 0 && i2 > 0) {
            try {
               CellRange range = CellRange.parse(expr.substring(i1, i2+1));
               range.adjustIndex(insertRow, insertCol, row - orow, col - ocol);

               String rstr = range.toString();

               expr = expr.substring(0, i1) + rstr + expr.substring(i2 + 1);
               nextI = i1 + rstr.length();
            }
            catch(Exception ex) {
               // ignore exception if fail to parse range
            }
         }

         idx = nextI;
      }

      return expr;
   }

   private void merge(VSCompositeFormat oformat, VSCompositeFormat nformat) {
      merge(oformat.getDefaultFormat(), nformat.getDefaultFormat());
      merge(oformat.getUserDefinedFormat(), nformat.getUserDefinedFormat());
      //merge(oformat.getCSSFormat(), nformat.getCSSFormat());
   }

   private void merge(VSFormat oformat, VSFormat nformat) {
      if(nformat == null) {
         return;
      }

      if(!oformat.isBackgroundValueDefined() && nformat.isBackgroundValueDefined()) {
         oformat.setBackgroundValue(nformat.getBackgroundValue());
      }

      if(!oformat.isForegroundValueDefined() && nformat.isForegroundValueDefined()) {
         oformat.setForegroundValue(nformat.getForegroundValue());
      }

      if(!oformat.isFontValueDefined() && nformat.isFontValueDefined()) {
         oformat.setFontValue(nformat.getFontValue());
      }

      if(!oformat.isFormatValueDefined() && nformat.isFormatValueDefined())
      {
         oformat.setFormatValue(nformat.getFormatValue());
         oformat.setFormatExtentValue(nformat.getFormatExtentValue());
      }

      if(!oformat.isAlphaValueDefined() && nformat.isAlphaValueDefined()) {
         oformat.setAlphaValue(nformat.getAlphaValue());
      }

      if(!oformat.isAlignmentValueDefined() && nformat.isAlignmentValueDefined()) {
         oformat.setAlignmentValue(nformat.getAlignmentValue());
      }

      if(!oformat.isBordersValueDefined() && nformat.isBordersValueDefined()) {
         oformat.setBordersValue(nformat.getBordersValue());
      }

      if(!oformat.isBorderColorsValueDefined() && nformat.isBorderColorsValueDefined())
      {
         oformat.setBorderColorsValue(nformat.getBorderColorsValue());
      }

      if(!oformat.isWrappingValueDefined() && nformat.isWrappingValueDefined()) {
         oformat.setWrappingValue(nformat.getWrappingValue());
      }

      oformat.setSpan(nformat.getSpan());

      oformat.setBackgroundDefined(nformat.isBackgroundDefined());
      oformat.setFontDefined(nformat.isFontDefined());
      oformat.setForegroundDefined(nformat.isForegroundDefined());
      oformat.setTransDefined(nformat.isAlphaDefined());
      oformat.setAlignmentDefined(nformat.isAlignmentDefined());
      oformat.setBorderColorDefined(nformat.isBorderColorsDefined());
      oformat.setBorderDefined(nformat.isBordersDefined());
      oformat.setWrappingDefined(nformat.isWrappingDefined());
      oformat.setFormatDefined(nformat.isFormatDefined());

      if(!oformat.isPresenterValueDefined() && nformat.isPresenterValueDefined()) {
         oformat.setPresenterValue(nformat.getPresenterValue());
      }

      oformat.setPDefined(nformat.isPresenterDefined());
   }

   /**
    * The runtime data path should match the data path in table lens. For example, the
    * first detail row would have a index of 0 in region bug 1 in runtime (if there is
    * one header row). The path for region would have a row == 0, but runtime (in table
    * lens) would have row == 1.
    * @path data path in region
    */
   private static TableDataPath getRuntimeTableDataPath(TableDataPath path, int row, int col) {
      TableDataPath rpath = new TableDataPath(-1, path.getType(), path.getIndex());
      rpath.setRow(path.isRow());
      rpath.setCol(path.isCol());
      rpath.setDataType(path.getDataType());

      if(path.isRow()) {
         rpath.setPath(path.getPath());
      }
      else if(path.isCol()) {
         rpath.setPath(new String[]{"Column [" + col + "]"});
      }
      else {
         rpath.setPath(new String[]{"Cell [" + row + "," + col + "]"});
      }

      return rpath;
   }

   private TableLayout.RegionIndex reg;
   private CellBinding binding;
   private Dimension span;
   private VSCompositeFormat format;
   private VSCompositeFormat runtimeFormat;
   private Hyperlink hyperlink;
   private HighlightGroup highlight;
   private boolean isRow;
   private boolean isCol;
   private VSCompositeFormat rowFormat;
   private VSCompositeFormat runtimeRowFormat;
   private Hyperlink rowHyperlink;
   private HighlightGroup rowHighlight;
   private VSCompositeFormat colFormat;
   private VSCompositeFormat runtimeColFormat;
   private Hyperlink colHyperlink;
   private HighlightGroup colHighlight;
   private int row, col; // original row/column where info is retrieved
   private String field; // current cell binding field, group/aggregate/detail
}
