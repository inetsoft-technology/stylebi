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

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.AttributeDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.*;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.gui.viewsheet.cylinder.VSCylinder;
import inetsoft.report.gui.viewsheet.gauge.VSGauge;
import inetsoft.report.gui.viewsheet.slidingscale.VSSlidingScale;
import inetsoft.report.gui.viewsheet.thermometer.VSThermometer;
import inetsoft.report.internal.ParameterTool;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr.HighlightTableLens;
import inetsoft.report.io.excel.SheetMaxRowsException;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.report.io.viewsheet.pdf.PDFVSExporter;
import inetsoft.report.io.viewsheet.svg.PNGVSExporter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.report.script.viewsheet.ChartVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.util.*;
import inetsoft.util.audit.*;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.log.LogContext;
import inetsoft.util.log.LogUtil;
import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract exporter for viewsheet.
 *
 * @version 8.5, 8/23/2006
 * @author InetSoft Technology Corp
 */
public abstract class AbstractVSExporter implements VSExporter {
   /**
    * Get viewsheet exporter. Setup workbook and show level exporter and stream
    * etc. This is the builder method to return appropriate type of exporter.
    * @param type excel or ppt.
    * @param theme flex color theme, e.g. blue, vista, ...
    * @param stream the specified OutputStream.
    */
   public static VSExporter getVSExporter(int type, String theme,
                                          OutputStream stream)
   {
      return getVSExporter(type, theme, stream, false);
   }

   /**
    * Get viewsheet exporter. Setup workbook and show level exporter and stream
    * etc. This is the builder method to return appropriate type of exporter.
    * @param type excel or ppt.
    * @param theme flex color theme, e.g. blue, vista, ...
    * @param stream the specified OutputStream.
    * @param print flag that provides a hint to the exporter that the file is
    *              being printed.
    */
   public static VSExporter getVSExporter(int type, String theme,
                                          OutputStream stream, boolean print)
   {
      return getVSExporter(type, theme, stream, print, null);
   }

   /**
    * Get viewsheet exporter. Setup workbook and show level exporter and stream
    * etc. This is the builder method to return appropriate type of exporter.
    * @param type excel or ppt.
    * @param theme flex color theme, e.g. blue, vista, ...
    * @param stream the specified OutputStream.
    * @param print flag that provides a hint to the exporter that the file is
    *              being printed.
    */
   public static VSExporter getVSExporter(int type, String theme,
                                          OutputStream stream, boolean print, Object extraConfig)
   {
      AbstractVSExporter exporter = null;
      fileType = type;

      if(type == FileFormatInfo.EXPORT_TYPE_EXCEL) {
         exporter = OfficeExporterFactory.getInstance().createExcelExporter(stream);
      }
      else if(type == FileFormatInfo.EXPORT_TYPE_POWERPOINT) {
         exporter = OfficeExporterFactory.getInstance().createPowerpointExporter(stream);
      }
      else if(type == FileFormatInfo.EXPORT_TYPE_PDF) {
         exporter = new PDFVSExporter(stream);
         ((PDFVSExporter) exporter).setPrintOnOpen(print);
      }
      else if(type == FileFormatInfo.EXPORT_TYPE_PNG) {
         exporter = new PNGVSExporter(stream);
      }
      else if(type == FileFormatInfo.EXPORT_TYPE_HTML) {
         exporter = new HTMLVSExporter(stream);
      }
      else if(type == FileFormatInfo.EXPORT_TYPE_CSV) {
         exporter = new CSVVSExporter(stream, extraConfig);
      }
      else {
         throw new RuntimeException("Unsupported document format: " + type);
      }

      try {
         exporter.theme = new FlexTheme(theme);
      }
      catch(Exception ex) {
         LOG.error("Failed to create theme", ex);
      }

      return exporter;
   }

