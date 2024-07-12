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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.event.VSDndEvent;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Controller that processes VS Selection List events.
 */
@Controller
public class ComposerVSSelectionListController {
   /**
    * Creates a new instance of <tt>ComposerVSSelectionListController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerVSSelectionListController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            ViewsheetService viewsheetService,
                                            PlaceholderService placeholderService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
   }

   /**
    * Change the number of columns in the selection list.
    *
    * @param count      the number of columns.
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/changeColCount/{count}")
   public void changeColCount(@DestinationVariable("count") int count,
                              VSObjectEvent event, Principal principal,
                              CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();

      box.lockRead();

      try {
         VSAssembly assembly = viewsheet.getAssembly(event.getName());

         if(assembly == null) {
            return;
         }

         assert assembly instanceof SelectionListVSAssembly;
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
         info.setColumnCount(count);
         placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Change the height of the cells  in the selection
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/updateCellHeight/")
   public void updateCellHeight(VSSetCellHeightEvent event,
                                Principal principal,
                                CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());

      assert (assembly instanceof SelectionListVSAssembly
              || assembly instanceof SelectionTreeVSAssembly);

      int cellHeight = event.getCellHeight() <= 0 ? AssetUtil.defh : event.getCellHeight();

      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setCellHeight(cellHeight);

      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   /**
    * Change the size of the cell measures in the selection
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/updateMeasureSize/")
   public void updateMeasureSize(VSSetMeasuresEvent event,
                                 Principal principal,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());

      assert (assembly instanceof SelectionListVSAssembly
              || assembly instanceof SelectionTreeVSAssembly);

      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setMeasureSize(event.getTextWidth());
      info.setBarSize(event.getBarWidth());

      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   /**
    * Remove column from selection list or tree by dragging a selection list cell with that
    * column's data to the asset tree
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value="/vsselection/dnd/removeColumns")
   public void removeColumnByDrag(@Payload VSDndEvent event, Principal principal,
                         @LinkUri String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TableTransfer tableData = (TableTransfer) event.getTransfer();

      String name = tableData.getAssembly();
      VSAssembly assembly = viewsheet.getAssembly(name);

      if(assembly == null) {
         return;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) Tool.clone(assembly.getInfo());

      if(info instanceof SelectionListVSAssemblyInfo) {
         ((SelectionListVSAssemblyInfo) info).setDataRef(null);
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         SelectionTreeVSAssemblyInfo treeInfo = ((SelectionTreeVSAssemblyInfo) info);
         DataRef[] oldRefs = treeInfo.getDataRefs();

         if(oldRefs.length == 1) {
            treeInfo.setDataRefs(new DataRef[0]);
         }
         else {
            ArrayList<DataRef> refList = new ArrayList<>(Arrays.asList(oldRefs));
            refList.remove(tableData.getDragIndex());
            treeInfo.setDataRefs(refList.toArray(new DataRef[refList.size()]));
         }
      }

      int hint = assembly.setVSAssemblyInfo(info);
      placeholderService.execute(rvs, name, linkUri, hint, dispatcher);
   }

   /**
    * Change selection list to a range slider. Mimic of ConvertCSComponentEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionList/convertToRangeSlider")
   public void convertToRangeSlider(@Payload ConvertToRangeSliderEvent event,
                                    Principal principal,
                                    CommandDispatcher dispatcher,
                                    @LinkUri String linkUri) throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();

      if(viewsheet == null) {
         return;
      }

      final VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

      if(assembly == null) {
         return;
      }

      // embedded object not support convert
      if(((VSAssemblyInfo) assembly.getInfo()).isEmbedded()) {
         return;
      }

      final VSAssembly container = assembly.getContainer();

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(container instanceof CurrentSelectionVSAssembly))
      {
         return;
      }

      final CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) container;
      final String[] assemblies = containerAssembly.getAssemblies();
      final VSAssembly newTimeSliderAssembly = createTimeSliderVSAssembly(viewsheet, assembly);
      VSEventUtil.copyFormat(assembly, newTimeSliderAssembly);
      placeholderService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, false);
      containerAssembly.setAssemblies(assemblies);
      viewsheet.addAssembly(newTimeSliderAssembly);
      placeholderService.addDeleteVSObject(rvs, newTimeSliderAssembly, dispatcher);
      placeholderService.refreshVSAssembly(rvs, containerAssembly.getName(), dispatcher, true);
      placeholderService.execute(rvs, name, linkUri, VSAssembly.VIEW_CHANGED, dispatcher);
   }

   /**
    * Create time slider assembly.
    */
   private VSAssembly createTimeSliderVSAssembly(Viewsheet viewsheet, VSAssembly assembly) {
      SelectionListVSAssembly list = (SelectionListVSAssembly) assembly;
      DataRef ref = list.getDataRef();
      SelectionListVSAssemblyInfo linfo = list.getSelectionListInfo();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(viewsheet, assembly.getName());
      TimeSliderVSAssemblyInfo info = slider.getTimeSliderInfo();
      TimeInfo tinfo = createTimeInfo(ref);
      slider.setTitleValue(list.getTitleValue());
      slider.setTableName(linfo.getFirstTableName());
      slider.setTimeInfo(tinfo);
      info.setComposite(tinfo instanceof CompositeTimeInfo);
      info.setPixelSize(new Dimension(list.getPixelSize().width,
                                      list.getPixelSize().height > AssetUtil.defh
                                      ? info.getPixelSize().height + AssetUtil.defh
                                      : AssetUtil.defh * 2));
      final Point pos = assembly.getPixelOffset();
      slider.setPixelOffset(pos);

      // set up info
      info.initDefaultFormat(true);
      info.setPrimary(assembly.isPrimary());
      info.setEnabledValue(assembly.getVSAssemblyInfo().isEnabled() + "");
      info.setVisible(assembly.isVisible());
      info.setEditable(assembly.isEditable());
      info.setAdditionalTableNames(linfo.getAdditionalTableNames());

      return slider;
   }

   /**
    * Create time info.
    */
   private TimeInfo createTimeInfo(DataRef ref) {
      if(ref == null) {
         return new SingleTimeInfo();
      }

      if(XSchema.isNumericType(ref.getDataType()) ||
         XSchema.isDateType(ref.getDataType()) ||
         (ref.getRefType() & DataRef.CUBE) != 0)
      {
         return createSingleTimeInfo(ref);
      }

      return createCompositeTimeInfo(ref);
   }

   /**
    * Create single time info.
    */
   private TimeInfo createSingleTimeInfo(DataRef ref) {
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setDataRef(ref);

      // fix bug1241406371753, create member range for dimension member no
      // matter its data type is number or date
      if((ref.getRefType() & DataRef.CUBE_DIMENSION) == DataRef.CUBE_DIMENSION)
      {
         tinfo.setRangeTypeValue(TimeInfo.MEMBER);
      }
      else if(XSchema.isNumericType(ref.getDataType())) {
         tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      }
      else if(XSchema.TIME.equals(ref.getDataType())) {
         tinfo.setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
      }
      else {
         tinfo.setRangeTypeValue(TimeInfo.MONTH);
      }

      return tinfo;
   }

   /**
    * Create composite time info.
    */
   private TimeInfo createCompositeTimeInfo(DataRef ref) {
      CompositeTimeInfo tinfo = new CompositeTimeInfo();
      tinfo.setDataRefs(new DataRef[] {ref});
      return tinfo;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComposerVSSelectionListController.class);
}
