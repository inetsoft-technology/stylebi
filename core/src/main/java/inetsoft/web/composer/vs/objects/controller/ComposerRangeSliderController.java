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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.event.ConvertToRangeSliderEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

/**
 * Controller that processes VS Selection List events.
 */
@Controller
public class ComposerRangeSliderController {
   /**
    * Creates a new instance of <tt>ComposerRangeSliderController</tt>.
    *  @param runtimeViewsheetRef the runtime viewsheet reference
    * @param placeholderService the placeholder service
    * @param viewsheetService
    */
   @Autowired
   public ComposerRangeSliderController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Change range slider to a selection list. Mimic of ConvertCSComponentEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/rangeSlider/convertToSelectionList")
   public void convertCSComponent(@Payload ConvertToRangeSliderEvent event,
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

      final VSAssembly assembly = viewsheet.getAssembly(name);

      if(assembly == null) {
         return;
      }

      // embedded object not support convert
      if(((VSAssemblyInfo) assembly.getInfo()).isEmbedded()) {
         return;
      }

      final VSAssembly container = assembly.getContainer();

      if(!(assembly instanceof SelectionListVSAssembly) &&
         !(assembly instanceof TimeSliderVSAssembly) &&
         !(container instanceof CurrentSelectionVSAssembly))
      {
         return;
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
      placeholderService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, false);
      containerAssembly.setAssemblies(assemblies);
      viewsheet.addAssembly(newAssembly);
      placeholderService.addDeleteVSObject(rvs, newAssembly, dispatcher);
      placeholderService.execute(rvs, name, linkUri, VSAssembly.VIEW_CHANGED, dispatcher);
      placeholderService.refreshVSAssembly(rvs, containerAssembly.getName(), dispatcher, true);
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

   private final PlaceholderService placeholderService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
}
