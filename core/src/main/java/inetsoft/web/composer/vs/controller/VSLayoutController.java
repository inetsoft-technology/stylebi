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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.*;
import inetsoft.web.composer.vs.dialog.ImagePreviewPaneController;
import inetsoft.web.composer.vs.event.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.DataTipInLayoutCheckResult;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.UpdateLayoutCommand;
import inetsoft.web.viewsheet.command.UpdateLayoutUndoStateCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that provides endpoints for viewsheet layout actions.
 */
@Controller
public class VSLayoutController {
   /**
    * Creates a new instance of <tt>VSLayoutController</tt>.
    */
   @Autowired
   public VSLayoutController(RuntimeViewsheetRef runtimeViewsheetRef,
                             CoreLifecycleService coreLifecycleService,
                             ViewsheetService viewsheetService,
                             VSObjectModelFactoryService objectModelService,
                             VSLayoutService vsLayoutService,
                             VSObjectTreeService vsObjectTreeService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.vsLayoutService = vsLayoutService;
      this.vsObjectTreeService = vsObjectTreeService;
   }

   /**
    * Get the info for selected layout.
    *
    * @param layoutName the name of the layout being retrieved.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/changeLayout/{layoutName}")
   public void changeViewsheetLayout(@DestinationVariable("layoutName") String layoutName,
                                     Principal principal,
                                     @LinkUri String linkUri,
                                     CommandDispatcher dispatcher)
      throws Exception
   {
      String id = this.runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      vsLayoutService.updateVSLayouts(rvs);
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      Catalog catalog = Catalog.getCatalog(principal);
      String currLayout = this.runtimeViewsheetRef.getFocusedLayoutName();

      if(catalog.getString("Master").equals(layoutName)) {
         ChangeCurrentLayoutCommand command = new ChangeCurrentLayoutCommand(null);
         dispatcher.sendCommand(command);
         coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
      }
      else {
         // Clone rvs so that we do not change state of Master rvs when creating layouts
         String rvsCloneID = viewsheetService.openTemporaryViewsheet(
            (AssetEntry) rvs.getEntry().clone(), principal, null);
         RuntimeViewsheet rvsClone = viewsheetService.getViewsheet(rvsCloneID, principal);
         // apply same parameters in layout as in master vs. (59025)
         rvsClone.getViewsheetSandbox().getAssetQuerySandbox().getVariableTable()
            .addAll(rvs.getViewsheetSandbox().getAssetQuerySandbox().getVariableTable());
         rvsClone.setOriginalID(id);

         vsLayoutService.findViewsheetLayout(viewsheet, layoutName).ifPresent(layout -> {
            AbstractLayout layoutClone = layout.clone();
            // Use scale font 1 when editing layouts.
            layoutClone.setScaleFont(1);
            rvsClone.setViewsheet(layoutClone.apply(viewsheet));
            // reset box for new layout vs
            rvsClone.getViewsheetSandbox().resetAll(new ChangedAssemblyList());

            if(!layoutName.equals(currLayout)) {
               rvs.resetLayoutUndoRedo();
               rvs.addLayoutCheckPoint(layoutClone);
            }

            vsLayoutService.sendLayout(rvsClone, layoutClone, dispatcher);
         });

         UpdateLayoutCommand updateLayoutCommand = UpdateLayoutCommand.builder()
            .layoutName(layoutName)
            .build();
         dispatcher.sendCommand(updateLayoutCommand);

         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         dispatcher.sendCommand(treeCommand);
      }
   }

   /**
    * Undo/revert to a previous viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/vs/layouts/undo/{runtimeId}")
   public void layoutUndo(@DestinationVariable("runtimeId") String runtimeId, Principal principal,
                          @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(runtimeId), principal);
      RuntimeViewsheet parentRvs = viewsheetService
         .getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      String layoutName = this.runtimeViewsheetRef.getFocusedLayoutName();

      if(layoutName != null && !Catalog.getCatalog().getString("Master").equals(layoutName)) {
         AbstractLayout layoutClone = (AbstractLayout) parentRvs.layoutUndo().clone();
         this.vsLayoutService.updateVSLayouts(parentRvs, layoutClone, layoutName);
         rvs.setViewsheet(layoutClone.apply(parentRvs.getViewsheet()));
         rvs.getViewsheetSandbox().reset(
            null, rvs.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true, true, null);
         vsLayoutService.sendLayout(rvs, layoutClone, dispatcher);
         //update client side layout points
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
         UpdateLayoutUndoStateCommand command = new UpdateLayoutUndoStateCommand();
         command.setLayoutPoint(parentRvs.getLayoutPoint());
         command.setLayoutPoints(parentRvs.getLayoutPointsSize());
         command.setId(parentRvs.getID());
         dispatcher.sendCommand(command);
      }
   }

   /**
    * Redo/change to a future viewsheet layout state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/vs/layouts/redo/{runtimeId}")
   public void layoutRedo(@DestinationVariable("runtimeId") String runtimeId, Principal principal,
                          @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(runtimeId), principal);
      RuntimeViewsheet parentRvs = viewsheetService
         .getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      String layoutName = this.runtimeViewsheetRef.getFocusedLayoutName();

      if(layoutName != null && !Catalog.getCatalog().getString("Master").equals(layoutName)) {
         AbstractLayout layoutClone = (AbstractLayout) parentRvs.layoutRedo().clone();
         this.vsLayoutService.updateVSLayouts(parentRvs, layoutClone, layoutName);
         rvs.setViewsheet(layoutClone.apply(parentRvs.getViewsheet()));
         rvs.getViewsheetSandbox().reset(
            null, rvs.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true, true, null);
         vsLayoutService.sendLayout(rvs, layoutClone, dispatcher);
         //update client side layout points
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
         UpdateLayoutUndoStateCommand command = new UpdateLayoutUndoStateCommand();
         command.setLayoutPoint(parentRvs.getLayoutPoint());
         command.setLayoutPoints(parentRvs.getLayoutPointsSize());
         command.setId(parentRvs.getID());
         dispatcher.sendCommand(command);
      }
   }

   /**
    * Move/Resize layout object.
    *
    * @param event      the event model.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/layouts/moveResizeObjects")
   public void moveResizeLayoutObjects(@Payload MoveResizeLayoutObjectsEvent event,
                                       Principal principal,
                                       CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet viewsheet = parentRvs.getViewsheet();

      String vslayoutName = event.layoutName();
      final boolean isPrintLayout = vsLayoutService.isPrintLayout(vslayoutName);
      AbstractLayout abstractLayout = vsLayoutService
         .findViewsheetLayout(viewsheet, vslayoutName)
         .orElse(null);
      List<VSAssemblyLayout> layouts =
         vsLayoutService.getVSAssemblyLayouts(abstractLayout, event.region());

      boolean refreshAll = false;
      String[] vsLayoutObjects = event.objectNames();
      List<String> changedAssemblies = new ArrayList<>();

      if(abstractLayout != null) {
         List<VSAssemblyLayout> newLayouts = new ArrayList<>();

         for(int i = 0; i < vsLayoutObjects.length; i++) {
            String layoutName = vsLayoutObjects[i];
            Optional<VSAssemblyLayout> layout =
               vsLayoutService.findAssemblyLayout(abstractLayout,
                                                  layoutName, event.region());

            if(layout.isPresent() && event.left()[i] >= 0 && event.top()[i] >= 0) {
               VSAssemblyLayout newLayout = layout.get();
               Point position = new Point(event.left()[i], event.top()[i]);
               Dimension size = new Dimension(event.width()[i], event.height()[i]);
               boolean move = !Tool.equals(position, newLayout.getPosition());
               newLayout.setPosition(position);
               newLayout.setSize(size);

               if(newLayout instanceof VSEditableAssemblyLayout) {
                  VSAssemblyInfo vsAssemblyInfo = ((VSEditableAssemblyLayout) newLayout).getInfo();
                  vsAssemblyInfo.setLayoutSize(size);
                  vsAssemblyInfo.setPixelSize(size);
                  vsAssemblyInfo.setLayoutPosition(position);
                  vsAssemblyInfo.setPixelOffset(position);
               }
               else {
                  VSAssembly vsAssembly = viewsheet.getAssembly(layoutName);

                  if(vsAssembly.getContainer() == null) {
                     addChangedAssemblies(vsAssembly, changedAssemblies);
                  }
               }

               newLayouts.add(newLayout);

               if(isPrintLayout) {
                  List<VSAssemblyLayout> sortedList = vsLayoutService.getSortAssemblyLayouts(layouts);
                  int action = move ? VSLayoutService.MOVE_ACTION : VSLayoutService.RESIZE_ACTION;
                  refreshAll = vsLayoutService.fixAssemblyLayoutsPosition(viewsheet,
                     (PrintLayout) abstractLayout, sortedList, newLayout, action) || refreshAll;
               }
            }
         }

         AbstractLayout layoutClone = abstractLayout.clone();
         // Use scale font 1 when editing layouts.
         layoutClone.setScaleFont(1);
         // Call apply layout after move/resize as it updates the underlying VSAssemblyInfo,
         // and handles special logic to update lines and containers correctly.
         Viewsheet vs2 = layoutClone.apply(viewsheet);
         rvs.setViewsheet(vs2);
         List<VSAssemblyLayout> refreshLayouts = !refreshAll ? newLayouts :
            vsLayoutService.getVSAssemblyLayouts(abstractLayout, event.region());
         refreshLayoutObjects(rvs, refreshLayouts, dispatcher, event.region());
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
         vsLayoutService.makeUndoable(parentRvs, dispatcher,
                                         this.runtimeViewsheetRef.getFocusedLayoutName());
      }
   }

   /**
    * Refresh the layout objects.
    * @param rvs        the runtime vs.
    * @param objLayouts the assembly layouts need to be refreshed.
    * @param dispatcher the command dispatcher.
    * @param region     the event region (HEADER/FOOTER/CONTENT).
    */
   private void refreshLayoutObjects(RuntimeViewsheet rvs, List<VSAssemblyLayout> objLayouts,
                                     CommandDispatcher dispatcher, int region)
   {
      if(objLayouts == null || objLayouts.size() == 0) {
         return;
      }

      objLayouts.forEach(layout -> {
         AddLayoutObjectCommand command = new AddLayoutObjectCommand();
         command.setObject(vsLayoutService.createObjectModel(rvs, layout, objectModelService));
         command.setRegion(region);
         dispatcher.sendCommand(command);
      });
   }

