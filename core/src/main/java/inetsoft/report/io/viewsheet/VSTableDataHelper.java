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
package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.io.rtf.RichText;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

/**
 * Excel exporting helper for crosstab.
 *
 * @version 8.5, 8/16/2006
 * @author InetSoft Technology Corp
 */
public abstract class VSTableDataHelper extends ExporterHelper {
   /**
    * Template method for subclass to write crosstab cell to specific document.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param span the Dimension returned by getSpan().
    * @param pixelbounds pixel position and size.
    * @param row the cell's row.
    * @param col the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    * @param dispObj the cell's object (e.g. presenter) to be displayed.
    */
   protected abstract void writeTableCell(int startX, int startY,
                                          Dimension span,
                                          Rectangle2D pixelbounds,
                                          int row, int col,
                                          VSCompositeFormat format,
                                          String dispText,
                                          Object dispObj,
                                          Hyperlink.Ref hyperlink,
                                          VSCompositeFormat parentformat,
                                          Rectangle rec,
                                          Insets padding);

   /**
    * Template method for subclass to write crosstab cell to specific document.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param span the Dimension returned by getSpan().
    * @param pixelbounds pixel position and size.
    * @param row the cell's row.
    * @param col the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    * @param dispObj the cell's object (e.g. presenter) to be displayed.
    * @param fmtPattern the format pattern of the cell.
    */
   protected void writeTableCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText,
                                 Object dispObj, Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat, Rectangle rec,
                                 String fmtPattern, int[] columnPixelW,
                                 Insets padding)
   {
      writeTableCell(startX, startY, span, pixelbounds, row, col, format,
         dispText, dispObj, hyperlink, parentformat, rec, padding);
   }

   /**
    * Calculate the columns start position and width.
    * @param info the specified CrosstabVSAssemblyInfo.
    * @param info the specified table assembly info.
    */
   protected abstract int calculateColumnsPosition(TableDataVSAssemblyInfo info,
                                                   VSTableLens lens);

   /**
    * Write the data for crosstab assembly.
    * @param info the specified CrosstabVSAssemblyInfo.
    * @param lens the specified VSTableLens.
    */
   protected abstract void writeData(TableDataVSAssemblyInfo info, VSTableLens lens);

   /**
    * Write the data for crosstab assembly.
    * @param info the specified TableVSAssemblyInfo.
    * @param lens the specified VSTableLens.
    */
   protected abstract void drawObjectFormat(TableDataVSAssemblyInfo info,
                                            VSTableLens lens, boolean borderOnly);

   /**
    * Get the table range for export.
    */
   protected abstract Rectangle getTableRectangle(TableDataVSAssemblyInfo info,
                                                  VSTableLens lens);

   /**
    * Template method for subclass to write crosstab cell to specific document.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param span the Dimension returned by getSpan().
    * @param pixelbounds pixel position and size.
    * @param row the cell's row.
    * @param col the cell's col.
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    * @param dispObj the cell's object (e.g. presenter) to be displayed.
    */
   protected void writeTitleCell(int startX, int startY,
                                 Dimension span,
                                 Rectangle2D pixelbounds,
                                 int row, int col,
                                 VSCompositeFormat format,
                                 String dispText,
                                 Object dispObj,
                                 Hyperlink.Ref hyperlink,
                                 VSCompositeFormat parentformat,
                                 Rectangle rec)
   {
      writeTableCell(startX, startY, span, pixelbounds, row, col, format,
         dispText, dispObj, hyperlink, parentformat, rec, null);
   }

   /**
    * Get the object's bounds in pixels.
    */
   protected Rectangle2D getObjectPixelBounds(TableDataVSAssemblyInfo info,
      VSTableLens lens, CoordinateHelper vHelper)
   {
      boolean match = getExporter().isMatchLayout();
      Rectangle tableRange = getTableRectangle(info, lens);
      Dimension size = new Dimension(tableRange.width, tableRange.height);
      Rectangle2D oBounds = getPixelBounds(info, 0, 0, size, lens);
      Rectangle bounds = new Rectangle(oBounds.getBounds());
      int rows = tableRange.height;
      int height = 0;
      int[] heights = lens.getRowHeights();

      // rows contains header, but not contains title
      for(int i = 0; i < rows + 1; i++) {
         if(i == 0) {
            height = info.isTitleVisible() ? info.getTitleHeight() : 0;
            continue;
         }

         if(heights != null && heights.length > i - 1) {
            height += lens.getRowHeightWithPadding(lens.getWrappedHeight(i - 1, true),
                                                   i - 1);
         }
         else {
            height += getCellHeight(vs, info, i, lens);
         }
      }

      bounds.height = (int) (height * getPixelToPointRatio());

      if(match ||
         tableRange.height < info.getPixelSize().height ||
         tableRange.width < info.getPixelSize().width)
      {
         Rectangle2D rec = vHelper.getBounds(info);

         if(info.isShrink()) {
            Dimension d = new Dimension(getShrinkTableWidth(lens), 0);
            d = vHelper.getOutputSize(d);

            if(!getExporter().isMatchLayout()) {
               rec.setRect(rec.getX(), rec.getY(), d.getWidth(), bounds.getHeight());
            }
            else {
               rec.setRect(rec.getX(), rec.getY(), d.getWidth(),
                           Math.min(rec.getHeight(), bounds.getHeight()));
            }
         }
         else {
            if(!getExporter().isMatchLayout()) {
               rec.setRect(rec.getX(), rec.getY(), rec.getWidth(),
                           Math.max(rec.getHeight(), bounds.getHeight()));
            }
         }

         return rec;
      }

      return bounds;
   }

   private int getShrinkTableWidth(VSTableLens lens) {
      int totalWidth = 0;

      for(int i = 0; i < lens.getColCount(); i++) {
         totalWidth += columnPixelW[i];
      }

      return totalWidth;
   }

   /**
    * Get the pixel to point ratio.
    */
   protected double getPixelToPointRatio() {
      return 1;
   }

   protected void initRows(TableDataVSAssemblyInfo info, VSTableLens lens) {
      rowPixelH = calculateRowHeights(info, lens);
   }

   /**
    * Initialize column widths.
    */
   protected void initColumns(TableDataVSAssemblyInfo info,
                              VSTableLens lens, int[] columnStarts,
                              int[] columnWidths, int n)
   {
      columnPixelW = calculateColumnWidths(info, lens);
      columnPixelWidthOffsets = calculateColumnPixelWidthOffsets(columnPixelW);

      if(needDistributeWidth()) {
         distributeColumn(info, lens, columnStarts, columnWidths, n);
      }
   }

   private int[] calculateColumnPixelWidthOffsets(int[] columnPixelW) {
      final int[] offsets = new int[columnPixelW.length];
      int widthSum = 0;

      for(int i = 0; i < columnPixelW.length; i++) {
         widthSum += columnPixelW[i];
         offsets[i] = widthSum;
      }

      return offsets;
   }

   /**
    * Distribute the remaining column to the column list.
    */
   protected void distributeColumn(TableDataVSAssemblyInfo info,
                                   VSTableLens lens, int[] columnStarts,
                                   int[] columnWidths, int n)
   {
      if(n <= 0) {
         return;
      }

      double[] weights = new double[columnStarts.length];

      for(int i = 0; i < weights.length; i++) {
         double weight = Double.isNaN(info.getColumnWidth2(i, lens)) ?
            DEFAULT_COLWIDTH : info.getColumnWidth2(i, lens);

         double w = 0;

         for(int k = 0; k < columnWidths[i]; k++) {
            w += AssetUtil.defw;
         }

         weights[i] = w / weight;
      }

      // find the column with the smallest weight (assigned vs. actual width)
      int idx = 0;
      double curr = Double.MAX_VALUE;

      for(int i = 0; i < weights.length; i++) {
         if(weights[i] < curr) {
            idx = i;
            curr = weights[i];
         }
      }

      // add a column to this column
      columnWidths[idx]++;

      for(int i = idx + 1; i < columnStarts.length; i++) {
         columnStarts[i]++;
      }

      distributeColumn(info, lens, columnStarts, columnWidths, n - 1);
   }

   /**
    * Write the crosstab assembly to excel.
    * @param assembly the specified CrosstabVSAssembly to be exported.
    * @param lens the specified VSTableLens.
    */
   public void write(TableDataVSAssembly assembly, VSTableLens lens) {
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      // @by ankitmathur, Bug #1282
      // Ensure rows/widths array are populated for the table. With out this
      // information pre-populated, we can not return the rendered number of
      // lines per cell.
      if(lens != null) {
         lens.initTableGrid(info);
      }

      int infoWidth = CoordinateHelper.getAssemblySize(
         assembly, CoordinateHelper.getLensSize(lens, true)).width;
      calculateColumnsPosition(info, lens);
      drawObjectFormat(info, lens, false);
      writeTitle(info, infoWidth);

      if(lens != null) {
         writeData(info, lens);
      }

      // @by stephenwebster, draw after the data so the object borders are on top
      // of the data borders.
      drawObjectFormat(info, lens, true);
   }

   /**
    * Write the title for table assembly.
    * @param info the specified table assembly info.
    */
   protected void writeTitle(TableDataVSAssemblyInfo info, int totalColumnWidth) {
      if(!info.isTitleVisible()) {
         return;
      }

      Point position = info.getPixelOffset();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format =
         finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);

      // set the defaultfont to make the foreground take effect.
      if(format != null && format.getFont() == null) {
         format.getDefaultFormat().setFont(VSFontHelper.getDefaultFont());
      }

      Dimension titleBounds = new Dimension(totalColumnWidth, info.getTitleHeight());
      int viewsheetX =
         (int) (getViewsheetBounds() != null ? getViewsheetBounds().getX() : 0);
      int viewsheetY =
         (int) (getViewsheetBounds() != null ? getViewsheetBounds().getY() : 0);
      writeTitleCell(position.x - viewsheetX, position.y - viewsheetY, titleBounds,
                     null, 0, 0, format, Tool.localize(info.getTitle()), null, null,
                     info.getFormat(), new Rectangle(0, 0, 1, 1));
   }

   /**
    * Set the table (vsobject) borders to the left/bottom/right cell so the
    * border on the outer edge doesn't look like broken in to (different color)
    * segments.
    */
   protected void applyTableBorders(VSFormat cellfmt, VSAssemblyInfo info,
                boolean left, boolean bottom,
                boolean right, boolean top)
   {
      if(!left && !bottom && !right && !top) {
         return;
      }

      Insets borders = cellfmt.getBorders();
      BorderColors colors = cellfmt.getBorderColors();
      Insets tborders = info.getFormat().getBorders();
      BorderColors tcolors = info.getFormat().getBorderColors();

      if(borders == null) {
         borders = new Insets(0, 0, 0, 0);
      }

      if(colors == null) {
         colors = new BorderColors(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                                   VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                                   VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                                   VSAssemblyInfo.DEFAULT_BORDER_COLOR);
      }

      if(tborders != null) {
         if(left && tborders.left != 0 && !Double.isNaN(tborders.left)) {
            borders.left = tborders.left;

            if(tcolors != null) {
               colors.leftColor = tcolors.leftColor;
            }
         }

         if(bottom && tborders.bottom != 0 && !Double.isNaN(tborders.bottom)) {
            borders.bottom = tborders.bottom;

            if(tcolors != null) {
               colors.bottomColor = tcolors.bottomColor;
            }
         }

         if(right && tborders.right != 0 && !Double.isNaN(tborders.right)) {
            borders.right = tborders.right;

            if(tcolors != null) {
               colors.rightColor = tcolors.rightColor;
            }
         }

         if(top && tborders.top != 0 && !Double.isNaN(tborders.top)) {
            borders.top = tborders.top;

            if(tcolors != null) {
               colors.topColor = tcolors.topColor;
            }
         }
      }

      cellfmt.setBorders(borders);
      cellfmt.setBorderColors(colors);
   }

   /**
    * Calculate the pixel column widths.
    */
   protected int[] calculateColumnWidths(TableDataVSAssemblyInfo info,
                                         VSTableLens lens)
   {
      return ExcelVSUtil.calculateColumnWidths(
         getViewsheet(), info, lens, isFillColumns(info), getExporter().isMatchLayout(),
         needDistributeWidth());
   }

   /**
    * Calculate the pixel column widths.
    */
   protected int[] calculateRowHeights(TableDataVSAssemblyInfo info, VSTableLens lens) {
      return ExcelVSUtil.calculateRowHeights(
         getViewsheet(), info, lens, getExporter().isMatchLayout(), needDistributeWidth());
   }

   /**
    * Check need distribute width or not.
    */
   protected Boolean needDistributeWidth() {
      return true;
   }

   /**
    * Get the pixel bounds of a table cell.
    */
   protected Rectangle2D getPixelBounds(TableDataVSAssemblyInfo info,
                                        int r, int c, Dimension span,
                                        VSTableLens lens)
   {
      Point pos = getViewsheet().getPixelPosition(info.getPixelOffset());
      int titleHeight = info.isTitleVisible() ? info.getTitleHeight() : 0;
      int x = pos.x;
      int y = pos.y + titleHeight;
      int w = 0;
      int h = 0;
      int ncol = (span != null) ? span.width : 1;
      int nrow = (span != null) ? span.height : 1;

      for(int i = 0; i < c; i++) {
         x += columnPixelW[i];
      }

      // @by gregm factor in line count from the table lens, otherwise will only
      // print one line.
      for(int i = 0; i < r; i++) {
         int cellh = 0;

         if(i < cellHeights.size()) {
            cellh = cellHeights.getInt(i);
         }
         else {
            cellh = getCellHeight(getViewsheet(), info, i, lens);
            cellHeights.add(cellh);
         }

         y += cellh;
      }

      for(int i = 0; i < ncol && i < columnPixelW.length; i++) {
         w += columnPixelW[c + i];
      }

      for(int i = 0; i < nrow; i++) {
         // @by jasons, bug1387228981472, calculate the line count for each row,
         // because it may be different for each row, especially in a crosstab
         // table with more than one header column.
         int row = r + i;

         h += getCellHeight(getViewsheet(), info, row, lens);
      }

      int totalInfoY = pos.y + info.getPixelSize().height;

      if(y + h > totalInfoY) {
         h = totalInfoY - y;
      }

      int viewsheetX = (int) (getViewsheetBounds() != null ? getViewsheetBounds().getX() : 0);
      int viewsheetY = (int) (getViewsheetBounds() != null ? getViewsheetBounds().getY() : 0);

      return new Rectangle2D.Double(x - viewsheetX, y - viewsheetY, w, h);
   }

   // check if cell is wrapped, matches BaseTable.as
   protected boolean isCellWrapping(TableDataVSAssemblyInfo info) {
      return false;
   }

   protected int getWrappingCellHeight(TableDataVSAssemblyInfo info, VSTableLens lens,
                                       int row, int lines)
   {
      return getCellHeight(getViewsheet(), info, row, lens);
   }

   /**
    * Returns number of lines in given row.
    */
   protected int getLineCount(int row, VSTableLens lens) {
      if(lens == null) {
         return 1;
      }

      return lens.getLineCount(row, getExporter().isMatchLayout());
   }

   /**
    * Set the viewsheet to data helper.
    */
   protected void setViewsheet(Viewsheet vs) {
      this.vs = vs;
   }

   /**
    * Get the viewsheet from data helper.
    */
   protected Viewsheet getViewsheet() {
      return vs == null ? getExporter().getViewsheet() : vs;
   }

   /**
    * Get the row height of the cell.
    */
   protected int getCellHeight(Viewsheet vs, TableDataVSAssemblyInfo info,
                               int r, VSTableLens lens)
   {
      // get cell height from table lens.
      if(lens == null || lens.getRowHeights() == null || r >= lens.getRowHeights().length) {
         return lens != null ? (int) lens.getRowHeightWithPadding(AssetUtil.defh, r) :
            AssetUtil.defh;
      }

      int h = lens.getWrappedHeight(r, true);
      return (int) lens.getRowHeightWithPadding(Double.isNaN(h) ? AssetUtil.defh : h, r);
   }

   /**
    * Check whether to have the columns fill the whole table width.
    */
   protected boolean isFillColumns(TableDataVSAssemblyInfo info) {
      return true;
   }

   protected Hyperlink.Ref getHyperLink(VSTableLens lens, TableDataVSAssemblyInfo info,
                                        int r, int c)
   {
      if(info instanceof TableVSAssemblyInfo && !(info instanceof EmbeddedTableVSAssemblyInfo)) {
         Hyperlink rowHyperlink = info.getRowHyperlink();

         if(rowHyperlink != null && r >= lens.getHeaderRowCount()) {
            return new Hyperlink.Ref(rowHyperlink, lens, r, -1);
         }
      }

      return getHyperLink(lens, r, c);
   }

   /**
    * Get the hyperlink.
    */
   protected Hyperlink.Ref getHyperLink(VSTableLens lens, int r, int c) {
      ColumnIndexMap columnIndexMap = columnIndexMaps.computeIfAbsent(
         lens.hashCode(), k -> ColumnIndexMap.createColumnIndexMap(lens));
      Hyperlink.Ref link = ExportUtil.getTableCellHyperLink(lens, r, c, columnIndexMap);

      return link != null && link.getLinkType() == Hyperlink.WEB_LINK ? link : null;
   }

   /**
    * Init the annotation map with pattern "rowIdx_colIdx --> value".
    */
   protected void initAnnotation() {
      if(annotationMap != null || assembly == null) {
         return;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info instanceof BaseAnnotationVSAssemblyInfo) {
         List<String> annotations =
            ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();
         Viewsheet vs = VSUtil.getTopViewsheet(getViewsheet());

         if(vs == null) {
            return ;
         }

         annotationMap = new HashMap<>();

         for(String annotation : annotations) {
            AnnotationVSAssembly ass = (AnnotationVSAssembly) vs.getAssembly(annotation);

            if(ass == null) {
               continue;
            }

            AnnotationVSAssemblyInfo aInfo = (AnnotationVSAssemblyInfo) ass.getVSAssemblyInfo();

            if(aInfo.getType() != AnnotationVSAssemblyInfo.DATA) {
               continue;
            }

            if(!aInfo.isVisible()) {
               continue;
            }

            String key = aInfo.getRow() + "_" + aInfo.getCol();
            List<VSAssemblyInfo> list = annotationMap.get(key);

            if(list == null) {
               list = new ArrayList<>();
               annotationMap.put(key, list);
            }

            list.add(aInfo);
         }
      }
   }

   /**
    * Get annotation content.
    */
   protected String getAnnotationContent(int row, int col) {
      List<VSAssemblyInfo> infos = getAnnotation(row, col);
      Viewsheet vs = VSUtil.getTopViewsheet(getViewsheet());

      if(vs == null || infos == null) {
         return null;
      }

      StringBuilder buf = new StringBuilder();

      for(VSAssemblyInfo info : infos) {
         if(!(info instanceof AnnotationVSAssemblyInfo)) {
            continue;
         }

         AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) info;

         String rectangle = ainfo.getRectangle();
         AnnotationRectangleVSAssembly recAss =
            (AnnotationRectangleVSAssembly) vs.getAssembly(rectangle);
         AnnotationRectangleVSAssemblyInfo recInfo =
            (AnnotationRectangleVSAssemblyInfo) recAss.getVSAssemblyInfo();

         String content = "";

         if(recInfo != null) {
            Rectangle2D bounds = new Rectangle(0, 0, 100, 100);
            List list =
               AnnotationVSUtil.getAnnotationContent(vs, recInfo, bounds);

            for(int i = 0; i < list.size(); i++) {
               content += ((RichText) list.get(i)).getContent() + "\n";
            }
         }

         buf.append(content);
      }

      return buf.toString();
   }

   /**
    * Get the specified cell's annotation.
    */
   protected List<VSAssemblyInfo> getAnnotation(int row, int col) {
      return annotationMap == null ? null : annotationMap.get(row + "_" + col);
   }

   /**
    * Get cell data's format.
    */
   protected String getCellFormat(VSTableLens lens, int row, int col) {
      return ExportUtil.getCellFormat(
         lens, row, col, getExporter().isMatchLayout());
   }

   /**
    * Get current cell value.
    */
   protected Object getObject(VSTableLens lens, int row, int col, String fmt) {
      return ExportUtil.getObject(
         lens, row, col, fmt,
         getExporter().getFileFormatType() == FileFormatInfo.EXPORT_TYPE_EXCEL);
   }

   /**
    *Get current ViewsheetVSAssemblyInfo's assemblybounds
    */
   protected Rectangle getViewsheetBounds() {
      if(getViewsheet().getInfo() instanceof ViewsheetVSAssemblyInfo) {
         return ((ViewsheetVSAssemblyInfo) getViewsheet().getInfo()).getAssemblyBounds();
      }

      return null;
   }

   /**
    * @return the pixel offset of the right side of the column at colIndex.
    */
   protected int getPixelOffsetRightColumn(int colIndex) {
      return columnPixelWidthOffsets[colIndex];
   }

   /**
    * @return the pixel offset of the left side of the column at colIndex.
    */
   protected int getPixelOffsetLeftColumn(int colIndex) {
      if(colIndex == 0) {
         return 0;
      }
      else {
         return getPixelOffsetRightColumn(colIndex - 1);
      }
   }

   // Get cell format and fill background with white if the preceeding cells have background.
   // This is necessary for PDF export. If two cells adjacent to each other (e.g, with
   // [x,width] as [0,100] and [100,200]), filling the two rectangles will results in a slight
   // overlap (< 1px). If two rows with first column having background, and second column
   // has background on alternate rows, the right side of the first column on the second row
   // may appear protrude a little because it's not covered by the background of the second cell.
   // This method ensures once a cell has background, all subsequent cells have background so
   // there is no jugged edge.
   protected VSFormat getCellFormatWithBackground(VSTableLens tbl, int row, int col) {
      VSFormat fmt = tbl.getFormat(row, col);

      if(fmt != null && fmt.getBackground() == null) {
         for(int i = 0; i < col; i++) {
            VSFormat fmt2 = tbl.getFormat(row, i);

            if(fmt2 != null && fmt2.getBackground() != null) {
               fmt.setBackground(Color.WHITE);
            }
         }
      }

      return fmt;
   }

    /**
    * Get row count of table lens.
    * @param lens the specified VSTableLens.
    */
   protected int getRowCount(VSTableLens lens) {
      return lens.getRowCount();
   }

   protected Insets getTitlePadding() {
      if(assembly != null) {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         Insets padding = null;

         if(info instanceof TitledVSAssemblyInfo) {
            padding = ((TitledVSAssemblyInfo) info).getTitlePadding();
         }

         return padding;
      }

      return null;
   }

   public static final int DEFAULT_COLWIDTH = ExcelVSUtil.DEFAULT_COLWIDTH;
   protected VSAssembly assembly;
   private Map<String, List<VSAssemblyInfo>> annotationMap = null;
   protected int[] columnPixelW;
   protected int[] rowPixelH;
   private IntList cellHeights = new IntArrayList();
   private Viewsheet vs;
   private int[] columnPixelWidthOffsets;
   private Map<Integer, ColumnIndexMap> columnIndexMaps = new HashMap<>();
}
