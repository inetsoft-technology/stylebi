/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
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
import inetsoft.web.viewsheet.service.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

@Service
@ClusterProxy
public class ComposerVSSelectionListService {

   public ComposerVSSelectionListService(ViewsheetService viewsheetService,
                                         CoreLifecycleService coreLifecycleService) {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeColCount(@ClusterProxyKey String vsId, int count, VSObjectEvent event,
                              Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();

      box.lockRead();

      try {
         VSAssembly assembly = viewsheet.getAssembly(event.getName());

         if(assembly == null) {
            return null;
         }

         assert assembly instanceof SelectionListVSAssembly;
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) assembly.getVSAssemblyInfo();
         info.setColumnCount(count);
         coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
      }
      finally {
         box.unlockRead();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateCellHeight(@ClusterProxyKey String vsId, VSSetCellHeightEvent event,
                                Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());

      assert (assembly instanceof SelectionListVSAssembly
         || assembly instanceof SelectionTreeVSAssembly);

      int cellHeight = event.getCellHeight() <= 0 ? AssetUtil.defh : event.getCellHeight();

      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setCellHeight(cellHeight);

      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateMeasureSize(@ClusterProxyKey String vsId, VSSetMeasuresEvent event,
                                 Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());

      assert (assembly instanceof SelectionListVSAssembly
         || assembly instanceof SelectionTreeVSAssembly);

      SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setMeasureSize(event.getTextWidth());
      info.setBarSize(event.getBarWidth());

      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeColumnByDrag(@ClusterProxyKey String vsId, VSDndEvent event, Principal principal,
                                  String linkUri, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TableTransfer tableData = (TableTransfer) event.getTransfer();

      String name = tableData.getAssembly();
      VSAssembly assembly = viewsheet.getAssembly(name);

      if(assembly == null) {
         return null;
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
      coreLifecycleService.execute(rvs, name, linkUri, hint, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void convertToRangeSlider(@ClusterProxyKey String vsId, ConvertToRangeSliderEvent event,
                                    Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();

      if(viewsheet == null) {
         return null;
      }

      final VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

      if(assembly == null) {
         return null;
      }

      // embedded object not support convert
      if(((VSAssemblyInfo) assembly.getInfo()).isEmbedded()) {
         return null;
      }

      final VSAssembly container = assembly.getContainer();

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(container instanceof CurrentSelectionVSAssembly))
      {
         return null;
      }

      final CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) container;
      final String[] assemblies = containerAssembly.getAssemblies();
      final VSAssembly newTimeSliderAssembly = createTimeSliderVSAssembly(viewsheet, assembly);
      VSEventUtil.copyFormat(assembly, newTimeSliderAssembly);
      coreLifecycleService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, false);
      containerAssembly.setAssemblies(assemblies);
      viewsheet.addAssembly(newTimeSliderAssembly);
      coreLifecycleService.addDeleteVSObject(rvs, newTimeSliderAssembly, dispatcher);
      coreLifecycleService.refreshVSAssembly(rvs, containerAssembly.getName(), dispatcher, true);
      coreLifecycleService.execute(rvs, name, linkUri, VSAssembly.VIEW_CHANGED, dispatcher);

      return null;
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

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