   /**
    * Drop object from component tree into layout.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/layouts/addObject")
   public void addObject(@Payload AddVSLayoutObjectEvent event,
                         Principal principal,
                         CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      final Viewsheet parentViewsheet = parentRvs.getViewsheet();
      final Viewsheet viewsheet = rvs.getViewsheet().clone();
      String layoutName = event.getLayoutName();
      final LayoutInfo layoutInfo = parentViewsheet.getLayoutInfo();
      final boolean isPrintLayout = vsLayoutService.isPrintLayout(layoutName);
      AbstractLayout vsLayout = vsLayoutService.getViewsheetLayout(layoutInfo, layoutName);
      List<VSAssemblyLayout> layouts =
         vsLayoutService.getVSAssemblyLayouts(vsLayout, event.getRegion());
      boolean refreshAll = true;
      Map<VSAssemblyLayout, VSAssembly> newLayouts = new HashMap<>();
      VSAssemblyLayout layout = null;

      for(String name : event.getNames()) {
         VSAssembly assembly = viewsheet.getAssembly(name);
         boolean existAssembly = assembly != null;

         if(!existAssembly) {
            assembly = vsLayoutService.createVSAssembly(event, vsLayout, viewsheet, name);
         }

         layout = vsLayoutService.createAssemblyLayout(event, viewsheet, name, assembly, existAssembly);
         layouts.add(layout);
         newLayouts.put(layout, assembly);

         if(isPrintLayout) {
            List<VSAssemblyLayout> sortedList = vsLayoutService.getSortAssemblyLayouts(layouts);
            refreshAll = vsLayoutService.fixAssemblyLayoutsPosition(viewsheet,
               (PrintLayout) vsLayout, sortedList, layout, VSLayoutService.ADD_ACTION) || refreshAll;
         }
      }

      vsLayoutService.setVSAssemblyLayouts(vsLayout, layouts, event.getRegion());

      if(isPrintLayout) {
         parentViewsheet.updateCSSFormat(null, null);
      }

      // Clone rvs so that we do not change state of Master rvs
      AbstractLayout layoutClone = vsLayout.clone();
      // Use scale font 1 when editing layouts.
      layoutClone.setScaleFont(1);
      Viewsheet vs = viewsheet;

      if(refreshAll) {
         vs = layoutClone.apply(viewsheet);
      }
      else {
         newLayouts.forEach((l, a) -> layoutClone.applyAssembly(a, l));
      }

      rvs.setViewsheet(vs);
      rvs.getViewsheetSandbox().reset(null, viewsheet.getAssemblies(),
         new ChangedAssemblyList(), true, true, null);
      List<VSAssemblyLayout> refreshLayouts = null;

      if(refreshAll) {
         refreshLayouts = vsLayoutService.getVSAssemblyLayouts(vsLayout, event.getRegion());
      }
      else {
         refreshLayouts = new ArrayList<>();
         refreshLayouts.addAll(newLayouts.keySet());
      }

      refreshLayoutObjects(rvs, refreshLayouts, dispatcher, event.getRegion());
      loadTableData(rvs, newLayouts.keySet(), dispatcher);
      runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      vsLayoutService.makeUndoable(
         parentRvs, dispatcher, runtimeViewsheetRef.getFocusedLayoutName());
   }

   /**
    * Load table data after adding layout for table assembly.
    * @param rvs           the runtime vs.
    * @param layouts       the new added object layouts.
    * @param dispatcher
    */
   private void loadTableData(RuntimeViewsheet rvs, Collection<VSAssemblyLayout> layouts,
                              CommandDispatcher dispatcher)
   {
      if(rvs == null || layouts == null || layouts.size() == 0) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      layouts.forEach(layout -> {
         Assembly assembly = vs.getAssembly(layout.getName());

         if(!(assembly instanceof TableDataVSAssembly)) {
            return;
         }

         try {
            BaseTableController.loadTableData(rvs, layout.getName(), 0, 0, 100, null, dispatcher);
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to load table data", e);
         }
      });
   }

