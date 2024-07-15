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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.report.io.viewsheet.VSCurrentSelectionHelper;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static inetsoft.uql.asset.internal.AssetUtil.defh;

/**
 * Current selection helper for excel.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ExcelCurrentSelectionHelper extends VSCurrentSelectionHelper {
   /**
    * Constructor.
    */
   public ExcelCurrentSelectionHelper(Workbook book, Sheet sheet) {
      this.book = book;
      this.sheet = sheet;
   }

   /**
    * Write the title.
    * @param info the specified SelectionListVSAssemblyInfo.
    */
   @Override
   protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = new VSCompositeFormat();

      if(finfo != null) {
         format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
      }

      String title = Tool.localize(info.getTitle());
      RichTextString hrText = PoiExcelVSUtil.createRichTextString(book, Tool.convertHTMLSymbol(title));
      Font font = PoiExcelVSUtil.getPOIFont(format, book, true);
      hrText.applyFont(font);

      ClientAnchor anchor = exporter.getAnchorPosition(info);
      Row row = PoiExcelVSUtil.getRow(anchor.getRow1(), sheet);
      Cell mcell = PoiExcelVSUtil.getCell(anchor.getCol1(), row);

      mcell.setCellValue(hrText);

      VSCompositeFormat paformat = info.getFormat();
      CellRangeAddress cellRange = new CellRangeAddress(anchor.getRow1(), anchor.getRow2(),
                                                        anchor.getCol1(), anchor.getCol2() - 1);

      PoiExcelVSUtil.addMergedRegion(sheet, cellRange);

      if(paformat.getBorders() == null) {
         int line = StyleConstants.THIN_LINE;
         paformat.getDefaultFormat().setBorders(new Insets(line, line, line, line));
      }

      PoiExcelVSUtil.setCellStyles(book, sheet, format, paformat, cellRange,
                                   null, font, format.getFont(),
                                   format.getForeground(), true,
                                   ExcelVSUtil.CELL_HEADER| ExcelVSUtil.CELL_TAIL,
                                   ExcelVSUtil.CELL_HEADER| ExcelVSUtil.CELL_TAIL,
                                   stylecache);
   }

   /**
    * Write the out selections.
    * @param info the specified SelectionListVSAssemblyInfo.
    */
   @Override
   protected void writeSelections(CurrentSelectionVSAssemblyInfo info) {
      PoiExcelVSExporter exporter = (PoiExcelVSExporter) getExporter();
      Dimension size = info.getPixelSize();
      Point position = info.getViewsheet().getPixelPosition(info);
      VSCompositeFormat pFormat = info.getFormat();
      boolean show = info.isShowCurrentSelection();
      String[] titles = info.getOutSelectionTitles();
      String[] values = info.getOutSelectionValues();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = new VSCompositeFormat();
      int titleH = info.isTitleVisible() ? info.getTitleHeight() : 0;
      size.height = PoiExcelVSUtil.floorY(size.height);
      int infoRows = (int) Math.floor((double) (size.height - titleH) / defh);

      if(finfo != null) {
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.OBJECT), false);
      }

      // floor + defh guarantees a row offset from the container.
      position = new Point(position.x, PoiExcelVSUtil.floorY(position.y + titleH + defh));

      for(int i = 0; i < infoRows; i++) {
         boolean force = show && i < titles.length;
         String title = force ? Tool.localize(titles[i]) : "";
         String value = force ?
            (values[i] == null ? catalog.getString("(none)") : values[i]) : "";
         VSCompositeFormat cellfmt = format;

         if(!force) {
            cellfmt = null;
            Insets borders = format.getBorders();

            if(borders != null) {
               boolean last = i == infoRows - 1;
               cellfmt = (VSCompositeFormat) format.clone();
               cellfmt.getUserDefinedFormat().setBorders(
                  new Insets(0, borders.left, last ? borders.bottom : 0, borders.right));
            }
         }

         VSCompositeFormat valueFormat = (VSCompositeFormat) pFormat.clone();
         PoiExcelVSUtil.writeTitleInContainer(position, size, i, title, value,
                                              cellfmt, sheet, book, exporter, null, valueFormat,
                                              AssetUtil.defh, force, info.getTitleRatio());
      }

      Viewsheet vs = info.getViewsheet();
      int y = position.y + (show ? values.length * defh : 0);

      for(String child: info.getAssemblies()) {
         Assembly cobj = vs.getAssembly(child);

         if(cobj != null) {
            Point pos = cobj.getPixelOffset();
            Dimension csize = cobj.getPixelSize();
            Object cinfo = cobj.getInfo();
            int nHeight = PoiExcelVSUtil.floorY(csize.height);
            csize.height = nHeight == 0 && csize.height > 0 ? AssetUtil.defh : nHeight;
            int ypos = y;

            if(info.isEmbedded() &&
               info.getViewsheet().getInfo() instanceof ViewsheetVSAssemblyInfo)
            {
               ViewsheetVSAssemblyInfo vsInfo =
                  (ViewsheetVSAssemblyInfo) info.getViewsheet().getInfo();
               Point vsPos = exporter.getViewsheet().getPixelPosition(vsInfo);
               Rectangle vsBounds = vsInfo.getAssemblyBounds();

               if(vsBounds != null) {
                  ypos += vsBounds.y;
               }

               if(vsPos != null) {
                  ypos -= vsPos.y;
               }
            }

            cobj.setPixelOffset(new Point(pos.x, ypos));

            if(cinfo instanceof SelectionListVSAssemblyInfo &&
               ((SelectionListVSAssemblyInfo)cinfo).getShowType() ==
                  SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
            {
               y += PoiExcelVSUtil.getSelectionTitleHeight((SelectionListVSAssemblyInfo)cinfo);
            }
            else {
               y += csize.height;
            }
         }
      }
   }

   /**
    * Write the object background.
    * @param info the current selection assembly info.
    */
   @Override
   protected void writeObjectBackground(CurrentSelectionVSAssemblyInfo info) {
      // do nothing
   }

   private Map<XSSFFormatRecord, XSSFCellStyle> stylecache = new HashMap<>();
   private Workbook book = null;
   private Sheet sheet = null;
}
