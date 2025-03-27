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
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.event.CopyVSObjectsEvent;
import inetsoft.web.composer.vs.objects.command.ChangeVSSelectionTitleCommand;
import inetsoft.web.composer.vs.objects.command.ForceEditModeCommand;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static inetsoft.uql.asset.internal.AssetUtil.defh;
import static inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
import static inetsoft.uql.viewsheet.internal.VSAssemblyInfo.TITLEPATH;

@Service
@ClusterProxy
public class ComposerObjectService {

   public ComposerObjectService(VSObjectTreeService vsObjectTreeService,
                                CoreLifecycleService coreLifecycleService,
                                ViewsheetService engine,
                                VSAssemblyInfoHandler assemblyHandler,
                                VSObjectModelFactoryService objectModelService,
                                VSObjectService vsObjectService,
                                VSCompositionService vsCompositionService)
   {
      this.vsObjectTreeService = vsObjectTreeService;
      this.coreLifecycleService = coreLifecycleService;
      this.engine = engine;
      this.assemblyHandler = assemblyHandler;
      this.objectModelService = objectModelService;
      this.vsObjectService = vsObjectService;
      this.vsCompositionService = vsCompositionService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addNewObject(@ClusterProxyKey String vsId, AddNewVSObjectEvent event, Principal principal,
                            CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Point position = new Point(event.getxOffset(), event.getyOffset());

      if(event.getType() == AbstractSheet.VIEWSHEET_ASSET) {
         addEmbeddedViewsheet(event.getEntry(), rvs, position, principal, dispatcher, linkUri);
         return null;
      }

      VSAssembly assembly = VSEventUtil.createVSAssembly(rvs, event.getType());
      assert assembly != null;

      assembly.getVSAssemblyInfo().setPixelOffset(position);
      viewsheet.addAssembly(assembly);

      this.coreLifecycleService.addDeleteVSObject(rvs, assembly, dispatcher);
      BaseTableController.loadTableData(rvs, assembly.getAbsoluteName(), 0, 0, 100,
                                        linkUri, dispatcher);

      AssemblyRef[] vrefs = viewsheet.getViewDependings(assembly.getAssemblyEntry());

      for(AssemblyRef aref: vrefs) {
         coreLifecycleService.refreshVSAssembly(rvs, aref.getEntry().getAbsoluteName(), dispatcher);
      }

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);

      if(event.isForceEditMode()) {
         ForceEditModeCommand forceEditModeCommand = ForceEditModeCommand
            .builder()
            .select(true)
            .editMode(true)
            .build();
         dispatcher.sendCommand(assembly.getAbsoluteName(), forceEditModeCommand);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeObject(@ClusterProxyKey String vsId, ResizeVSObjectEvent event,
                            Principal principal, CommandDispatcher dispatcher,
                            String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly assembly = viewsheet.getAssembly(event.getName());
      Dimension size = new Dimension(event.getWidth(), event.getHeight());
      Point position = new Point(Math.max(0, event.getxOffset()), Math.max(0, event.getyOffset()));

      if(assembly instanceof VSAssembly) {
         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();
         Dimension originalSize = new Dimension(info.getLayoutSize() != null ?
                                                   info.getLayoutSize() : viewsheet.getPixelSize(info));

         if(info.getLayoutSize() != null) {
            info.setLayoutSize(size);
         }

         info.setPixelSize(size);

         if(assembly instanceof LineVSAssembly) {
            LineVSAssembly line = (LineVSAssembly) assembly;

            // horizontal
            if(size.height <= 1) {
               line.setStartPos(new Point(size.width, 0));
            }
            // vertical
            else if(size.width <= 1) {
               line.setStartPos(new Point(0, size.height));
            }
            else {
               line.setStartPos(new Point(size.width, size.height));
            }
         }

         if(assembly instanceof CurrentSelectionVSAssembly) {
            String[] children = ((CurrentSelectionVSAssembly) assembly).getAssemblies();

            for(String child : children) {
               Assembly childAssembly = viewsheet.getAssembly(child);

               if(childAssembly == null) {
                  continue;
               }

               VSAssemblyInfo childInfo = ((VSAssembly) childAssembly).getVSAssemblyInfo();
               Dimension childSize = childInfo.getLayoutSize() != null ?
                  childInfo.getLayoutSize() : viewsheet.getPixelSize(childInfo);
               Dimension newSize = new Dimension(size.width, childSize.height);

               if(childInfo.getLayoutSize() != null) {
                  childInfo.setLayoutSize(newSize);
               }

               childInfo.setPixelSize(newSize);
            }
         }

         if(assembly instanceof SelectionListVSAssembly) {
            SelectionListVSAssembly selectionList = (SelectionListVSAssembly) assembly;
            SelectionListVSAssemblyInfo selectionListInfo =
               (SelectionListVSAssemblyInfo) selectionList.getInfo();

            if(event.getHeight() > selectionListInfo.getTitleHeight()) {
               selectionListInfo.setListHeight((event.getHeight() -
                  selectionListInfo.getTitleHeight()) / defh);
            }
         }

         //Fixed bug #19625 that reset vs-table border, should be setExplicitTableWidthValue
         if(assembly instanceof TableVSAssembly) {
            TableDataVSAssembly table = (TableDataVSAssembly) assembly;
            TableDataVSAssemblyInfo dataInfo = (TableDataVSAssemblyInfo) table.getInfo();
            dataInfo.setExplicitTableWidthValue(true);
         }

         if(assembly instanceof TabVSAssembly) {
            TabVSAssembly tab = (TabVSAssembly) assembly;
            int ychange = event.getHeight() - originalSize.height;

            if(ychange != 0) {
               this.updateContainerChildrenYChange(tab, viewsheet, ychange);
            }
         }

         move(viewsheet, position, (VSAssembly) assembly);
         ChangedAssemblyList clist = this.coreLifecycleService.createList(false, dispatcher, rvs,
                                                                          linkUri);
         this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                                   info.getAbsoluteName(), clist);
         this.coreLifecycleService.refreshVSAssembly(rvs, assembly.getAbsoluteName(), dispatcher);
         this.coreLifecycleService.loadTableLens(rvs, assembly.getAbsoluteName(), linkUri, dispatcher);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveObjects(@ClusterProxyKey String vsId, MultiMoveVsObjectEvent multiEvent,
                           Principal principal, CommandDispatcher dispatcher,String linkUri)
      throws Exception
   {

      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      List<String> assemblies = Arrays.stream(multiEvent.getEvents())
         .map(MoveVSObjectEvent::getName)
         .collect(Collectors.toList());

      assemblyHandler.updateAnchoredLines(rvs, assemblies, dispatcher);

      return null;
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveObject(@ClusterProxyKey String vsId, MoveVSObjectEvent event,
                          Principal principal, CommandDispatcher dispatcher,
                          String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());
      Point position = new Point(event.getxOffset(), event.getyOffset());

      if(assembly instanceof LineVSAssembly) {
         LineVSAssemblyInfo lineInfo = (LineVSAssemblyInfo) assembly.getInfo();
         lineInfo.setStartAnchorID(null);
         lineInfo.setEndAnchorID(null);
      }

      if(assembly instanceof VSAssembly) {
         move(viewsheet, position, assembly);

         if(assembly instanceof SelectionListVSAssembly) {
            int type = ((SelectionListVSAssembly) assembly).getShowType();

            if(type == DROPDOWN_SHOW_TYPE) {
               Dimension size = assembly.getPixelSize();
               assembly.setPixelSize(new Dimension(size.width, defh));
            }
         }

         // if container assembly, re-layout viewsheet to refresh children
         if(assembly instanceof ContainerVSAssembly) {
            ChangedAssemblyList clist = this.coreLifecycleService.createList(
               false, dispatcher, rvs, linkUri);
            this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                                      assembly.getAbsoluteName(), clist);
         }

         if(assembly.getContainer() != null) {
            if(assembly.getContainer() instanceof GroupContainerVSAssembly) {
               ((GroupContainerVSAssembly) assembly.getContainer()).updateGridSize();
            }

            this.coreLifecycleService.refreshVSAssembly(
               rvs, assembly.getContainer().getAbsoluteName(), dispatcher);
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void copyObject(@ClusterProxyKey String vsId, CopyVSObjectsEvent event,
                          Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      //Logic to copy object.
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      List<Assembly> assemblies = new ArrayList<>();

      for(String assemblyName: event.getObjects()) {
         Assembly assembly = viewsheet.getAssembly(assemblyName);
         if(assembly != null && assembly instanceof VSAssembly) {
            VSAssembly vsAssembly = (VSAssembly) assembly;
            //Avoid duplicate if both container and children are selected in copy.
            boolean containerInvolved = false;

            if(vsAssembly.getContainer() != null) {
               containerInvolved = Arrays.stream(event.getObjects()).anyMatch(name -> name.equals(vsAssembly.getContainer().getName()));
            }

            if(!containerInvolved) {
               assemblies.add(vsAssembly);
            }

            addChildren(viewsheet, assemblies, vsAssembly);
         }
      }

      // Logic to paste object.
      if(assemblies == null) {
         return null;
      }

      List<Assembly> nassemblies = new ArrayList<>();

      for(Assembly assembly: assemblies) {
         nassemblies.add((Assembly) assembly.clone());
      }

      pasteAssemblies(vsId, event, principal, dispatcher, assemblies);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeObjectTitle(@ClusterProxyKey String vsId, ResizeVSObjectTitleEvent event,
                                 Principal principal, CommandDispatcher dispatcher,
                                 String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly assembly = viewsheet.getAssembly(event.getName());
      int titleHeight = event.getTitleHeight();

      if(assembly instanceof TitledVSAssembly) {
         ((TitledVSAssemblyInfo) assembly.getInfo()).setTitleHeightValue(titleHeight);
      }

      resizeObject(vsId, event, principal, dispatcher, linkUri);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeSelectedObjects(@ClusterProxyKey String vsId, RemoveVSObjectsEvent event,
                                     String linkUri, Principal principal,
                                     CommandDispatcher dispatcher) throws Exception
   {
      final RuntimeViewsheet rvs =
         this.engine.getViewsheet(vsId, principal);
      final Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null) {
         return null;
      }

      if(event.objectNames().length == 1 && event.objectNames()[0] != null) {
         removeObject(vsId, event.objectNames()[0], linkUri, principal, dispatcher);
         return null;
      }

      try {
         box.lockWrite();
         boolean getGrayedOutFields = false;
         List<VSAssembly> assemblies = new ArrayList<>();

         for(String objectName : event.objectNames()) {
            final VSAssembly assembly = (VSAssembly) vs.getAssembly(objectName);

            if(assembly == null) {
               continue;
            }

            assemblies.add(assembly);

            if(assembly instanceof CurrentSelectionVSAssembly ||
               assembly instanceof GroupContainerVSAssembly)
            {
               final ContainerVSAssembly container = (ContainerVSAssembly) assembly;

               for(final String childName : container.getAssemblies()) {
                  final VSAssembly child = (VSAssembly) vs.getAssembly(childName);

                  if(child != null) {
                     assemblies.add(child);
                  }
               }
            }
            else if(assembly instanceof TableVSAssembly || assembly instanceof ChartVSAssembly ||
               assembly instanceof SelectionVSAssembly || assembly instanceof ListInputVSAssembly)
            {
               getGrayedOutFields = true;
            }
         }

         this.coreLifecycleService.removeVSAssemblies(rvs, linkUri, dispatcher, false, true, true,
                                                      assemblies.toArray(new VSAssembly[0]));

         if(getGrayedOutFields) {
            assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         }
      }
      finally {
         box.unlockWrite();
      }

      final VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      final PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeObject(@ClusterProxyKey String vsId, String objectName, String linkUri, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      final RuntimeViewsheet rvs =
         this.engine.getViewsheet(vsId, principal);
      final Viewsheet vs = rvs.getViewsheet();
      final VSAssembly assembly = (VSAssembly) vs.getAssembly(objectName);

      if(assembly == null) {
         return null;
      }

      // First remove assembly
      coreLifecycleService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, true);

      // Next if the assembly is a Selection or Group Container remove its children
      if(assembly instanceof CurrentSelectionVSAssembly ||
         assembly instanceof GroupContainerVSAssembly)
      {
         final ContainerVSAssembly container = (ContainerVSAssembly) assembly;

         for(final String childName : container.getAssemblies()) {
            final VSAssembly child = (VSAssembly) vs.getAssembly(childName);

            if(child != null) {
               coreLifecycleService.removeVSAssembly(rvs, linkUri, child, dispatcher, false, true);
            }
         }
      }

      if(assembly instanceof TableVSAssembly || assembly instanceof ChartVSAssembly ||
         assembly instanceof SelectionVSAssembly ||
         assembly instanceof ListInputVSAssembly)
      {
         assemblyHandler.getGrayedOutFields(rvs, dispatcher);
      }

      final VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      final PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ComposerObjectController.DependentAssemblies checkAssemblyInUse(@ClusterProxyKey String runtimeId,
                                                                          String[] objectNames,
                                                                          Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = this.engine.getViewsheet(runtimeId, principal);
      final Viewsheet vs = rvs.getViewsheet();
      Map<String, String> dependentAssembliesMap = new HashMap<>();

      for(String objectName : objectNames) {
         Assembly assembly = vs.getAssembly(objectName);
         String dependingObjects = null;

         if(assembly instanceof GroupContainerVSAssembly) {
            GroupContainerVSAssembly group = (GroupContainerVSAssembly) assembly;

            for(String child : group.getAssemblies()) {
               dependingObjects = getDependingObjects(vs, vs.getAssembly(child),
                                                      objectNames);
            }
         }
         else {
            dependingObjects = getDependingObjects(vs, assembly, objectNames);
         }

         if(dependingObjects != null) {
            dependentAssembliesMap.put(objectName, dependingObjects);
         }
      }

      return new ComposerObjectController.DependentAssemblies(dependentAssembliesMap);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeZIndex(@ClusterProxyKey String vsId, ChangeVSObjectLayerEvent event, Principal principal,
                            CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());
      VSAssembly switchAssembly = (VSAssembly) viewsheet.getAssembly(event.getSwitchWithObject());

      if(switchAssembly != null && assembly != null) {
         final int zIndex = assembly.getZIndex();
         assembly.setZIndex(switchAssembly.getZIndex());
         switchAssembly.setZIndex(zIndex);
      }
      else if(assembly != null) {
         assembly.setZIndex(event.getzIndex());
      }

      this.vsCompositionService.shrinkZIndex(viewsheet, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeTitle(@ClusterProxyKey String vsId, ChangeVSObjectTextEvent event, Principal principal,
                           CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TitledVSAssembly assembly = (TitledVSAssembly) viewsheet.getAssembly(event.getName());
      VSAssemblyInfo assemblyInfo = assembly != null
         ? ((VSAssembly) assembly).getVSAssemblyInfo() : null;
      TitledVSAssemblyInfo titledInfo = (TitledVSAssemblyInfo) assemblyInfo;

      // for a adhoc filter (range slider), change title without commit, then drag the slider
      // to clear the range. it will cause the range slider to be removed AND the title
      // changed to be committed at the same time, and cause the assembly not found. (50559)
      if(assemblyInfo == null) {
         return null;
      }

      String oldTitle = assembly.getTitleValue();
      assembly.setTitleValue(event.getText());

      if(WizardRecommenderUtil.isTempAssembly(((VSAssembly) assembly).getName())) {
         VSTemporaryInfo vsTemporaryInfo = rvs.getVSTemporaryInfo();
         vsTemporaryInfo.setDescription(event.getText());
      }

      // clear out message format (51057)
      VSCompositeFormat titleFmt = assemblyInfo.getFormatInfo().getFormat(TITLEPATH);

      if(titleFmt != null) {
         titleFmt.getUserDefinedFormat().setFormatValue(null);
      }

      // force to set title visible to true when edit title in viewsheet wizard.
      if(((VSAssembly) assembly).isWizardTemporary()) {
         titledInfo.setTitleVisibleValue(true);
      }

      if(assembly instanceof SelectionListVSAssembly ||
         assembly instanceof SelectionTreeVSAssembly)
      {
         ChangeVSSelectionTitleCommand command =
            new ChangeVSSelectionTitleCommand(oldTitle, event.getText());
         dispatcher.sendCommand(command);
      }
      else {
         TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
         FormatInfo fmtInfo = assemblyInfo.getFormatInfo();
         VSCompositeFormat titleFormat = fmtInfo.getFormat(titlepath, false);

         // if format is defined for title, need to apply format to the new value
         if(titleFormat.getFormat() != null) {
            this.coreLifecycleService.refreshVSAssembly(rvs, (VSAssembly) assembly, dispatcher);
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeLockState(@ClusterProxyKey String vsId, LockVSObjectEvent event,
                               Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly)viewsheet.getAssembly(event.getName());

      assert assembly != null;

      if(assembly instanceof ImageVSAssembly) {
         ((ImageVSAssembly) assembly).setLocked(event.isLocked());
      }
      else if(assembly instanceof ShapeVSAssembly) {
         ((ShapeVSAssembly) assembly).setLocked(event.isLocked());
      }

      this.coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void moveFromContainer(@ClusterProxyKey String runtimeId, MoveVSObjectEvent event, Principal principal,
                                 CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      final RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final String name = event.getName();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      box.lockWrite();

      try {
         final VSAssembly assembly = viewsheet.getAssembly(name);
         final Point position = new Point(event.getxOffset(), event.getyOffset());
         final ContainerVSAssembly container = (ContainerVSAssembly) assembly.getContainer();

         if(container == null) {
            return null;
         }

         //check if moved inside group or selection container
         if(!(container instanceof TabVSAssembly)) {
            final ContainerVSAssemblyInfo containerInfo =
               (ContainerVSAssemblyInfo) container.getVSAssemblyInfo();
            final Point containerPosition = vsObjectService.getPosition(containerInfo);
            final Dimension containerSize = vsObjectService.getSize(containerInfo);
            final Rectangle containerBounds = new Rectangle(containerPosition, containerSize);

            if(containerBounds.contains(position)) {
               //if moved inside group then move object to new position
               if(container instanceof GroupContainerVSAssembly) {
                  moveObject(runtimeId, event, principal, dispatcher, linkUri);
               }
               else {
                  //if moved inside selection container then refresh object to reset position
                  RefreshVSObjectCommand refresh = new RefreshVSObjectCommand();
                  refresh.setInfo(objectModelService.createModel(assembly, rvs));
                  dispatcher.sendCommand(container.getAbsoluteName(), refresh);
               }

               return null;
            }
         }

         //remove assembly from container and move to new location
         container.removeAssembly(name);
         moveObject(runtimeId, event, principal, dispatcher, linkUri);

         if(container instanceof CurrentSelectionVSAssembly) {
            assembly.initDefaultFormat();
         }

         //Bug #15998 should refresh the moved assembly
         RefreshVSObjectCommand refreshMovedAssembly = new RefreshVSObjectCommand();
         refreshMovedAssembly.setInfo(objectModelService.createModel(assembly, rvs));
         dispatcher.sendCommand(refreshMovedAssembly);

         if(container instanceof TabVSAssembly || container instanceof GroupContainerVSAssembly) {
            //if only 1 assembly left in container, remove container and refresh last assembly
            if(container.getAssemblies().length == 1) {
               String lastAssemblyName = container.getAssemblies()[0];
               VSAssembly lastAssembly = viewsheet.getAssembly(lastAssemblyName);
               coreLifecycleService.removeVSAssembly(rvs, linkUri, container, dispatcher, false, true);

               // If the last assembly is a group container, refresh it's children to fix visibility
               if(lastAssembly instanceof GroupContainerVSAssembly) {
                  VSAssembly childAssembly = null;

                  for(String childName : ((GroupContainerVSAssembly) lastAssembly).getAssemblies()) {
                     childAssembly = viewsheet.getAssembly(childName);
                     RefreshVSObjectCommand refreshChild = new RefreshVSObjectCommand();
                     refreshChild.setInfo(objectModelService.createModel(childAssembly, rvs));
                     dispatcher.sendCommand(refreshChild);
                  }
               }

               VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
               PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
               dispatcher.sendCommand(treeCommand);
               return null;
            }
            //if tab assembly with more children, set new selected tab and refresh vs
            else if(container instanceof TabVSAssembly) {
               ((TabVSAssemblyInfo) container.getVSAssemblyInfo())
                  .setSelectedValue(container.getAssemblies()[0]);
               coreLifecycleService.execute(rvs, container.getAbsoluteName(), linkUri,
                                            VSAssembly.VIEW_CHANGED, dispatcher);
               return null;
            }
            else if(container instanceof GroupContainerVSAssembly) {
               ((GroupContainerVSAssembly) container).updateGridSize();
            }
         }
         else if(container instanceof CurrentSelectionVSAssembly &&
            ((CurrentSelectionVSAssembly) container).getShowCurrentSelectionValue()) {
            ((CurrentSelectionVSAssembly) container).updateOutSelection();
         }

         // refresh container so it has correct children
         RefreshVSObjectCommand refresh = new RefreshVSObjectCommand();
         refresh.setInfo(objectModelService.createModel(container, rvs));
         dispatcher.sendCommand(refresh);
         dispatcher.flush();
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   /**
    * Get the depending objects. From GetDependingVSAssemblyEvent.java
    */
   private String getDependingObjects(Viewsheet vs, Assembly assembly,
                                      String[] deleteNames)
   {
      boolean isSelectionTree = assembly instanceof SelectionTreeVSAssembly;

      if(!(assembly instanceof InputVSAssembly || isSelectionTree)) {
         return null;
      }

      HashSet<String> set = new HashSet<>();
      String name = assembly.getAssemblyEntry().getAbsoluteName();
      AssemblyRef[] refs = vs.getDependings(assembly.getAssemblyEntry(), true);
      AssemblyRef[] vrefs = vs.getViewDependings(
         assembly.getAssemblyEntry(), true);

      if(!isSelectionTree) {
         set.addAll(getRefNames(vs, refs, name, deleteNames));
      }

      set.addAll(getRefNames(vs, vrefs, name, deleteNames));

      if(!set.isEmpty()) {
         StringBuilder builder = new StringBuilder();

         set.forEach(dependentName -> {
            if(builder.length() != 0) {
               builder.append(", ");
            }

            builder.append(dependentName);
         });

         return builder.toString();
      }

      return null;
   }

   /**
    * Get the AssemblyRef names. From GetDependingVSAssemblyEvent.java
    */
   private ArrayList<String> getRefNames(Viewsheet vs, AssemblyRef[] refs,
                                         String self, String[] deleteNames) {
      ArrayList<String> names = new ArrayList<>();
      String name;

      for(AssemblyRef ref : refs) {
         name = ref.getEntry().getAbsoluteName();

         if(name != null && !name.equals(self) && !inDelete(deleteNames, name) &&
            !isChildAssembly(vs, name, self))
         {
            if(name.endsWith("_O")) {
               name = VSUtil.stripOuter(name);
            }

            names.add(name);
         }
      }

      return names;
   }

   /**
    * Check if the name is part of the deleted objects.
    */
   private boolean inDelete(String[] deletedNames, String name) {
      for(String deletedName : deletedNames) {
         if(name.equals(deletedName)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check whether current assembly is in a container assembly.
    */
   private boolean isChildAssembly(Viewsheet vs, String name, String self) {
      Assembly assembly = vs.getAssembly(name);

      if(!(assembly instanceof ContainerVSAssembly)) {
         return false;
      }

      ContainerVSAssembly container = (ContainerVSAssembly) assembly;
      boolean child = false;

      for(String childName : container.getAssemblies()) {
         if(childName.equals(self)) {
            child = true;
            break;
         }
      }

      if(!child) {
         return false;
      }

      if(container instanceof GroupContainerVSAssembly) {
         return true;
      }

      // one at a time for tab
      if(container instanceof TabVSAssembly) {
         // if tab only have 2 elements, after this element removed,
         // the tab will be removed, so no need to check it
         return container.getAssemblies().length <= 2;
      }

      return false;
   }

   /**
    * Get the upperLeft point of a list of assemblies.
    * @return the upperLeft position.
    */
   private Point getUpperLeftPosition(List<Assembly> assemblies) {
      Point upperLeft = null;

      if(assemblies != null) {
         for(int i = 0; i < assemblies.size(); i++) {
            Assembly assembly = assemblies.get(i);

            if(assembly instanceof VSAssembly) {
               VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();
               Point pos = info.getPixelOffset();
               Dimension size = info.getPixelSize();

               if(upperLeft == null) {
                  upperLeft = (Point) pos.clone();
               }
               else {
                  upperLeft.x = Math.min(upperLeft.x, pos.x);
                  upperLeft.y = Math.min(upperLeft.y, pos.y);
               }
            }
         }
      }

      if(upperLeft == null) {
         upperLeft = new Point(0, 0);
      }

      return upperLeft;
   }

   /**
    * Upload image from the source viewsheet to target viewsheet.
    */
   private void uploadImg(ImageVSAssembly image, Viewsheet vs, Viewsheet srcvs) {
      String img = image.getImage();

      if(img != null && img.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
         img = img.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length());
         vs.addUploadedImage(img, srcvs.getUploadedImageBytes(img));
      }
   }

   private void pasteAssemblies(String vsId, CopyVSObjectsEvent event, Principal principal,
                                CommandDispatcher dispatcher, List<Assembly> assemblies) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      int dx = event.getxOffset();
      int dy = event.getyOffset();
      Point upperLeft = getUpperLeftPosition(assemblies);
      Point moveOffset = null;

      if(event.isRelative() && !assemblies.isEmpty()) {
         Point opos = assemblies.get(0).getInfo().getPixelOffset();
         moveOffset = new Point(dx - opos.x, dy - opos.y);
      }

      for(int i = 0; i < assemblies.size(); ) {
         Assembly assembly = assemblies.get(i);
         i += pasteAssembly(assemblies, viewsheet, dx, dy, moveOffset, i, assembly);
      }

      for(int i = 0; i < assemblies.size(); i++) {
         Assembly vsAssembly = assemblies.get(i);
         this.coreLifecycleService.addDeleteVSObject(rvs, (VSAssembly) vsAssembly, dispatcher);
         this.coreLifecycleService.refreshVSAssembly(rvs, (VSAssembly) vsAssembly, dispatcher);
         this.coreLifecycleService.loadTableLens(rvs, vsAssembly.getAbsoluteName(), null,
                                                 dispatcher);
      }

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
   }

   private int pasteAssembly(List<Assembly> assemblies, Viewsheet viewsheet, int dx, int dy,
                             Point moveOffset, int i, Assembly assembly)
   {
      int n = 1;

      if(assembly instanceof VSAssembly) {
         VSAssembly vsAssembly = (VSAssembly) assembly.clone();
         Point position = new Point(dx, dy);

         if(vsAssembly.getAssemblyType() == Viewsheet.IMAGE_ASSET) {
            ImageVSAssembly image = (ImageVSAssembly) vsAssembly;
            uploadImg(image, viewsheet, vsAssembly.getViewsheet());
         }

         if(vsAssembly.getContainer() != null &&
            assemblies.stream().anyMatch(a -> a.getName().equals(vsAssembly.getContainer().getName()))) {
            return n;
         }

         if(moveOffset != null) {
            Point opos = vsAssembly.getVSAssemblyInfo().getPixelOffset();
            position = new Point(opos.x + moveOffset.x, opos.y + moveOffset.y);
         }

         String name = AssetUtil.getNextName(viewsheet, vsAssembly.getAssemblyType());
         vsAssembly.getVSAssemblyInfo().setName(name);
         vsAssembly.getVSAssemblyInfo().setPixelOffset(position);
         viewsheet.addAssembly(vsAssembly);
         assemblies.set(i, vsAssembly);

         n += pastChildren(assemblies, viewsheet, moveOffset != null ? moveOffset.x : dx,
                           moveOffset != null ? moveOffset.y : dy, i, vsAssembly);
      }

      return n;
   }

   private static int pastChildren(List<Assembly> assemblies, Viewsheet viewsheet, int dx, int dy,
                                   int i, VSAssembly vsAssembly)
   {
      if(vsAssembly instanceof ContainerVSAssembly) {
         ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
         String[] children = container.getAbsoluteAssemblies();
         String[] newChildren = new String[children.length];
         int selected = -1;
         int nested = 0;

         for(int j = 0; j < children.length; j++) {
            VSAssembly childAssembly = (VSAssembly) assemblies.get(i + j + nested + 1).clone();

            if(container instanceof TabVSAssembly &&
               ((TabVSAssembly) container).getSelectedValue()
                  .equals(childAssembly.getAbsoluteName()))
            {
               selected = j;
            }

            String newName = AssetUtil.getNextName(viewsheet, childAssembly.getAssemblyType());
            childAssembly.getVSAssemblyInfo().setName(newName);
            childAssembly.getPixelOffset().translate(dx, dy);
            viewsheet.addAssembly(childAssembly);
            newChildren[j] = childAssembly.getAbsoluteName();
            assemblies.set(i + j + nested + 1, childAssembly);

            int n = pastChildren(assemblies, viewsheet, dx, dy, i + j + nested + 1, childAssembly);
            nested += n;
         }

         container.setAssemblies(newChildren);

         if(container instanceof TabVSAssembly) {
            ((TabVSAssembly) container).setSelectedValue(newChildren[selected]);
         }

         return children.length + nested;
      }

      return 0;
   }


   private void move(Viewsheet viewsheet, Point position, VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Point originalPosition = info.getLayoutPosition() != null ?
         info.getLayoutPosition() : viewsheet.getPixelPosition(info);
      ContainerVSAssembly container = (ContainerVSAssembly) assembly.getContainer();

      if(assembly instanceof AbstractContainerVSAssembly ||
         // if an assembly inside a container is moved, just move the position within
         // the container instead of the entire container. this allows a user to
         // adjust component position within a container without first ungroup, move,
         // and then group again.
         container != null && !(container instanceof GroupContainerVSAssembly))
      {
         int xchange = position.x - originalPosition.x;
         int ychange = position.y - originalPosition.y;
         boolean moveParent= container != null && !(container instanceof GroupContainerVSAssembly);

         if(container instanceof TabVSAssembly) {
            ContainerVSAssemblyInfo containerInfo =
               (ContainerVSAssemblyInfo) container.getVSAssemblyInfo();

            if(containerInfo.getLayoutPosition() != null) {
               containerInfo.getLayoutPosition().translate(xchange, ychange);
            }

            containerInfo.getPixelOffset().translate(xchange, ychange);
         }
         else if(!moveParent) {
            if(info.getLayoutPosition() != null) {
               info.setLayoutPosition(position);
            }

            info.setPixelOffset(position);
         }

         moveContainer(viewsheet, moveParent ? container : (ContainerVSAssembly) assembly,
                       xchange, ychange);
      }
      else {
         if(info.getLayoutPosition() != null) {
            info.setLayoutPosition(position);
         }

         info.setPixelOffset(position);
      }
   }

   private void moveContainer(Viewsheet viewsheet, ContainerVSAssembly container,
                              int xchange, int ychange)
   {
      String[] children = container.getAssemblies();

      for(String child : children) {
         VSAssembly childAssembly = viewsheet.getAssembly(child);

         if(childAssembly == null) {
            continue;
         }

         VSAssemblyInfo childInfo = childAssembly.getVSAssemblyInfo();

         if(childInfo.getLayoutPosition() != null) {
            childInfo.getLayoutPosition().translate(xchange, ychange);
         }

         childInfo.getPixelOffset().translate(xchange, ychange);

         if(childAssembly instanceof ContainerVSAssembly) {
            moveContainer(viewsheet, (ContainerVSAssembly) childAssembly, xchange, ychange);
         }
      }
   }




   private static void addChildren(Viewsheet viewsheet, List<Assembly> assemblies, VSAssembly vsAssembly) {
      if(vsAssembly instanceof ContainerVSAssembly) {
         ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
         String[] names = container.getAbsoluteAssemblies();

         Arrays.stream(names).forEach((name) -> {
            Assembly child = viewsheet.getAssembly(name);
            assemblies.add(child);

            addChildren(viewsheet, assemblies, (VSAssembly) child);
         });
      }
   }


   private void updateContainerChildrenYChange(AbstractContainerVSAssembly containerVSAssembly, Viewsheet viewsheet,
                                               int ychange)
   {
      String[] assemblies = containerVSAssembly.getAssemblies();

      if(assemblies == null) {
         return;
      }

      for(String child : assemblies) {
         Assembly childAssembly = viewsheet.getAssembly(child);

         if(childAssembly == null) {
            continue;
         }

         VSAssemblyInfo childInfo = ((VSAssembly) childAssembly).getVSAssemblyInfo();

         if(childInfo.getLayoutPosition() != null) {
            childInfo.getLayoutPosition().translate(0, ychange);
         }

         childInfo.getPixelOffset().translate(0, ychange);

         if(childAssembly instanceof GroupContainerVSAssembly) {
            this.updateContainerChildrenYChange((GroupContainerVSAssembly) childAssembly, viewsheet, ychange);
         }
      }
   }

   /**
    * Add new embedded viewsheet in composer.
    */
   private void addEmbeddedViewsheet(AssetEntry entry, RuntimeViewsheet rvs, Point position,
                                    Principal principal, CommandDispatcher dispatcher,
                                    String linkUri)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();

      assert entry.isViewsheet() && !entry.isVSSnapshot();

      AssetRepository engine = this.engine.getAssetRepository();
      Viewsheet assembly = (Viewsheet)
         engine.getSheet(entry, principal, true, AssetContent.ALL);
      VSEventUtil.syncEmbeddedTableVSAssembly(assembly);
      String name = AssetUtil.getNextName(viewsheet, assembly.getAssemblyType());
      assembly = assembly.createVSAssembly(name);
      assembly.getVSAssemblyInfo().setPixelOffset(position);
      assembly.setEntry(entry);
      assembly.initDefaultFormat();
      viewsheet.addAssembly(assembly);

      rvs.initViewsheet(assembly, false);

      coreLifecycleService.refreshEmbeddedViewsheet(rvs, linkUri, dispatcher);
      coreLifecycleService.addDeleteVSObject(rvs, assembly, dispatcher);
      coreLifecycleService.initTable(rvs, dispatcher, linkUri, assembly.getAssemblies(true, false));
      coreLifecycleService.refreshVSAssembly(rvs, assembly, dispatcher);
      vsCompositionService.shrinkZIndex(assembly, dispatcher);

      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);
   }

   private final VSObjectTreeService vsObjectTreeService;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService engine;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final VSObjectModelFactoryService objectModelService;
   private final VSObjectService vsObjectService;
   private final VSCompositionService vsCompositionService;
}