   /**
    * Removes an object from the layout.
    *
    * @param event             the event model
    * @param principal         the principal
    * @param commandDispatcher the command dispatcher
    */
   @MessageMapping("/composer/vs/layouts/removeObjects")
   public void removeObject(@Payload RemoveVSLayoutObjectEvent event,
                            Principal principal,
                            CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(this.runtimeViewsheetRef.getRuntimeId(), principal);
      rvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      LayoutInfo layoutInfo = viewsheet.getLayoutInfo();
      String layoutName = event.layoutName();

      AbstractLayout viewsheetLayout;
      List<VSAssemblyLayout> layouts;
      List<VSAssemblyLayout> newLayouts = new ArrayList<>();
      int layoutType;

      if(Catalog.getCatalog().getString("Print Layout").equals(layoutName)) {
         PrintLayout printLayout = layoutInfo.getPrintLayout();
         viewsheetLayout = printLayout;

         if(event.region() == VSLayoutService.HEADER) {
            layouts = printLayout.getHeaderLayouts();
            layoutType = VSLayoutService.HEADER;
         }
         else if(event.region() == VSLayoutService.FOOTER) {
            layouts = printLayout.getFooterLayouts();
            layoutType = VSLayoutService.FOOTER;
         }
         else {
            layouts = printLayout.getVSAssemblyLayouts();
            layoutType = VSLayoutService.CONTENT;
         }
      }
      else {
         viewsheetLayout = layoutInfo.getViewsheetLayouts()
            .stream()
            .filter(l -> l.getName().equals(layoutName))
            .findFirst()
            .orElse(null);

         layouts = viewsheetLayout.getVSAssemblyLayouts();
         layoutType = VIEWSHEETLAYOUT;
      }

      if(layouts == null) {
         layouts = new ArrayList<>();
      }
      else {
         layouts = new ArrayList<>(layouts);
      }

      for(int i = 0; i < layouts.size(); i++) {
         VSAssemblyLayout assemblyLayout = layouts.get(i);

         if(!Arrays.asList(event.names()).contains(assemblyLayout.getName())) {
            newLayouts.add(layouts.get(i));
         }
      }

      switch(layoutType) {
      case VSLayoutService.HEADER:
         ((PrintLayout) viewsheetLayout).setHeaderLayouts(newLayouts);
         break;
      case VSLayoutService.CONTENT:
      case VIEWSHEETLAYOUT:
         viewsheetLayout.setVSAssemblyLayouts(newLayouts);
         break;
      case VSLayoutService.FOOTER:
         ((PrintLayout) viewsheetLayout).setFooterLayouts(newLayouts);
         break;
      }

      RemoveLayoutObjectsCommand command = RemoveLayoutObjectsCommand.builder()
         .layoutName(layoutName)
         .assemblies(event.names())
         .build();
      commandDispatcher.sendCommand(command);
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      vsLayoutService.makeUndoable(rvs, commandDispatcher, this.runtimeViewsheetRef.getFocusedLayoutName());
   }

