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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.*;
import inetsoft.report.internal.AllCompositeTextFormat;
import inetsoft.report.internal.AllLegendDescriptor;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.css.CSSParameter;
import inetsoft.util.script.ScriptException;
import inetsoft.web.adhoc.model.*;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.command.AddLayoutObjectCommand;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.text.Format;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Controller that provides a REST endpoint for manipulating object format.
 */
@Controller
public class FormatPainterController {
   /**
    * Creates a new instance of <tt>FormatPainterController</tt>.
    */
   @Autowired
   public FormatPainterController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  CoreLifecycleService coreLifecycleService,
                                  ChartRegionHandler regionHandler,
                                  ViewsheetService viewsheetService,
                                  VSObjectModelFactoryService objectModelService,
                                  VSBindingService bindingService,
                                  VSLayoutService vsLayoutService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.regionHandler = regionHandler;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.bindingService = bindingService;
      this.vsLayoutService = vsLayoutService;
   }

   /**
    * Get the format of the currently selected object to fill the toolbar.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get viewsheet or objects
    */
   @MessageMapping("composer/viewsheet/getFormat")
   public void getFormat(@Payload GetVSObjectFormatEvent event, Principal principal,
                         CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = null;

      if(event.isLayout()) {
         RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
            viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
         final Viewsheet parentViewsheet = parentRvs.getViewsheet();
         PrintLayout printLayout = parentViewsheet.getLayoutInfo().getPrintLayout();
         VSAssemblyLayout layoutAssembly =
            vsLayoutService.findAssemblyLayout(printLayout, event.getName(),
                                               event.getLayoutRegion())
               .orElse(null);

         if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
            return;
         }

         VSAssemblyInfo info = ((VSEditableAssemblyLayout) layoutAssembly).getInfo();

         if(info instanceof TextVSAssemblyInfo) {
            assembly = new TextVSAssembly(viewsheet, layoutAssembly.getName());
         }
         else {
            assembly = new ImageVSAssembly(viewsheet, layoutAssembly.getName());
         }

         assembly.setVSAssemblyInfo(info);
      }
      else if(event.getName() == null) {
         assembly = viewsheet;
      }
      else {
         assembly = viewsheet.getAssembly(event.getName());
      }

      if(assembly == null) {
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      // fetch circle packing container format.
      if(assembly instanceof ChartVSAssembly && ChartFormatConstants.VO.equals(event.getRegion())) {
         getVOFormat(event, (ChartVSAssembly) assembly, commandDispatcher);
         return;
      }

      if(assembly instanceof ChartVSAssembly && event.getRegion() != null
         && (!"plot_area".equals(event.getRegion())
         || ("plot_area".equals(event.getRegion()) && event.getColumnName() != null)))
      {
         try {
            if(!event.getRegion().endsWith("_axis") || event.getColumnName() != null) {
               getChartFormat(event, principal, (ChartVSAssembly) assembly, commandDispatcher);
               return;
            }
         }
         catch(ArrayIndexOutOfBoundsException ex) {
            // 6-26-2017 Commands are not queued up and same commands are not consolidated,
            // this makes it so a object being changed may send back multiple Refresh Commands
            // at different points in the updating process. And when trying to get the format for
            // the earlier refresh commands, by the time it gets the object here it will be fully
            // updated and out of sync leading to an Index out of Bounds Exception
            // Expect same commands to be consolidated at end of processing an event and this
            // catch not to be needed anymore.
            // continue
         }
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      FormatInfo formatInfo = info.getFormatInfo();
      TableDataPath dataPath;

      if(event.getDataPath() != null) {
         if(assembly instanceof SelectionTreeVSAssembly &&
            ((SelectionTreeVSAssembly) assembly).isIDMode() &&
            !isMeasureTextBar(event.getDataPath()))
         {
            dataPath = new TableDataPath(-1, TableDataPath.DETAIL);
         }
         else {
            dataPath = event.getDataPath();
         }
      }
      else {
         dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
      }

      String presenter = null;
      String presenterLabel = null;
      boolean hasDescriptors = false;
      VSCompositeFormat format = formatInfo.getFormat(dataPath, false);

      if(format == null) {
         format = new VSCompositeFormat();
      }
      else {
         format = format.clone();
      }

      if(assembly instanceof Viewsheet) {
         format.getCSSFormat().setCSSType(CSSConstants.VIEWSHEET);
      }

      if(assembly instanceof TableDataVSAssembly &&
         (!event.isBinding() || !(assembly instanceof CalcTableVSAssembly)) &&
         dataPath.getType() != TableDataPath.TITLE &&
         (event.getRow() > -1 && event.getColumn() > -1))
      {
         String oname = assembly.getAbsoluteName();
         boolean detail = oname.startsWith(Assembly.DETAIL);

         if(detail) {
            oname = oname.substring(Assembly.DETAIL.length());
         }

         if(box.isCancelled(System.currentTimeMillis())) {
            return;
         }

         VSTableLens lens = box.getVSTableLens(oname, detail);

         if(lens == null || !lens.moreRows(event.getRow()) ||
            event.getColumn() >= lens.getColCount())
         {
            return;
         }

         TableDataPath cellpath = lens.getTableDataPath(event.getRow(), event.getColumn());
         VSCompositeFormat cellFormat0 = formatInfo.getFormat(cellpath, false);
         VSCompositeFormat objFormat = formatInfo.getFormat(
            new TableDataPath(-1, TableDataPath.OBJECT), false);
         VSFormat cellFormat = lens.getFormat(event.getRow(), event.getColumn());

         if(cellFormat0 != null) {
            format = cellFormat0.clone();
            format.setDefaultFormat(cellFormat);
         }
         else {
            format.setUserDefinedFormat(cellFormat);
         }

         Color cellFg = cellFormat.getForeground();
         Color cellBg = cellFormat.getBackground();
         Font cellFont = cellFormat.getFont();

         if(cellFg != null) {
            format.getDefaultFormat().setForegroundValue(cellFg.getRGB() + "");
         }
         else {
            format.getDefaultFormat().setForegroundValue(objFormat.getForegroundValue());
         }

         if(cellBg != null) {
            format.getDefaultFormat().setBackgroundValue(cellBg.getRGB() + "");
         }
         else {
            format.getDefaultFormat().setBackgroundValue(objFormat.getBackgroundValue());
         }

         if(cellFont != null) {
            format.getDefaultFormat().setFontValue(cellFont);
         }
         else {
            format.getDefaultFormat().setFontValue(objFormat.getFontValue());
         }

         if(lens.moreRows(event.getRow()) && event.getColumn() < lens.getColCount()) {
            Object data = lens.getObject(event.getRow(), event.getColumn());

            if(data instanceof PresenterPainter) {
               presenterLabel = ((PresenterPainter) data).getPresenter().getDisplayName();
               presenter = ((PresenterPainter) data).getPresenter().getClass().getName();
               PresenterRef ref = new PresenterRef(presenter);
               hasDescriptors = ref.getPropertyDescriptors() != null
                  && ref.getPropertyDescriptors().length > 0;
            }
            else {
               presenterLabel = Catalog.getCatalog().getString("(none)");
            }
         }
      }
      else {
         if(format.getPresenter() != null) {
            try {
               presenterLabel = format.getPresenter().createPresenter().getDisplayName();
               presenter = format.getPresenter().createPresenter().getClass().getName();
            }
            catch(Exception ex) {
               presenter = presenterLabel = format.getPresenter().getName();
            }

            hasDescriptors = format.getPresenter().getPropertyDescriptors() != null
               && format.getPresenter().getPropertyDescriptors().length > 0;
         }
      }

      VSObjectFormatInfoModel formatModel = new VSObjectFormatInfoModel();
      formatModel.setPresenterLabel(presenterLabel);
      formatModel.setPresenter(presenter);
      formatModel.setPresenterHasDescriptors(hasDescriptors);
      formatModel.setRoundCorner(format.getRoundCornerValue());

      if(assembly instanceof TabVSAssembly) {
         TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) info;
         formatModel.setRoundTopCornersOnly(tabInfo.isRoundTopCornersOnly());
      }

      if(assembly instanceof GaugeVSAssembly) {
         GaugeVSAssemblyInfo gaugeInfo = (GaugeVSAssemblyInfo) info;
         formatModel.setValueFillColor(gaugeInfo.getValueFillColorValue());
      }

      VSCSSFormat cssFormat = format.getCSSFormat();
      formatModel.setCssID(cssFormat.getCSSID());
      formatModel.setCssClass(cssFormat.getCSSClass());
      formatModel.setCssIDs(cssFormat.getCSSIDs());
      formatModel.setCssClasses(cssFormat.getCSSClasses());

      if(assembly instanceof TabVSAssembly && dataPath.getType() != TableDataPath.OBJECT) {
         formatModel.setCssType(cssFormat.getCSSType() + "[active=true]");
      }
      else {
         formatModel.setCssType(cssFormat.getCSSType());
      }

      if(format.getFontValue() != null) {
         formatModel.setFont(new FontInfo(format.getFontValue()));
      }

      if(format.getBorders() != null) {
         Insets border = format.getBorders();
         formatModel.setBorderTopStyle(String.valueOf(border.top));
         formatModel.setBorderLeftStyle(String.valueOf(border.left));
         formatModel.setBorderBottomStyle(String.valueOf(border.bottom));
         formatModel.setBorderRightStyle(String.valueOf(border.right));
      }
      else {
         formatModel.setBorderTopStyle(null);
         formatModel.setBorderLeftStyle(null);
         formatModel.setBorderBottomStyle(null);
         formatModel.setBorderRightStyle(null);
      }

      if(format.getBorderColors() != null) {
         BorderColors borderColors = format.getBorderColors();
         formatModel.setBorderTopColor("#" + Tool.colorToHTMLString(borderColors.topColor));
         formatModel.setBorderLeftColor("#" + Tool.colorToHTMLString(borderColors.leftColor));
         formatModel.setBorderBottomColor("#" + Tool.colorToHTMLString(borderColors.bottomColor));
         formatModel.setBorderRightColor("#" + Tool.colorToHTMLString(borderColors.rightColor));
      }
      else {
         String defaultColor = "#" + Tool.colorToHTMLString(VSAssemblyInfo.DEFAULT_BORDER_COLOR);
         formatModel.setBorderTopColor(defaultColor);
         formatModel.setBorderLeftColor(defaultColor);
         formatModel.setBorderBottomColor(defaultColor);
         formatModel.setBorderRightColor(defaultColor);
      }

      formatModel.setBackgroundAlpha(format.getAlphaValue());

      String colorString = format.getForegroundValue();
      Color colorValue = format.getForeground();

      if(colorString != null && (colorString.startsWith("=") || colorString.startsWith("$"))) {
         formatModel.setColorType(colorString);
         formatModel.setColor("");
      }
      else {
         formatModel.setColorType("Static");

         if(colorString != null) {
            formatModel.setColor(colorString);
         }
         else if(colorValue != null) {
            formatModel.setColor("#" + Tool.colorToHTMLString(colorValue));
         }
      }

      colorString = format.getBackgroundValue();
      colorValue = format.getBackground();

      if(colorString != null && (colorString.startsWith("=") || colorString.startsWith("$"))) {
         formatModel.setBackgroundColorType(colorString);
         formatModel.setBackgroundColor("");
      }
      else {
         formatModel.setBackgroundColorType("Static");

         if(colorString != null) {
            formatModel.setBackgroundColor(colorString);
         }
         else if(colorValue != null) {
            formatModel.setBackgroundColor("#" + Tool.colorToHTMLString(colorValue));
         }
      }

      formatModel.setAlign(new AlignmentInfo(format.getAlignment()));
      formatModel.setWrapText(format.isWrapping());
      formatModel.setFormat(format.getFormat());
      formatModel.setFormatSpec(format.getFormatExtentValue());
      formatModel.fixDateSpec(format.getFormat(), format.getFormatExtentValue());
      formatModel.setShape(assembly instanceof ShapeVSAssembly);
      formatModel.setImage(assembly instanceof ImageVSAssembly);
      formatModel.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

      SetCurrentFormatCommand command = new SetCurrentFormatCommand(formatModel);
      commandDispatcher.sendCommand(command);
      //For vs binding pane, set the command to assembly editor.
      commandDispatcher.sendCommand(event.getName(), command);
   }

   // get circle packing container format.
   private void getVOFormat(GetVSObjectFormatEvent event, ChartVSAssembly assembly,
                            CommandDispatcher commandDispatcher)
   {
      VSChartInfo cinfo = assembly.getVSChartInfo();
      VSObjectFormatInfoModel formatModel = new VSObjectFormatInfoModel();

      if(cinfo.getChartType() == GraphTypes.CHART_CIRCLE_PACKING) {
         CompositeTextFormat fmt = GraphFormatUtil.getVOTextFormat(
            event.getColumnName(), cinfo, assembly.getChartDescriptor().getPlotDescriptor(), false);

         formatModel.setBackgroundColorType("Static");

         if(fmt != null) {
            formatModel.setBackgroundColor(Tool.toString(fmt.getBackground()));
            formatModel.setBackgroundAlpha(fmt.getAlpha());
            formatModel.setCssClass(fmt.getCSSFormat().getCSSClass());
            formatModel.setCssID(fmt.getCSSFormat().getCSSID());

            CSSTextFormat cssFormat = fmt.getCSSFormat();
            formatModel.setCssIDs(cssFormat.getCSSIDs());
            formatModel.setCssClasses(cssFormat.getCSSClasses());
            formatModel.setCssType(cssFormat.getCSSType());

            formatModel.setFont(new FontInfo(fmt.getFont()));
            formatModel.setColorType("Static");
            formatModel.setColor(Tool.toString(fmt.getColor()));
            formatModel.setAlign(new AlignmentInfo(fmt.getAlignment()));
            XFormatInfo xfmt = fmt.getFormat();
            formatModel.setFormat(xfmt.getFormat());
            formatModel.setFormatSpec(xfmt.getFormatSpec());
         }
      }

      SetCurrentFormatCommand command = new SetCurrentFormatCommand(formatModel);
      commandDispatcher.sendCommand(command);
      commandDispatcher.sendCommand(event.getName(), command);
   }

   /**
    * Set the format of the given data paths.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get viewsheet or objects
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/format")
   public void setFormat(@Payload FormatVSObjectEvent event, Principal principal,
                         CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      final Viewsheet parentViewsheet = parentRvs.getViewsheet();
      PrintLayout printLayout = parentViewsheet.getLayoutInfo().getPrintLayout();
      String[] objects;
      boolean vs = (event.getObjects() == null || event.getObjects().length == 0) &&
         (event.getCharts() == null || event.getCharts().length == 0);
      ChangedAssemblyList clist = coreLifecycleService.createList(false, commandDispatcher,
                                                                rvs, linkUri);

      if(vs) {
         objects = new String[]{ null };
      }
      else {
         objects = event.getObjects();
      }

      box.lockWrite();

      try {
         for(int i = 0; i < objects.length; i++) {
            String name = objects[i];
            VSAssembly assembly;
            VSAssemblyLayout layoutAssembly = null;

            if(event.isLayout()) {
               layoutAssembly = vsLayoutService.findAssemblyLayout(printLayout, name,
                                                                   event.getLayoutRegion())
                  .orElse(null);

               if(!(layoutAssembly instanceof VSEditableAssemblyLayout)) {
                  continue;
               }

               VSAssemblyInfo layoutInfo = ((VSEditableAssemblyLayout) layoutAssembly).getInfo();

               if(layoutInfo instanceof TextVSAssemblyInfo) {
                  assembly = new TextVSAssembly(viewsheet, layoutAssembly.getName());
               }
               else {
                  assembly = new ImageVSAssembly(viewsheet, layoutAssembly.getName());
               }

               assembly.setVSAssemblyInfo(layoutInfo);
            }
            else if(name == null) {
               assembly = viewsheet;
            }
            else {
               assembly = viewsheet.getAssembly(name);
            }

            VSAssemblyInfo info = assembly.getVSAssemblyInfo();

            if(info instanceof TabVSAssemblyInfo) {
               TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) info;

               if(event.getFormat() != null && !event.isReset()) {
                  tabInfo.setRoundTopCornersOnly(event.getFormat().isRoundTopCornersOnly());
               }
               else if(event.isReset()) {
                  tabInfo.setRoundTopCornersOnly(true);
               }
            }

            if(info instanceof GaugeVSAssemblyInfo) {
               GaugeVSAssemblyInfo gaugeInfo = (GaugeVSAssemblyInfo) info;
               gaugeInfo.setValueFillColorValue(event.getFormat() == null ? null :
                  event.getFormat().getValueFillColor());
            }

            FormatInfo formatInfo = info.getFormatInfo();
            TableDataPath[] paths = null;

            if(event.getData() != null && event.getData().size() > i
               && event.getData().get(i) != null && event.getData().get(i).length > 0)
            {
               paths = event.getData().get(i);
            }

            TableDataPath dataPath;
            boolean isIDSelectTree = assembly instanceof SelectionTreeVSAssembly &&
               ((SelectionTreeVSAssembly) assembly).isIDMode();

            if(paths != null) {
               boolean warnStringFormat = false;

               for(TableDataPath path : paths) {
                  if(isIDSelectTree && path.getType() != TableDataPath.OBJECT &&
                     !isMeasureTextBar(path))
                  {
                     path = new TableDataPath(-1, TableDataPath.DETAIL);
                  }

                  warnStringFormat = warnStringFormat || isFormattedStringColumn(event, assembly, path);
                  VSObjectFormatInfoModel format = event.getFormat();

                  //if setting a column header to a number format when not applicable, don't write format to prevent breaking later
                  if(path.getType() == TableDataPath.HEADER) {
                     warnStringFormat = handleHeaderFormats(event, assembly, format, path, warnStringFormat, catalog);
                  }

                  changeFormat(formatInfo, format, event.getOrigFormat(),
                               path, event.isReset());
               }

               if(warnStringFormat) {
                  Tool.addUserMessage(new UserMessage(
                     catalog.getString("composer.stringColumnFormat"), ConfirmException.WARNING,
                     assembly.getName()));
               }
            }
            else {
               dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
               changeFormat(formatInfo, event.getFormat(), event.getOrigFormat(),
                            dataPath, event.isReset(), event.isCopyFormat());

               if(isFormattedStringColumn(event, assembly, dataPath)) {
                  Tool.addUserMessage(new UserMessage(
                     catalog.getString("composer.stringColumnFormat"), ConfirmException.WARNING,
                     assembly.getName()));
               }
            }

            // clear the old version(13.3) format store when set the format by new way(13.4).
            if(info instanceof CalendarVSAssemblyInfo && calendarFormatIsSet(info)) {
               ((CalendarVSAssemblyInfo) info).setSelectedDateFormatValue(null);
               ((CalendarVSAssemblyInfo) info).setSelectedDateFormat(null);
            }

            int hint = VSAssembly.VIEW_CHANGED;

            if(event.isLayout()) {
               AddLayoutObjectCommand command = new AddLayoutObjectCommand();
               command.setObject(vsLayoutService.createObjectModel(rvs, layoutAssembly,
                                                                   objectModelService));
               command.setRegion(event.getLayoutRegion());
               commandDispatcher.sendCommand(command);
            }
            else if(vs) {
               this.coreLifecycleService.setViewsheetInfo(rvs, linkUri, commandDispatcher);

               // refresh vs when css class/id changed to update the format of all assemblies
               if(event.getOrigFormat() != null && event.getFormat() != null &&
                  (!Tool.equals(event.getOrigFormat().getCssClass(), event.getFormat().getCssClass()) ||
                     !Tool.equals(event.getOrigFormat().getCssID(), event.getFormat().getCssID())))
               {
                  this.coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri,
                                                           commandDispatcher, false, false,
                                                           true, clist);
               }
            }
            else {
               this.coreLifecycleService.execute(rvs, assembly.getAbsoluteName(), linkUri, hint,
                                               commandDispatcher);
               this.coreLifecycleService.refreshVSAssembly(rvs, assembly, commandDispatcher);
               this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher);
            }
         }

         for(int i = 0; i < event.getCharts().length; i++) {
            String name = event.getCharts()[i];
            ChartVSAssembly assembly = (ChartVSAssembly) viewsheet.getAssembly(name);
            ChartVSAssemblyInfo info = assembly.getChartInfo();
            VSChartInfo chartInfo = info.getVSChartInfo();
            Color plotbg = info.getChartDescriptor().getPlotDescriptor().getBackground();

            Boolean hasResetClist = false;

            if(ChartFormatConstants.VO.equals(event.getRegions()[i]) &&
               chartInfo.getChartType() == GraphTypes.CHART_CIRCLE_PACKING)
            {
               setVOFormat(info, assembly.getChartDescriptor(), event.getFormat(),
                           event.getOrigFormat(), event.getColumnNames()[i], event.isReset());
            }
            else if(event.getRegions()[i] == null ||
               event.getRegions()[i].equals(ChartFormatConstants.VO) ||
               ((event.getRegions()[i].equals("plot_area") || event.getRegions()[i].endsWith("_axis")) &&
                  (event.getColumnNames()[i] == null || event.getColumnNames()[i].length == 0)))
            {
               FormatInfo chartFormatInfo = info.getFormatInfo();
               TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
               changeFormat(chartFormatInfo, event.getFormat(), event.getOrigFormat(),
                            dataPath, event.isReset(), event.isCopyFormat());

               if(event.isReset()) {
                  CSSChartStyles.resetUserValues(info.getChartDescriptor(),
                                                 info.getVSChartInfo());
               }

               box.reset(clist);
               hasResetClist = true;

               setChartRegionsFormat(chartFormatInfo, event.getFormat(), event.getOrigFormat(),
                                     info, event.isReset());

               if(event.isReset()) {
                  GraphUtil.syncWorldCloudColor(chartInfo);
               }
            }
            else if(ChartFormatConstants.TARGET_LABEL.equals(event.getRegions()[i])) {
               setChartFormat(event.getRegions()[i], event.getIndexes()[i], null, assembly,
                              event.getFormat(), event.getOrigFormat(), event.isValueText(),
                              event.isReset(), principal);
            }
            else {
               setChartFormat(event.getRegions()[i], event.getIndexes()[i],
                              event.getColumnNames()[i], assembly, event.getFormat(),
                              event.getOrigFormat(), event.isValueText(),
                              event.isReset(), principal);
            }

            info.getChartDescriptor().getPlotDescriptor().setBackground(plotbg);
            int hint = VSAssembly.VIEW_CHANGED;

            VSChartInfo cinfo = assembly.getVSChartInfo();
            info.setRTChartDescriptor(null);
            cinfo.clearRuntime();
            box.updateAssembly(name);

            if(!hasResetClist) {
               box.reset(clist);
            }

            this.coreLifecycleService.execute(rvs, assembly.getAbsoluteName(), linkUri, hint,
                                            commandDispatcher);
            this.coreLifecycleService.refreshVSAssembly(rvs, assembly, commandDispatcher);
            this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher);

            BindingModel binding = bindingService.createModel(assembly);
            SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
            commandDispatcher.sendCommand(bcommand);
         }
      }
      finally {
         box.unlockWrite();
      }

      SetCurrentFormatCommand command = new SetCurrentFormatCommand(event.getFormat());
      commandDispatcher.sendCommand(command);

      List<Exception> exs = WorksheetEngine.ASSET_EXCEPTIONS.get();

      if(exs != null && exs.size() > 0) {
         for(Exception ex : exs) {
            if(ex instanceof ScriptException) {
               throw ex;
            }
         }
      }
   }

   // set circle packing container format.
   private void setVOFormat(ChartVSAssemblyInfo chartInfo, ChartDescriptor desc,
                            VSObjectFormatInfoModel format, VSObjectFormatInfoModel origFormat,
                            String[] columnNames, boolean reset)
   {
      if(chartInfo.getVSChartInfo().getChartType() != GraphTypes.CHART_CIRCLE_PACKING) {
         return;
      }

      CompositeTextFormat fmt = GraphFormatUtil.getVOTextFormat(columnNames[0],
                                                                chartInfo.getVSChartInfo(), desc.getPlotDescriptor(), false);

      if(reset) {
         fmt.getUserDefinedFormat().setBackground(null, false);
         fmt.getUserDefinedFormat().setAlpha(1, false);
         fmt.getCSSFormat().setCSSClass(null);
         fmt.getCSSFormat().setCSSID(null);
      }
      else {
         if(!Objects.equals(format.getBackgroundColor(), origFormat.getBackgroundColor())) {
            fmt.setBackground(Tool.getColorFromHexString(format.getBackgroundColor()));
         }

         if(!Objects.equals(format.getBackgroundAlpha(), origFormat.getBackgroundAlpha())) {
            fmt.setAlpha(format.getBackgroundAlpha());
         }

         fmt.getCSSFormat().setCSSClass(format.getCssClass());
         fmt.getCSSFormat().setCSSID(format.getCssID());

         if(!Objects.equals(format.getColor(), origFormat.getColor())) {
            fmt.setColor(Tool.getColorFromHexString(format.getColor()));
         }

         fmt.setAlignment(format.getAlign().toAlign());
         fmt.setFormat(new XFormatInfo(format.getFormat(), format.getFormatSpec()));
      }
   }

   private boolean handleHeaderFormats(FormatVSObjectEvent event, VSAssembly assembly, VSObjectFormatInfoModel format,
                                       TableDataPath path, boolean warnStringFormat, Catalog catalog)
   {
      if(!XSchema.STRING.equals(path.getDataType())) {
         return warnStringFormat;
      }

      String formatType = format.getFormat();
      boolean badFormat = false;
      Pattern PLACEHOLDER = Pattern.compile("\\s*Cell \\[\\d+,\\d+\\]\\s*");

      if("DecimalFormat".equals(formatType)) {
         badFormat = Arrays.stream(path.getPath()).allMatch(p -> {
            try {
               if(p == null || p.isEmpty()) {
                  return false;
               }

               //ignore aggregate or other placeholder value of type Cell [x, y]
               if(PLACEHOLDER.matcher(p).matches()) {
                  return false;
               }

               Integer.parseInt(p);
               return false;
            }
            catch(NumberFormatException e) {
               return true;
            }
         });

         if(!badFormat) {
            warnStringFormat = false;
         }
      }

      if("DateFormat".equals(formatType)) {
         badFormat = Arrays.stream(path.getPath()).allMatch(p -> {
            try {
               if(p == null || p.isEmpty()) {
                  return false;
               }

               //ignore aggregate or other placeholder value of type Cell [x, y]
               if(PLACEHOLDER.matcher(p).matches()) {
                  return false;
               }

               Instant.parse(p);
               return false;
            }
            catch (DateTimeParseException e1) {
               try {
                  LocalDateTime.parse(p);
                  return false;
               }
               catch(DateTimeParseException e2) {
                  try {
                     LocalDate.parse(p);
                     return false;
                  }
                  catch(DateTimeParseException e3) {
                     return true;
                  }
               }
            }
         });

         if(!badFormat) {
            warnStringFormat = false;
         }
      }

      if(badFormat) {
         event.getFormat().setFormat(null);
         Tool.addUserMessage(new UserMessage(
            catalog.getString("composer.invalidHeaderFormat"), ConfirmException.WARNING,
            assembly.getName()));
         warnStringFormat = false;
      }

      return warnStringFormat;
   }

   private boolean isMeasureTextBar(TableDataPath path) {
      if(path.getType() != TableDataPath.DETAIL) {
         return false;
      }

      if(path.getPath() == null || path.getPath().length != 1) {
         return false;
      }

      String str = path.getPath()[0];

      return "Measure Text".equals(str) || "Measure Bar".equals(str);
   }

   private boolean isFormattedStringColumn(FormatVSObjectEvent event, VSAssembly assembly,
                                           TableDataPath path)
   {
      return (assembly instanceof TableVSAssembly) &&
         "string".equals(path.getDataType()) &&
         (event.getOrigFormat() == null || event.getOrigFormat().getFormat() == null) &&
         event.getFormat() != null && event.getFormat().getFormat() != null;
   }

   /**
    * Check whether the calendar detail area format.
    */
   private boolean calendarFormatIsSet(VSAssemblyInfo info) {
      if(!(info instanceof CalendarVSAssemblyInfo)) {
         return false;
      }

      CalendarVSAssemblyInfo calendarVSAssemblyInfo = (CalendarVSAssemblyInfo) info;
      TableDataPath dataPath = null;

      if(calendarVSAssemblyInfo.isYearView()) {
         dataPath = new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);
      }
      else {
         dataPath = new TableDataPath(-1, TableDataPath.MONTH_CALENDAR);
      }

      FormatInfo formatInfo = info.getFormatInfo();

      if(formatInfo != null) {
         VSCompositeFormat format = formatInfo.getFormat(dataPath);

         if(format != null && "DateFormat".equals(format.getFormat()) &&
            format.getFormatExtent() != null)
         {
            return true;
         }
      }

      return false;
   }

   private void changeFormat(FormatInfo info, VSObjectFormatInfoModel model,
                             VSObjectFormatInfoModel origFormat, TableDataPath path,
                             boolean reset)
   {
      changeFormat(info, model, origFormat, path, reset, false);
   }

   /**
    * Update Format.
    */
   private void changeFormat(FormatInfo info, VSObjectFormatInfoModel model,
                             VSObjectFormatInfoModel origFormat, TableDataPath path,
                             boolean reset, boolean copyFormat)
   {
      VSCompositeFormat format = info.getFormat(path, false);

      if(format == null) {
         format = new VSCompositeFormat();
      }

      if(reset) {
         model = new VSObjectFormatInfoModel();
      }

      setUserFormat(format, model, origFormat, reset, copyFormat);

      VSCSSFormat cssFormat = format.getCSSFormat();

      if(!Tool.equals(cssFormat.getCSSClass(), model.getCssClass()) ||
         !Tool.equals(cssFormat.getCSSID(), model.getCssID()))
      {
         cssFormat.setCSSClass(model.getCssClass());
         cssFormat.setCSSID(model.getCssID());
         updateFormatModel(model, format);
      }

      info.setFormat(path, format);
   }

   // if CSS class/id changed, we may need to update the formats to reflect the new css
   private void updateFormatModel(VSObjectFormatInfoModel model, VSCompositeFormat format) {
      VSCSSFormat cssFormat = format.getCSSFormat();

      if(cssFormat.isAlignmentValueDefined()) {
         model.setAlign(new AlignmentInfo(format.getAlignmentValue()));
      }

      if(cssFormat.isBorderColorsValueDefined()) {
         BorderColors borderColors = format.getBorderColors();

         if(borderColors != null) {
            model.setBorderTopColor("#" + Tool.colorToHTMLString(borderColors.topColor));
            model.setBorderLeftColor("#" + Tool.colorToHTMLString(borderColors.leftColor));
            model.setBorderBottomColor("#" + Tool.colorToHTMLString(borderColors.bottomColor));
            model.setBorderRightColor("#" + Tool.colorToHTMLString(borderColors.rightColor));
         }
      }

      if(cssFormat.isBordersValueDefined()) {
         Insets border = format.getBorders();

         if(border != null) {
            model.setBorderTopStyle(String.valueOf(border.top));
            model.setBorderLeftStyle(String.valueOf(border.left));
            model.setBorderBottomStyle(String.valueOf(border.bottom));
            model.setBorderRightStyle(String.valueOf(border.right));
         }
      }

      if(cssFormat.isFontValueDefined()) {
         model.setFont(new FontInfo(format.getFontValue()));
      }

      if(cssFormat.isForegroundValueDefined()) {
         model.setColor(format.getForegroundValue());
      }

      if(cssFormat.isWrappingValueDefined()) {
         model.setWrapText(format.getWrappingValue());
      }

      if(cssFormat.isAlphaValueDefined()) {
         model.setBackgroundAlpha(format.getAlphaValue());
      }

      if(cssFormat.isBackgroundValueDefined()) {
         model.setBackgroundColor(format.getBackgroundValue());
      }
   }

   private void setChartRegionsFormat(FormatInfo info, VSObjectFormatInfoModel model,
                                      VSObjectFormatInfoModel origFormat,
                                      ChartVSAssemblyInfo chartInfo, boolean reset)
   {
      TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
      VSCompositeFormat format = info.getFormat(path, false);
      VSCSSFormat cssFormat = format.getCSSFormat();

      VSCompositeFormat sheetFormat = info.getFormat(VSAssemblyInfo.SHEETPATH, false);
      VSCSSFormat sheetCssFormat = sheetFormat.getCSSFormat();
      //Copy font and color settings to descriptors
      List<CSSParameter> parentParams = new ArrayList<>();
      parentParams.add(sheetCssFormat.getCSSParam());
      parentParams.add(cssFormat.getCSSParam());
      ChartDescriptor desc = chartInfo.getChartDescriptor();

      if(reset) {
         model = new VSObjectFormatInfoModel();
      }

      if(desc != null) {
         LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat();
            copyUserFormat(legendsDesc.getTitleTextFormat(), format, parentParams,
                           model, origFormat, reset);
            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat();
               copyUserFormat(colorDesc.getContentTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat();
               copyUserFormat(shapeDesc.getContentTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat();
               copyUserFormat(sizeDesc.getContentTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }
         }

         copyFormatToLegend(chartInfo.getVSChartInfo().getColorField(), format, parentParams,
                            model, origFormat, reset);
         copyFormatToLegend(chartInfo.getVSChartInfo().getShapeField(), format, parentParams,
                            model, origFormat, reset);
         copyFormatToLegend(chartInfo.getVSChartInfo().getSizeField(), format, parentParams,
                            model, origFormat, reset);
         copyFormatToLegend(chartInfo.getVSChartInfo().getTextField(), format, parentParams,
                            model, origFormat, reset);

         VSObjectFormatInfoModel finalModel = model;
         Arrays.stream(chartInfo.getVSChartInfo().getAestheticRefs(false)).forEach(a -> {
            copyFormatToLegend(a, format, parentParams, finalModel, origFormat, reset);
         });

         VSChartInfo vinfo = chartInfo.getVSChartInfo();

         if(vinfo instanceof RelationChartInfo) {
            RelationChartInfo relationInfo = (RelationChartInfo) vinfo;
            copyFormatToLegend(relationInfo.getNodeColorField(), format, parentParams,
               model, origFormat, reset);
            copyFormatToLegend(relationInfo.getNodeSizeField(), format, parentParams,
               model, origFormat, reset);
         }

         TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat();
               copyUserFormat(xDesc.getTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat();
               copyUserFormat(xDesc2.getTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat();
               copyUserFormat(yDesc.getTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat();
               copyUserFormat(yDesc2.getTextFormat(), format, parentParams,
                              model, origFormat, reset);
            }
         }

         PlotDescriptor plotDesc = desc.getPlotDescriptor();

         if(plotDesc != null) {
            copyUserFormat(plotDesc.getTextFormat(), format, parentParams,
                           model, origFormat, reset);
         }

         for(int i = 0; i < desc.getTargetCount(); i++) {
            GraphTarget graphTarget = desc.getTarget(i);
            graphTarget.initDefaultFormat();
            copyUserFormat(graphTarget.getTextFormat(), format, parentParams,
                           model, origFormat, reset);
         }
      }

      VSChartInfo cinfo = chartInfo.getVSChartInfo();

      copyFormatToRefs(model, origFormat, reset, format, parentParams, cinfo.getFields());
      copyFormatToRefs(model, origFormat, reset, format, parentParams,
                       cinfo.getRuntimeDateComparisonRefs());

      setAxisLabelTextFormats(cinfo.getAxisDescriptor(), format, parentParams,
                              model, origFormat, reset);
      setAxisLabelTextFormats(cinfo.getAxisDescriptor2(), format, parentParams,
                              model, origFormat, reset);

      if(GraphTypes.isRadarN(cinfo)) {
         AxisDescriptor axisDescriptor = ((RadarChartInfo) cinfo).getLabelAxisDescriptor();
         setAxisLabelTextFormats(axisDescriptor, format, parentParams,
                                 model, origFormat, reset);
      }
   }

   private void copyFormatToLegend(AestheticRef aref, VSCompositeFormat format,
                                   List<CSSParameter> parentParams, VSObjectFormatInfoModel model,
                                   VSObjectFormatInfoModel origFormat, boolean reset)
   {
      if(aref == null) {
         return;
      }

      LegendDescriptor legendDesc = aref.getLegendDescriptor();

      if(legendDesc != null) {
         legendDesc.initDefaultFormat();
         copyUserFormat(legendDesc.getContentTextFormat(), format, parentParams,
                        model, origFormat, reset);
      }
   }

   private void copyFormatToRefs(VSObjectFormatInfoModel model, VSObjectFormatInfoModel origFormat,
                                 boolean reset, VSCompositeFormat format,
                                 List<CSSParameter> parentParams, DataRef[] refs)
   {
      for(DataRef ref : refs) {
         if(ref instanceof ChartRef) {
            copyUserFormat(((ChartRef) ref).getTextFormat(), format, parentParams,
                           model, origFormat, reset);

            setAxisLabelTextFormats(((ChartRef) ref).getAxisDescriptor(), format, parentParams,
                                    model, origFormat, reset);
         }
      }
   }

   private void setUserFormat(VSCompositeFormat format, VSObjectFormatInfoModel model,
                              VSObjectFormatInfoModel origFormat, boolean reset, boolean copyFormat)
   {
      VSFormat userFormat = format.getUserDefinedFormat();
      FontInfo font = model.getFont();

      if(origFormat != null && !Tool.equals(font, origFormat.getFont()) || reset) {
         userFormat.setFontValue((font == null) ? null : font.toFont(), !reset);
      }

      setUserBorderFormat(model, origFormat, userFormat, reset);
      String colorString = model.getColorType();
      String ocolorString = origFormat.getColorType();
      String colorValue = model.getColor();
      String ocolorValue = origFormat.getColor();

      if(!Tool.equals(colorString, ocolorString) || !Tool.equals(colorValue, ocolorValue) ||
         reset)
      {
         if(colorString == null || "Static".equals(colorString)) {
            userFormat.setForegroundValue(colorValue, !reset);
         }
         else {
            userFormat.setForegroundValue(colorString, !reset);
         }
      }

      colorString = model.getBackgroundColorType();
      ocolorString = origFormat.getBackgroundColorType();
      colorValue = model.getBackgroundColor();
      ocolorValue = origFormat.getBackgroundColor();

      if(!Tool.equals(colorString, ocolorString) || !Tool.equals(colorValue, ocolorValue) ||
         reset)
      {
         if(colorString == null || "Static".equals(colorString)) {
            userFormat.setBackgroundValue(colorValue, !reset);
         }
         else {
            userFormat.setBackgroundValue(colorString, !reset);
         }

         userFormat.setGradientColorValue(null);
      }

      int alpha = model.getBackgroundAlpha();
      int oalpha = origFormat.getBackgroundAlpha();

      if(alpha != oalpha || reset) {
         userFormat.setAlphaValue(alpha, !reset);
      }

      int roundCorner = model.getRoundCorner();
      int oroundCorner = origFormat.getRoundCorner();

      if(roundCorner != oroundCorner || reset) {
         userFormat.setRoundCornerValue(roundCorner, !reset);
      }

      int align = model.getAlign() == null ? 0 : model.getAlign().toAlign();
      int oalign = origFormat.getAlign() == null ? 0 : origFormat.getAlign().toAlign();

      if(align != oalign || reset) {
         userFormat.setAlignmentValue(align, !reset);
      }

      String presenter = model.getPresenter();
      String opresenter = origFormat.getPresenter();

      if(presenter == null &&
         !Catalog.getCatalog().getString("(none)").equals(model.getPresenterLabel()))
      {
         presenter = model.getPresenterLabel();
      }

      if(!Tool.equals(presenter, opresenter) || reset) {
         if(presenter == null) {
            userFormat.setPresenterValue(null, !reset);
         }
         else {
            userFormat.setPresenterValue(new PresenterRef(presenter), !reset);
         }
      }

      boolean wrap = model.isWrapText();
      boolean owrap = origFormat.isWrapText();

      if(wrap != owrap || reset || copyFormat) {
         userFormat.setWrappingValue(wrap, !reset);
      }

      String modelFormat = FormatInfoModel.getDurationFormat(model.getFormat(),
         model.isDurationPadZeros());
      String formatSpec = model.getFormatSpec();
      String dateSpec = model.getDateSpec();
      String omodelFormat = FormatInfoModel.getDurationFormat(origFormat.getFormat(),
         origFormat.isDurationPadZeros());
      String oformatSpec = origFormat.getFormatSpec();
      String odateSpec = origFormat.getDateSpec();

      if(!Tool.equals(modelFormat, omodelFormat) || !Tool.equals(formatSpec, oformatSpec) ||
         !Tool.equals(dateSpec, odateSpec) || reset)
      {
         if(modelFormat != null && modelFormat.equals(XConstants.COMMA_FORMAT)) {
            modelFormat = XConstants.DECIMAL_FORMAT;
            String decimalSpec = "#,##0";
            userFormat.setFormatExtentValue(decimalSpec, !reset);
         }
         else {
            if(XConstants.DATE_FORMAT.equals(modelFormat) && !"Custom".equals(dateSpec)) {
               formatSpec = dateSpec;
            }

            userFormat.setFormatExtentValue(formatSpec != null && formatSpec.length() > 0 ?
                                               formatSpec : null, !reset);
         }

         userFormat.setFormatValue(modelFormat, !reset);
      }

      format.setUserDefinedFormat(userFormat);
   }

   /**
    * Set the user format's border style if any border properties have been changed.
    *
    * @param model      the current format
    * @param origFormat the original format
    * @param userFormat the user format
    * @param reset      if true, force a format reset to the new format model
    */
   private void setUserBorderFormat(VSObjectFormatInfoModel model,
                                    VSObjectFormatInfoModel origFormat, VSFormat userFormat,
                                    boolean reset)
   {
      final String topBorder = model.getBorderTopStyle();
      final String leftBorder = model.getBorderLeftStyle();
      final String bottomBorder = model.getBorderBottomStyle();
      final String rightBorder = model.getBorderRightStyle();
      final String otopBorder = origFormat.getBorderTopStyle();
      final String oleftBorder = origFormat.getBorderLeftStyle();
      final String obottomBorder = origFormat.getBorderBottomStyle();
      final String orightBorder = origFormat.getBorderRightStyle();

      final Color topColor = model.getBorderTopColor() != null
         ? Tool.getColorFromHexString(model.getBorderTopColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color leftColor = model.getBorderLeftColor() != null
         ? Tool.getColorFromHexString(model.getBorderLeftColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color bottomColor = model.getBorderBottomColor() != null
         ? Tool.getColorFromHexString(model.getBorderBottomColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color rightColor = model.getBorderRightColor() != null
         ? Tool.getColorFromHexString(model.getBorderRightColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color otopColor = origFormat.getBorderTopColor() != null
         ? Tool.getColorFromHexString(origFormat.getBorderTopColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color oleftColor = origFormat.getBorderLeftColor() != null
         ? Tool.getColorFromHexString(origFormat.getBorderLeftColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color obottomColor = origFormat.getBorderBottomColor() != null
         ? Tool.getColorFromHexString(origFormat.getBorderBottomColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;
      final Color orightColor = origFormat.getBorderRightColor() != null
         ? Tool.getColorFromHexString(origFormat.getBorderRightColor())
         : VSAssemblyInfo.DEFAULT_BORDER_COLOR;

      final BorderColors bcolors = new BorderColors(topColor, bottomColor, leftColor, rightColor);
      final BorderColors obcolors = new BorderColors(otopColor, obottomColor, oleftColor, orightColor);

      if(reset || !Tool.equals(topBorder, otopBorder) ||
         !Tool.equals(leftBorder, oleftBorder) ||
         !Tool.equals(bottomBorder, obottomBorder) ||
         !Tool.equals(rightBorder, orightBorder) ||
         !bcolors.equals(obcolors))
      {
         if(topBorder == null && leftBorder == null && bottomBorder == null &&
            rightBorder == null)
         {
            //if default, set borders to null
            userFormat.setBorderDefined(false);
            userFormat.setBordersValue(null, false);
         }
         else {
            final Insets borders = new Insets(
               topBorder == null ? 0 : Integer.parseInt(topBorder),
               leftBorder == null ? 0 : Integer.parseInt(leftBorder),
               bottomBorder == null ? 0 : Integer.parseInt(bottomBorder),
               rightBorder == null ? 0 : Integer.parseInt(rightBorder));
            userFormat.setBordersValue(borders, !reset);
         }

         userFormat.setBorderColorsValue(bcolors, !reset);
      }
   }

   private void setAxisLabelTextFormats(AxisDescriptor axis, VSCompositeFormat cfmt,
                                        List<CSSParameter> parentParams,
                                        VSObjectFormatInfoModel model,
                                        VSObjectFormatInfoModel origFormat,
                                        boolean reset)
   {
      copyUserFormat(axis.getAxisLabelTextFormat(), cfmt, parentParams,
                     model, origFormat, reset);

      for(String col : axis.getColumnLabelTextFormatColumns()) {
         copyUserFormat(axis.getColumnLabelTextFormat(col), cfmt, parentParams,
                        model, origFormat, reset);
      }
   }

   /**
    * Copies font and color settings from Chart's specified TextFormat.
    */
   private void copyUserFormat(CompositeTextFormat tofmt, VSCompositeFormat cfmt,
                               List<CSSParameter> parentParams, VSObjectFormatInfoModel model,
                               VSObjectFormatInfoModel origFormat, boolean reset)
   {
      TextFormat textFormat = tofmt.getUserDefinedFormat();

      if(textFormat == null) {
         tofmt.setUserDefinedFormat(textFormat = new TextFormat());
      }

      if(tofmt != null) {
         String colorString = model.getColorType();
         String ocolorString = origFormat.getColorType();
         String colorValue = model.getColor();
         String ocolorValue = origFormat.getColor();
         FontInfo font = model.getFont();
         FontInfo ofont = origFormat.getFont();
         AlignmentInfo align = model.getAlign();
         AlignmentInfo oalign = origFormat.getAlign();
         Boolean fontChanged = origFormat != null ? !Tool.equals(font, ofont) : true;
         VSFormat userFormat = cfmt.getUserDefinedFormat();

         if(fontChanged) {
            // don't copy object font to region if region is null and it's for reset
            if(!reset || textFormat.getFont() != null) {
               textFormat.setFont(cfmt.getFont(), !reset && userFormat.isFontValueDefined());
            }
         }

         if((!Tool.equals(colorString, ocolorString) ||
            !Tool.equals(colorValue, ocolorValue)) && cfmt.getForeground() != null)
         {
            if(!reset || textFormat.getColor() != null) {
               textFormat.setColor(cfmt.getForeground(),
                  !reset && userFormat.isForegroundValueDefined());
            }
         }

         if(!Tool.equals(align, oalign)) {
            textFormat.setAlignment(align != null ? align.toAlign() : 0,
               !reset && userFormat.isAlignmentValueDefined());
         }

         tofmt.getCSSFormat().setParentCSSParams(parentParams);
      }
   }

   /**
    * Get the format of the currently selected region of a chart.
    */
   private RegionInfo getChartRegionFormat(Principal principal, String name, String region,
                                           int index, String columnName, boolean ignoreTextField)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(name);
      assert assembly instanceof ChartVSAssembly;
      ChartVSAssembly chart = (ChartVSAssembly) assembly;
      VSChartInfo chartInfo = chart.getVSChartInfo();
      ChartDescriptor chartDescriptor = chart.getChartDescriptor();

      // Get Chart Area
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getInfo();
      Dimension maxSize = info.getMaxSize();
      VGraphPair pair = box.getVGraphPair(name, false, maxSize);

      XCube cube = chart.getXCube();
      boolean drill = !rvs.isTipView(name) &&
         ((ChartVSAssemblyInfo) chart.getInfo()).isDrillEnabled();

      if(cube == null) {
         SourceInfo src = chart.getSourceInfo();

         if(src != null) {
            cube = AssetUtil.getCube(src.getPrefix(), src.getSource());
         }
      }

      if(pair != null) {
         pair.waitInit();

         if(ChartFormatConstants.PLOT_AREA.equals(region)) {
            VGraph vgraph = pair.getExpandedVGraph();
            region = getPolarAxisRegion(region, columnName, chartInfo, vgraph);
         }
      }

      ChartArea area = pair == null || !pair.isCompleted() || pair.getRealSizeVGraph() == null
         ? null : new ChartArea(pair, null, chartInfo, cube, drill);
      CompositeTextFormat format = null;
      TitlesDescriptor titlesDescriptor = chartDescriptor.getTitlesDescriptor();

      if("_Parallel_Label_".equals(columnName) && GraphTypes.isRadarN(chartInfo)) {
         AxisDescriptor axisDescriptor =
            ((RadarChartInfo) chartInfo).getLabelAxisDescriptor();
         format = axisDescriptor.getColumnLabelTextFormat(columnName);

         if(format == null) {
            format = new CompositeTextFormat();
            axisDescriptor.setColumnLabelTextFormat(columnName, format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_LABELS);
      }
      else if(region.equals(ChartFormatConstants.TARGET_LABEL) && area != null) {
         PlotArea plotArea = area.getPlotArea();
         DefaultArea[] areas = plotArea.getAllAreas();

         if(index < areas.length && areas[index] instanceof LabelArea) {
            LabelArea childArea = (LabelArea) areas[index];
            int tindex = childArea.getTargetIndex();

            GraphTarget target = chartDescriptor.getTarget(tindex);
            format = target.getTextFormat();

            if(format == null) {
               format = new CompositeTextFormat();
               target.setTextFormat(format);
            }

            format.getCSSFormat().setCSSType(CSSConstants.CHART_TARGET_LABELS);
         }
      }
      else if(region.equals(ChartFormatConstants.PLOT_AREA) ||
         region.equals(ChartFormatConstants.TEXT) ||
         region.equals(ChartFormatConstants.TEXT_FIELD))
      {
         ChartRef ref = chartInfo.getFieldByName(columnName, false);

         if(ref == null && info != null) {
            ref = (ChartRef) info.getDCBIndingRef(columnName);
         }

         if(!(GraphTypes.isRadarN(chartInfo) && GraphUtil.isMeasure(ref) ||
            GraphTypes.isRadarOne(chartInfo)) ||
            region.equals(ChartFormatConstants.TEXT) ||
            region.equals(ChartFormatConstants.TEXT_FIELD))
         {
            PlotDescriptor descriptor = chartDescriptor.getPlotDescriptor();
            ChartBindable bindable = regionHandler.getChartBindable(chartInfo, columnName);
            bindable = chartInfo.isMultiAesthetic() ? bindable : chartInfo;
            boolean textField = bindable.getRTTextField() != null && !ignoreTextField;
            format = GraphFormatUtil.getBindingTextFormat(bindable, ref, descriptor, textField);

            if(format == null) {
               format = new CompositeTextFormat();
               GraphFormatUtil.setBindingTextFormat(bindable, ref, descriptor, format, textField);
            }

            format.getCSSFormat().setCSSType(CSSConstants.CHART_PLOTLABELS);
         }
         else if(ref != null) {
            AxisDescriptor descriptor = ref.getAxisDescriptor();
            format = descriptor.getColumnLabelTextFormat(columnName);

            if(format == null) {
               format = new CompositeTextFormat();
               descriptor.setColumnLabelTextFormat(columnName, format);
            }

            if(GraphTypes.isRadarN(chartInfo)) {
               String axisIndex = ChartRegionHandler.getXfieldsIndex(chartInfo, columnName, true);
               format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_LABELS);
               format.getCSSFormat().addCSSAttribute("axis", axisIndex);
            }
            else if(GraphTypes.isRadarOne(chartInfo)) {
               format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_LABELS);
            }
         }
      }
      else if("x_title".equals(region)) {
         TitleDescriptor descriptor = titlesDescriptor.getXTitleDescriptor();
         format = descriptor.getTextFormat();

         if(format == null) {
            format = new CompositeTextFormat();
            descriptor.setTextFormat(format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_TITLE);
         format.getCSSFormat().addCSSAttribute("axis", "x");
      }
      else if("x2_title".equals(region)) {
         TitleDescriptor descriptor = titlesDescriptor.getX2TitleDescriptor();
         format = descriptor.getTextFormat();

         if(format == null) {
            format = new CompositeTextFormat();
            descriptor.setTextFormat(format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_TITLE);
         format.getCSSFormat().addCSSAttribute("axis", "x2");
      }
      else if("y_title".equals(region)) {
         TitleDescriptor descriptor = titlesDescriptor.getYTitleDescriptor();
         format = descriptor.getTextFormat();

         if(format == null) {
            format = new CompositeTextFormat();
            descriptor.setTextFormat(format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_TITLE);
         format.getCSSFormat().addCSSAttribute("axis", "y");
      }
      else if("y2_title".equals(region)) {
         TitleDescriptor descriptor = titlesDescriptor.getY2TitleDescriptor();
         format = descriptor.getTextFormat();

         if(format == null) {
            format = new CompositeTextFormat();
            descriptor.setTextFormat(format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_AXIS_TITLE);
         format.getCSSFormat().addCSSAttribute("axis", "y2");
      }
      else if(region.equals(ChartFormatConstants.BOTTOM_X_AXIS)) {
         if(columnName == null || columnName.isEmpty()) {
            columnName = regionHandler.getAxisColumnName(chartInfo, area, region, index);
         }

         format = getAxisLabelTextFormat(name, region, index, columnName, chartInfo, box,
                                         area, true);
         format.getCSSFormat().addCSSAttribute("axis", "x");
      }
      else if(region.equals(ChartFormatConstants.TOP_X_AXIS)) {
         if(columnName == null || columnName.isEmpty()) {
            columnName = regionHandler.getAxisColumnName(chartInfo, area, region, index);
         }

         format = getAxisLabelTextFormat(name, region, index, columnName, chartInfo, box,
                                         area, true);
         format.getCSSFormat().addCSSAttribute("axis", "x2");
      }
      else if(region.equals(ChartFormatConstants.LEFT_Y_AXIS)) {
         if(columnName == null || columnName.isEmpty()) {
            columnName = regionHandler.getAxisColumnName(chartInfo, area, region, index);
         }

         format = getAxisLabelTextFormat(name, region, index, columnName, chartInfo, box,
                                         area, false);
         String axisIndex = ChartRegionHandler.getXfieldsIndex(chartInfo, columnName, true);
         format.getCSSFormat().addCSSAttribute("axis", axisIndex);
      }
      else if(region.equals(ChartFormatConstants.RIGHT_Y_AXIS)) {
         format = getAxisLabelTextFormat(name, region, index, columnName, chartInfo, box,
                                         area, false);
         format.getCSSFormat().addCSSAttribute("axis", "y2");
      }
      else if(region.equals(ChartFormatConstants.LEGEND_CONTENT)) {
         LegendDescriptor descriptor = getLegendDescriptor(chartDescriptor, chartInfo, area, index);

         if(descriptor != null) {
            format = descriptor.getContentTextFormat();
         }

         if(format == null) {
            format = new CompositeTextFormat();

            if(descriptor != null) {
               descriptor.setContentTextFormat(format);
            }
         }

         fixAxisDefaultFormat(box, chartInfo, format, name, columnName);
         format.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_CONTENT);
      }
      else if(region.equals(ChartFormatConstants.LEGEND_TITLE)) {
         LegendsDescriptor descriptor = chartDescriptor.getLegendsDescriptor();
         format = descriptor.getTitleTextFormat();

         if(format == null) {
            format = new CompositeTextFormat();
            descriptor.setTitleTextFormat(format);
         }

         format.getCSSFormat().setCSSType(CSSConstants.CHART_LEGEND_TITLE);
      }

      return new RegionInfo(format, area);
   }

   private CompositeTextFormat getAxisLabelTextFormat(String name, String region, int index,
                                                      String columnName, VSChartInfo chartInfo,
                                                      ViewsheetSandbox box, ChartArea area,
                                                      boolean isX)
      throws Exception
   {
      AxisDescriptor descriptor = regionHandler
         .getAxisDescriptor(chartInfo, area, region, index, columnName);
      CompositeTextFormat format = GraphFormatUtil.getAxisLabelTextFormat(
         columnName, chartInfo, descriptor, isX);
      fixAxisDefaultFormat(box, chartInfo, format, name, columnName);
      return format;
   }

   // get the region name if this is really an axis in (script created) poloar coord. (58865)
   private String getPolarAxisRegion(String region, String columnName, VSChartInfo chartInfo,
                                     VGraph vgraph)
   {
      if(vgraph != null && vgraph.getCoordinate() instanceof PolarCoord &&
         ((PolarCoord) vgraph.getCoordinate()).getCoordinate() instanceof RectCoord &&
         !GraphTypes.isPolar(chartInfo.getRTChartType()))
      {
         String finalColumnName = columnName;
         boolean isX = Arrays.stream(chartInfo.getRTXFields())
            .anyMatch(f -> Objects.equals(f.getFullName(), finalColumnName));
         boolean isY = Arrays.stream(chartInfo.getRTYFields())
            .anyMatch(f -> Objects.equals(f.getFullName(), finalColumnName));

         if(isX) {
            region = ChartFormatConstants.BOTTOM_X_AXIS;
         }
         else if(isY) {
            region = ChartFormatConstants.LEFT_Y_AXIS;
         }
      }

      return region;
   }

   private void fixAxisDefaultFormat(ViewsheetSandbox box, VSChartInfo chartInfo,
                                     CompositeTextFormat format, String cname, String columnName)
      throws Exception
   {
      if(format == null) {
         return;
      }

      Format defFormat = getAxisDefaultFormat(box, chartInfo, cname, columnName);
      boolean userDefined = !format.getUserDefinedFormat().getFormat().isEmpty();

      if(!userDefined && defFormat != null) {
         TextFormat defaultFormat = format.getDefaultFormat();
         defaultFormat.setFormat(new XFormatInfo(defFormat));
      }
   }

   private Format getAxisDefaultFormat(ViewsheetSandbox box, VSChartInfo chartInfo,
                                       String cname, String columnName)
      throws Exception
   {
      Object data = box.getData(cname);

      return columnName == null ? null : GraphFormatUtil.getDefaultFormat((VSDataSet) data,
         chartInfo, chartInfo.getChartDescriptor(), columnName);
   }

   /**
    * Get the format of the chart region
    */
   private void getChartFormat(GetVSObjectFormatEvent event, Principal principal,
                               ChartVSAssembly chart, CommandDispatcher commandDispatcher)
      throws Exception
   {
      String colname = BoxDataSet.getBaseName(event.getColumnName());
      ChartRef ref = chart.getVSChartInfo().getFieldByName(colname, false);

      if(event.isValueText()) {
         GraphFormatUtil.syncAggregateTextFormat(chart.getVSChartInfo(), ref, true);
      }

      RegionInfo regionInfo = getChartRegionFormat(
         principal, event.getName(), event.getRegion(), event.getIndex(),
         BoxDataSet.getBaseName(event.getColumnName()), event.isValueText());

      if(regionInfo == null) {
         return;
      }

      CompositeTextFormat format = regionInfo.format;
      ChartArea chartArea = regionInfo.area;

      if(format == null) {
         return;
      }

      DefaultArea area = regionHandler.getChartRegionArea(chartArea, event.getRegion(),
                                                          event.getIndex());
      VSObjectFormatInfoModel formatModel = new VSObjectFormatInfoModel();
      Font font = format.getFont();

      if(font != null) {
         formatModel.setFont(new FontInfo(font));
      }

      formatModel.setColor(Tool.toString(format.getColor()));
      formatModel.setBackgroundColor(Tool.toString(format.getBackground()));
      formatModel.setColorType("Static");
      formatModel.setBackgroundColorType("Static");
      formatModel.setBackgroundAlpha(format.getAlpha());
      formatModel.setAlign(new AlignmentInfo(format.getAlignment()));

      // this is called from adhoc too. seems the if-else below should be moved into
      // this method too
      regionHandler.setFormatsEnabled(chartArea, event.getRegion(), event.getIndex(),
                                      formatModel, chart.getVSChartInfo());

      if(area instanceof DimensionLabelArea) {
         formatModel.setHAlignmentEnabled(((DimensionLabelArea) area).isHAlignmentEnabled());
         formatModel.setVAlignmentEnabled(((DimensionLabelArea) area).isVAlignmentEnabled());
      }
      else if("x_title".equals(event.getRegion()) ||
         "x2_title".equals(event.getRegion()) ||
         "text".equals(event.getRegion()) ||
         "legend_content".equals(event.getRegion()) && event.isDimensionColumn() ||
         "legend_title".equals(event.getRegion()))
      {
         formatModel.setHAlignmentEnabled(true);
         formatModel.setVAlignmentEnabled(false);
      }
      else if("y_title".equals(event.getRegion()) || "y2_title".equals(event.getRegion())) {
         formatModel.setHAlignmentEnabled(false);
         formatModel.setVAlignmentEnabled(true);
         formatModel.getAlign().convertToValign();
      }
      else if("plot_area".equals(event.getRegion())) {
         boolean radar = GraphTypes.isRadar(chart.getVSChartInfo().getChartType());
         formatModel.setHAlignmentEnabled(!radar);
         formatModel.setVAlignmentEnabled(false);
      }
      else if("top_x_axis".equals(event.getRegion()) && event.isDimensionColumn()) {
         formatModel.setHAlignmentEnabled(true);
         formatModel.setVAlignmentEnabled(false);
      }
      else if("left_y_axis".equals(event.getRegion()) && event.isDimensionColumn()) {
         formatModel.setHAlignmentEnabled(true);
         formatModel.setVAlignmentEnabled(false);
      }
      else {
         formatModel.setHAlignmentEnabled(false);
         formatModel.setVAlignmentEnabled(false);
      }

      if("text".equals(event.getRegion()) || "textField".equals(event.getRegion())) {
         formatModel.setBorderDisabled(true);
         formatModel.setDynamicColorDisabled(true);
         formatModel.setAlignEnabled(true);
         formatModel.setHAlignmentEnabled(true);
      }

      String modelFormat = format.getFormat().getFormat();
      String formatSpec = format.getFormat().getFormatSpec();
      String decimalSpec = "#,##0";

      if(XConstants.DECIMAL_FORMAT.equals(modelFormat) &&
         decimalSpec.equals(format.getFormat().getFormatSpec()))
      {
         modelFormat = XConstants.COMMA_FORMAT;
      }

      formatModel.setFormat(modelFormat);
      formatModel.setFormatSpec(formatSpec);
      formatModel.fixDateSpec(modelFormat, formatSpec);
      formatModel.setChart(true);

      CSSTextFormat cssFormat = format.getCSSFormat();
      String cssType = cssFormat.getCSSType();

      if(cssFormat.getCSSAttributes() != null) {
         String axis = cssFormat.getCSSAttributes().get("axis");

         if(axis != null) {
            cssType += "[axis=" + axis + "]";
         }
      }

      formatModel.setCssID(cssFormat.getCSSID());
      formatModel.setCssClass(cssFormat.getCSSClass());
      formatModel.setCssIDs(cssFormat.getCSSIDs());
      formatModel.setCssClasses(cssFormat.getCSSClasses());
      formatModel.setCssType(cssType);
      formatModel.setDecimalFmts(ExtendedDecimalFormat.getSuffix().toArray(new String[0]));

      SetCurrentFormatCommand command = new SetCurrentFormatCommand(formatModel);
      commandDispatcher.sendCommand(command);
   }

   /**
    * Set the format of the chart regions.
    */
   private void setChartFormat(String region, int[] indexes, String[] columnNames,
                               ChartVSAssembly chart, VSObjectFormatInfoModel model,
                               VSObjectFormatInfoModel origFormat, boolean valueText,
                               boolean reset, Principal principal)
      throws Exception
   {
      if(indexes == null || indexes.length == 0) {
         CompositeTextFormat format = getChartRegionFormat(
            principal, chart.getAbsoluteName(), region, 0, null, valueText).format;
         changeChartRegionFormat(model, origFormat, format, region, reset, null);
      }
      else {
         for(int i = 0; i < indexes.length; i++) {
            int index = indexes[i];
            String columnName = BoxDataSet.getBaseName(columnNames == null ? null : columnNames[i]);
            CompositeTextFormat format = null;

            if(GraphTypes.isRadarN(chart.getVSChartInfo()) &&
               region.equals(ChartFormatConstants.PLOT_AREA) &&
               !"_Parallel_Label_".equals(columnName))
            {
               AxisDescriptor descriptor = chart.getVSChartInfo().
                  getFieldByName(columnName, false).getAxisDescriptor();
               format = descriptor.getColumnLabelTextFormat(columnName);

               if(format == null) {
                  format = new CompositeTextFormat();
                  descriptor.setColumnLabelTextFormat(columnName, format);
               }
            }
            else {
               format = getChartRegionFormat(principal, chart.getAbsoluteName(), region, index,
                                             columnName, valueText).format;
            }

            if(format == null) {
               if(reset) {
                  model = new VSObjectFormatInfoModel();
               }

               ChartVSAssemblyInfo chartInfo = chart.getChartInfo();
               FormatInfo chartFormatInfo = chartInfo.getFormatInfo();
               TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
               changeFormat(chartFormatInfo, origFormat, model, dataPath, false);
            }
            else {
               if(reset) {
                  model = createTextFormatModel(format.getDefaultFormat());
               }

               changeChartRegionFormat(principal, chart.getAbsoluteName(), region,
                                       columnName, model, origFormat, format, valueText, reset, chart.getVSChartInfo());
            }

            if(valueText) {
               ChartRef ref = chart.getVSChartInfo().getFieldByName(columnName, false);
               GraphFormatUtil.syncAggregateTextFormat(chart.getVSChartInfo(), ref, false);
            }
         }
      }
   }

   private VSObjectFormatInfoModel createTextFormatModel(TextFormat format) {
      VSObjectFormatInfoModel formatModel = new VSObjectFormatInfoModel();
      Font font = format.getFont();

      if(font != null) {
         formatModel.setFont(new FontInfo(font));
      }

      formatModel.setColor(Tool.toString(format.getColor()));
      formatModel.setBackgroundColor(Tool.toString(format.getBackground()));
      formatModel.setColorType("Static");
      formatModel.setBackgroundColorType("Static");
      formatModel.setBackgroundAlpha(format.getAlpha());
      formatModel.setAlign(new AlignmentInfo(format.getAlignment()));
      String modelFormat = format.getFormat().getFormat();
      String formatSpec = format.getFormat().getFormatSpec();
      String decimalSpec = "#,##0";

      if(XConstants.DECIMAL_FORMAT.equals(modelFormat) &&
         decimalSpec.equals(format.getFormat().getFormatSpec()))
      {
         modelFormat = XConstants.COMMA_FORMAT;
      }

      formatModel.setFormat(modelFormat);
      formatModel.setFormatSpec(formatSpec);
      formatModel.fixDateSpec(modelFormat, formatSpec);
      formatModel.setChart(true);

      return formatModel;
   }

   private void changeChartRegionFormat(Principal principal, String name, String region,
                                        String columnName, VSObjectFormatInfoModel model,
                                        VSObjectFormatInfoModel origFormat,
                                        CompositeTextFormat format, boolean valueText,
                                        boolean reset, ChartInfo info)
      throws Exception
   {
      Color ocolor = format.getColor();
      this.changeChartRegionFormat(model, origFormat, format, region, reset, info);

      // @by robert.wang, this only used for the all aggregate text format in
      // multi style chart.
      if("text".equals(region) || "textField".equals(region)) {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(
            this.runtimeViewsheetRef.getRuntimeId(), principal);
         Viewsheet viewsheet = rvs.getViewsheet();
         VSAssembly assembly = viewsheet.getAssembly(name);
         assert assembly instanceof ChartVSAssembly;
         ChartVSAssembly chart = (ChartVSAssembly) assembly;
         VSChartInfo chartInfo = chart.getVSChartInfo();

         if(!Tool.equals(ocolor, format.getColor())) {
            GraphUtil.textColorChanged(chartInfo, columnName, format.getColor());
         }

         if(!chartInfo.isMultiAesthetic()) {
            return;
         }

         ChartDescriptor chartDescriptor = chart.getChartDescriptor();
         PlotDescriptor descriptor = chartDescriptor.getPlotDescriptor();
         ChartBindable bindable = regionHandler.getChartBindable(chartInfo, columnName);
         ChartAggregateRef aggr = (bindable instanceof ChartAggregateRef)
            ? (ChartAggregateRef) bindable : null;
         boolean textField = bindable.getRTTextField() != null && !valueText;
         GraphFormatUtil.setBindingTextFormat(bindable, aggr, descriptor, format, textField);
      }
   }

   private void changeChartRegionFormat(VSObjectFormatInfoModel model,
                                        VSObjectFormatInfoModel origFormat,
                                        CompositeTextFormat format, String region,
                                        boolean reset, ChartInfo info)
   {
      if(model == null) {
         return;
      }

      final TextFormat userFormat = format.getUserDefinedFormat();
      final TextFormat defaultFormat = format.getDefaultFormat();
      final CSSTextFormat cssFormat = format.getCSSFormat();
      FontInfo font = model.getFont();
      FontInfo ofont = origFormat.getFont();

      if(!Tool.equals(font, ofont) || reset) {
         if(font == null) {
            userFormat.setFont(null, false);
         }
         else {
            userFormat.setFont(font.toFont(), !reset);
         }
      }

      Color foreground;
      boolean isWordCloud = false;

      if(reset && GraphTypeUtil.isWordCloud(info)) {
         ColorFrameWrapper color = info.getColorFrameWrapper();
         foreground = ((StaticColorFrameWrapper) color).getDefaultColor();
         isWordCloud = true;
      }
      else {
         foreground = model.getColor() == null || model.getColor().isEmpty() ?
            null : Color.decode(model.getColor());
      }

      Color oforeground = origFormat.getColor() == null || origFormat.getColor().isEmpty() ?
         null : Color.decode(origFormat.getColor());

      if(!Tool.equals(foreground, oforeground) || reset) {
         userFormat.setColor(foreground, foreground != null && (!reset || isWordCloud));
      }

      Color background = model.getBackgroundColor() == null ||
         model.getBackgroundColor().isEmpty() ? null : Color.decode(model.getBackgroundColor());
      Color obackground = origFormat.getBackgroundColor() == null ||
         origFormat.getBackgroundColor().isEmpty() ? null
         : Color.decode(origFormat.getBackgroundColor());

      if(!Tool.equals(background, obackground) || reset) {
         // support transparent bg (null) so it's true regardless of whether color is null
         userFormat.setBackground(background, !reset);
      }

      int alpha = model.getBackgroundAlpha();
      int oalpha = origFormat.getBackgroundAlpha();

      if(alpha != oalpha || reset) {
         if(format instanceof AllCompositeTextFormat) {
            format.setAlpha(alpha);
         }
         else {
            userFormat.setAlpha(alpha, !reset);
         }
      }

      AlignmentInfo align = model.getAlign();
      AlignmentInfo oalign = origFormat.getAlign();

      if(!Objects.equals(align, oalign) || reset) {
         if(align != null) {
            // valign is supported in some cases. since we have client side logic for when
            // to enable valign, shouldn't need to clear it here

            // Bug #18065 still need to clear align for y_title and y2_title since their text
            // are rotated by 90 degrees. If user set their align to TOP, in fact they mean Right.
            // Similar logic exists when getting format in line 1170. Bug #17497 won't regress
            if("y_title".equals(region) || "y2_title".equals(region)) {
               align.convertToHalign();
            }

            userFormat.setAlignment(align.toAlign(), !align.isAuto() && !reset);
         }
         else {
            int defaultAlign = defaultFormat.getAlignment();
            userFormat.setAlignment(defaultAlign, false);
         }
      }

      String modelFormat = FormatInfoModel.getDurationFormat(model.getFormat(),
         model.isDurationPadZeros());
      String formatSpec = model.getFormatSpec();
      String dateSpec = model.getDateSpec();
      boolean textFormatChanged = reset || !Tool.equals(modelFormat, origFormat.getFormat()) ||
         !Tool.equals(formatSpec, origFormat.getFormatSpec()) ||
         !Tool.equals(dateSpec, origFormat.getDateSpec());

      if(textFormatChanged) {
         if(modelFormat != null && modelFormat.equals(XConstants.COMMA_FORMAT)) {
            modelFormat = XConstants.DECIMAL_FORMAT;
            userFormat.getFormat().setFormatSpec("#,##0");
         }
         else {
            if(XConstants.DATE_FORMAT.equals(modelFormat) && !"Custom".equals(dateSpec)) {
               formatSpec = dateSpec;
            }
         }

         userFormat.getFormat().setFormatSpec(formatSpec != null && formatSpec.length() > 0 ?
                                                 formatSpec : null);
         userFormat.getFormat().setFormat(modelFormat);
      }

      if(reset) {
         userFormat.setRotation(null, false);
      }

      cssFormat.setCSSClass(model.getCssClass());
      cssFormat.setCSSID(model.getCssID());
   }

   /**
    * Get specific legend descriptor.
    */
   public LegendDescriptor getLegendDescriptor(ChartDescriptor chartDescriptor,
                                               ChartInfo chartInfo,
                                               ChartArea chartArea,
                                               int legendIdx)
   {
      LegendsArea legendsArea = chartArea == null ? null : chartArea.getLegendsArea();

      if(legendsArea == null) {
         return null;
      }

      LegendArea legendArea = legendsArea.getLegendAreas()[legendIdx];
      List<String> targetFields = legendArea.getTargetFields();
      String aestheticType = legendArea.getAestheticType();
      String field = legendArea.getField();

      LegendsDescriptor legendsDesc = chartDescriptor.getLegendsDescriptor();

      if(chartInfo.isMultiAesthetic()) {
         List<LegendDescriptor> list = new ArrayList<>();
         VSDataRef[] compileTimeRefs = ((VSChartInfo) chartInfo).getBindingRefs(false);
         VSDataRef[] dateComparisonRefs = ((VSChartInfo) chartInfo).getRuntimeDateComparisonRefs();
         dateComparisonRefs = dateComparisonRefs == null ? new VSDataRef[0] : dateComparisonRefs;
         VSDataRef[] runTimeRefs = chartInfo.getAggregateRefs();
         VSDataRef[] refs = Stream.concat(Arrays.stream(dateComparisonRefs),
                                          Stream.concat(Arrays.stream(compileTimeRefs), Arrays.stream(runTimeRefs)))
            .toArray(VSDataRef[]::new);

         for(VSDataRef aggr : refs) {
            if(targetFields.contains(aggr.getFullName())) {
               AestheticRef aref = null;

               if(ChartArea.COLOR_LEGEND.equals(aestheticType)) {
                  aref = ((ChartAggregateRef) aggr).getColorField();
               }
               else if(ChartArea.SHAPE_LEGEND.equals(aestheticType) ||
                  ChartArea.LINE_LEGEND.equals(aestheticType) ||
                  ChartArea.TEXTURE_LEGEND.equals(aestheticType))
               {
                  aref = ((ChartAggregateRef) aggr).getShapeField();
               }
               else if(ChartArea.SIZE_LEGEND.equals(aestheticType)) {
                  aref = ((ChartAggregateRef) aggr).getSizeField();
               }

               if(aref != null && (field == null || field.length() == 0 ||
                  field.equals(aref.getFullName())))
               {
                  list.add(aref.getLegendDescriptor());
               }
            }
         }

         if(list.size() == 1) {
            return list.get(0);
         }
         else if(list.size() > 1) {
            return new AllLegendDescriptor(list);
         }
      }

      return GraphUtil.getLegendDescriptor(chartInfo, legendsDesc, field, targetFields,
                                           aestheticType, legendArea.isNodeAesthetic());
   }

   private static class RegionInfo {
      public RegionInfo(CompositeTextFormat format, ChartArea area) {
         this.format = format;
         this.area = area;
      }

      CompositeTextFormat format;
      ChartArea area;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ChartRegionHandler regionHandler;
   private final ViewsheetService viewsheetService;
   private final VSObjectModelFactoryService objectModelService;
   private final VSBindingService bindingService;
   private final VSLayoutService vsLayoutService;
}
