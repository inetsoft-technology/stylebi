/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.util.List;
import java.util.*;

import static inetsoft.uql.asset.internal.AssetUtil.defh;

/**
 * A service for grouping objects together into tab or selection container.
 */
@Controller
public class GroupingService {
   /**
    * Creates a new instance of <tt>GroupingService</tt>.
    *
    * @param placeholderService service for general viewsheet actions.
    */
   @Autowired
   public GroupingService(PlaceholderService placeholderService,
                          VSLayoutService vsLayoutService)
   {
      this.placeholderService = placeholderService;
      this.vsLayoutService = vsLayoutService;
   }

   public void groupComponents(RuntimeViewsheet rvs, VSAssembly target, VSAssembly object,
                               boolean selection, String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      groupComponents(rvs, target, object, selection, linkUri, dispatcher, false);
   }

   public void groupComponents(RuntimeViewsheet rvs, VSAssembly target, VSAssembly object,
                               boolean selection, String linkUri, CommandDispatcher dispatcher,
                               boolean adhocFilter)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly objectContainer = object != null ? object.getContainer(): null;
      VSAssembly targetContainer = target != null ? target.getContainer(): null;

      if(selection) {
         assert target instanceof CurrentSelectionVSAssembly
            || targetContainer instanceof CurrentSelectionVSAssembly;

         boolean ovisible = target.isVisible();
         AbstractContainerVSAssembly container = adhocFilter || targetContainer == null ||
            (target instanceof CurrentSelectionVSAssembly && targetContainer instanceof TabVSAssembly) ?
            (AbstractContainerVSAssembly) target : (AbstractContainerVSAssembly) targetContainer;

         if(object instanceof CurrentSelectionVSAssembly) {
            AbstractContainerVSAssembly additions = (AbstractContainerVSAssembly) object;

            combineContainers(additions, container, viewsheet);

            RemoveVSObjectCommand command = new RemoveVSObjectCommand();
            command.setName(additions.getAbsoluteName());

            dispatcher.sendCommand(command);

            placeholderService.execute(rvs, container.getAbsoluteName(), linkUri,
                                       VSAssembly.VIEW_CHANGED, dispatcher);
         }
         else {
            if(target instanceof CurrentSelectionVSAssembly && object != null) {
               object.getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH).getDefaultFormat()
                  .setBordersValue(new Insets(1, 0, 1, 0));
            }

            RemoveVSObjectCommand command = new RemoveVSObjectCommand();
            command.setName(object.getAbsoluteName());

            // remove assembly from viewsheet pane, it will be part of new containers children
            if(objectContainer instanceof CurrentSelectionVSAssembly) {
               // If assembly is in selection container, send event to selection
               // container to remove child object
               dispatcher.sendCommand(container.getAbsoluteName(), command);
            }
            else if(object.isEmbedded()) {
               dispatcher.sendCommand(object.getViewsheet().getAbsoluteName(), command);
            }
            else {
               dispatcher.sendCommand(command);
            }

            String[] assemblies = container.getAssemblies();
            List<String> list = new ArrayList<>(Arrays.asList(assemblies));
            list.add(object.getName());
            container.setAssemblies(list.toArray(new String[list.size()]));

            if(objectContainer instanceof CurrentSelectionVSAssembly) {
               ((CurrentSelectionVSAssembly) objectContainer)
                  .removeAssembly(object.getAbsoluteName());

               placeholderService.execute(rvs, objectContainer.getAbsoluteName(), linkUri,
                                          VSAssembly.VIEW_CHANGED, dispatcher);
            }

            VSAssemblyInfo containerInfo = (VSAssemblyInfo) container.getInfo();
            VSAssemblyInfo objectInfo = (VSAssemblyInfo) object.getInfo();

            Point pos = containerInfo.getLayoutPosition() != null ?
               containerInfo.getLayoutPosition() : viewsheet.getPixelPosition(containerInfo);
            Dimension size = containerInfo.getLayoutSize() != null ?
               containerInfo.getLayoutSize() : viewsheet.getPixelSize(containerInfo);
            Dimension objSize = objectInfo.getLayoutSize() != null ?
               objectInfo.getLayoutSize() : viewsheet.getPixelSize(objectInfo);

            if(objectInfo instanceof SelectionBaseVSAssemblyInfo) {
               SelectionBaseVSAssemblyInfo sinfo = ((SelectionBaseVSAssemblyInfo) objectInfo);
               sinfo.setShowTypeValue(container instanceof CurrentSelectionVSAssembly
                  ? SelectionBaseVSAssemblyInfo.DROPDOWN_SHOW_TYPE : sinfo.getShowTypeValue());
               objSize.height = sinfo.getListHeight() * defh + sinfo.getTitleHeight();
            }

            Point objectPos = new Point(pos.x, pos.y + size.height);
            objectInfo.setPixelOffset(objectPos);

            if(objectInfo instanceof TimeSliderVSAssemblyInfo && ((TimeSliderVSAssemblyInfo) objectInfo).isHidden()) {
               objectInfo.setPixelSize(new Dimension(size.width,
                  ((TimeSliderVSAssemblyInfo) objectInfo).getTitleHeight()));
            }
            else {
               objectInfo.setPixelSize(new Dimension(size.width, objSize.height));
            }

            objectInfo.setZIndex(container.getZIndex());

            if(containerInfo.getLayoutSize() != null && objectInfo.getLayoutSize() == null) {
               objectInfo.setLayoutSize(new Dimension(size.width, objSize.height));
            }

            placeholderService.execute(rvs, container.getAbsoluteName(), linkUri,
                                       VSAssembly.VIEW_CHANGED, dispatcher);
         }