   /**
    * Gets the top-level descriptor of a text component belonging to a layout.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param runtimeId  the runtime identifier of the viewsheet.
    *
    * @return the text descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/layouts/text-property-dialog/{region}/{layoutName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TextPropertyDialogModel getTextPropertyDialogModel(
      @PathVariable("region") int region, @PathVariable("layoutName") String layoutName,
      @RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      PrintLayout layout = vs.getLayoutInfo().getPrintLayout();
      TextPropertyDialogModel result = new TextPropertyDialogModel();

      vsLayoutService.findAssemblyLayout(layout, layoutName, region)
         .ifPresent(l -> {
            TextVSAssemblyInfo textAssemblyInfo =
               (TextVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo();

            TextGeneralPaneModel textGeneralPaneModel = result.getTextGeneralPaneModel();
            TextPaneModel textPaneModel = textGeneralPaneModel.getTextPaneModel();
            textPaneModel.setText(textAssemblyInfo.getTextValue());
            PaddingPaneModel padding = textGeneralPaneModel.getPaddingPaneModel();
            padding.setTop(textAssemblyInfo.getPadding().top);
            padding.setLeft(textAssemblyInfo.getPadding().left);
            padding.setBottom(textAssemblyInfo.getPadding().bottom);
            padding.setRight(textAssemblyInfo.getPadding().right);
         });

      return result;
   }

   /**
    * Sets the specified text assembly info for layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param value      the info model.
    */
   @MessageMapping("/composer/vs/layouts/text-property-dialog/{region}/{layoutName}")
   public void setTextPropertyDialogModel(@DestinationVariable("region") int region,
                                          @DestinationVariable("layoutName") String layoutName,
                                          @Payload TextPropertyDialogModel value,
                                          Principal principal,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      RuntimeViewsheet parentRvs = viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet vs = parentRvs.getViewsheet();
      PrintLayout layout = vs.getLayoutInfo().getPrintLayout();

      vsLayoutService.findAssemblyLayout(layout, layoutName, region)
         .ifPresent(l -> {
            TextVSAssemblyInfo textAssemblyInfo =
               (TextVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo();

            TextGeneralPaneModel generalPaneModel = value.getTextGeneralPaneModel();
            String text = generalPaneModel.getTextPaneModel().getText();
            textAssemblyInfo.setTextValue(text);
            PaddingPaneModel padding = generalPaneModel.getPaddingPaneModel();
            textAssemblyInfo.getPadding().top = padding.getTop();
            textAssemblyInfo.getPadding().left = padding.getLeft();
            textAssemblyInfo.getPadding().bottom = padding.getBottom();
            textAssemblyInfo.getPadding().right = padding.getRight();

            AddLayoutObjectCommand command = new AddLayoutObjectCommand();
            command.setObject(vsLayoutService.createObjectModel(parentRvs, l, objectModelService));
            command.setRegion(region);
            commandDispatcher.sendCommand(command);
            this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
            vsLayoutService.makeUndoable(parentRvs, commandDispatcher, this.runtimeViewsheetRef.getFocusedLayoutName());
         });
   }

   /**
    * Gets the top-level descriptor of an image object contained in a layout.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param runtimeId  the runtime identifier of the viewsheet.
    *
    * @return the image descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/layouts/image-property-dialog/{region}/{layoutName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ImagePropertyDialogModel getImagePropertyDialogModel(
      @PathVariable("region") int region, @PathVariable("layoutName") String layoutName,
      @RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet vs = parentRvs.getViewsheet();
      PrintLayout layout = vs.getLayoutInfo().getPrintLayout();

      ImageVSAssemblyInfo imageAssemblyInfo =
         vsLayoutService.findAssemblyLayout(layout, layoutName, region)
            .map(l -> (ImageVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo())
            .orElse(null);

      ImagePreviewPaneController imageController = new ImagePreviewPaneController();
      String imageValue = imageAssemblyInfo.getImageValue();
      int imageAlpha;

      try {
         imageAlpha = Integer.parseInt(imageAssemblyInfo.getImageAlphaValue());
      }
      catch(Exception e) {
         imageAlpha = 100;
      }

      ImagePreviewPaneModel imagePreviewPaneModel = ImagePreviewPaneModel.builder()
         .alpha(imageAlpha)
         .animateGifImage(imageAssemblyInfo.isAnimateGIF())
         .selectedImage(imageValue)
         .imageTree(imageController.getImageTree(rvs))
         .build();

      StaticImagePaneModel staticImagePaneModel = StaticImagePaneModel.builder()
         .imagePreviewPaneModel(imagePreviewPaneModel)
         .build();

      ImageGeneralPaneModel imageGeneralPaneModel = ImageGeneralPaneModel.builder()
         .staticImagePaneModel(staticImagePaneModel)
         .build();

      ImageScalePaneModel imageScalePaneModel = ImageScalePaneModel.builder()
         .scaleImageChecked(imageAssemblyInfo.isScaleImageValue())
         .maintainAspectRatio(imageAssemblyInfo.isMaintainAspectRatioValue())
         .tile(imageAssemblyInfo.isTileValue())
         .insets(imageAssemblyInfo.getScale9Value())
         .size(imageAssemblyInfo.getPixelSize())
         .build();

      ImageAdvancedPaneModel imageAdvancedPaneModel = ImageAdvancedPaneModel.builder()
         .imageScalePaneModel(imageScalePaneModel)
         .build();

      ImagePropertyDialogModel result = ImagePropertyDialogModel.builder()
         .imageGeneralPaneModel(imageGeneralPaneModel)
         .imageAdvancedPaneModel(imageAdvancedPaneModel)
         .build();

      return result;
   }

   /**
    * Sets the assembly info of an image layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param value      the info model.
    */
   @MessageMapping("/composer/vs/layouts/image-property-dialog/{region}/{layoutName}")
   public void setImagePropertyDialogModel(@DestinationVariable("region") int region,
                                           @DestinationVariable("layoutName") String layoutName,
                                           @Payload ImagePropertyDialogModel value,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet vs = parentRvs.getViewsheet();
      PrintLayout layout = vs.getLayoutInfo().getPrintLayout();

      Optional<VSAssemblyLayout> assemblyLayout =
         vsLayoutService.findAssemblyLayout(layout, layoutName, region);

      assemblyLayout
         .map(l -> (ImageVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo())
         .ifPresent(imageAssemblyInfo -> {
            ImageGeneralPaneModel imageGeneralPaneModel = value.imageGeneralPaneModel();
            StaticImagePaneModel staticImagePaneModel =
               imageGeneralPaneModel.staticImagePaneModel();
            ImagePreviewPaneModel imagePreviewPaneModel =
               staticImagePaneModel.imagePreviewPaneModel();
            ImageAdvancedPaneModel imageAdvancedPaneModel = value.imageAdvancedPaneModel();
            ImageScalePaneModel imageScalePaneModel = imageAdvancedPaneModel.imageScalePaneModel();

            if(imagePreviewPaneModel.selectedImage() != null) {
               imageAssemblyInfo.setImageValue(imagePreviewPaneModel.selectedImage());
               imageAssemblyInfo.setImageAlphaValue(Integer.toString(imagePreviewPaneModel.alpha()));

               if(imagePreviewPaneModel.selectedImage().startsWith(ImageVSAssemblyInfo.SKIN_IMAGE))
               {
                  imageAssemblyInfo.setMaintainAspectRatioValue(false);
                  imageAssemblyInfo.setScale9Value(new Insets(1, 1, 1, 1));
               }
            }

            imageAssemblyInfo.setScaleImageValue(imageScalePaneModel.scaleImageChecked());
            imageAssemblyInfo.setTileValue(imageScalePaneModel.tile());

            if(imageScalePaneModel.scaleImageChecked()) {
               imageAssemblyInfo.setMaintainAspectRatioValue(
                  imageScalePaneModel.maintainAspectRatio());

               if(!imageScalePaneModel.maintainAspectRatio()) {
                  Insets insets = new Insets(
                     imageScalePaneModel.top(), imageScalePaneModel.left(),
                     imageScalePaneModel.bottom(), imageScalePaneModel.right());
                  imageAssemblyInfo.setScale9Value(insets);
               }
            }
         });

      assemblyLayout
         .ifPresent(l -> {
            AddLayoutObjectCommand command = new AddLayoutObjectCommand();
            command.setObject(vsLayoutService.createObjectModel(parentRvs, assemblyLayout.get(),
                                                                objectModelService));
            command.setRegion(region);
            commandDispatcher.sendCommand(command);
            this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
            vsLayoutService.makeUndoable(parentRvs, commandDispatcher, this.runtimeViewsheetRef.getFocusedLayoutName());
         });
   }

   /**
    * Sets the assembly info of an image layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    */
   @MessageMapping("/composer/vs/layouts/table-layout-property-dialog/{region}/{layoutName}")
   public void setTableLayoutPropertyDialogModel(@DestinationVariable("region") int region,
                                             @DestinationVariable("layoutName") String layoutName,
                                             @Payload TableLayoutPropertyDialogModel model,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService
         .getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      RuntimeViewsheet parentRvs = rvs.getOriginalID() == null ? rvs :
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet vs = parentRvs.getViewsheet();
      PrintLayout layout = vs.getLayoutInfo().getPrintLayout();

      Optional<VSAssemblyLayout> assemblyLayout =
         vsLayoutService.findAssemblyLayout(layout, layoutName, region);

      if(assemblyLayout.isPresent()) {
         assemblyLayout.get().setTableLayout(model.tableLayout());
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
         vsLayoutService.makeUndoable(parentRvs, commandDispatcher,
            this.runtimeViewsheetRef.getFocusedLayoutName());
      }
   }

   @RequestMapping(
      value="/api/vs/layouts/check-assembly-in-layout/{layoutName}/{assemblyName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public DataTipInLayoutCheckResult checkAssemblyInLayout(@PathVariable("layoutName") String layoutName,
                                                           @PathVariable("assemblyName") String assemblyName,
                                                           @RemainingPath String runtimeId,
                                                           Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      AbstractLayout rvsLayout = rvs.getRuntimeVSLayout();
      Optional<AbstractLayout> viewsheetLayout = vsLayoutService.findViewsheetLayout(vs, layoutName);

      if("null".equals(layoutName)) {
         if(rvsLayout == null) {
            return DataTipInLayoutCheckResult.builder()
               .isAssemblyInLayout(true)
               .build();
         }
         else {
            viewsheetLayout = Optional.of(rvsLayout);
         }
      }

      if(viewsheetLayout.isPresent()) {
         Optional<VSAssemblyLayout> vsAssemblyLayout = vsLayoutService.findAssemblyLayout(
            viewsheetLayout.get(), assemblyName, VIEWSHEETLAYOUT);
         return DataTipInLayoutCheckResult.builder()
            .isAssemblyInLayout(vsAssemblyLayout.isPresent())
            .build();
      }

      return DataTipInLayoutCheckResult.builder()
         .isAssemblyInLayout(false)
         .build();
   }

   private void addChangedAssemblies(VSAssembly vsAssembly, List<String> changedAssemblies) {
      changedAssemblies.add(vsAssembly.getAbsoluteName());

      if(vsAssembly instanceof AbstractContainerVSAssembly) {
         Viewsheet viewsheet = vsAssembly.getViewsheet();

         for(String assembly : ((AbstractContainerVSAssembly) vsAssembly).getAssemblies())
         {
            VSAssembly child = (VSAssembly) viewsheet.getAssembly(assembly);

            if(child != null) {
               addChangedAssemblies(child, changedAssemblies);
            }
         }
      }
      else if(vsAssembly instanceof Viewsheet) {
         for(Assembly assembly : ((Viewsheet) vsAssembly).getAssemblies()) {
            if(assembly != null) {
               VSAssembly child = (VSAssembly) assembly;

               if(child.getContainer() == null) {
                  addChangedAssemblies(child, changedAssemblies);
               }
            }
         }
      }
   }

   public static final int VIEWSHEETLAYOUT = 4;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSObjectModelFactoryService objectModelService;
   private final VSLayoutService vsLayoutService;
   private final VSObjectTreeService vsObjectTreeService;
}