   /**
    * Execute the dynamic properties so it could be applied differently
    * for export/print.
    */
   protected void executeViewDynamicValues(Viewsheet vsheet, ViewsheetSandbox box)
         throws Exception
   {
      Assembly[] assemblies = vsheet.getAssemblies(false);

      for(int i = 0; box != null && i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];

         // make sure EGraph is populated from binding if script accesses it (48662).
         if(assembly instanceof ChartVSAssembly) {
            ViewsheetScope scope = box.getScope();
            ChartVSAScriptable chartScope = (ChartVSAScriptable)
               scope.getVSAScriptable(assembly.getName());
            VGraphPair graphPair = box.getVGraphPair(assembly.getAbsoluteName());

            if(chartScope != null && graphPair != null && graphPair.isChangedByScript0()) {
               chartScope.setGraphCreator(graphPair.getGraphCreator());
            }
         }

         if(assembly instanceof Viewsheet) {
            executeViewDynamicValues((Viewsheet) assembly,
                                     box.getSandbox(assembly.getName()));
         }
         else {
            List<DynamicValue> dvalues = assembly.getViewDynamicValues(true);
            box.executeDynamicValues(assembly.getName(), dvalues);
         }
      }
   }

   /**
    * Specify the size and cell size of sheet.
    * @param vsheet the Viewsheet to be exported.
    * @param sheet the specified sheet name.
    * @param box the specified viewsheet sandbox.
    */
   protected void prepareSheet(Viewsheet vsheet, String sheet, ViewsheetSandbox box)
      throws Exception
   {
      box.prepareForExport();
      Assembly[] assemblies = viewsheet.getAssemblies(true);

      for(int i = 0; box != null && i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];

         // make sure EGraph is populated from binding if script accesses it (48662).
         if(assembly instanceof ChartVSAssembly) {
            ViewsheetScope scope = box.getScope();
            ChartVSAScriptable chartScope = (ChartVSAScriptable)
               scope.getVSAScriptable(assembly.getName());
            VGraphPair graphPair = box.getVGraphPair(assembly.getAbsoluteName());

            if(chartScope != null && graphPair != null && graphPair.isChangedByScript0()) {
               chartScope.setGraphCreator(graphPair.getGraphCreator());
            }
         }

         box.executeScript(assembly);

         if(assembly instanceof CalcTableVSAssembly) {
            continue;
         }

         if(assembly instanceof TableDataVSAssembly ) {
            // for crosstab assembly, the info can not make sure
            // absolute correct, like column count and column width,
            // so here just update the info same as flex
            updateColumns((TableDataVSAssembly) assembly, box);
         }
      }

      if(!isMatchLayout()) {
         List<VSAssembly> expandable = new ArrayList<>();

         // here add all expand able assemblies, then sort them by bottom or
         // right comparator, and expand each assembly by order
         for(int i = 0; box != null && i < assemblies.length; i++) {
            VSAssembly assembly = (VSAssembly) assemblies[i];

            if(needExport(assembly)) {
               if(assembly instanceof TableDataVSAssembly || assembly instanceof ChartVSAssembly) {
                  expandable.add(assembly);
               }
               else if(isExpandSelections() &&
                  (assembly instanceof CurrentSelectionVSAssembly ||
                     assembly instanceof SelectionListVSAssembly ||
                     assembly instanceof SelectionTreeVSAssembly))
               {
                  expandable.add(assembly);
               }
            }
         }

         expandable.sort(bottomComparator);
         expandAll(expandable, box, true);
         expandable.sort(rightComparator);
         expandAll(expandable, box, false);
      }
      else {
         for(Assembly assembly : assemblies) {
            if(!(assembly instanceof ChartVSAssembly)) {
               continue;
            }

            VGraphPair pair = box.getVGraphPair(assembly.getAbsoluteName());

            if(ExportUtil.needExpandChart(pair)) {
               expandChart((ChartVSAssembly) assembly, box, true);
               expandChart((ChartVSAssembly) assembly, box, false);
            }
         }
      }

      addTableMaxRowMessage(assemblies, box);
      TextVSAssembly warningText = viewsheet.getWarningTextAssembly(false);

      if(warningText != null) {
         VSUtil.setAutoSizeTextHeight(warningText.getInfo(), viewsheet);
         viewsheet.adjustWarningTextPosition();
      }

      ArrayList<Viewsheet> list = new ArrayList<>();
      getViewsheets(viewsheet, list);

      for(Viewsheet vs : list) {
         vs.getInfo().setPixelSize(null);
         vs.layout();
      }

      prepareAnnotation(box);
   }

   private void addTableMaxRowMessage(Assembly[] assemblies, ViewsheetSandbox box)
      throws Exception
   {
      if(assemblies != null && box != null) {
         VSTableLens lens;
         TextVSAssembly textVSAssembly = null;

         for(Assembly assembly : assemblies) {
            String name = assembly.getAbsoluteName();

            if(assembly instanceof TableDataVSAssembly && this.needExport((VSAssembly) assembly)) {
               lens = box.getVSTableLens(name, false, 1);
               lens = getRegionTableLens(lens, (TableDataVSAssembly) assembly, box);
               VSEventUtil.addWarningText(lens, box, name, true);
            }
            else if(this.needExport((VSAssembly) assembly)) {
               String limitMessage = box.getLimitMessage(name);

               if(Tool.isEmptyString(limitMessage)) {
                  continue;
               }

               textVSAssembly = textVSAssembly == null ? box.getViewsheet().getWarningTextAssembly()
                  : textVSAssembly;

               if(textVSAssembly != null && textVSAssembly.getTextValue() != null &&
                  !textVSAssembly.getTextValue().contains(limitMessage))
               {
                  textVSAssembly.setTextValue(textVSAssembly.getTextValue() + "\n" + limitMessage);
               }
            }
         }
      }
   }

   /**
    * Prepare annotation.
    */
   private void prepareAnnotation(ViewsheetSandbox box) throws Exception {
      Assembly[] assemblies = viewsheet.getAssemblies(true, true);

      for(int i = 0; box != null && i < assemblies.length; i++) {
         VSAssembly assembly = (VSAssembly) assemblies[i];

         if(!(assembly instanceof AnnotationVSAssembly)) {
            continue;
         }

         AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) assembly.getInfo();
         VSAssembly base = (VSAssembly) AnnotationVSUtil.getBaseAssembly(
            viewsheet, assembly.getAbsoluteName());

         if(base == null || !(base.getInfo() instanceof BaseAnnotationVSAssemblyInfo)) {
            continue;
         }

         if(!needExport(base) || !base.isEnabled()) {
            ainfo.setVisible(false);
            continue;
         }

         if(ainfo.getType() == AnnotationVSAssemblyInfo.DATA) {
            String name = base.getAbsoluteName();

            if(base instanceof TableDataVSAssembly) {
               // this should match the AnnotationVSUtil.getAnnotationDataValue
               // otherwise value formatting may cause cell not to match
               // fix Bug #32150, should get TableLens that just have visiable rows and cols.
               TableLens lens = box.getVSTableLens(name, false, 1);
               lens = getRegionTableLens(lens, (TableDataVSAssembly) base, box);
               int[] ridx = AnnotationVSUtil.getRuntimeIndex(box, base, lens, ainfo);
               ainfo.setVisible(ridx != null);

               if(ridx != null) {
                  ainfo.setRow(ridx[0]);
                  ainfo.setCol(ridx[1]);
               }
            }
            else if(base instanceof ChartVSAssembly) {
               ChartVSAssembly chart = (ChartVSAssembly) base;
               ChartVSAssemblyInfo info =
                  (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
               info.setScalingRatio(new DimensionD(1.0, 1.0));
               VGraphPair pair = box.getVGraphPair(name);
               DataSet data = pair != null ? pair.getData() : (DataSet) box.getData(name);
               int ridx = AnnotationVSUtil.getRuntimeRowIndex(data, ainfo, chart);
               ainfo.setVisible(ridx != -1);

               if(ridx > -1) {
                  ainfo.setRow(ridx);

                  if(pair != null) {
                     boolean hscrollable =
                        GraphUtil.isHScrollable(pair.getRealSizeVGraph(), info.getVSChartInfo());
                     boolean vscrollable =
                        GraphUtil.isVScrollable(pair.getRealSizeVGraph(), info.getVSChartInfo());

                     if(isMatchLayout() && !ExportUtil.needExpandChart(pair) &&
                        (hscrollable || vscrollable))
                     {
                        AnnotationVSUtil.adjustAnnotationPosition(box.getViewsheet(), ainfo, pair);
                     }
                  }

                  // don't move annotation inside since bounds include annotation already
                  /*
                  VSChartInfo cinfo = chart.getVSChartInfo();
                  ChartArea area = pair == null || !pair.isCompleted()
                     ? null : new ChartArea(pair, null, cinfo, null, false);

                  if(area != null) {
                     AnnotationVSUtil.resetAnnotationPosition(viewsheet, base, area, ainfo, ridx, null);

                     // position is reset relative to the base assembly. need to modify the position
                     // to be relative to the parent viewsheet top/left.
                     final Point chartPos = info.getViewsheet().getPixelPosition(info);
                     final Assembly line = viewsheet.getAssembly(ainfo.getLine());
                     final Assembly rectangle = viewsheet.getAssembly(ainfo.getRectangle());

                     if(line != null) {
                        line.getInfo().getPixelOffset().translate(chartPos.x, chartPos.y);
                     }

                     // this change rectangle position is absulate position.
                     if(rectangle != null) {
                        rectangle.getInfo().getPixelOffset().translate(chartPos.x, chartPos.y);
                     }

                     if(fileType != FileFormatInfo.EXPORT_TYPE_EXCEL &&
                        fileType != FileFormatInfo.EXPORT_TYPE_SNAPSHOT)
                     {
                        Rectangle viewRect = new Rectangle(viewsheet.getPreferredBounds(true));
                        viewRect.translate(chartPos.x, chartPos.y);
                        Point newPos = moveAnnotationWithinRect(ainfo, viewRect);
                        Point oldPos = new Point(ainfo.getPixelOffset());

                        if(line != null) {
                           int offX = newPos.x - oldPos.x;
                           int offY = newPos.y - oldPos.y;
                           LineVSAssemblyInfo linfo = (LineVSAssemblyInfo) line.getInfo();
                           linfo.getEndPos().translate(-offX, -offY);
                           ((LineVSAssembly) line).updateAnchor(viewsheet);
                        }

                        AnnotationVSUtil.refreshAnnoPosition(viewsheet, ainfo, newPos);
                     }
                  }
                  */
               }
            }
         }

         if(ainfo.getType() == AnnotationVSAssemblyInfo.ASSEMBLY ||
            ainfo.getType() == AnnotationVSAssemblyInfo.DATA)
         {
            AnnotationVSUtil.refreshAssemblyAnnoPosition(viewsheet, ainfo);
         }
      }
   }

   /**
    * Expand assembly.
    */
   private void expandAll(List<VSAssembly> expandable, ViewsheetSandbox box, boolean exprow)
      throws Exception
   {
      for(int i = 0; i < expandable.size(); i++) {
         VSAssembly assembly = expandable.get(i);

         if(assembly instanceof TableDataVSAssembly) {
            expandTable((TableDataVSAssembly) assembly, box, exprow);
         }
         else if(assembly instanceof ChartVSAssembly) {
            expandChart(assembly, box, exprow);
         }
         else if(exprow && (assembly instanceof SelectionListVSAssembly ||
            assembly instanceof SelectionTreeVSAssembly))
         {
            expandSelectionAssembly(assembly);
         }
      }

      for(int i = 0; i < expandable.size(); i++) {
         VSAssembly assembly = expandable.get(i);

         if(exprow && assembly instanceof CurrentSelectionVSAssembly) {
            expandCurrentSelection((CurrentSelectionVSAssembly) assembly);
         }
      }
   }

   /**
    * Update the crosstab column count and column widths.
    */
   private void updateColumns(TableDataVSAssembly ass, ViewsheetSandbox box) throws Exception {
      VSAssemblyInfo vinfo = ass == null ? null : ass.getVSAssemblyInfo();

      if(!(vinfo instanceof TableDataVSAssemblyInfo)) {
         return;
      }

      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) vinfo;
      int pixelW = ass.getViewsheet().getPixelSize(info).width;
      int ncolumn = 0;
      VSTableLens lens = box.getVSTableLens(ass.getAbsoluteName(), false);

      if(lens == null) {
         return;
      }

      lens.initTableGrid(info);
      TableDataDescriptor desc = lens.getDescriptor();
      Set<TableDataPath> processed = new HashSet<>();
      int colCount = lens.getColCount();
      int headerRowCount = lens.getHeaderRowCount();

      for(int i = 0; pixelW > 0 && i < colCount; i++) {
         boolean useDefault = false;
         double cw = info.getColumnWidth2(i, lens);

         if(Double.isNaN(cw)) {
            useDefault = true;
         }

         cw = useDefault ? AssetUtil.defw : cw;

         if(isMatchLayout()) {
            if(lens.moreRows(headerRowCount)) {
               Object data = lens.getObject(headerRowCount, i);

               // Bug #36640. The table last col should be filled with width.
               if(info instanceof TableVSAssemblyInfo
                  && data instanceof PresenterPainter
                  && i == colCount - 1)
               {
                  // -1: BaseTable.updateDisplayColumnWidth
                  // -4: VSTableCell.presenter
                  cw = pixelW - 5; // cw = pixelW -1 -4
               }
            }

            // make sure when match layout, the bounds will not out, and
            // when not match layout, the next cell will just match the default width
            // last column should fill the remaining width when width of it is not defined.
            if(i == colCount - 1 && useDefault) {
               cw = pixelW;
            }
            else {
               cw = Math.min(cw, pixelW);
            }
         }

         boolean isHiddenColumn = isHiddenColumn(info, lens, i);

         if(desc != null && !processed.contains(desc.getColDataPath(i)) && !isHiddenColumn) {
            info.setColumnWidthValue2(i, cw, lens);
            processed.add(desc.getColDataPath(i));
         }

         pixelW -= cw;
         ncolumn++;
      }

      if(vinfo instanceof CrosstabVSAssemblyInfo) {
         CrosstabVSAssemblyInfo cinfo = (CrosstabVSAssemblyInfo) vinfo;
         cinfo.setColumnCount(ncolumn);
      }
   }

   // Check column is hidden or not, for calctable, hidden column can only create by convert from
   // crosstab to calc, it can not create on its own action.
   private boolean isHiddenColumn(TableDataVSAssemblyInfo info, TableLens lens, int col) {
      if(info instanceof CrosstabVSAssemblyInfo) {
         return ((CrosstabVSAssemblyInfo) info).isColumnHidden(col, lens);
      }

      if(info instanceof CalcTableVSAssemblyInfo) {
         CalcTableVSAssemblyInfo cinfo = (CalcTableVSAssemblyInfo) info;
         double w = cinfo.getColumnWidth(col);

         if(w == 0 && !Double.isNaN(w)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check is user defined column width(s).
    */
   private boolean isUserDefinedWidth(VSAssemblyInfo vinfo) {
      if(vinfo instanceof TableDataVSAssemblyInfo) {
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) vinfo;
         return info.isUserDefinedWidth();
      }

      return false;
   }

   private void getViewsheets(Viewsheet vs, ArrayList<Viewsheet> list) {
      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof Viewsheet) {
            getViewsheets((Viewsheet) assembly, list);
         }
      }

      list.add(vs);
   }

   /**
    * Get image of specified assembly.
    */
   protected BufferedImage getImage(VSAssembly assembly) {
      return getImage(assembly, null);
   }

   /**
    * Get image of specified assembly.
    */
   protected BufferedImage getImage(VSAssembly assembly, Image rawImage) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Viewsheet vs = info.getViewsheet();
      int type = assembly.getAssemblyType();
      VSObject obj = null;
      BufferedImage img = null;

      switch(type) {
      case AbstractSheet.RADIOBUTTON_ASSET:
         obj = new VSRadioButton(vs);
         break;
      case AbstractSheet.CHECKBOX_ASSET:
         obj = new VSCheckBox(vs);
         break;
      case AbstractSheet.CALENDAR_ASSET:
         obj = new VSCalendar(vs);
         break;
      case AbstractSheet.CHART_ASSET:
         obj = new VSChart(viewsheet);
         obj.setDpiScale(2); // should match AbstractVSExporter pair.getImage()
         ((VSChart) obj).setRawImage(rawImage);
         break;
      case AbstractSheet.COMBOBOX_ASSET:
         obj = new VSComboBox(vs);
         break;
      case AbstractSheet.CYLINDER_ASSET:
         obj = VSCylinder.getCylinder(((CylinderVSAssemblyInfo) info).getFace());
         break;
      case AbstractSheet.GAUGE_ASSET:
         obj = VSGauge.getGauge(((GaugeVSAssemblyInfo) info).getFace());
         VSGauge.setGaugeName(info.getName());
         break;
      case AbstractSheet.IMAGE_ASSET:
         obj = new VSImage(vs);
         ((VSImage) obj).setRawImage(rawImage);
         break;
      case AbstractSheet.SLIDER_ASSET:
         obj = new VSSlider(vs);
         break;
      case AbstractSheet.SLIDING_SCALE_ASSET:
         obj = VSSlidingScale.getSlidingScale(
            ((SlidingScaleVSAssemblyInfo) info).getFace());
         break;
      case AbstractSheet.SPINNER_ASSET:
         obj = new VSSpinner(vs);
         break;
      case AbstractSheet.THERMOMETER_ASSET:
         obj = VSThermometer.getThermometer(
            ((ThermometerVSAssemblyInfo) info).getFace());
         break;
      case AbstractSheet.TIME_SLIDER_ASSET:
         obj = new VSTimeSlider(vs);
         break;
      case AbstractSheet.TAB_ASSET:
         while(vs.isEmbedded()) {
            vs = vs.getViewsheet();
         }

         VSUtil.fixSelected((TabVSAssemblyInfo) info, vs, true);
         obj = new VSTab(vs);
         break;
      case AbstractSheet.GROUPCONTAINER_ASSET:
         obj = new VSGroupContainer(vs);
         break;
      case AbstractSheet.LINE_ASSET:
      case AbstractSheet.ANNOTATION_LINE_ASSET:
         obj = new VSLine(vs);
         break;
      case AbstractSheet.RECTANGLE_ASSET:
      case AbstractSheet.ANNOTATION_RECTANGLE_ASSET:
         obj = new VSRectangle(vs);
         break;
      case AbstractSheet.OVAL_ASSET:
         obj = new VSOval(vs);
         break;
      case AbstractSheet.TEXT_ASSET:
         img = getPresenterImage((TextVSAssembly) assembly);
         break;
      }

      if(obj != null) {
         obj.setViewsheet(vs);
         obj.setTheme(theme);
         obj.setAssemblyInfo(info);

         resetTimeSliderSize(assembly, obj);

         if(obj instanceof VSFloatable) {
            // background should be drawn after shadow.
            if(obj instanceof VSGauge) {
               ((VSGauge) obj).setDrawbg(false);
            }

            img = (BufferedImage) ((VSFloatable) obj).getImage(true);
         }
         else if(obj instanceof VSCompound) {
            img = (BufferedImage) ((VSCompound) obj).getImage();
         }
      }

      return img;
   }

   protected BufferedImage getPresenterImage(TextVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      PresenterPainter painter = VSUtil.createPainter(assembly);
      Dimension size = info.getPixelSize();

      return ExportUtil.getPainterImage(painter, size.width, size.height,
                                        info.getFormat());
   }

   /**
    * Recalculate the time slider size in pixel layout.
    */
   protected void resetTimeSliderSize(VSAssembly assembly, VSObject obj) {
      int type = assembly.getAssemblyType();

      if(isPixelLayout() && type == AbstractSheet.TIME_SLIDER_ASSET &&
         assembly.getContainer() instanceof CurrentSelectionVSAssembly)
      {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         VSAssembly cassembly = assembly.getContainer();
         VSAssemblyInfo cinfo = cassembly.getVSAssemblyInfo();
         Dimension pixelsize = cinfo.getPixelSize();
         pixelsize = new Dimension(pixelsize.width, info.getPixelSize().height);
         obj.setPixelSize(pixelsize);
      }
   }

   /**
    * Check if matches layout.
    * @return <tt>true</tt> if matches layout, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isMatchLayout() {
      return match;
   }

   /**
    * Set whether matches layout.
    * @param match <tt>true</tt> if matches layout, <tt>false</tt> otherwise.
    */
   @Override
   public void setMatchLayout(boolean match) {
      this.match = match;
   }

   @Override
   public boolean isExpandSelections() {
      return expandSelections;
   }

   @Override
   public void setExpandSelections(boolean expandSelections) {
      this.expandSelections = expandSelections;
   }

   @Override
   public boolean isOnlyDataComponents() {
      return onlyDataComponents;
   }

   @Override
   public void setOnlyDataComponents(boolean onlyDataComponents) {
      this.onlyDataComponents = onlyDataComponents;
   }

   /**
    * Check if output is laid out in at pixel position or grid position.
    */
   protected boolean isPixelLayout() {
      return true;
   }

   /**
    * Check if should log execution.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isLogExecution() {
      return log;
   }

   /**
    * Set whether should log execution.
    * @param log <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   @Override
   public void setLogExecution(boolean log) {
      this.log = log;
   }

   /**
    * Check if should log export.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isLogExport() {
      return logExport;
   }

   /**
    * Set whether should log export.
    * @param log <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   @Override
   public void setLogExport(boolean log) {
      this.logExport = log;
   }

   /**
    * Get the region table lens.
    * @param data the specified table lens.
    * @param table the specified table data assembly.
    */
   protected VSTableLens getRegionTableLens(TableLens data, TableDataVSAssembly table,
                                            ViewsheetSandbox box)
   {
      HighlightTableLens hlens = (HighlightTableLens) Util.getNestedTable(
         data, HighlightTableLens.class);

      if(hlens != null) {
         hlens.setQuerySandbox(box.getConditionAssetQuerySandbox(table.getViewsheet()));
      }

      if(!needRegionLens() && data instanceof VSTableLens || data == null) {
         return (VSTableLens) data;
      }

      VSAssemblyInfo info = table.getVSAssemblyInfo();

      if(data instanceof VSTableLens) {
         ((VSTableLens) data).initTableGrid(info);
      }

      int width = getRegionColCount(table, data);
      int height = getRegionRowCount(table, data);
      return new RegionTableLens(data, height, width);
   }

   /**
    * Check need use the region table lens or not.
    */
   protected boolean needRegionLens() {
      return match;
   }

   /**
    * Get the number of cols to display in a table.
    */
   protected int getRegionColCount(TableDataVSAssembly table, TableLens data) {
      if(isMatchLayout()) {
         TableDataVSAssemblyInfo info =
            (TableDataVSAssemblyInfo) table.getVSAssemblyInfo();

         int totalWidth = table.getPixelSize().width;
         int w = 0;
         int idx = 0;

         for(idx = 0; idx < data.getColCount(); idx++) {
            w += Double.isNaN(info.getColumnWidth2(idx, data)) ?
               AssetUtil.defw : info.getColumnWidth2(idx, data);

            if(w >= totalWidth) {
               break;
            }
         }

         return idx + 1;
      }
      else {
         return data.getColCount();
      }
   }

   /**
    * Get the number of rows to display in a table.
    */
   protected int getRegionRowCount(TableDataVSAssembly table, TableLens data) {
      if(isMatchLayout() && data instanceof VSTableLens) {
         Dimension size = table.getPixelSize();
         VSTableLens vdata = (VSTableLens) data;

         if(!isPixelLayout()) {
            return Math.max(size.height - AssetUtil.defh, 0);
         }

         TableDataVSAssemblyInfo info =
            (TableDataVSAssemblyInfo) table.getInfo();

         int hrow = 1;

         if(table instanceof CrosstabVSAssembly) {
            VSCrosstabInfo vsinfo =
               ((CrosstabVSAssembly) table).getVSCrosstabInfo();
            hrow = vsinfo != null ?
               Math.max(1, vsinfo.getRuntimeColHeaders().length) : 0;
         }
         else if(table instanceof CalcTableVSAssembly) {
            hrow = ((CalcTableVSAssemblyInfo) info).getHeaderRowCount();
         }

         Viewsheet vs = table.getViewsheet();
         int h = 0;
         int expandH = 0;
         int headerHeight = 0;

         // @by yanie:
         // consider the header row height is set to 0 via script
         // if in this case, we have to add more details rows
         // int invisibleHR = 0;
         // @by klause:
         // when warp text, the header's line count may be not 1.
         int hLineCount = 0;
         int realLineCount = 0;

         for(int i = 0; i < hrow; i++) {
            int rh = AssetUtil.defh;
            int dh = vs.getDisplayRowHeight(true, table.getName(), i);
            int lineCount = vdata.getLineCount(i);
            realLineCount += lineCount;

            if(dh == 0) {
               continue;
            }
            else if(rh < dh) {
               expandH += dh - rh;
            }

            hLineCount += lineCount;
            headerHeight += dh;
         }

         int tableRowsHeight = size.height - headerHeight;

         if(info.isTitleVisible()) {
            tableRowsHeight -= info.getTitleHeight();
         }

         int displayRowCount = 0;

         for(int i = hLineCount; i < data.getRowCount(); i++) {
            // fix bug#53192 If the current line contains a null value, the line should not be displayed when export,
            // so the total line height should not accumulate the current line height.
            // When calculating the number of display rows, should add the current row.
            if(!checkDisplayRow(data, i)) {
               displayRowCount++;

               continue;
            }

            h += vs.getDisplayRowHeight(false, table.getName());

            if(h >= tableRowsHeight) {
               h = tableRowsHeight;
               break;
            }
         }

         int displayRowHeight =
            vs.getDisplayRowHeight(false, table.getName());

         // @by stephenwebster, For Bug #8610
         // If all the header rows are hidden, we have to make sure we get the correct
         // first detail row height instead of the first header row height.
         if(hLineCount == 0) {
            displayRowHeight = vs.getDisplayRowHeight(false, table.getName());

            // @by stephenwebster, special case when all header rows are not visible.
            // Make sure to include the number of header row lines in the row count.
            // Use realLineCount to keep track of the real height that the header rows
            // would consume.  This is necessary since printing iterates all rows
            // including header rows.  If the row count for the RegionTableLens does not
            // include the header rows, and you hide the header rows, you end up short
            // when calculating the printing. Stop-gap solution here.
            if(displayRowHeight > 0) {
               hLineCount = realLineCount;
            }
         }

         // @by klause:
         // if the first detail row height is 0, the other detail row height
         // is alse 0, so the region row number is header line count row number.
         return displayRowHeight == 0 ? hLineCount :
            (int) Math.round((double) h / displayRowHeight) + hLineCount + displayRowCount;
      }
      else {
         data.moreRows(Integer.MAX_VALUE);
         return data.getRowCount();
      }
   }

   private boolean checkDisplayRow(TableLens tableLens, int row) {
      for(int i = 0; i < tableLens.getColCount(); i++) {
         Object object = tableLens.getObject(row, i);

         if(Tool.equals(Tool.toString(object), "")) {
            return false;
         }
      }

      return true;
   }

   /**
    * Export one single viewsheet to document.
    * @param box the viewsheet sandbox.
    * @parma sheet the sheet name.
    */
   @Override
   public void export(ViewsheetSandbox box, String sheet, XPortalHelper helper)
         throws Exception
   {
      export(box, sheet, 0, helper);
   }

   /**
    * Export one single viewsheet to document.
    * @param box the viewsheet sandbox.
    * @parma sheet the sheet name.
    * @parma index the sheet index, Current View is 0, bookmark1 is 1, etc.
    */
   @Override
   public void export(ViewsheetSandbox box, String sheet, int index,
                      XPortalHelper helper)
      throws Exception
   {
      export(box, sheet, index, helper, true);
   }

   /**
    * Export one single viewsheet to document.
    * @param box the viewsheet sandbox.
    * @parma sheet the sheet name.
    * @parma index the sheet index, Current View is 0, bookmark1 is 1, etc.
    */
   public void export(ViewsheetSandbox box, String sheet, int index,
                      XPortalHelper helper, boolean cloneVs)
      throws Exception
   {
      this.index = index;
      this.box = box;
      Viewsheet origViewsheet = box.getViewsheet();
      Viewsheet rvsOrigViewsheet = rvs != null ? rvs.getViewsheet() : null;

      try {
         // vs may be modified (e.g. expand table) so we should make
         // a copy. otherwise exporting from scheduler may carry the
         // email setting in subsequent save-to-server
         Viewsheet viewsheet = box.getViewsheet();
         LogUtil.PerformanceLogEntry logEntry = new LogUtil.PerformanceLogEntry();

         if(cloneVs) {
            viewsheet = viewsheet.clone();
         }

         box.setViewsheet(viewsheet, false);
         this.viewsheet = viewsheet;
         LayoutInfo layoutInfo = viewsheet.getLayoutInfo();

         if(layoutInfo != null && layoutInfo.hasViewsheetLayout()) {
            viewsheet.clearLayoutState();
         }

         if(rvs != null && viewsheet.getAssemblies() != null) {
            rvs.setViewsheet(viewsheet);

            for(Assembly assembly : viewsheet.getAssemblies()) {
               if(!(assembly instanceof VSAssembly)) {
                  continue;
               }

               AnnotationVSUtil.refreshAllAnnotations(rvs, (VSAssembly) assembly, null, null);
            }
         }

         rmap = new HashMap<>();
         cmap = new HashMap<>();

         // log viewsheet execution
         Date execTimestamp = null;
         ExecutionRecord executionRecord = null;

         if(log) {
            String userSessionID = box.getUser() == null ?
                                   XSessionService.createSessionID(XSessionService.USER, null) :
                                   ((XPrincipal) box.getUser()).getSessionID();
            String objectName = entry.getDescription();
            String execSessionID = XSessionService.createSessionID(
               XSessionService.EXPORE_VIEW, entry.getName());
            String objectType = ExecutionRecord.OBJECT_TYPE_VIEW;
            String execType = ExecutionRecord.EXEC_TYPE_START;
            execTimestamp = new Date(System.currentTimeMillis());
            logEntry.setStartTime(execTimestamp.getTime());

            executionRecord = new ExecutionRecord(execSessionID,
                                                  userSessionID, objectName, objectType, execType,
                                                  execTimestamp,
                                                  ExecutionRecord.EXEC_STATUS_SUCCESS, null);
            Audit.getInstance().auditExecution(executionRecord, box.getUser());
            executionRecord = new ExecutionRecord(execSessionID,
                                                  userSessionID, objectName, objectType,
                                                  ExecutionRecord.EXEC_TYPE_FINISH,
                                                  execTimestamp,
                                                  ExecutionRecord.EXEC_STATUS_SUCCESS, null);
         }

         Thread thread = Thread.currentThread();

         if(thread instanceof GroupedThread) {
            GroupedThread pthread = (GroupedThread) thread;

            if(executionRecord == null) {
               pthread.addRecord(LogContext.DASHBOARD, sheet);
            }
            else {
               if(pthread.getRecord(executionRecord) != null) {
                  pthread.removeRecord(executionRecord);
               }

               pthread.addRecord(executionRecord);
            }
         }

         try {
            if(log && executionRecord != null) {
               execTimestamp = new Date(System.currentTimeMillis());
               executionRecord.setExecTimestamp(execTimestamp);
               executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_SUCCESS);
               executionRecord.setExecError(sheet);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to update execution record", ex);

            if(log && executionRecord != null) {
               execTimestamp = new Date(System.currentTimeMillis());
               executionRecord.setExecTimestamp(execTimestamp);
               executionRecord.setExecStatus(ExecutionRecord.EXEC_STATUS_FAILURE);
               executionRecord.setExecError(ex.getMessage());
            }
         }
         finally {
            if(log && executionRecord != null) {
               Audit.getInstance().auditExecution(executionRecord, box.getUser());
            }

            if(executionRecord != null && executionRecord.getExecTimestamp() != null) {
               logEntry.setFinishTime(executionRecord.getExecTimestamp().getTime());
               LogUtil.logPerformance(logEntry);
            }
         }

         String ftype = getFileType(fileType);

         // log export action
         if(logExport) {
            XPrincipal principal = (XPrincipal) box.getUser();
            Timestamp exportTimestamp = new Timestamp(System.currentTimeMillis());
            ExportRecord exportRecord = null;
            String objectName = entry.getDescription();

            if(principal != null) {
               IdentityID principalID = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
               exportRecord = new ExportRecord(principalID.getName(), objectName,
                                               ExportRecord.OBJECT_TYPE_VIEWSHEET, ftype,
                                               exportTimestamp, Tool.getHost());
            }

            if(exportRecord != null) {
               Audit.getInstance().auditExport(exportRecord, principal);
            }

            setLogExport(false);
         }

         viewsheet.setPrintMode(true);
         box.setExportFormat(ftype);
         viewsheet.updateCSSFormat(ftype, null, box);
         AssetQuerySandbox assetQuerySandbox = box.getAssetQuerySandbox();

         if(assetQuerySandbox != null) {
            assetQuerySandbox.setActive(true);
         }

         try {
            executeViewDynamicValues(viewsheet, box);
         }
         catch(ScriptException scriptException) {
            LOG.warn("Failed to execute dynamic values: {}", scriptException.getMessage());
         }
         catch(Exception ex) {
            LOG.error("Failed to execute dynamic values", ex);
         }

         prepareSheet(viewsheet, sheet, box);
         viewsheet = this.viewsheet; // maybe cloned
         viewsheet.calcChildZIndex();
         Assembly[] assemblies = viewsheet.getAssemblies(true, true);
         sortAssemblies(assemblies);

         try {
            for(int i = 0; i < assemblies.length; i++) {
               VSAssembly assembly = (VSAssembly) assemblies[i];

               if(!needExport(assembly) ||
                  viewsheet.getWarningTextAssembly(false) == assembly)
               {
                  continue;
               }

               prepareAssembly(assembly);

               int type = assembly.getAssemblyType();
               String name = assembly.getAbsoluteName();
               VSTableLens lens = null;

               switch(type) {
               case AbstractSheet.IMAGE_ASSET:
                  writeImageAssembly((ImageVSAssembly) assembly, helper);
                  break;
               case AbstractSheet.THERMOMETER_ASSET:
                  writeThermometer((ThermometerVSAssembly) assembly);
                  break;
               case AbstractSheet.GAUGE_ASSET:
                  writeGauge((GaugeVSAssembly) assembly);
                  break;
               case AbstractSheet.SLIDING_SCALE_ASSET:
                  writeSlidingScale((SlidingScaleVSAssembly) assembly);
                  break;
               case AbstractSheet.CYLINDER_ASSET:
                  writeCylinder((CylinderVSAssembly) assembly);
                  break;
               case AbstractSheet.TEXT_ASSET:
                  writeText(box.getVariableTable(), (TextVSAssembly) assembly);
                  break;
               case AbstractSheet.TEXTINPUT_ASSET:
                  writeTextInput((TextInputVSAssembly) assembly);
                  break;
               case AbstractSheet.SPINNER_ASSET:
                  writeSpinner((SpinnerVSAssembly) assembly);
                  break;
               case AbstractSheet.COMBOBOX_ASSET:
                  writeComboBox((ComboBoxVSAssembly) assembly);
                  break;
               case AbstractSheet.CHECKBOX_ASSET:
                  writeCheckBox((CheckBoxVSAssembly) assembly);
                  break;
               case AbstractSheet.RADIOBUTTON_ASSET:
                  writeRadioButton((RadioButtonVSAssembly) assembly);
                  break;
               case AbstractSheet.TAB_ASSET:
                  writeVSTab((TabVSAssembly) assembly);
                  break;
               case AbstractSheet.SLIDER_ASSET:
                  writeSlider((SliderVSAssembly) assembly);
                  break;
               case AbstractSheet.TIME_SLIDER_ASSET:
                  writeTimeSlider((TimeSliderVSAssembly) assembly);
                  break;
               case AbstractSheet.CALENDAR_ASSET:
                  writeCalendar((CalendarVSAssembly) assembly);
                  break;
               case AbstractSheet.SELECTION_LIST_ASSET:
                  writeSelectionList((SelectionListVSAssembly) assembly);
                  break;
               case AbstractSheet.SELECTION_TREE_ASSET:
                  writeSelectionTree((SelectionTreeVSAssembly) assembly);
                  break;
               case AbstractSheet.TABLE_VIEW_ASSET:
               case AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET:
                  lens = box.getVSTableLens(name, false, 1);
                  lens = getRegionTableLens(lens, (TableVSAssembly) assembly, box);
                  writeTable((TableVSAssembly) assembly, lens);
                  break;
               case AbstractSheet.CROSSTAB_ASSET:
                  lens = box.getVSTableLens(name, false, 1);
                  lens = getRegionTableLens(lens, (CrosstabVSAssembly) assembly, box);
                  writeCrosstab((CrosstabVSAssembly) assembly, lens);
                  break;
               case AbstractSheet.FORMULA_TABLE_ASSET:
                  lens = box.getVSTableLens(name, false, 1);
                  lens = getRegionTableLens(lens, (CalcTableVSAssembly) assembly, box);
                  writeCalcTable((CalcTableVSAssembly) assembly, lens);
                  break;
               case AbstractSheet.CHART_ASSET:
                  ChartVSAssembly chart = (ChartVSAssembly) assembly;
                  ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
                  TextVSAssembly titleObj = null;
                  info.setScalingRatio(new DimensionD(1.0, 1.0));
                  Point pos = viewsheet.getPixelPosition(info);
                  Dimension size = info.getPixelSize();

                  writeChartBackgroundShape(chart, info, pos, size);

                  if(info.isTitleVisible()) {
                     chart = chart.clone();
                     info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();

                     String title = info.getTitle();
                     int theight = info.getTitleHeight();
                     TableDataPath tpath = new TableDataPath(-1, TableDataPath.TITLE);
                     VSCompositeFormat tformat = info.getFormatInfo().getFormat(tpath);

                     titleObj = new TextVSAssembly();
                     TextVSAssemblyInfo titleInfo = (TextVSAssemblyInfo) titleObj.getInfo();

                     Insets padding = info.getPadding();
                     titleInfo.setPixelOffset(new Point(pos.x + padding.left,
                                                        pos.y + padding.top));
                     titleInfo.setPixelSize(
                        new Dimension(size.width - padding.left - padding.right, theight));
                     titleObj.setTextValue(title);
                     titleInfo.setFormatInfo(info.getFormatInfo());
                     titleInfo.setPadding(info.getTitlePadding());
                     titleInfo.setZIndex(info.getZIndex());
                  }

                  VGraphPair pair = box.getVGraphPair(name, true, null, true, 1);
                  DataSet data = (DataSet) box.getData(name);
                  boolean imgOnly = exportChartAsImage(chart);
                  data = data == null && pair != null ? pair.getData() : data;

                  if(data != null && !(data.getRowCount() <= 0 && data.getColCount() <= 0)) {
                     VGraph graph = !isMatchLayout() && supportChartSlices() ?
                        pair.getExpandedVGraph() : pair.getRealSizeVGraph();

                     if(graph != null) {
                        Rectangle2D bounds = graph.getBounds();
                        // chart not included in onlyDataComponents. (59989)
                        boolean needSlice = !isMatchLayout() && !isOnlyDataComponents() &&
                           bounds.getWidth() * bounds.getHeight() > EXPORT_SIZE * EXPORT_SIZE;

                        if(!needSlice) {
                           writeChart(chart, graph, data, imgOnly);
                        }
                        else {
                           writeSliceChart(chart, data, pair, isMatchLayout(), imgOnly);
                        }
                     }
                  }

                  if(titleObj != null) {
                     writeText(titleObj);
                  }

                  break;
               case AbstractSheet.GROUPCONTAINER_ASSET:
                  writeGroupContainer((GroupContainerVSAssembly) assembly, helper);
                  break;
               case AbstractSheet.LINE_ASSET:
               case AbstractSheet.RECTANGLE_ASSET:
               case AbstractSheet.OVAL_ASSET:
                  writeShape((ShapeVSAssembly) assembly);
                  break;
               case AbstractSheet.CURRENTSELECTION_ASSET:
                  writeCurrentSelection((CurrentSelectionVSAssembly) assembly);
                  break;
               case AbstractSheet.SUBMIT_ASSET:
                  writeSubmit((SubmitVSAssembly) assembly);
                  break;
               case AbstractSheet.ANNOTATION_ASSET:
                  writeAnnotation((AnnotationVSAssembly) assembly);
                  break;
               case AbstractSheet.ANNOTATION_LINE_ASSET:
               case AbstractSheet.ANNOTATION_RECTANGLE_ASSET:
                  break;
               default:
                  throw new RuntimeException("Unsupported assembly found:" +
                                                assembly);
               }
            }

            TextVSAssembly warningText = viewsheet.getWarningTextAssembly(false);

            if(warningText != null) {
               VSUtil.setAutoSizeTextHeight(warningText.getInfo(), viewsheet);
               viewsheet.adjustWarningTextPosition();
               writeText(box.getVariableTable(), warningText);
            }
         }
         catch(SheetMaxRowsException maxrows) {
            setMaxRows(maxrows.getMaxRows());
         }

         if(isExceedQueryMax(assemblies, box)) {
            String vsobj = XUtil.VS_ASSEMBLY.get();
            String mstr = SreeEnv.getProperty("query.runtime.maxrow");
            String warning = Catalog.getCatalog().getString(
               "viewer.viewsheet.common.maxRows",
               (vsobj != null) ? vsobj : "Viewsheet", mstr);
            VSCompositeFormat format = new VSCompositeFormat();
            format.getUserDefinedFormat().setForeground(Color.RED);
            writeWarningText(assemblies, warning, format);
         }

         writeAdditionalTipMessage();

         if(getMaxRows() != 0) {
            String warning = "";

            if(fileType == FileFormatInfo.EXPORT_TYPE_POWERPOINT ||
               fileType == FileFormatInfo.EXPORT_TYPE_PDF)
            {
               warning = Catalog.getCatalog().getString("vs.export.pdfppttable", getMaxRows());
            }
            else if(fileType == FileFormatInfo.EXPORT_TYPE_EXCEL) {
               warning = Catalog.getCatalog().getString("vs.export.excel.maxrows", getMaxRows());
            }

            VSCompositeFormat format = new VSCompositeFormat();
            format.getUserDefinedFormat().setForeground(Color.RED);
            writeWarningText(assemblies, warning, format);
         }

         box.setExportFormat(null);
      }
      finally {
         if(rvs != null) {
            rvs.setViewsheet(rvsOrigViewsheet);
         }

         box.setViewsheet(origViewsheet, false);
         fixFileError();
      }
   }

   protected boolean supportChartSlices() {
      return true;
   }

   /**
    * Write slice chart.
    */
   protected void writeSliceChart(ChartVSAssembly assembly, DataSet data,
                                  VGraphPair pair, boolean match,
                                  boolean imgOnly) {
      VGraph graph = pair.getExpandedVGraph();
      ChartVSAssembly nassembly = assembly.clone();
      ChartVSAssemblyInfo ninfo = (ChartVSAssemblyInfo) nassembly.getVSAssemblyInfo();
      Viewsheet vs = ninfo.getViewsheet();
      BufferedImage img;

      Dimension pixelSize = ninfo.getPixelSize();
      Point pixelOff = ((FloatableVSAssemblyInfo) ninfo).getPixelOffset();

      final VSChart chart = new VSChart(vs);
      chart.setViewsheet(vs);
      chart.setTheme(theme);
      chart.setAssemblyInfo(ninfo.clone());

      Integer[] xs = slice(ninfo, true);
      Integer[] ys = slice(ninfo, false);

      pixelOff = pixelOff == null ? new Point(0, 0) : pixelOff;
      pixelOff = new Point(Math.max(0, pixelOff.x), Math.max(0, pixelOff.y));
      // position and end position of the chart info
      Integer sx = ninfo.getPixelOffset().x;
      Integer sy = ninfo.getPixelOffset().y;
      Integer ex = ninfo.getPixelOffset().x + ninfo.getPixelSize().width;
      Integer ey = ninfo.getPixelOffset().y + ninfo.getPixelSize().height;
      boolean firstTime = true;

      for(int i = 0; i < ys.length; i++) {
         for(int j = 0; j < xs.length; j++) {
            // next row and column
            Integer nr = i == ys.length - 1 ? ey : ys[i + 1];
            Integer nc = j == xs.length - 1 ? ex : xs[j + 1];
            // current vschart's info position and size
            Point infostart = new Point(xs[j].intValue(), ys[i].intValue());
            Dimension infosize = new Dimension(nc.intValue() - xs[j].intValue(),
                                               nr.intValue() - ys[i].intValue());
            // current piece image's position and size
            Point chartstart = new Point(Math.max(0,infostart.x - pixelOff.x),
                                         Math.max(0, infostart.y - pixelOff.y));
            Dimension chartsize = getSize(xs[j], nc, ys[i], nr, vs);

            if(chartsize.width > 0 && chartsize.height > 0) {
               updateAssembly(nassembly, infostart, infosize);
               img = pair.getImage(match, chartstart, chartsize, chart);
               // here do not need the param for image position and size,
               // the nassembly will handle it
               writeChart(assembly, nassembly, graph, data, img, firstTime, imgOnly);
               firstTime = false;
            }
         }
      }
   }

   /**
    * Get the image with padding defined in chart info.
    * @param graph graph rendering without padding.
    */
   protected BufferedImage getImageWithPadding(ChartVSAssemblyInfo info, BufferedImage graph) {
      Insets padding = info.getPadding();
      // dpi is x2
      BufferedImage outer = new BufferedImage(
         graph.getWidth() + padding.left * 2 + padding.right * 2,
         graph.getHeight() + padding.top * 2 + padding.bottom * 2,
         BufferedImage.TYPE_INT_ARGB);

      VSCompositeFormat fmt = info.getFormat();
      Graphics2D g = (Graphics2D) outer.getGraphics();
      final Color background = fmt.getBackground();

      if(background != null) {
         g.setColor(background);
      }
      else {
         g.setColor(new Color(255, 255, 255, 0));
      }

      g.fillRect(0, 0, outer.getWidth(), outer.getHeight());
      g.drawImage(graph, padding.left * 2, padding.top * 2, null);
      g.dispose();

      return outer;
   }

   /**
    * Update the assembly.
    */
   private void updateAssembly(ChartVSAssembly assembly, Point infostart, Dimension infosize) {
      assembly.setPixelSize(infosize);
      assembly.setPixelOffset(infostart);
   }

   /**
    * Get piece chart size.
    * @param col the current slice image's start column.
    * @param ncol the next slice image's start column.
    * @param row the current slice image's start row.
    * @param nrow the next slice image's start row.
    * @param vs the viewsheet.
    */
   private Dimension getSize(Integer col, Integer ncol,
                             Integer row, Integer nrow, Viewsheet vs) {
      Dimension size = new Dimension(ncol.intValue() - col.intValue(),
                                     nrow.intValue() - row.intValue());
      return size;
   }

   /**
    * Slice the rows or columns, make sure after slice, each row or column is
    * just in grid.
    * @param info the assembly info
    * @param sliceWidth identify to slice width or height, true to slice width, false
    *  to slcie height.
    */
   private Integer[] slice(VSAssemblyInfo info, boolean sliceWidth) {
      // Deprecated exporter logic, needs updating
      // @damianwysocki, Bug #9543
      // Removed grid, this logic needs to be updated
      Viewsheet vs = info.getViewsheet();
      Point start = info.getPixelOffset();
      Dimension size = info.getPixelSize();

      java.util.List slices = new ArrayList();
      int from = sliceWidth ? start.x : start.y;
      int to = sliceWidth ? size.width : size.height;
      to = from + to;

      int current = from;
      slices.add(current);
      double sz = 0;

      for(int i = from + 1; i <= to; i++) {
         Dimension dim = new Dimension(sliceWidth ? i - current : 0, sliceWidth ? 0 : i - current);
         sz = sliceWidth ? dim.width : dim.height;

         if(sz > EXPORT_SIZE) {
            // if the one col or row larger than EXPORT_SIZE, force to export
            if(i == current + 1) {
               current = i;
            }
            else {
               i--;
               current = i;
            }

            slices.add(current);
         }
      }

      Integer[] ints = new Integer[slices.size()];
      slices.toArray(ints);

      return ints;
   }

   /**
    * Write the slice chart.
    * @param originalAsm the original chart vs assembly.
    * @param asm the slice chart vs assembly.
    * @param graph the vgraph to paint chart.
    * @param data the data set.
    * @param img the buffered image of the slice chart.
    * @param firstTime the first to write a slice chart of the whole chart.
    */
   protected abstract void writeChart(ChartVSAssembly originalAsm,
                                      ChartVSAssembly asm, VGraph graph,
                                      DataSet data, BufferedImage img,
                                      boolean firstTime, boolean imgOnly);

   /**
    * Write the warning text.
    */
   protected abstract void writeWarningText(Assembly[] assemblies,
                                            String warning,
                                            VSCompositeFormat format);

   /**
    * Write the additional tip message.
    */
   protected void writeAdditionalTipMessage(){}

   /**
    * Set max rows.
    */
   protected void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
   }

   /**
    * Get max rows.
    */
   protected int getMaxRows() {
      return maxRows;
   }

   /**
    * This method is called before writing the specified assembly.
    */
   protected void prepareAssembly(VSAssembly assembly) {
      if(match && (assembly.getAssemblyType() == Viewsheet.TIME_SLIDER_ASSET ||
         assembly.getAssemblyType() == Viewsheet.SELECTION_LIST_ASSET))
      {
         Assembly container = assembly.getContainer();

         if(container instanceof CurrentSelectionVSAssembly) {
            CurrentSelectionVSAssembly csAssembly =
               (CurrentSelectionVSAssembly) container;
            int end = csAssembly.getPixelSize().height + csAssembly.getPixelOffset().y;
            Dimension size = assembly.getPixelSize();

            if(end - assembly.getPixelOffset().y > 0 &&
               end - assembly.getPixelOffset().y - size.height < 0)
            {
               size.height = end - assembly.getPixelOffset().y;
            }
         }
      }
   }

   /**
    * Judge the query.runtime.maxrow take effect or not.
    * @param assemblies the assemblies containing in viewsheet.
    */
   private boolean isExceedQueryMax(Assembly[] assemblies, ViewsheetSandbox box)
   {
      try {
         int qmax = Util.getRuntimeMaxRows();

         for(int i = 0; i < assemblies.length; i++) {
            int type = assemblies[i].getAssemblyType();
            String name = assemblies[i].getAbsoluteName();
            TableLens lens = null;

            switch(type) {
            // table
            case AbstractSheet.TABLE_VIEW_ASSET:
            case AbstractSheet.EMBEDDEDTABLE_VIEW_ASSET:
            case AbstractSheet.CROSSTAB_ASSET:
               lens = box.getVSTableLens(name, false, 1);
               break;
            case AbstractSheet.CHART_ASSET:
               Object data = (assemblies[i] instanceof TableDataVSAssembly) ?
                  box.getVSTableLens(name, false, 1) :
                  box.getData(name, false, DataMap.NORMAL);
               lens = (TableLens) data;
               break;
            case AbstractSheet.TIME_SLIDER_ASSET:
            case AbstractSheet.CALENDAR_ASSET:
            case AbstractSheet.SELECTION_LIST_ASSET:
            case AbstractSheet.SELECTION_TREE_ASSET:
               lens = (TableLens) box.getData(name, false, DataMap.DETAIL);
               break;
            default:
               break;
            }

            if(lens != null) {
               int amax = Util.getAppliedMaxRows(lens);

               if(amax > 0 && amax == qmax) {
                  return true;
               }
            }
         }

         return false;
      }
      catch(Exception e) {
         return false;
      }
   }

   /**
    * Convert the size of control from cell unit to pixcel.
    * @param info the VSAssemblyInfo containing size and pos info.
    */
   protected Dimension calculateSizeInPixel(VSAssemblyInfo info) {
      return info.getViewsheet().getPixelSize(info);
   }

   /**
    * Get the corresponding asset entry of the viewsheet.
    * @return asset entry.
    */
   @Override
   public AssetEntry getAssetEntry() {
      return entry;
   }

   /**
    * Set the corresponding asset entry of the viewsheet.
    * @param entry the specified asset entry.
    */
   @Override
   public void setAssetEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Get the text format, including highlight.
    */
   protected VSCompositeFormat getTextFormat(VSAssemblyInfo info) {
      VSCompositeFormat chartTitleFormat = null;
      FormatInfo formatInfo = info.getFormatInfo();

      if(formatInfo != null) {
         VSCompositeFormat fmt =
            formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

         if(CSSConstants.CHART.equals(fmt.getCSSFormat().getCSSType())) {
            TableDataPath tpath = new TableDataPath(-1, TableDataPath.TITLE);
            chartTitleFormat = formatInfo.getFormat(tpath, false);
         }
      }

      VSCompositeFormat fmt = chartTitleFormat != null ? chartTitleFormat : info.getFormat();

      if(fmt != null && info instanceof TextVSAssemblyInfo) {
         TextVSAssemblyInfo tinfo = (TextVSAssemblyInfo) info;

         fmt = fmt.clone();

         if(tinfo.getHighlightForeground() != null) {
            fmt.getUserDefinedFormat().setForeground(
               tinfo.getHighlightForeground());
         }

         if(tinfo.getHighlightBackground() != null) {
            fmt.getUserDefinedFormat().setBackground(
               tinfo.getHighlightBackground());
         }

         if(tinfo.getHighlightFont() != null) {
            fmt.getUserDefinedFormat().setFont(tinfo.getHighlightFont());
         }
      }

      return fmt;
   }

   /**
    * Write text assembly.
    */
   protected void writeText(TextVSAssembly assembly) {
      writeText(assembly, assembly.getText());
   }

   /**
    * Write text assembly.
    */
   protected void writeText(VariableTable variableTable, TextVSAssembly assembly) {
      writeText(assembly, getDisplayText(variableTable, assembly.getText()));
   }

   private String getDisplayText(VariableTable variableTable, String text) {
      ParameterTool ptool = new ParameterTool();

      if(ptool.containsParameter(text)) {
         return ptool.parseParameters(variableTable, text);
      }

      return text;
   }

   /**
    * Expand Chart.
    */
   private void expandChart(VSAssembly chart, ViewsheetSandbox box, boolean expandrow)
      throws Exception
   {
      String name = chart.getAbsoluteName();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setScalingRatio(new DimensionD(1.0, 1.0));
      VGraphPair vpair = box.getVGraphPair(name);

      if(vpair != null && vpair.isCompleted()) {
         // here should use real size graph preferred size, because
         // in VGraphPair the expand graph size depends on the real
         // size graph's preferred size
         VGraph vgraph = vpair.getRealSizeVGraph();
         VGraph evgraph = vpair.getExpandedVGraph();
         VSChartInfo cinfo = info.getVSChartInfo();
         boolean hscrollable = GraphUtil.isHScrollable(vgraph, cinfo);
         boolean vscrollable = GraphUtil.isVScrollable(vgraph, cinfo);

         if(!hscrollable && !vscrollable) {
            return;
         }

         double w = Math.min(10000, evgraph.getSize().getWidth());
         double h = Math.min(10000, evgraph.getSize().getHeight());
         int titleHeight = getTitleHeight(info);
         h += titleHeight;
         Insets padding = info.getPadding();

         if(padding != null) {
            w += padding.left + padding.right;
            h += padding.top + padding.bottom;
         }

         Dimension pixelsize = info.getPixelSize();
         Dimension osize = new Dimension(pixelsize.width, pixelsize.height);

         int insertRow = 0;
         int insertCol = 0;
         boolean wchanged = false;
         boolean hchanged = false;

         if(!expandrow && pixelsize.width < w && hscrollable) {
            wchanged = true;

            while(true) {
               if(pixelsize.width >= w) {
                  break;
               }

               insertCol++;
               pixelsize.width = pixelsize.width + AssetUtil.defw;
            }
         }

         if(expandrow && pixelsize.height < h && vscrollable) {
            hchanged = true;

            while(true) {
               if(pixelsize.height >= h) {
                  break;
               }

               insertRow++;
               pixelsize.height += AssetUtil.defh;
            }
         }

         Dimension pixelsize2 = new Dimension((int) w, (int) h);

         // use original pixel size width
         if(!wchanged) {
            pixelsize2.width = pixelsize.width;
         }

         if(!hchanged) {
            pixelsize2.height = pixelsize.height;
         }

         info.setPixelSize(pixelsize2);
         insertRowCol(chart, osize, expandrow ? insertRow * AssetUtil.defh : 0,
                      expandrow ? 0 : insertCol * AssetUtil.defw);
      }
   }

   /**
    * Expand currentSelection.
    */
   private void expandCurrentSelection(CurrentSelectionVSAssembly cs) {
      String[] children = cs.getAssemblies();
      int insertRow = 0;
      Dimension osize = new Dimension(cs.getPixelSize().width, cs.getPixelSize().height);

      if(children.length > 0) {
         Assembly assembly =
            cs.getViewsheet().getAssembly(children[children.length - 1]);

         if(assembly != null) {
            Dimension size = cs.getPixelSize();

            if(!isPixelLayout()) {
               int nheight = assembly.getPixelOffset().y +
                  assembly.getPixelSize().height - cs.getPixelOffset().y;
               insertRow = nheight - size.height;
               size.height = Math.max(size.height, nheight);
            }
            else {
               Viewsheet vs = cs.getViewsheet();
               CoordinateHelper helper = new CoordinateHelper();
               helper.setViewsheet(vs);
               Rectangle2D lastbounds = helper.getBounds(
                  (AbstractVSAssembly) assembly, CoordinateHelper.ALL,
                  false, true, null);
               double endY = lastbounds.getY() + lastbounds.getHeight();
               Rectangle2D csbounds = helper.getBounds(cs.getVSAssemblyInfo());
               int csEndY = (int) (csbounds.getY() + csbounds.getHeight());

               while(true) {
                  if(csEndY >= endY) {
                     break;
                  }

                  insertRow++;
                  cs.getPixelSize().height += AssetUtil.defh;
                  csEndY += AssetUtil.defh;
               }

               insertRow = insertRow * AssetUtil.defh;
            }
         }
      }

      insertRowCol(cs, osize, insertRow, 0);
   }

   /**
    * Expand selection list to make all rows visible.
    */
   private void expandSelectionAssembly(VSAssembly assembly) {
      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info.getShowType() == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         return;
      }

      Dimension oldSize = (Dimension) info.getPixelSize().clone();
      List<Double> rowHeights;

      if(info instanceof SelectionListVSAssemblyInfo) {
         SelectionListVSAssemblyInfo listInfo = (SelectionListVSAssemblyInfo) info;
         rowHeights = listInfo.getRowHeights();
      }
      else  {
         SelectionTreeVSAssemblyInfo treeInfo = (SelectionTreeVSAssemblyInfo) info;
         rowHeights = treeInfo.getRowHeights();
      }

      boolean isInContainer = assembly.getContainer() instanceof CurrentSelectionVSAssembly;
      double newHeight = getSelectionHeight(info, rowHeights, isInContainer);

      // find new height by adding one cell height for each displayed row
      int addRows = (int)newHeight - oldSize.height;
      assembly.setPixelSize(new Dimension(oldSize.width,
                                          (int) Math.max(oldSize.height, newHeight)));
      insertRowCol(assembly, oldSize, addRows, 0);
   }

   protected double getSelectionHeight(SelectionBaseVSAssemblyInfo info, List<Double> rowHeights,
      boolean isInContainer)
   {
      // Add title height if it is visible
      double newHeight = getTitleHeight(info);

      for(int i = 0; i < rowHeights.size(); i++) {
         newHeight += rowHeights.get(i);
      }

      return newHeight;
   }

   /**
    * Write image assembly.
    * @param assembly the ImageVSAssembly to be written.
    */
   protected abstract void writeImageAssembly(ImageVSAssembly assembly,
                                              XPortalHelper helper);

   /**
    * Write Textinput assembly.
    * @param assembly the specified TextInputVSAssembly.
    */
   protected abstract void writeTextInput(TextInputVSAssembly assembly);

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeText(VSAssembly assembly, String txt);

   /**
    * Write text with format.
    * @param text the specified text.
    * @param pos the specified position.
    * @param size the specified text size.
    * @param format the specified text format.
    */
   protected abstract void writeText(String text, Point pos,
                                     Dimension size, VSCompositeFormat format);

   /**
    * Write a range slider.
    */
   protected abstract void writeTimeSlider(TimeSliderVSAssembly assm);

   /**
    * Write a calendar.
    */
   protected abstract void writeCalendar(CalendarVSAssembly assm);

   /**
    * Write gauge assembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeGauge(GaugeVSAssembly assembly);

   /**
    * Write thermometer assembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeThermometer(ThermometerVSAssembly assembly);

   /**
    * Write cylinder assembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeCylinder(CylinderVSAssembly assembly);

   /**
    * Write SlidingScale assembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeSlidingScale(SlidingScaleVSAssembly assembly);

   /**
    * Write RadioButton VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeRadioButton(RadioButtonVSAssembly assembly);

   /**
    * Write CheckBox VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   protected abstract void writeCheckBox(CheckBoxVSAssembly assembly);

   /**
    * Write slider assembly.
    * @param assembly the SliderVSAssembly.
    */
   protected abstract void writeSlider(SliderVSAssembly assembly);

   /**
    * Write spinner assembly.
    * @param assembly the spinnerVSAssembly.
    */
   protected abstract void writeSpinner(SpinnerVSAssembly assembly);

   /**
    * Write comboBox assembly.
    * @param assembly the specified ComboBoxVSAssembly.
    */
   protected abstract void writeComboBox(ComboBoxVSAssembly assembly);

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   protected abstract void writeSelectionList(SelectionListVSAssembly assembly);

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   protected abstract void writeSelectionTree(SelectionTreeVSAssembly assembly);

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   protected abstract void writeTable(TableVSAssembly assembly,
      VSTableLens lens);

   /**
    * Write annotation assembly.
    * @param assembly the specified AnnotationVSAssembly.
    */
   protected void writeAnnotation(AnnotationVSAssembly assembly) {
      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      String lineName = ainfo.getLine();

      if(lineName != null) {
         AnnotationLineVSAssembly line = (AnnotationLineVSAssembly) viewsheet.getAssembly(lineName);

         if(line != null) {
            AnnotationLineVSAssemblyInfo info =
               (AnnotationLineVSAssemblyInfo) line.getVSAssemblyInfo();
            VSUtil.refreshLineInfo(viewsheet, info);
         }
      }
   }

   /**
    * Write crosstab assembly.
    * @param assembly the specified CrosstabVSAssembly.
    */
   protected abstract void writeCrosstab(CrosstabVSAssembly assembly,
      VSTableLens lens);

   /**
    * Write calctable assembly.
    * @param assembly the specified CalcTableVSAssembly.
    */
   protected abstract void writeCalcTable(CalcTableVSAssembly assembly,
      VSTableLens lens);

   /**
    * Write chart assembly.
    * @param chartAsm the specified ChartVSAssembly.
    * @param vgraph the graph object
    * @param data the dataset
    */
   protected abstract void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                                      DataSet data, boolean imgOnly);

   protected void writeChart(Graphics2D g2, ChartVSAssembly chartAsm, VGraph vgraph, double scale) {
      final VSChart chart = new VSChart(viewsheet);
      final ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getVSAssemblyInfo();
      chart.setViewsheet(info.getViewsheet());
      chart.setTheme(theme);
      chart.setAssemblyInfo(info);

      writeChart(g2, chart, vgraph, new Rectangle2D.Double(), scale);
   }

   protected void writeChart(Graphics2D g2, VSChart chart, VGraph vgraph, Rectangle2D bounds) {
      writeChart(g2, chart, vgraph, bounds, 1);
   }

   protected void writeChart(Graphics2D g2, VSChart chart, VGraph vgraph,
                             Rectangle2D bounds, double scale)
   {
      final ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getAssemblyInfo();
      Color ocolor = g2.getBackground();
      g2.scale(scale, scale);
      g2.translate((int) bounds.getX(), (int) bounds.getY());
      chart.paint(g2);

      final int titleHeight = getTitleHeight(info);
      g2.translate(info.getPadding().left, info.getPadding().top + titleHeight);
      final Insets2D border = getBorderOffset(info.getFormat());
      g2.translate(border.left, border.top);
      g2.setColor(ocolor);

      vgraph.paintGraph(g2, false);
      LegendGroup legends = vgraph.getLegendGroup();
      final double height = vgraph.getSize().getHeight();
      g2.translate(0, height);
      g2.transform(GDefaults.FLIPY);
      VSChartInfo cinfo = info.getVSChartInfo();
      boolean isDonut = cinfo.isDonut();

      for(int i = 0; legends != null && i < legends.getLegendCount(); i++) {
         Legend legend = legends.getLegend(i);

         if(isDonut && legend.getVisualFrame() instanceof MultiMeasureColorFrame) {
            Scale legendScale = legend.getVisualFrame().getScale();
            Object[] values = legendScale == null ? null : legendScale.getValues();
            boolean shouldIgnore = values != null && Arrays.stream(values)
               .filter(v -> v instanceof String && ((String) v).contains("Total@"))
               .findAny().isPresent();

            if(shouldIgnore) {
               continue;
            }
         }

         legend.paint(g2);
      }
   }

   /**
    * Write tab assembly.
    * @param assembly the specified TabVSAssembly.
    */
   protected abstract void writeVSTab(TabVSAssembly assembly);

   /**
    * Write group container assembly.
    * @param assembly the specified GroupContainerVSAssembly.
    */
   protected abstract void writeGroupContainer(
      GroupContainerVSAssembly assembly, XPortalHelper helper);

   /**
    * Write shape assembly.
    * @param assembly the specified ShapeVSAssembly.
    */
   protected abstract void writeShape(ShapeVSAssembly assembly);

   /**
    * Write current selection assembly.
    * @param assembly the specified CurrentSelectionVSAssembly.
    */
   protected abstract void writeCurrentSelection(
      CurrentSelectionVSAssembly assembly);

   /**
    * Write submit assembly.
    * @param assembly the specified SubmitVSAssembly.
    */
   protected abstract void writeSubmit(SubmitVSAssembly assembly);

   /**
    * Check the assembly should export or not.
    */
   protected boolean needExport(VSAssembly assembly) {
      if(VSUtil.isTipView(assembly.getAbsoluteName(), assembly.getViewsheet())
         || VSUtil.isPopComponent(assembly.getAbsoluteName(),
                                  assembly.getViewsheet()))
      {
         return false;
      }

      if(assembly.isEmbedded() &&
         (assembly instanceof AnnotationVSAssembly ||
         assembly  instanceof AnnotationRectangleVSAssembly ||
         assembly  instanceof AnnotationLineVSAssembly))
      {
         return false;
      }

      if(assembly.getVSAssemblyInfo() != null &&
         viewsheet.isVisible(assembly, AbstractSheet.SHEET_RUNTIME_MODE))
      {
         Dimension psize = viewsheet.getPixelSize(assembly.getVSAssemblyInfo());

         if((psize.width <= 0 || psize.height <= 0) &&
            !(assembly instanceof AnnotationVSAssembly))
         {
            return false;
         }

         if(assembly.getAssemblyType() == Viewsheet.SELECTION_LIST_ASSET ||
            assembly.getAssemblyType() == Viewsheet.TIME_SLIDER_ASSET)
         {
            // For expand table and chart, it will expand selection list, so container will expand
            // to large, should not hide selection over container.
            if(!match) {
               return true;
            }

            if(assembly instanceof SelectionListVSAssembly &&
               ((SelectionListVSAssembly) assembly).getShowType() ==
               SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
            {
               return true;
            }

            Assembly container = assembly.getContainer();

            if(container instanceof CurrentSelectionVSAssembly) {
               CurrentSelectionVSAssembly csAssembly =
                  (CurrentSelectionVSAssembly) container;
               int gap = assembly.getPixelOffset().y - csAssembly.getPixelOffset().y;
               return gap > 0 && gap < csAssembly.getPixelSize().height;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Get the viewsheet for exporting.
    */
   @Override
   public Viewsheet getViewsheet() {
      return viewsheet;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setSandbox(ViewsheetSandbox box) {
      this.box = box;
   }

   public void setRuntimeViewsheet(RuntimeViewsheet rvs) {
      this.rvs = rvs;
   }

   /**
    * Get the table assembly max row count.
    */
   protected int getMaxRow(int start) {
      return Integer.MAX_VALUE;
   }

   /**
    * Get the table assembly max col count.
    */
   protected int getMaxCol(int start) {
      return Integer.MAX_VALUE;
   }

   /**
    * Get the table assembly expand max height.
    * @param obj the table assembly
    * @param table the table data
    */
   protected int getExpandTableHeight(TableDataVSAssembly obj, XTable table) {
      final int ypos = obj.getPixelOffset().y;
      int rowCount = 0;
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) obj.getVSAssemblyInfo();
      final int titleRow = info.isTitleVisible() ? 1 : 0;

      if(!isPixelLayout()) {
         rowCount = Math.min(table.getRowCount() + titleRow, getMaxRow(ypos));
      }
      else {
         int hrow = 1;

         if(obj instanceof CrosstabVSAssembly) {
            VSCrosstabInfo vsinfo = ((CrosstabVSAssembly) obj).getVSCrosstabInfo();
            hrow = vsinfo != null ? Math.max(1, vsinfo.getRuntimeColHeaders().length) : 0;
         }

         int tableLineCount = getExpandTableLineCount(table, obj, titleRow, hrow, match);;
         rowCount = Math.min(tableLineCount, getMaxRow(ypos));
      }

      int expandedHeight = 0;

      for(int i = 0; i < rowCount; i++) {
         double rowHeight = info.getRowHeight(i);

         if(Double.isNaN(rowHeight) && table instanceof VSTableLens) {
            rowHeight = getCellHeight(i, (VSTableLens) table);
         }

         expandedHeight += Double.isNaN(rowHeight) ? AssetUtil.defh : rowHeight;
      }

      if(!match) {
         int total = getTotalHeight(info, table);
         expandedHeight = Math.max(expandedHeight, total);
      }

      return expandedHeight;
   }

   private int getCellHeight(int r, VSTableLens lens) {
      // get cell height from table lens.
      if(lens == null || lens.getRowHeights() == null || r >= lens.getRowHeights().length) {
         return lens != null ? (int) lens.getRowHeightWithPadding(AssetUtil.defh, r) :
            AssetUtil.defh;
      }

      int h = lens.getWrappedHeight(r, true);
      return (int) lens.getRowHeightWithPadding(Double.isNaN(h) ? AssetUtil.defh : h, r);
   }

   private int getTotalHeight(TableDataVSAssemblyInfo info, XTable table) {
      VSTableLens lens = (VSTableLens) table;
      int[] hs = new int[lens.getRowCount()];
      int[] heights = lens.getRowHeights();
      int totalHeight = 0;

      for(int i = 0; i < hs.length; i++) {
         int h = 0;

         if(heights == null || i >= heights.length) {
            h = AssetUtil.defh;
         }
         else {
            h = lens.getWrappedHeight(i, true);
         }

         totalHeight += lens.getRowHeightWithPadding(h, i);
      }

      if(info.isTitleVisible()) {
         totalHeight += info.getTitleHeight();
      }

      return totalHeight;
   }

   /**
    * Gets the number of lines, if there is cell wrapping, in all table content
    * rows.
    */
   private static int getExpandTableLineCount(
      XTable table, TableDataVSAssembly obj, int titleRow, int hrow,
      boolean isMatch)
   {
      final int totalHeight = (table.getRowCount() - hrow) * AssetUtil.defh;

      int h = 0;
      int gridRowCounter = titleRow + hrow;
      int tableLineCount = 0;

      boolean isTable = obj instanceof TableVSAssembly;

      while(h < totalHeight) {
         int lines = 1;

         if(isTable) {
            h += AssetUtil.defh;
            VSTableLens vsTable = (VSTableLens) table;
            lines = vsTable.getLineCount(gridRowCounter - titleRow, false);
         }
         else {
            h += AssetUtil.defh;
         }

         tableLineCount += lines;
         gridRowCounter++;
      }

      // @by ankitmathur, Bug #1282, ensure we have enough lines to fit
      // all data rows and header rows.
      if(tableLineCount < gridRowCounter && !isMatch) {
         tableLineCount = Math.max(gridRowCounter, tableLineCount);
      }

      return tableLineCount;
   }

   /**
    * Gets the number of lines, if wrapped, in all header rows.
    */
   private static int getExpandTableHeaderLineCount(XTable table, int hrow) {
      int lineCount = hrow;

      if(table instanceof VSTableLens) {
         final VSTableLens vsTable = (VSTableLens) table;
         lineCount -= hrow;

         for(int i = 0; i < hrow; i++) {
            int headerLines = vsTable.getLineCount(i, false);
            lineCount += headerLines;
         }
      }

      return lineCount;
   }

   /**
    * Get the table assembly expand column count.
    */
   private int getExpandTableWidth(TableDataVSAssembly obj, VSTableLens table) {
      Point pos = obj.getPixelOffset();
      int ccount = table.getColCount();
      int colCount = 0;
      TableDataVSAssemblyInfo tinfo =
         (TableDataVSAssemblyInfo) obj.getVSAssemblyInfo();

      if(!isPixelLayout() || !(tinfo instanceof CrosstabVSAssemblyInfo) ||
         !isUserDefinedWidth(tinfo))
      {
         colCount = Math.min(ccount, getMaxCol(pos.x));
      }
      else {
         CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) tinfo;
         Dimension size = obj.getPixelSize();
         int count = info.getColumnCount();
         int tableColumns  = 0;
         int w = 0;

         while(w < size.width && tableColumns < ccount) {
            double colWidth = tinfo.getColumnWidth2(tableColumns, table);
            w += Double.isNaN(colWidth) ? AssetUtil.defw : colWidth;
            tableColumns++;
         }

         if(count >= ccount) {
            colCount = Math.min(tableColumns, getMaxCol(pos.x));
         }
         else {
            /* shouldn't override the column width since crosstab column width is set on
               column datapath. (49726)
            for(int i = count; i < ccount; i++) {
               info.setColumnWidthValue2(i, AssetUtil.defw, table);
            }
            */

            colCount = Math.min(tableColumns + ccount - count, getMaxCol(pos.x));
         }
      }

      int expandedWidth = 0;
      int i = 0;

      while(i < colCount) {
         double colWidth = tinfo.getColumnWidth2(i, table);
         expandedWidth += table.getColumnWidthWithPadding(
            Double.isNaN(colWidth) ? AssetUtil.defw : colWidth, i);
         i++;
      }

      return expandedWidth;
   }

   /**
    * Get the calendar title format.
    */
   protected VSCompositeFormat getCalendarTitleFormat(CalendarVSAssemblyInfo info) {
      VSCompositeFormat format = new VSCompositeFormat();

      if(info == null) {
         return format;
      }

      FormatInfo finfo = info.getFormatInfo();

      if(finfo != null) {
         VSCompositeFormat oformat = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.OBJECT));
         format = finfo.getFormat(
            new TableDataPath(-1, TableDataPath.TITLE));
         Insets oborder = oformat.getBorders();
         BorderColors ocolor = oformat.getBorderColors();
         Insets border = format.getBorders();
         BorderColors color = format.getBorderColors();

         BorderColors defbcolors = new BorderColors(
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR,
            VSAssemblyInfo.DEFAULT_BORDER_COLOR);

         if(ocolor == null) {
            ocolor = (BorderColors) defbcolors.clone();
         }

         if(color == null) {
            color = (BorderColors) defbcolors.clone();
         }

         if(border != null && oborder != null) {
            border.left = border.left == 0 ? oborder.left : border.left;
            border.right = border.right == 0 ? oborder.right : border.right;
            border.top = border.top == 0 ? oborder.top : border.top;
            border.bottom = border.bottom == 0 ? oborder.bottom : border.bottom;
         }

         color.topColor = mergeDarkColor(color.topColor, ocolor.topColor);
         color.bottomColor = mergeDarkColor(color.bottomColor,
                                            ocolor.bottomColor);
         color.leftColor = mergeDarkColor(color.leftColor, ocolor.leftColor);
         color.rightColor = mergeDarkColor(color.rightColor, ocolor.rightColor);
      }

      return format;
   }

   /**
    * Merge the two color and return the dark value.
    */
   private Color mergeDarkColor(Color c1, Color c2) {
      if(c1 == null) {
         return c2;
      }
      else if(c2 == null) {
         return c1;
      }

      return new Color(Math.min(c1.getRed(), c2.getRed()),
                       Math.min(c1.getGreen(), c2.getGreen()),
                       Math.min(c1.getBlue(), c2.getBlue()),
                       Math.min(c1.getAlpha(), c2.getAlpha()));
   }

   /**
    * Expand tables to make all rows/columns visible.
    */
   private void expandTable(TableDataVSAssembly assembly, ViewsheetSandbox box, boolean exprow)
      throws Exception
   {
      String name = assembly.getAbsoluteName();
      VSTableLens olens = box.getVSTableLens(name, false, 1);
      VSTableLens lens = getRegionTableLens(olens, assembly, box);

      //lens may not be initialized for expand table
      lens.initTableGrid(assembly.getVSAssemblyInfo());

      expandTable(assembly, lens, exprow);
   }

   /**
    * Expand the table data assembly to show data without scrolling.
    * @param obj the table assembly.
    * @param table the specified table.
    * @param exprow true to expand row, false to expand column.
    */
   protected void expandTable(TableDataVSAssembly obj, VSTableLens table, boolean exprow) {
      if(table == null) {
         return;
      }

      table.moreRows(Integer.MAX_VALUE);
      int n = exprow ? getExpandTableHeight(obj, table) :
                       getExpandTableWidth(obj, table);
      Dimension size = (Dimension) obj.getPixelSize().clone();

      // @by yanie: bug1430948796260
      // if the expand table's total columns width is still less than
      // the assmebly's width, we should not expand the assembly's width
      // otherwise, some extra useless column will be introduced.
      int expandWidth = exprow ? size.width : getExpandWidth(obj, table, size.width, n);

      // when expanding table, if it pushes directly to the assembly below, it could cause
      // cell overlapping in excel. add 1 row in between to avoid this problem. (43986)
      int more = exprow ? n - size.height + 1: expandWidth - size.width;
      Dimension nsize = exprow
         ? new Dimension(size.width, Math.max(size.height, n))
         : new Dimension(expandWidth, size.height);
      obj.setPixelSize(nsize);
      insertRowCol(obj, size, exprow ? more : 0, exprow ? 0 : more);
   }

   /**
    * Get the width of the table assembly after expand
    * @param obj the specified TableAssembly obj
    * @param table the expand table.
    * @param ow the original table assembly width
    * @param ew the expand width
    */
   private int getExpandWidth(TableDataVSAssembly obj, XTable table, int ow, int ew) {
      if(!(table instanceof VSTableLens) || obj == null || ow >= ew) {
         return Math.max(ow, ew);
      }

      VSTableLens lens = (VSTableLens) table;
      TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo) obj.getInfo();
      int[] ws = new int[lens.getColCount()];
      int[] widths = lens.getColumnWidths();
      int totalWidth = 0;

      // calculate the total columns' width of the expand table
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, table);

         if(Double.isNaN(w)) {
            w = ExcelVSUtil.DEFAULT_COLWIDTH;
         }

         if(widths != null && i < widths.length) {
            w = widths[i];
         }

         w = lens.getColumnWidthWithPadding(w, i);
         ws[i] = (int) w;
         totalWidth += w;
      }

      // if the expand table's total columns width is still less than the
      // assembly size, keep original assembly width
      if(ow >= totalWidth) {
         return ow;
      }

      return ew;
   }

   /**
    * Get row/column count to insert, the result will be more minus
    * those assemblies which has been insert row/column, but not cause the
    * current object to move.
    * @param expanded the row/column insert map.
    * @param obj the object assembly cause the viewsheet to insert row/column.
    * @param size the obj assembly old grid size.
    * @param more the insert row/column count.
    * @param exprow inset row or column.
    */
   protected int getMore(Map<String, Map<Integer, Integer>> expanded,
      VSAssembly obj, Dimension size, int more, boolean exprow)
   {
      if(obj == null || obj.getViewsheet() == null) {
         return more;
      }

      Map<Integer, Integer> vexpand =
         expanded.get(obj.getViewsheet().getAbsoluteName());

      if(vexpand == null) {
         return more;
      }

      Set keys = vexpand.keySet();

      if(keys == null) {
         return more;
      }

      int start = exprow ? obj.getPixelOffset().y : obj.getPixelOffset().x;
      int added = 0;
      Iterator<Integer> iterator = keys.iterator();

      while(iterator.hasNext()) {
         int pos = iterator.next();

         // the insert place not cause current assembly move
         if(pos > start) {
            added += vexpand.get(pos);
         }
      }

      return more - added;
   }

   /**
    * Add more row/column.
    */
   protected void addMore(Map<String, Map<Integer, Integer>> expanded,
                          VSAssembly obj, Dimension size, int more,
                          boolean exprow)
   {
      if(obj == null || obj.getViewsheet() == null) {
         return;
      }

      Map<Integer, Integer> vexpand = expanded.get(obj.getViewsheet().getAbsoluteName());

      if(vexpand == null) {
         vexpand = new HashMap<>();
         expanded.put(obj.getViewsheet().getAbsoluteName(), vexpand);
      }

      int pos = exprow ? obj.getPixelOffset().y + size.height :
                         obj.getPixelOffset().x + size.width;
      Integer added = vexpand.get(pos);

      if(added != null) {
         more = more + added;
      }

      vexpand.put(pos, more);
   }

   /**
    * Get the assembly's grid point on top level viewsheet.
    */
   private Point getPointInTopViewsheet(VSAssembly ass, boolean leftTop) {
      Point gridp = ass.getPixelOffset();
      Viewsheet vs = ass.getViewsheet();

      if(vs.isEmbedded()) {
         return viewsheet.getPixelPosition(ass.getVSAssemblyInfo());
      }
      else if(!leftTop && ass instanceof FloatableVSAssembly) {
         Point pixelOff = ass.getPixelOffset();

         if(pixelOff != null) {
            int x = gridp.x;
            int y = gridp.y;
            x = pixelOff.x > 0 ? x + 1 : x;
            y = pixelOff.y > 0 ? y + 1 : y;
            gridp = new Point(x, y);
         }
      }

      return gridp;
   }

   /**
    * Get the assembly's grid point on top level viewsheet.
    */
   private Point getPointInViewsheet(VSAssembly ass, boolean leftTop) {
      Point gridp = ass.getPixelOffset();

      if(!leftTop && ass instanceof FloatableVSAssembly) {
         Point pixelOff = ass.getPixelOffset();

         if(pixelOff != null) {
            int x = gridp.x;
            int y = gridp.y;
            x = pixelOff.x > 0 ? x + 1 : x;
            y = pixelOff.y > 0 ? y + 1 : y;
            gridp = new Point(x, y);
         }
      }

      return gridp;
   }

   /**
    * Insert row and column when expand assembly.
    * @param obj the expand assembly.
    * @param size the obj's size original size.
    * @param insertrow the rows to be insert.
    * @param insertcol the columns to be insert.
    */
   protected void insertRowCol(VSAssembly obj, Dimension size, int insertrow,
                             int insertcol) {
      /*
        For example insert row to viewsheet, the step:
        1: get all assemblies in the viewsheet which same as obj.
        2: move the position of all the assemblies if needed.
        3: insert rows for the viewsheet which the obj in.
        4: if the viewsheet is embedded, then the viewsheet will cause the
           parent viewsheet to expand(just same as a object assembly).
        5: loop 1->2->3->4.

        functions:
        insert()-->
           moveAssemblies()-->
              addGrid()-->
                 expandParent()-->
                    goto moveAssemblies()
       */
      insert(obj, size, insertrow, true);
      insert(obj, size, insertcol, false);
   }

   /**
    * Insert row or column.
    * @param obj the object assembly cause the viewsheet to insert row/column.
    * @param size the obj assembly old grid size.
    * @param more expand row/column count by grid.
    * @param exprow inset row or column.
    */
   private void insert(VSAssembly obj, Dimension size, int more, boolean exprow) {
      int omore = more;

      if(more > 0) {
         more = getMore(exprow ? rmap : cmap, obj, size, more, exprow);
      }

      if(more <= 0) {
         return;
      }

      addMore(exprow ? rmap : cmap, obj, size, more, exprow);
      Dimension oldSize = getViewsheetSize(obj.getViewsheet(), true);
      moveAssemblies(obj, size, omore, more, exprow);
      addGrid(obj, size, more, exprow);
      expandParent(obj.getViewsheet(), oldSize, exprow);
   }

   /**
    * Move all assembies which in the same viewsheet with the assembly.
    * @param obj the expand object assembly cause the move assemby.
    * @param size the obj old size by grid.
    * @param more move row/column count.
    * @param exprow to move row or column direction.
    */
   protected void moveAssemblies(VSAssembly obj, Dimension size, int omore, int more, boolean exprow) {
      Viewsheet vs = obj == null ? null : obj.getViewsheet();

      if(vs == null) {
         return;
      }

      Point tpos = getPointInViewsheet(obj, true);
      int next = exprow ? tpos.y + size.height : tpos.x + size.width;
      Assembly[] objs = vs.getAssemblies(false);

      // fix bug1256630642885(children assemblies display
      // in uncorrect position in container)
      objs = removeChildrenInContainer(vs, objs, obj);

      for(int i = 0; i < objs.length; i++) {
         // annotation position base on assembly, so don't move it
         if(objs[i] == null || objs[i] instanceof AnnotationVSAssembly ||
            objs[i] instanceof AnnotationLineVSAssembly ||
            objs[i] instanceof AnnotationRectangleVSAssembly)
         {
            continue;
         }

         if(objs[i] instanceof SelectionListVSAssembly ||
            objs[i] instanceof SelectionTreeVSAssembly)
         {
            more = omore;
         }

         Point p2 = objs[i].getPixelOffset();
         Point topp2 = getPointInViewsheet((VSAssembly) objs[i], false);
         int pos2 = exprow ? topp2.y : topp2.x;

         if(pos2 >= next) {
            if(exprow) {
               objs[i].setPixelOffset(new Point(p2.x, p2.y + more));
            }
            else {
               objs[i].setPixelOffset(new Point(p2.x + more, p2.y));
            }
         }
      }
   }

   /**
    * Remove assemblies contained in container assembly to avoid children
    * assemblies's position being caculated again.
    * @param vs view sheet.
    * @param objs all assemblies in current view sheet.
    */
   private Assembly[] removeChildrenInContainer(Viewsheet vs, Assembly[] objs,
                                                VSAssembly source) {
      List<Assembly> assemblies = Arrays.stream(objs).collect(Collectors.toList());
      List<Assembly> children = new ArrayList<>();

      for(int i = assemblies.size() - 1; i >= 0; i--) {
         Assembly current = assemblies.get(i);

         if(current instanceof ContainerVSAssembly) {
            ContainerVSAssembly parent = (ContainerVSAssembly) current;

            if(!parent.containsAssembly(source.getName())) {
               String[] childNames = parent.getAssemblies();

               for(int j = childNames.length - 1; j >= 0; j--) {
                  children.add(vs.getAssembly(childNames[j]));
               }
            }
         }
      }

      for(int i = children.size() - 1; i >= 0; i--) {
         assemblies.remove(children.get(i));
      }

      return assemblies.toArray(new Assembly[] {});
   }

   /**
    * Add grid for the viewsheet which the assembly in.
    * @param obj the object assembly cause the viewsheet to add grid.
    * @param size the object old size by grid, to tag where start add grid.
    * @param more add grid row/column count.
    * @param exprow add row or column.
    */
   private void addGrid(VSAssembly obj, Dimension size, int more,
                        boolean exprow) {
      Viewsheet vs = obj == null ? null : obj.getViewsheet();

      if(vs == null) {
         return;
      }

      if(vs.isEmbedded()) {
         ViewsheetVSAssemblyInfo vinfo = (ViewsheetVSAssemblyInfo) vs.getInfo();
         Dimension pixelsize = getViewsheetSize(vs, false);

         if(exprow) {
            pixelsize.height = pixelsize.height + more;
         }
         else {
            pixelsize.width = pixelsize.width + more;
         }

         vinfo.setPixelSize(pixelsize);
      }
   }

   /**
    * Expand the parent viewsheet which the viewsheet in.
    * @param vs the expanded viewsheet cause parent viewsheet to expand.
    * @param osize the expanded viewsheet old size.
    * @param exprow expand row or column direction.
    */
   protected void expandParent(Viewsheet vs, Dimension osize, boolean exprow) {
      Viewsheet pvs = vs == null ? null : vs.getViewsheet();

      if(pvs == null) {
         return;
      }

      Dimension nsize = getViewsheetSize(vs, true);
      int more = exprow ? nsize.height - osize.height :
                 nsize.width - osize.width;
      // @by davyc, for embedded viewsheet, may expand more times, so
      // its hard to maintain the really more insert columns, so here
      // just ignore it
      //more = getMore(expanded, vs, osize, more, exprow);

      if(more <= 0) {
         return;
      }

      //addMore(expanded, vs, osize, more, exprow);
      moveAssemblies(vs, osize, more, more, exprow);
      Dimension posize = getViewsheetSize(pvs, true);
      addGrid(vs, osize, more, exprow);
      expandParent(pvs, posize, exprow);
   }

   /**
    * Check whether an assembly is child of another assembly.
    */
   private boolean isChildOfContainer(Assembly child, Assembly parent,
                                      Viewsheet vs) {
      if(!(parent instanceof ContainerVSAssembly)) {
         return false;
      }

      ContainerVSAssembly container = (ContainerVSAssembly) parent;

      if(container.containsAssembly(child.getName())) {
         return true;
      }
      else {
         String[] children = container.getAssemblies();

         for(int i = 0; i < children.length; i++) {
            Assembly assembly = vs.getAssembly(children[i]);

            if(isChildOfContainer(child, assembly, vs)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if an assembly overlaps with others.
    * @param ignoreContainer indicates whether ignore the container the assembly
    * belongs to.
    */
   protected boolean isOverlaps(VSAssembly assembly, Viewsheet vs,
                                boolean ignoreContainer) {
      Rectangle ibounds = assembly.getBounds();
      Assembly[] assemblies = vs.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         Assembly asm = assemblies[i];

         if(asm.equals(assembly) || !needExport((VSAssembly) asm) ||
            AnnotationVSUtil.isAnnotation((VSAssembly) asm))
         {
            continue;
         }

         Rectangle bounds = asm.getBounds();

         if(isIntersect(ibounds, bounds, true) &&
            isIntersect(ibounds, bounds, false))
         {
            if(ignoreContainer && (isChildOfContainer(assembly, asm, vs) ||
               isChildOfContainer(asm, assembly, vs)))
            {
               continue;
            }
            else {
               if(!isExportedAsText(asm)) {
                  continue;
               }
               else {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Bug #26801. All assemblies are floatable assemblies in 12-3.
    *    When exporting to Excel, because the text is written into the cell,
    * image will overwrite the text display, so add a hook to determine whether to
    * export as text when the assembly is exported to Excel. If it is exported as text,
    * and If image overlaps, image is not exported.
    */
   protected boolean isExportedAsText(Assembly assembly) {
      return false;
   }

   /**
    * Write the image to the output stream.
    * @param img  the image to write
    * @param out  the output stream to write to
    * @throws IOException  if writing to the output stream failed
    */
   protected void writeSheetImage(BufferedImage img, OutputStream out) throws Exception {
      byte[] buf = VSUtil.getImageBytes(img, 72 * 2);
      out.write(buf);
   }

   /**
    * Check two rectangle is x/y interaction.
    */
   private boolean isIntersect(Rectangle rect1, Rectangle rect2, boolean x) {
      if(x) {
         return rect2.x >= rect1.x && rect2.x < rect1.x + rect1.width ||
                rect1.x >= rect2.x && rect1.x < rect2.x + rect2.width;
      }
      else {
         return rect2.y >= rect1.y && rect2.y < rect1.y + rect1.height ||
                rect1.y >= rect2.y && rect1.y < rect2.y + rect2.height;
      }
   }

   /**
    * Get pixel/grid size of the viewsheet.
    * @param grid identify to get grid size or pixel size.
    */
   private Dimension getViewsheetSize(Viewsheet vs, boolean grid) {
      if(vs == null || vs.getViewsheet() == null) {
         return new Dimension(0, 0);
      }

      ViewsheetVSAssemblyInfo info = (ViewsheetVSAssemblyInfo) vs.getInfo();
      return (Dimension) info.getPixelSize().clone();
   }

   /**
    * Add hyperlink to the chart.
    * @param vgraph graph info
    * @param data the data set
    * @param info chart info
    * @param bounds chart bounds
    */
   protected void processHyperlink(VGraph vgraph, DataSet data,
                                 VSAssemblyInfo info, Rectangle2D bounds)
   {
      if(!(data instanceof AttributeDataSet)) {
         return;
      }

      VSChartInfo vsinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();
      AttributeDataSet adata = (AttributeDataSet) data;

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         Visualizable v = vgraph.getVisual(i);

         if(v instanceof ElementVO) {
            if(v instanceof GraphVO) {
               ArrayList subvos = new ArrayList();
               VGraph subgraph =((GraphVO) v).getVGraph();
               subvos.addAll(GTool.getVOs(subgraph));
               DataSet sub = subgraph.getCoordinate().getDataSet();

               for(int j = 0; j < subvos.size(); j++) {
                  Object vo = subvos.get(j);

                  if(vo instanceof ElementVO) {
                     processGraphVO((ElementVO) vo, adata, vgraph, sub, bounds);
                  }
               }
            }
            else {
               processGraphVO((ElementVO) v, adata, vgraph, null, bounds);
            }
         }
      }

      processAxisLink(vgraph, bounds, vsinfo);
      drawLinkUnderLine(vgraph, data, vsinfo);
   }

   /**
    * Draw the underline of hyperlink.
    * @param vo elementvo
    * @param data the data set
    * @param vgraph graph info
    * @param sub dataset
    * @param bounds chart bounds
    */
   private void processGraphVO(ElementVO vo, AttributeDataSet data, VGraph vgraph,
                               DataSet sub, Rectangle2D bounds)
   {
      processElementVOLink(vo, data, vgraph, sub, bounds);
      processVOTextLink(vo, data, vgraph, sub, bounds);
   }

   /**
    * Draw the underline of hyperlink.
    * @param vgraph graph info
    * @param data the data set
    * @param vsinfo chart info
    */
   private void drawLinkUnderLine(VGraph vgraph, DataSet data, VSChartInfo vsinfo) {
      GraphUtil.processHyperlink(vsinfo, vgraph, data);
   }

   /**
    * Add hyperlink to dimension label.
    * @param vgraph graph info
    * @param bounds chart bounds
    * @param vsinfo chart info
    */
   private void processAxisLink(VGraph vgraph, Rectangle2D bounds,
                                VSChartInfo vsinfo)
   {
      Hyperlink.Ref hyperlink = null;
      Map<String,Hyperlink> links = new HashMap<>();
      addDimensionHyperlink(vsinfo.getRTAxisFields(), links);
      Object[] axises = Tool.mergeArray(
         vgraph.getAxesAt(Coordinate.TOP_AXIS),
         vgraph.getAxesAt(Coordinate.BOTTOM_AXIS));
      axises = Tool.mergeArray(axises,
                               vgraph.getAxesAt(Coordinate.RIGHT_AXIS));
      axises = Tool.mergeArray(axises,
                               vgraph.getAxesAt(Coordinate.LEFT_AXIS));

      for(int i = 0; i < axises.length; i++) {
         Axis axis = (DefaultAxis) axises[i];
         VLabel[] labels = axis.getLabels();

         for(int j = 0; j < labels.length; j++) {
            if(!(labels[j] instanceof VDimensionLabel)) {
               continue;
            }

            VDimensionLabel dlabel = (VDimensionLabel) labels[j];
            Shape shape = dlabel.getTransformedBounds();
            VDimensionLabel vlbl = (VDimensionLabel) labels[j];
            String dimName = vlbl.getDimensionName();
            Object value = Tool.getDataString(vlbl.getValue(), false);
            Hyperlink link = links.get(dimName);

            if(dimName != null && value != null && link != null) {
               HashMap map = new HashMap();
               map.put(dimName, value);
               hyperlink = new Hyperlink.Ref(link, map);
               setHyperlink(vgraph, bounds, hyperlink, shape);
            }
         }
      }
   }

   /**
    * Add link in the ElementVO.
    * @param evo elementvo
    * @param data the dataset
    * @param vgraph graph info
    * @param sub dataset
    * @param bounds chart bounds
    */
   private void processElementVOLink(ElementVO evo, AttributeDataSet data,
                                     VGraph vgraph, DataSet sub, Rectangle2D bounds)
   {
      Shape[] shapes = evo.getShapes();

      if(GraphTypeUtil.supportPoint(evo) || GraphUtil.supportSubSection(evo)) {
         int[] arr = evo.getRowIndexes();

         for(int i = 0; i < shapes.length; i++) {
            if(i >= arr.length) { // script chart
               break;
            }

            Hyperlink.Ref hyperlink = getHyperlink(evo, evo.getColIndex(), data, sub, false);
            setHyperlink(vgraph, bounds, hyperlink, shapes[i]);
         }
      }
      else {
         for(Shape shape : shapes) {
            Hyperlink.Ref hyperlink = getHyperlink(evo, -1, data, sub, false);
            setHyperlink(vgraph, bounds, hyperlink, shape);
         }
      }
   }

   /**
    * Add link in the VOTexts which belong to the ElementVO.
    * @param evo elementvo
    * @param data the dataset
    * @param vgraph graph info
    * @param sub dataset
    * @param bounds chart bounds
    */
   private void processVOTextLink(ElementVO evo, AttributeDataSet data,
                                  VGraph vgraph, DataSet sub, Rectangle2D bounds)
   {
      VOText[] texts = {};

      if(evo instanceof LineVO) {
         texts = evo.getVOTexts();
      }
      else if(evo instanceof Pie3DVO) {
         texts = evo.getVOTexts();
      }
      else {
         texts = new VOText[] {evo.getVOText()};
      }

      if(GraphTypeUtil.supportPoint(evo) || GraphUtil.supportSubSection(evo)) {
         int[] arr = evo.getRowIndexes();

         for(int i = 0; i < texts.length; i++) {
            if(texts[i] == null || texts[i].getZIndex() < 0) {
               continue;
            }

            if(i >= arr.length) {
               break;
            }

            Hyperlink.Ref hyperlink = getHyperlink(evo, evo.getColIndex(),
                                                   data, sub, true);
            setHyperlink(vgraph, bounds, hyperlink,
                         texts[i].getTransformedBounds());
         }
      }
      else {
         for(int i = 0; i < texts.length; i++) {
            if(texts[i] == null || texts[i].getZIndex() < 0) {
               continue;
            }

            Hyperlink.Ref hyperlink = getHyperlink(evo, -1, data, sub, true);
            setHyperlink(vgraph, bounds, hyperlink,
                         texts[i].getTransformedBounds());
         }
      }
   }

   /**
    * Get hyperlink.
    * @param elemVO elementvo
    * @param col column index
    * @param data the dataset
    * @param sub dataset
    * @param text
    * @return the hyperlink ref
    */
   private Hyperlink.Ref getHyperlink(ElementVO elemVO, int col,
                                      AttributeDataSet data, DataSet sub, boolean text) {
      String measure = GraphUtil.getHyperlinkMeasure(elemVO, text);

      if(measure == null) {
         return null;
      }

      Object ref = null;

      if(data instanceof VSDataSet) {
         int subidx = GraphUtil.getSubRowIndex(elemVO, col);
         Hyperlink link = ((VSDataSet) data).getHyperlink(measure);
         sub = sub == null ? data : sub;
         ref = GraphUtil.getHyperlink(link, sub, subidx);
      }

      return (Hyperlink.Ref) ref;
   }

   /**
    * Add the hyperlink to the file.
    * @param vgraph vgraph
    * @param bounds the chart bounds
    * @param hyperlink the hyperlink to link to
    * @param shape the shape of the hyperlink area
    */
   protected void setHyperlink(VGraph vgraph, Rectangle2D bounds,
                             Hyperlink.Ref hyperlink, Shape shape)
   {
      if(hyperlink == null || hyperlink.getLinkType() != Hyperlink.WEB_LINK) {
         return;
      }
   }

   /**
    * Add dimension hyperlink.
    * @param refs refs
    * @param links hyperlinks
    */
   private void addDimensionHyperlink(VSDataRef[] refs, Map<String,Hyperlink> links) {
      if(refs == null || refs.length <= 0) {
         return;
      }

      for(int i = 0; i < refs.length; i++) {
         if(refs[i] instanceof XDimensionRef && refs[i] instanceof HyperlinkRef)
         {
            HyperlinkRef dim = (HyperlinkRef) refs[i];
            Hyperlink hyper = dim.getHyperlink();

            if(hyper != null) {
               links.put(((ChartRef) dim).getFullName(), hyper);
            }
         }
      }
   }

   // compare bottom of the table
   protected Comparator<VSAssembly> bottomComparator = Comparator.comparingInt(
      obj -> getPointInTopViewsheet(obj, false).y);

   // compare right side of the table
   protected Comparator<VSAssembly> rightComparator = Comparator.comparingInt(
      obj -> getPointInTopViewsheet(obj, false).x);

   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // draw background and border
      RectangleVSAssembly outer = new RectangleVSAssembly(chart.getViewsheet(), "");
      RectangleVSAssemblyInfo outerInfo = (RectangleVSAssemblyInfo) outer.getInfo();
      outerInfo.setLineStyle(0);
      Color background = info.getFormat().getBackground();
      outerInfo.getFormat().getUserDefinedFormat().setBackground(background);
      outerInfo.getFormat().getUserDefinedFormat()
         .setBorders(info.getFormat().getBorders());
      outerInfo.getFormat().getUserDefinedFormat()
         .setBorderColors(info.getFormat().getBorderColors());
      outerInfo.setPixelOffset(pos);
      outerInfo.setPixelSize(new Dimension(size.width + 1, size.height));
      writeShape(outer);
   }

   /**
    * @param info the info to get the title height of.
    *
    * @return the title height if the info has a visible title, otherwise 0.
    */
   protected int getTitleHeight(VSAssemblyInfo info) {
      if(info instanceof TitledVSAssemblyInfo) {
         final TitledVSAssemblyInfo titledInfo = (TitledVSAssemblyInfo) info;

         if(titledInfo.isTitleVisible()) {
            return titledInfo.getTitleHeight();
         }
      }

      return 0;
   }

   /**
    * @param format the format to get the border offsets of.
    *
    * @return the border offsets of the format.
    */
   protected Insets2D getBorderOffset(VSCompositeFormat format) {
      double top = 0;
      double left = 0;

      if(format != null && format.getBorders() != null) {
         if(format.getBorders().top == StyleConstants.THICK_LINE) {
            top = 2.5;
         }
         else if(format.getBorders().top == StyleConstants.MEDIUM_LINE) {
            top = 2;
         }
         else {
            top = 1;
         }

         if(format.getBorders().left == StyleConstants.THICK_LINE) {
            left = 4;
         }
         else if(format.getBorders().left == StyleConstants.MEDIUM_LINE) {
            left = 2;
         }
         else {
            left = 1;
         }
      }

      // bottom, right not yet needed for export layout so ignored.
      return new Insets2D(top, left, 0, 0);
   }

   /**
    * @return if only export chart as image.
    */
   protected boolean exportChartAsImage(ChartVSAssembly chart) {
      return true; // ExcelBuilder.exportChartAsImage() always returns true now
   }

   /*
    * Some operations may damage the file. Solve the file corruption problem here
    */
   protected void fixFileError() {}

   /**
    * Sort assemblies by the row count when export to html (54547).
    * Export the larger row count after the less row count, to avoid other assemblies not
    * renderred util the larget row count assembly render finished.
    */
   private void sortAssemblies(Assembly[] assemblies) {
      if(!(this instanceof HTMLVSExporter)) {
         return;
      }

      Arrays.sort(assemblies, new Comparator<Assembly>() {
         @Override
         public int compare(Assembly assembly1, Assembly assembly2) {
            if(assembly1 == null || assembly2 == null) {
               return 0;
            }

            int level_1 = assembly1 instanceof TableDataVSAssembly ? 1 : 0;
            int level_2 = assembly2 instanceof TableDataVSAssembly ? 1 : 0;

            if(level_1 == 0 && level_2 == 0) {
               try {
                  TableLens lens = box.getVSTableLens(assembly1.getAbsoluteName(), false, 1);
                  lens = getRegionTableLens(lens, (TableDataVSAssembly) assembly1, box);

                  if(lens != null) {
                     lens.moreRows(Integer.MAX_VALUE);
                     level_1 = lens.getRowCount();
                  }
               }
               catch(Exception ignore) {
               }

               try {
                  TableLens lens = box.getVSTableLens(assembly2.getAbsoluteName(), false, 1);
                  lens = getRegionTableLens(lens, (TableDataVSAssembly) assembly2, box);

                  if(lens != null) {
                     lens.moreRows(Integer.MAX_VALUE);
                     level_2 = lens.getRowCount();
                  }
               }
               catch(Exception ignore) {
               }
            }

            return level_1 < level_2 ? -1 : 1;
         }
      });
   }

   public static String getFileType(int type) {
      String ftype = null;

      switch(type) {
      case FileFormatInfo.EXPORT_TYPE_EXCEL:
         ftype = "xlsx";
         break;
      case FileFormatInfo.EXPORT_TYPE_POWERPOINT:
         ftype = "pptx";
         break;
      case FileFormatInfo.EXPORT_TYPE_PDF:
         ftype = "pdf";
         break;
      case FileFormatInfo.EXPORT_TYPE_PNG:
         ftype = "png";
         break;
      case FileFormatInfo.EXPORT_TYPE_HTML:
         ftype = "html";
         break;
      case FileFormatInfo.EXPORT_TYPE_CSV:
         ftype = "csv";
         break;
      default:
      }

      return ftype;
   }

   // viewsheet name --> map, map: insert row/column place --> insert number
   protected Map<String, Map<Integer, Integer>> rmap = new HashMap<>();
   protected Map<String, Map<Integer, Integer>> cmap = new HashMap<>();
   protected Viewsheet viewsheet; // base viewsheet
   protected AssetEntry entry; // corresponding asset entry of the viewsheet
   protected ViewsheetSandbox box;
   protected RuntimeViewsheet rvs;
   private boolean match; // match layout flag
   private boolean expandSelections; // expand selection list/tree
   protected boolean log = false; // should log execution
   private boolean logExport = false;
   protected FlexTheme theme;
   // the sheet index, Current View is 0, bookmark1 is 1, etc.
   // To fix bug1205749332140 caused by poi bug
   protected int index;
   protected int maxRows = 0;
   protected boolean onlyDataComponents;
   private static int fileType = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractVSExporter.class);
}