         // visibility of container changed in tab, need to refresh tab and its children. (62228)
         if(targetContainer instanceof TabVSAssembly && !ovisible && target.isVisible()) {
            placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher, false,
                                                false, false, new ChangedAssemblyList());
         }
      }
      else {
         if(target instanceof TabVSAssembly || targetContainer instanceof TabVSAssembly) {
            TabVSAssembly container = targetContainer == null ? (TabVSAssembly) target :
               (TabVSAssembly) targetContainer;

            if(object instanceof TabVSAssembly) {
               TabVSAssembly add = (TabVSAssembly) object;

               combineContainers(add, container, viewsheet);

               String[] assemblies = container.getAssemblies();
               container.setSelectedValue(assemblies[assemblies.length - 1]);

               RemoveVSObjectCommand command = new RemoveVSObjectCommand();
               command.setName(add.getAbsoluteName());

               dispatcher.sendCommand(command);

               placeholderService.execute(rvs, container.getAbsoluteName(), linkUri,
                                          VSAssembly.VIEW_CHANGED, dispatcher);
            }
            else {
               String[] assemblies = container.getAssemblies();
               List<String> list = new ArrayList<>(Arrays.asList(assemblies));
               list.add(object.getAbsoluteName());
               container.setAssemblies(list.toArray(new String[list.size()]));
               container.setSelectedValue(object.getAbsoluteName());

               if(objectContainer instanceof TabVSAssembly) {
                  ((TabVSAssembly) objectContainer).removeAssembly(object.getAbsoluteName());

                  placeholderService.execute(rvs, objectContainer.getAbsoluteName(), linkUri,
                                             VSAssembly.VIEW_CHANGED, dispatcher);
               }

               VSAssemblyInfo info = (VSAssemblyInfo) container.getInfo();

               Point pos = info.getLayoutPosition() != null ?
                  info.getLayoutPosition() : viewsheet.getPixelPosition(info);
               Dimension size = info.getLayoutSize() != null ?
                  info.getLayoutSize() : viewsheet.getPixelSize(info);

               VSAssemblyInfo objectInfo = (VSAssemblyInfo) object.getInfo();

               Dimension objectSize = objectInfo.getLayoutSize() != null ?
                  objectInfo.getLayoutSize() : viewsheet.getPixelSize(objectInfo);

               int width = size.width > objectSize.width ? size.width: objectSize.width;

               Dimension updatedSize = new Dimension(width, size.height);

               info.setPixelSize(updatedSize);
               Point objectPos = new Point(pos.x, pos.y + size.height);
               object.setPixelOffset(objectPos);
               objectInfo.setZIndex(container.getZIndex());

               placeholderService.execute(rvs, container.getAbsoluteName(), linkUri,
                                          VSAssembly.VIEW_CHANGED, dispatcher);
            }
         }
         else if(object instanceof TabVSAssembly) {
            TabVSAssembly container = (TabVSAssembly) object;

            String[] assemblies = container.getAssemblies();
            List<String> list = new ArrayList<>(Arrays.asList(assemblies));
            list.add(target.getAbsoluteName());
            container.setAssemblies(list.toArray(new String[list.size()]));
            container.setSelectedValue(object.getAbsoluteName());

            VSAssemblyInfo info = (VSAssemblyInfo) container.getInfo();

            Point pos = info.getLayoutPosition() != null ?
               info.getLayoutPosition() : viewsheet.getPixelPosition(info);
            Dimension size = info.getLayoutSize() != null ?
               info.getLayoutSize() : viewsheet.getPixelSize(info);

            VSAssemblyInfo targetInfo = (VSAssemblyInfo) target.getInfo();

            Dimension targetSize = targetInfo.getLayoutSize() != null ?
               targetInfo.getLayoutSize() : viewsheet.getPixelSize(targetInfo);

            int width = size.width > targetSize.width ? size.width: targetSize.width;

            Dimension updatedSize = new Dimension(width, size.height);

            info.setPixelSize(updatedSize);
            Point targetPos = new Point(pos.x, pos.y + size.height);
            target.setPixelOffset(targetPos);
            targetInfo.setZIndex(container.getZIndex());

            placeholderService.execute(rvs, container.getAbsoluteName(), linkUri,
                                       VSAssembly.VIEW_CHANGED, dispatcher);
         }
         else {
            VSAssemblyInfo info = (VSAssemblyInfo) target.getInfo();
            String tabName = AssetUtil.getNextName(viewsheet, AbstractSheet.TAB_ASSET);
            TabVSAssembly targetTab = new TabVSAssembly(viewsheet, tabName);
            VSAssemblyInfo targetTabInfo = (VSAssemblyInfo) targetTab.getInfo();
            targetTab.initDefaultFormat();
            viewsheet.addAssembly(targetTab);

            Point pos = info.getLayoutPosition() != null ?
               info.getLayoutPosition() : viewsheet.getPixelPosition(info);
            Dimension size = targetTabInfo.getLayoutSize() != null ?
               targetTabInfo.getLayoutSize() : viewsheet.getPixelSize(targetTabInfo);

            String[] tabs = {target.getAbsoluteName(), object.getAbsoluteName()};
            targetTab.setAssemblies(tabs);
            targetTab.setSelectedValue(tabs[1]);

            targetTab.setPixelOffset(pos);
            targetTab.initDefaultFormat();

            VSAssemblyInfo objectInfo = (VSAssemblyInfo) object.getInfo();
            VSAssemblyInfo targetInfo = (VSAssemblyInfo) target.getInfo();

            Dimension objectSize = objectInfo.getLayoutSize() != null ?
               objectInfo.getLayoutSize() : viewsheet.getPixelSize(objectInfo);

            Dimension targetSize = targetInfo.getLayoutSize() != null ?
               targetInfo.getLayoutSize() : viewsheet.getPixelSize(targetInfo);

            int width = objectSize.width > targetSize.width ?
               objectSize.width: targetSize.width;

            Dimension updatedSize = new Dimension(width, targetTab.getPixelSize().height);
            targetTab.setPixelSize(updatedSize);

            Point objectPos = new Point(pos.x, pos.y + size.height);
            objectInfo.setPixelOffset(objectPos);
            adjustTabZIndex(targetTabInfo, objectInfo, targetInfo);

            if(objectInfo.getLayoutPosition() != null) {
               objectInfo.setLayoutPosition(objectPos);
            }

            Point targetPos = new Point(pos.x, pos.y + size.height);
            targetInfo.setPixelOffset(targetPos);

            if(targetInfo.getLayoutPosition() != null) {
               targetInfo.setLayoutPosition(objectPos);
            }

            placeholderService.addDeleteVSObject(rvs, targetTab, dispatcher);
            placeholderService.execute(rvs, targetTab.getAbsoluteName(), linkUri,
                                       VSAssembly.VIEW_CHANGED, dispatcher);
         }
      }

      vsLayoutService.updateVSLayouts(rvs);
   }

   private void adjustTabZIndex(VSAssemblyInfo targetTabInfo,  VSAssemblyInfo objectInfo,
                                VSAssemblyInfo targetInfo)
   {
      int targetZIndex = targetInfo.getZIndex();
      int objectZIndex = objectInfo.getZIndex();
      int zIndex = targetZIndex > objectZIndex ? objectZIndex : targetZIndex;
      zIndex = zIndex <= 0 ? zIndex + 1 : zIndex;
      objectInfo.setZIndex(zIndex);
      targetInfo.setZIndex(zIndex);
      targetTabInfo.setZIndex(zIndex-1);
   }

   private void combineContainers(AbstractContainerVSAssembly object,
                                  AbstractContainerVSAssembly target, Viewsheet viewsheet)
   {
      String[] assemblies = target.getAssemblies();
      String[] addAssemblies = object.getAssemblies();

      List<String> targetAssemblies = new ArrayList<>(Arrays.asList(assemblies));
      List<String> objectAssemblies = new ArrayList<>(Arrays.asList(addAssemblies));

      VSAssemblyInfo objectInfo = (VSAssemblyInfo) object.getInfo();
      VSAssemblyInfo targetInfo = (VSAssemblyInfo) target.getInfo();

      Point pos = targetInfo.getLayoutPosition() != null ?
         targetInfo.getLayoutPosition() : viewsheet.getPixelPosition(targetInfo);
      Dimension size = targetInfo.getLayoutSize() != null ?
         targetInfo.getLayoutSize() : viewsheet.getPixelSize(targetInfo);


      Dimension objectSize = objectInfo.getLayoutSize() != null ?
         objectInfo.getLayoutSize() : viewsheet.getPixelSize(objectInfo);

      Dimension targetSize = targetInfo.getLayoutSize() != null ?
         targetInfo.getLayoutSize() : viewsheet.getPixelSize(targetInfo);

      int width = objectSize.width > targetSize.width ? objectSize.width: targetSize.width;

      Dimension updatedSize = new Dimension(width, defh);
      target.setPixelSize(updatedSize);

      for(String name: objectAssemblies) {
         VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

         VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();
         Point objectPos = new Point(pos.x, pos.y + size.height);
         info.setPixelOffset(objectPos);
         info.setZIndex(target.getZIndex());

         targetAssemblies.add(name);
      }

      target.setAssemblies(targetAssemblies.toArray(new String[targetAssemblies.size()]));

      viewsheet.removeAssembly(object.getName());
   }

   private final PlaceholderService placeholderService;
   private final VSLayoutService vsLayoutService;
}
