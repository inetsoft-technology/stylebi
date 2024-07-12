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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableTool;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.Arrays;

/**
 * Controller that provides the REST endpoints for the Calc Table
 * dialog
 *
 * @since 12.3
 */
@Controller
public class CalcTablePropertyDialogController {
   /**
    * Creates a new instance of <tt>CalcTablePropertyController</tt>.
    *  @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    * @param viewsheetService
    */
   @Autowired
   public CalcTablePropertyDialogController(
      VSObjectPropertyService vsObjectPropertyService,
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSDialogService dialogService,
      ViewsheetService viewsheetService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Gets the top-level descriptor of the calc table
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the calc table.
    *
    * @return the rectangle descriptor.
    */

   @RequestMapping(
      value = "/api/composer/vs/calc-table-property-dialog-model/{objectId}/{scrollX}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public CalcTablePropertyDialogModel getCalcTablePropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @PathVariable(value = "scrollX", required = false) double scrollX,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      CalcTableVSAssembly calcTableAssembly = (CalcTableVSAssembly) vs.getAssembly(objectId);
      CalcTableVSAssemblyInfo calcTableAssemblyInfo =
         (CalcTableVSAssemblyInfo) calcTableAssembly.getVSAssemblyInfo();
      VSTableLens lens;

      try {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         String name = calcTableAssemblyInfo.getAbsoluteName();
         boolean detail = name.startsWith(Assembly.DETAIL);

         if(detail) {
            name = name.substring(Assembly.DETAIL.length());
         }

         lens = box.getVSTableLens(name, detail);
      }
      catch(Exception e) {
         if(e instanceof ScriptException) {
            lens = null;
         }
         else {
            //TODO decide what to do with exception
            throw e;
         }
      }

      CalcTablePropertyDialogModel result = new CalcTablePropertyDialogModel();
      TableViewGeneralPaneModel tableViewGeneralPaneModel =
         result.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel =
         tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel =
         tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      CalcTableAdvancedPaneModel advPane =
         result.getCalcTableAdvancedPaneModel();
      TipPaneModel tipPaneModel =
         advPane.getTipPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      tableViewGeneralPaneModel.setShowMaxRows(false);

      titlePropPaneModel.setVisible(calcTableAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(calcTableAssemblyInfo.getTitleValue());

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(calcTableAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(calcTableAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(calcTableAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(calcTableAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, calcTableAssemblyInfo.getAbsoluteName()));

      TableViewStylePaneController styleController = new TableViewStylePaneController();
      tableStylePaneModel.setTableStyle(calcTableAssemblyInfo.getTableStyleValue());
      tableStylePaneModel.setStyleTree(styleController.getStyleTree(rvs, principal, true));

      Point pos = dialogService.getAssemblyPosition(calcTableAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(calcTableAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setTitleHeight(calcTableAssemblyInfo.getTitleHeightValue());
      sizePositionPaneModel.setContainer(calcTableAssembly.getContainer() != null);

      advPane.setShrink(calcTableAssemblyInfo.getShrinkValue());
      advPane.setTrailerColCount(calcTableAssemblyInfo.getTrailerColCount());
      advPane.setTrailerRowCount(calcTableAssemblyInfo.getTrailerRowCount());
      advPane.setFillBlankWithZero(calcTableAssemblyInfo.getFillBlankWithZeroValue());
      advPane.setSortOthersLast(calcTableAssemblyInfo.getSortOthersLastValue());
      advPane.setSortOthersLastEnabled(calcTableAssemblyInfo.isSortOthersLastEnabled());

      if(lens != null) {
         advPane.setHeaderColCount(BaseTableController.getHeaderColCount(calcTableAssembly, lens));
         advPane.setHeaderRowCount(BaseTableController.getHeaderRowCount(calcTableAssembly, lens));
         advPane.setApproxVisibleRows(getApproxVisibleRows(lens, calcTableAssembly));
         advPane.setApproxVisibleCols(getApproxVisibleCols(lens, scrollX, calcTableAssembly));
         advPane.setRowCount(lens.getRowCount());
         advPane.setColCount(lens.getColCount());
      }
      else {
         advPane.setApproxVisibleRows(1);
         advPane.setApproxVisibleCols(1);
         advPane.setRowCount(1);
         advPane.setColCount(1);
      }

      tipPaneModel.setTipOption(
         calcTableAssemblyInfo.getTipOptionValue() == TipVSAssemblyInfo.VIEWTIP_OPTION);
      tipPaneModel.setTipView(calcTableAssemblyInfo.getTipViewValue());
      tipPaneModel.setAlpha(calcTableAssemblyInfo.getAlphaValue() == null ?
                               "100" : calcTableAssemblyInfo.getAlphaValue());
      String[] flyoverViews = calcTableAssemblyInfo.getFlyoverViewsValue();
      tipPaneModel.setFlyOverViews(flyoverViews == null ? new String[0] : flyoverViews);
      tipPaneModel.setFlyOnClick(
         Boolean.valueOf(calcTableAssemblyInfo.getFlyOnClickValue()));
      tipPaneModel.setPopComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, calcTableAssemblyInfo.getAbsoluteName(), false));
      tipPaneModel.setFlyoverComponents(
         this.vsObjectPropertyService.getSupportedTablePopComponents(
            rvs, calcTableAssemblyInfo.getAbsoluteName(), true));
      String srctbl = calcTableAssemblyInfo.getTableName();
      tipPaneModel.setDataViewEnabled(srctbl != null && !VSUtil.isVSAssemblyBinding(srctbl));

      vsAssemblyScriptPaneModel.scriptEnabled(calcTableAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(calcTableAssemblyInfo.getScript() == null ?
                                              "" : calcTableAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   private int getHeaderHeight(VSTableLens lens, boolean isWrapped, int headerRowCount,
      CalcTableVSAssemblyInfo calcTableAssemblyInfo)
   {
      int tableHeight = 0;

      try {
        if(isWrapped) {
           tableHeight = BaseTableController.getHeaderRowPositions(lens,
              isWrapped, headerRowCount)[headerRowCount];
        }
        else {
           tableHeight = Arrays.stream(calcTableAssemblyInfo.getHeaderRowHeights()).sum();
        }
      } catch(Exception e) {
      }

      return tableHeight;
   }

   private double getTableHeight(VSTableLens lens, boolean isWrapped, int headerRowCount,
      CalcTableVSAssembly calcTableAssembly)
   {
      CalcTableVSAssemblyInfo calcTableAssemblyInfo =
         (CalcTableVSAssemblyInfo) calcTableAssembly.getVSAssemblyInfo();
      Dimension size = getCalcSize(calcTableAssembly);

      return size.getHeight() - getHeaderHeight(lens, isWrapped, headerRowCount,
         calcTableAssemblyInfo) - calcTableAssemblyInfo.getTitleHeight();
   }

   public int getRowPosition(VSTableLens lens, boolean isWrapped, int headerRowcount,
      CalcTableVSAssemblyInfo calcTableAssemblyInfo)
   {
      int rowCount = lens.getRowCount();
      int dataRowHeight = calcTableAssemblyInfo.getDataRowHeight();

      if(!isWrapped) {
         if(rowCount < headerRowcount) {
            return Arrays.stream(calcTableAssemblyInfo.getHeaderRowHeights()).sum();
         }
         else {
            return dataRowHeight * (rowCount - 1);
         }
      }

      if(rowCount < headerRowcount) {
         return BaseTableController.getHeaderRowPositions(lens, isWrapped,
            headerRowcount)[rowCount];
      }

      int dataRowCount = Math.max(0, rowCount - headerRowcount);
      lens.initTableGrid(calcTableAssemblyInfo);

      return BaseTableController.getDataRowPositions(lens, isWrapped,
         headerRowcount, dataRowCount)[dataRowCount];
   }

   private double getApproxVisibleRows(VSTableLens lens,
      CalcTableVSAssembly calcTableAssembly)
   {
      CalcTableVSAssemblyInfo calcTableAssemblyInfo =
         (CalcTableVSAssemblyInfo) calcTableAssembly.getVSAssemblyInfo();

      if(lens == null) {
         return 1;
      }

      int rowCount = lens.getRowCount();
      int headerRowcount = BaseTableController.getHeaderRowCount(calcTableAssembly, lens);
      boolean isWrapped = BaseTableController.isWrapped(lens,
         headerRowcount >= rowCount ? 0 : headerRowcount);
      double tableHeight = getTableHeight(lens, isWrapped, headerRowcount,
         calcTableAssembly);
      int dataRowHeight = calcTableAssemblyInfo.getDataRowHeight();

      double approxVisibleRows =  isWrapped ? (1d * tableHeight /
         Math.ceil(1d * getRowPosition(lens, isWrapped,
            headerRowcount, calcTableAssemblyInfo) / (rowCount - 1)))
         : (1d * tableHeight / (dataRowHeight != 0 ? dataRowHeight : 1));

      return approxVisibleRows;
   }

   private Dimension getCalcSize(CalcTableVSAssembly calcTableAssembly) {
      CalcTableVSAssemblyInfo calcTableAssemblyInfo =
         (CalcTableVSAssemblyInfo) calcTableAssembly.getVSAssemblyInfo();
      Dimension size = calcTableAssemblyInfo.getLayoutSize();

      return (size != null && size.width > 0 && size.height > 0) ?
         size : calcTableAssembly.getViewsheet().getPixelSize(calcTableAssemblyInfo);
   }

   private double getApproxVisibleCols(VSTableLens lens, Double scrollX,
      CalcTableVSAssembly calcTableAssembly)
   {
      CalcTableVSAssemblyInfo calcTableAssemblyInfo =
         (CalcTableVSAssemblyInfo) calcTableAssembly.getVSAssemblyInfo();
      final int headerColCount =
         BaseTableController.getHeaderColCount(calcTableAssembly, lens);
      int currentCol = headerColCount;
      double[] colWidths = BaseTableController.getColWidths(calcTableAssembly, lens);
      double approxVisibleCols = 0;
      double headerColW = 0;
      scrollX = scrollX == null ? 0 : scrollX;

      for(int i = 0; i < headerColCount; i++) {
         headerColW += colWidths[i];
      }

      Dimension size = getCalcSize(calcTableAssembly);

      double bodyW = size.getWidth() - headerColW;

      for(int i = headerColCount; i < colWidths.length && scrollX > 0; i++) {
         currentCol++;
         scrollX -= colWidths[i];
      }

      if(scrollX < 0) {
         currentCol--;
         bodyW -= scrollX;
      }

      for(int i = currentCol; i < colWidths.length && bodyW > 0; i++) {
         approxVisibleCols++;
         bodyW -= colWidths[i];
      }

      return approxVisibleCols;
   }

   /**
    * Sets the specified table assembly info.
    *
    * @param objectId   the table id
    * @param value the table property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/calc-table-property-dialog-model/{objectId}")
   public void setCalcTablePropertyModel(@DestinationVariable("objectId") String objectId,
                                         @Payload CalcTablePropertyDialogModel value,
                                         @LinkUri String linkUri,
                                         Principal principal,
                                         CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      CalcTableVSAssemblyInfo calcTableAssemblyInfo;

      try {
         viewsheet = viewsheetService.getViewsheet(
            this.runtimeViewsheetRef.getRuntimeId(), principal);
         CalcTableVSAssembly calcTableAssembly =
            (CalcTableVSAssembly) viewsheet.getViewsheet().getAssembly(objectId);
         calcTableAssemblyInfo =
            (CalcTableVSAssemblyInfo) Tool.clone(calcTableAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      TableViewGeneralPaneModel tableViewGeneralPaneModel = value.getTableViewGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = tableViewGeneralPaneModel.getGeneralPropPaneModel();
      TitlePropPaneModel titlePropPaneModel = tableViewGeneralPaneModel.getTitlePropPaneModel();
      TableStylePaneModel tableStylePaneModel = tableViewGeneralPaneModel.getTableStylePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         tableViewGeneralPaneModel.getSizePositionPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      CalcTableAdvancedPaneModel advPane = value.getCalcTableAdvancedPaneModel();
      TipPaneModel tipPaneModel = advPane.getTipPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      calcTableAssemblyInfo.setTitleVisibleValue(titlePropPaneModel.isVisible());
      calcTableAssemblyInfo.setTitleValue(titlePropPaneModel.getTitle());

      calcTableAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      calcTableAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      calcTableAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());

      calcTableAssemblyInfo.setTableStyleValue(tableStylePaneModel.getTableStyle());

      dialogService.setAssemblySize(calcTableAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(calcTableAssemblyInfo, sizePositionPaneModel);
      calcTableAssemblyInfo.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());

      calcTableAssemblyInfo.setShrinkValue(advPane.isShrink());
      calcTableAssemblyInfo.setHeaderRowCount(advPane.getHeaderRowCount());
      calcTableAssemblyInfo.setHeaderColCount(advPane.getHeaderColCount());
      calcTableAssemblyInfo.setTrailerRowCount(advPane.getTrailerRowCount());
      calcTableAssemblyInfo.setTrailerColCount(advPane.getTrailerColCount());
      calcTableAssemblyInfo.setFillBlankWithZeroValue(advPane.isFillBlankWithZero());
      calcTableAssemblyInfo.setSortOthersLastValue(advPane.isSortOthersLast());

      if(tipPaneModel.isTipOption()) {
         calcTableAssemblyInfo.setTipOptionValue(TipVSAssemblyInfo.VIEWTIP_OPTION);
         String str = tipPaneModel.getAlpha();
         calcTableAssemblyInfo.setAlphaValue(str != null && str.length() > 0 ? str : null);

         if(tipPaneModel.getTipView() != null && !"null".equals(tipPaneModel.getTipView())) {
            calcTableAssemblyInfo.setTipViewValue(tipPaneModel.getTipView());
         }
         else {
            calcTableAssemblyInfo.setTipViewValue(null);
         }
      }
      else {
         calcTableAssemblyInfo.setTipOptionValue(TipVSAssemblyInfo.TOOLTIP_OPTION);
         calcTableAssemblyInfo.setTipViewValue(null);
      }

      String[] flyovers = VSUtil.getValidFlyovers(tipPaneModel.getFlyOverViews(),
                                                  viewsheet.getViewsheet());
      calcTableAssemblyInfo.setFlyoverViewsValue(flyovers);
      calcTableAssemblyInfo.setFlyOnClickValue(tipPaneModel.isFlyOnClick() + "");

      calcTableAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      calcTableAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());
      calcTableAssemblyInfo.resetRColumnWidths();

      int trailerRows = calcTableAssemblyInfo.getTrailerRowCount();
      int headerRows = calcTableAssemblyInfo.getHeaderRowCount();
      int rowCnt = calcTableAssemblyInfo.getTableLayout().getRowCount();
      TableLayout layout = calcTableAssemblyInfo.getTableLayout();

      // if path changed, copy the formats so they are not lost
      for(TableDataPath path : calcTableAssemblyInfo.getFormatInfo().getPaths()) {
         Point loc = TableTool.getVSCalcCellLocation(path);

         if(loc == null) {
            continue;
         }

         if(path.isCell()) {
            TableDataPath layoutPath = layout.getCellDataPath(loc.y, loc.x);

            // check if this path is valid for the current calc table and not leftover from
            // the crosstab conversion
            if(layoutPath == null || layoutPath.getType() != path.getType()) {
               continue;
            }
         }

         int r = loc.y;
         int type = getRowType(r, rowCnt, headerRows, trailerRows);

         if(type != path.getType()) {
            VSCompositeFormat fmt = calcTableAssemblyInfo.getFormatInfo().getFormat(path);
            Hyperlink link = calcTableAssemblyInfo.getHyperlinkAttr() != null ?
                  calcTableAssemblyInfo.getHyperlinkAttr().getHyperlink(path) : null;
            HighlightGroup high = calcTableAssemblyInfo.getHighlightAttr() != null ?
                  calcTableAssemblyInfo.getHighlightAttr().getHighlight(path) : null;
            path = (TableDataPath) path.clone();
            path.setType(type);
            calcTableAssemblyInfo.getFormatInfo().setFormat(path, fmt);

            if(calcTableAssemblyInfo.getHyperlinkAttr() != null) {
               calcTableAssemblyInfo.getHyperlinkAttr().setHyperlink(path, link);
            }

            if(calcTableAssemblyInfo.getHighlightAttr() != null) {
               calcTableAssemblyInfo.getHighlightAttr().setHighlight(path, high);
            }
         }
      }

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, calcTableAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);
   }

   // should match CalcTableLens.getRowType()
   private static int getRowType(int r, int rowCnt, int headerRows, int trailerRows) {
      if(r < headerRows) {
         return TableDataPath.HEADER;
      }
      else if(r >= rowCnt - trailerRows) {
         return TableDataPath.TRAILER;
      }

      return TableDataPath.DETAIL;
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
}
