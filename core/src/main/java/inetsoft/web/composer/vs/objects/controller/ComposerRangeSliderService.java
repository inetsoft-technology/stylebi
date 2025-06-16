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
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class ComposerRangeSliderService {

   public ComposerRangeSliderService(ViewsheetService viewsheetService,
                                     CoreLifecycleService coreLifecycleService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void convertCSComponent(@ClusterProxyKey String vsId, ConvertToRangeSliderEvent event,
                                  Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();

      if(viewsheet == null) {
         return null;
      }

      final VSAssembly assembly = viewsheet.getAssembly(name);

      if(assembly == null) {
         return null;
      }

      // embedded object not support convert
      if(((VSAssemblyInfo) assembly.getInfo()).isEmbedded()) {
         return null;
      }

      final VSAssembly container = assembly.getContainer();

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(assembly instanceof TimeSliderVSAssembly) &&
         !(container instanceof CurrentSelectionVSAssembly))
      {
         return null;
      }

      final CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) container;
      final String[] assemblies = containerAssembly.getAssemblies();
      final VSAssembly newAssembly = createSelectionListVSAssembly(viewsheet, assembly);
      final VSAssemblyInfo info = newAssembly.getVSAssemblyInfo();
      info.setPrimary(assembly.isPrimary());
      info.setEnabledValue(assembly.getVSAssemblyInfo().isEnabled() + "");
      info.setVisible(assembly.isVisible());
      info.setEditable(assembly.isEditable());
      VSEventUtil.copyFormat(assembly, newAssembly);
      initCellFormat(newAssembly);
      coreLifecycleService.removeVSAssemblies(rvs, linkUri, dispatcher, false, false, false, false, assembly);
      containerAssembly.setAssemblies(assemblies);
      viewsheet.addAssembly(newAssembly);
      coreLifecycleService.addDeleteVSObject(rvs, newAssembly, dispatcher);
      coreLifecycleService.execute(rvs, name, linkUri, VSAssembly.VIEW_CHANGED, dispatcher);
      coreLifecycleService.refreshVSAssembly(rvs, containerAssembly.getName(), dispatcher, true);

      return null;
   }


   /**
    * Create selection list assembly.
    */
   private VSAssembly createSelectionListVSAssembly(Viewsheet vs, VSAssembly cobj) {
      TimeSliderVSAssembly slider = (TimeSliderVSAssembly) cobj;
      TimeSliderVSAssemblyInfo sliderInfo = slider.getTimeSliderInfo();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, cobj.getName());
      SelectionListVSAssemblyInfo info = list.getSelectionListInfo();
      list.setTableName(sliderInfo.getFirstTableName());
      list.setDataRef(slider.getDataRefs()[0]);
      list.setTitleValue(slider.getTitleValue());

      info.setShowTypeValue(SelectionBaseVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
      info.setSourceType(sliderInfo.getSourceType());
      info.setListHeight(list.getPixelSize().height / AssetUtil.defh - 1);
      info.setPixelSize(new Dimension(slider.getPixelSize().width,
                                      slider.getPixelSize().height > AssetUtil.defh
                                         ? list.getPixelSize().height : AssetUtil.defh));

      final Point pos = cobj.getPixelOffset();
      list.setPixelOffset(pos);
      list.initDefaultFormat();

      // set up info
      info.setAdditionalTableNames(sliderInfo.getAdditionalTableNames());

      return list;
   }

   /**
    * Init cell format, if the assembly is selection list, copy the object
    * format for the cell format, otherwise do nothing.
    */
   private void initCellFormat(VSAssembly assembly) {
      if(!(assembly instanceof SelectionListVSAssembly)) {
         return;
      }

      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getInfo();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat objfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      TableDataPath detailPath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat cellFmt = finfo.getFormat(detailPath);
      VSEventUtil.copyFormat(cellFmt.getUserDefinedFormat(), objfmt, false);
   }

   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
}
