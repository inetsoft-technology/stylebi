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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sijie Liu on 9/13/2017.
 */
@Service
public class VSSelectionContainerService {
   @Autowired
   public VSSelectionContainerService(VSObjectService service,
                                      PlaceholderService placeholderService,
                                      VSObjectModelFactoryService objectModelService)
   {
      this.service = service;
      this.placeholderService = placeholderService;
      this.objectModelService = objectModelService;
   }

   /**
    * Applies a new status for a selection container. (Handles dropdown visibility)
    *
    * @param rvs          the Runtime Viewsheet Instance
    * @param assemblyName the selected assembly name
    * @param setAsHidden  the dropdown visibility to set to the target assembly,
    *                     true if the assembly is being hidden
    * @param dispatcher   the command dispatcher.
    */
   public void applySelection(RuntimeViewsheet rvs, String assemblyName, boolean setAsHidden,
                              CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      final Viewsheet viewsheet = rvs.getViewsheet();
      final VSAssembly targetAssembly = viewsheet.getAssembly(assemblyName);
      final VSAssemblyInfo targetAssemblyInfo = targetAssembly.getVSAssemblyInfo();
      final List<VSAssembly> changedAssemblies = new ArrayList<>();
      changedAssemblies.add(targetAssembly);
      boolean haveDrop = false;

      //if target is becoming visible, we check if we should toggle other assembly dropdown
      if(!setAsHidden) {
         haveDrop = true;
         final CurrentSelectionVSAssembly container =
            (CurrentSelectionVSAssembly) targetAssembly.getContainer();
         final CurrentSelectionVSAssemblyInfo containerInfo =
            (CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo();
         final Dimension size = service.getSize(containerInfo);
         int contentHeight = size.height > 40 ? size.height - 40 : size.height;
         int totalHeight = 0;

         if(containerInfo.getTitleVisibleValue()) {
            contentHeight -= containerInfo.getTitleHeight();
         }

         if(containerInfo.isShowCurrentSelection()) {
            contentHeight -= AssetUtil.defh * containerInfo.getOutSelectionTitles().length;
         }

         final String[] children = container.getAbsoluteAssemblies();
         final List<VSAssembly> childAssemblies = new ArrayList<>();

         for(String name : children) {
            final VSAssembly childAssembly = viewsheet.getAssembly(name);
            final VSAssemblyInfo childAssemblyInfo = childAssembly.getVSAssemblyInfo();
            final Dimension childSize = service.getSize(childAssemblyInfo);
            totalHeight += childSize.height;
            final boolean sliderExpanded = childAssemblyInfo instanceof TimeSliderVSAssemblyInfo &&
               (childSize.height !=
                  ((TimeSliderVSAssemblyInfo) childAssemblyInfo).getTitleHeight());
            final boolean listExpanded = childAssemblyInfo instanceof SelectionListVSAssemblyInfo &&
               ((SelectionListVSAssemblyInfo) childAssemblyInfo).getShowTypeValue() ==
                  SelectionVSAssemblyInfo.LIST_SHOW_TYPE;

            if(childAssemblyInfo != targetAssemblyInfo && (sliderExpanded || listExpanded)) {
               childAssemblies.add(childAssembly);
            }
         }

         //Bug 16742 should add the selected assembly's dropdown height to the totalheight.
         //Need to check if it's in hidden status before because target assembly can be new added.
         if(targetAssemblyInfo instanceof SelectionListVSAssemblyInfo &&
            ((SelectionListVSAssemblyInfo) targetAssemblyInfo).getShowTypeValue() ==
               SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
         {
            totalHeight += ((SelectionListVSAssemblyInfo) targetAssemblyInfo)
               .getListHeight() * AssetUtil.defh;
         }
         else if(targetAssemblyInfo instanceof TimeSliderVSAssemblyInfo) {
            Dimension sliderSize = targetAssemblyInfo.getLayoutSize() != null ?
               targetAssemblyInfo.getLayoutSize() : viewsheet.getPixelSize(targetAssemblyInfo);

            if(sliderSize.height == ((TimeSliderVSAssemblyInfo) targetAssemblyInfo).getTitleHeight()) {
               totalHeight += ((TimeSliderVSAssemblyInfo) targetAssemblyInfo)
                  .getListHeight() * AssetUtil.defh;
            }
         }

         if(totalHeight >= contentHeight && !childAssemblies.isEmpty()) {
            for(int i = 0; i < childAssemblies.size() && totalHeight >= contentHeight; i++) {
               final VSAssembly assembly = childAssemblies.get(i);
               final VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
               final Dimension childSize = service.getSize(assemblyInfo);
               changedAssemblies.add(assembly);
               final int titleHeight = ((TitledVSAssemblyInfo) assemblyInfo).getTitleHeight();
               totalHeight -= childSize.height - titleHeight;
            }
         }
      }

      for(VSAssembly changedAssembly : changedAssemblies) {
         if(changedAssembly instanceof SelectionListVSAssembly) {
            final SelectionListVSAssemblyInfo selectionListInfo =
               (SelectionListVSAssemblyInfo) changedAssembly.getVSAssemblyInfo();
            final Dimension size = service.getSize(selectionListInfo);

            if(selectionListInfo.getShowTypeValue() == SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
               size.height = selectionListInfo.getListHeight() * AssetUtil.defh +
                  selectionListInfo.getTitleHeight();
               selectionListInfo.setShowTypeValue(SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
            }
            //Bug #16742 should account for new added assembly which was in show status at beginning
            else if(setAsHidden || !changedAssembly.getName().equals(assemblyName)){
               size.height = selectionListInfo.getTitleHeight();
               selectionListInfo.setShowTypeValue(SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);
            }
         }
         else if(changedAssembly instanceof TimeSliderVSAssembly){
            final TimeSliderVSAssemblyInfo timeSliderInfo =
               (TimeSliderVSAssemblyInfo) changedAssembly.getVSAssemblyInfo();
            final Dimension size = service.getSize(timeSliderInfo);

            if(size.height == timeSliderInfo.getTitleHeight()) {
               size.height = timeSliderInfo.getListHeight() * AssetUtil.defh +
                  timeSliderInfo.getTitleHeight();
               timeSliderInfo.setHidden(false);
            }
            //Bug #16742 should account for new added assembly which was in show status at beginning
            else if(setAsHidden || !changedAssembly.getName().equals(assemblyName)){
               size.height = timeSliderInfo.getTitleHeight();
               timeSliderInfo.setHidden(true);
            }
         }

         if(changedAssembly.getContainer() != null) {
            // Bug #64966, apply the scaling to the new height when scale to screen is enabled
            if(rvs.getViewsheet().getViewsheetInfo().isScaleToScreen() &&
               (rvs.isPreview() || rvs.isViewer()) &&
               changedAssembly.getContainer() instanceof CurrentSelectionVSAssembly)
            {
               Point2D.Double scaleRatio = (Point2D.Double) rvs.getProperty("viewsheet.scaleRatio");

               if(scaleRatio != null) {
                  VSEventUtil.applyCurrentSelectionScale(viewsheet,
                                                         (CurrentSelectionVSAssembly) changedAssembly.getContainer(),
                                                         scaleRatio);
               }
            }

            final RefreshVSObjectCommand command = new RefreshVSObjectCommand();
            command.setInfo(objectModelService.createModel(changedAssembly, rvs));
            dispatcher.sendCommand(changedAssembly.getContainer().getAbsoluteName(), command);
         }
      }

      if(targetAssembly.getContainer() == null) {
         placeholderService.removeVSAssembly(rvs, linkUri, targetAssembly, dispatcher, false, false);
      }
      else if(!haveDrop && targetAssembly.getContainer() != null &&
         !targetAssembly.getContainer().getViewsheet().isEmbedded())
      {
         final RefreshVSObjectCommand command = new RefreshVSObjectCommand();
         final VSAssembly containerAssembly = targetAssembly.getContainer();
         command.setInfo(objectModelService.createModel(containerAssembly, rvs));
         dispatcher.sendCommand(command);
      }
   }

   private final VSObjectService service;
   private final PlaceholderService placeholderService;
   private final VSObjectModelFactoryService objectModelService;
}
