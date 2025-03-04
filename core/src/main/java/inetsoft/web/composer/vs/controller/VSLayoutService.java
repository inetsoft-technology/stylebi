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

import inetsoft.graph.internal.DimensionD;
import inetsoft.report.Margin;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.VSLayoutModel;
import inetsoft.web.composer.model.vs.VSLayoutObjectModel;
import inetsoft.web.composer.vs.command.ChangeCurrentLayoutCommand;
import inetsoft.web.composer.vs.event.AddVSLayoutObjectEvent;
import inetsoft.web.viewsheet.command.UpdateLayoutUndoStateCommand;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;

/**
 * A service for updating all layouts of viewsheet when assemblies change.
 */
@Service
public class VSLayoutService {
   @Autowired
   public VSLayoutService(VSObjectModelFactoryService objectModelService) {
      this.objectModelService = objectModelService;
   }

   public boolean isPrintLayout(String layoutName) {
      return Catalog.getCatalog().getString("Print Layout").equals(layoutName);
   }

   public AbstractLayout getViewsheetLayout(LayoutInfo layoutInfo, String layoutName) {
      if(isPrintLayout(layoutName)) {
         return layoutInfo.getPrintLayout();
      }

      return layoutInfo.getViewsheetLayouts()
         .stream()
         .filter(l -> l.getName().equals(layoutName))
         .findFirst()
         .orElse(null);
   }

   public List<VSAssemblyLayout> getVSAssemblyLayouts(AbstractLayout layout, int region) {
      if(layout instanceof PrintLayout) {
         PrintLayout playout = (PrintLayout) layout;

         if(region == HEADER) {
            return playout.getHeaderLayouts();
         }
         else if(region == FOOTER) {
            return playout.getFooterLayouts();
         }
         else {
            return playout.getVSAssemblyLayouts();
         }
      }
      else if(layout != null) {
         return layout.getVSAssemblyLayouts();
      }

      return new ArrayList<>();
   }

   public void setVSAssemblyLayouts(AbstractLayout layout, List<VSAssemblyLayout> layouts,
                                    int region)
   {
      if(layout instanceof PrintLayout) {
         PrintLayout playout = (PrintLayout) layout;

         if(region == HEADER) {
            playout.setHeaderLayouts(layouts);
         }
         else if(region == FOOTER) {
            playout.setFooterLayouts(layouts);
         }
         else {
            playout.setVSAssemblyLayouts(layouts);
         }
      }
      else if(layout != null) {
         layout.setVSAssemblyLayouts(layouts);
      }
   }

   /**
    * Create vs assembly when dnd to add a new vs assembly on vs layout pane.
    */
   public VSAssembly createVSAssembly(AddVSLayoutObjectEvent event, AbstractLayout vsLayout,
                                      Viewsheet viewsheet, String name)
   {
      VSAssembly assembly = viewsheet.getAssembly(name);

      if(assembly != null) {
         return assembly;
      }

      Point position = new Point(event.getxOffset(), event.getyOffset());

      if(event.getType() == AbstractSheet.TEXT_ASSET) {
         assembly = new TextVSAssembly(viewsheet, name);
         ((TextVSAssembly) assembly).setValue("text");
         ((TextVSAssembly) assembly).setTextValue("text");
      }
      else if(event.getType() == AbstractSheet.PAGEBREAK_ASSET) {
         assembly = new PageBreakVSAssembly(viewsheet, name);
         Dimension size = getPrintPageSize((PrintLayout) vsLayout);
         assembly.getVSAssemblyInfo().setPixelSize(new Dimension(size.width, PAGE_BREAK_HEIGHT));
      }
      else {
         assembly = new ImageVSAssembly(viewsheet, name);
      }

      assembly.initDefaultFormat();
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      info.setPixelOffset(position);

      if(info.getPixelSize() == null ||
         (info.getPixelSize().height == 0 && info.getPixelSize().width == 0))
      {
         info.setPixelSize(new Dimension(70, 18));
      }

      return assembly;
   }

   /**
    * Create a assembly layout.
    * @param event       the AddVSLayoutObjectEvent.
    * @param viewsheet   the target viewsheet to add assembly layout.
    * @param name        name of the target assembly which to add assembly layout.
    * @param existAssembly  true if the target assembly originally exist on the viewsheet,
    *                       false if new create for vs layout.
    */
   public VSAssemblyLayout createAssemblyLayout(AddVSLayoutObjectEvent event, Viewsheet viewsheet,
                                                String name, VSAssembly assembly,
                                                boolean existAssembly)
   {
      Point position = new Point(event.getxOffset(), event.getyOffset());
      VSAssemblyLayout layout = null;
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      info.setLayoutVisible(assembly.isVisible() ? VSAssembly.ALWAYS_SHOW : VSAssembly.ALWAYS_HIDE);

      Dimension size = null;

      if(info instanceof TabVSAssemblyInfo) {
         size = getVSTabSize(viewsheet, assembly);
      }
      else if(assembly instanceof Viewsheet) {
         Viewsheet cloneAssembly = ((Viewsheet) assembly).clone();
         cloneAssembly.getVSAssemblyInfo().setLayoutVisible(VSAssembly.ALWAYS_SHOW);
         Rectangle bounds =
            cloneAssembly.getPreferredBounds(false, false, true);
         size = new Dimension(bounds.width, bounds.height);
      }
      else {
         size = viewsheet.getPixelSize(info);
      }

      if(!existAssembly) {
         layout = new VSEditableAssemblyLayout(info, name, position, size);
      }
      else {
         layout = new VSAssemblyLayout(name, position, size);
         boolean doubleCalendar = (assembly instanceof CalendarVSAssembly)
            && ((CalendarVSAssembly) assembly).getViewMode() == DOUBLE_CALENDAR_MODE;
         layout.setDoubleCalendar(doubleCalendar);
      }

      return layout;
   }

   private Dimension getVSTabSize(Viewsheet vs, VSAssembly object) {
      TabVSAssemblyInfo tinfo = (TabVSAssemblyInfo) object.getVSAssemblyInfo();
      String[] paneNames = tinfo.getAssemblies();
      int maxWidth = 0;
      int maxHeight = 0;

      for(String name: paneNames) {
         VSAssembly child = (VSAssembly) vs.getAssembly(name);

         if(child != null) {
            Dimension objsize = vs.getPixelSize(child.getVSAssemblyInfo());

            if(objsize.width > maxWidth) {
               maxWidth = objsize.width;
            }

            if(objsize.height > maxHeight) {
               maxHeight = objsize.height;
            }
         }
      }

      Dimension maxSize = new Dimension(maxWidth, maxHeight);
      Dimension size = vs.getPixelSize(tinfo);
      return new Dimension(Math.max(size.width, maxSize.width),
         size.height + maxSize.height);
   }

   /**
    * Update viewsheet layouts caused by grouping
    *
    * @param rvs runtime viewsheet
    */
   public final void updateVSLayouts(RuntimeViewsheet rvs) {
      Viewsheet viewsheet = rvs.getViewsheet();
      LayoutInfo layoutInfo = viewsheet.getLayoutInfo();

      //Update print layout
      PrintLayout printLayout = layoutInfo.getPrintLayout();

      if(printLayout != null) {
         List<VSAssemblyLayout> vsAssemblyPrintLayouts0 = printLayout.getVSAssemblyLayouts();
         List<VSAssemblyLayout> vsAssemblyPrintLayouts = getUpdatedVSAssemblyLayouts(
            viewsheet, vsAssemblyPrintLayouts0);
         printLayout.setVSAssemblyLayouts(vsAssemblyPrintLayouts);

         List<VSAssemblyLayout> vsHeaderLayouts0 = printLayout.getHeaderLayouts();
         List<VSAssemblyLayout> vsHeaderLayouts = getUpdatedVSAssemblyLayouts(
            viewsheet, vsHeaderLayouts0);
         printLayout.setHeaderLayouts(vsHeaderLayouts);

         List<VSAssemblyLayout> vsFooterLayouts0 = printLayout.getFooterLayouts();
         List<VSAssemblyLayout> vsFooterLayouts = getUpdatedVSAssemblyLayouts(
            viewsheet, vsFooterLayouts0);
         printLayout.setFooterLayouts(vsFooterLayouts);
      }

      //Update viewsheet layout
      List<ViewsheetLayout> viewsheetLayouts = layoutInfo.getViewsheetLayouts();

      for(ViewsheetLayout viewsheetLayout : viewsheetLayouts) {
         List<VSAssemblyLayout> vsAssemblyViewsheetLayouts0 = viewsheetLayout.getVSAssemblyLayouts();
         List<VSAssemblyLayout> vsAssemblyViewsheetLayouts = getUpdatedVSAssemblyLayouts(
            viewsheet, vsAssemblyViewsheetLayouts0);
         viewsheetLayout.setVSAssemblyLayouts(vsAssemblyViewsheetLayouts);
      }
   }

   /**
    * Update viewsheet layout from layout(printLayout / viewsheet layout)
    *
    * @param rvs runtime viewsheet
    * @param layout new layout
    * @param layoutName layout name of layout to update
    */
   public final void updateVSLayouts(RuntimeViewsheet rvs, AbstractLayout layout, String layoutName) {
      if(layout == null || layoutName == null) {
         return;
      }

      LayoutInfo layoutInfo = rvs.getViewsheet().getLayoutInfo();
      //update LayoutInfo
      if(Catalog.getCatalog().getString("Print Layout").equals(layoutName)) {
         layoutInfo.setPrintLayout((PrintLayout) layout);
      }
      else {
         List<ViewsheetLayout> viewsheetLayouts = layoutInfo.getViewsheetLayouts().stream()
            .map(viewsheetLayout -> viewsheetLayout.getName().equals(layoutName) ?
               (ViewsheetLayout) layout : viewsheetLayout)
            .collect(Collectors.toList());
         layoutInfo.setViewsheetLayouts(viewsheetLayouts);
      }
   }

   public final Optional<AbstractLayout> findViewsheetLayout(Viewsheet viewsheet, String name) {
      LayoutInfo info = viewsheet.getLayoutInfo();

      if(Catalog.getCatalog().getString("Print Layout").equals(name)) {
         return Optional.of(info.getPrintLayout());
      }
      else {
         return info.getViewsheetLayouts()
            .stream()
            .filter(l -> l.getName().equals(name))
            .findFirst()
            .map(l -> (AbstractLayout) l);
      }
   }

   public final Optional<VSAssemblyLayout> findAssemblyLayout(AbstractLayout layout,
                                                              String name, int region)
   {
      if(layout instanceof PrintLayout) {
         PrintLayout printLayout = (PrintLayout) layout;

         if(region == HEADER) {
            return printLayout.getHeaderLayouts()
               .stream()
               .filter(l -> l.getName().equals(name))
               .findAny();
         }
         else if(region == FOOTER) {
            return printLayout.getFooterLayouts()
               .stream()
               .filter(l -> l.getName().equals(name))
               .findAny();
         }
         else {
            return printLayout.getVSAssemblyLayouts()
               .stream()
               .filter(l -> l.getName().equals(name))
               .findAny();
         }
      }
      else {
         return layout.getVSAssemblyLayouts()
            .stream()
            .filter(l -> l.getName().equals(name))
            .findAny();
      }
   }

   public final void sendLayout(RuntimeViewsheet rvs, AbstractLayout layout,
                          CommandDispatcher dispatcher)
   {
      VSLayoutModel model;

      if(layout instanceof ViewsheetLayout) {
         model = VSLayoutModel.builder()
            .name(((ViewsheetLayout) layout).getName())
            .objects(getObjects(layout.getVSAssemblyLayouts(), rvs))
            .runtimeID(rvs.getID())
            .build();
      }
      else {
         PrintLayout printLayout = (PrintLayout) layout;
         PrintInfo info = printLayout.getPrintInfo();
         Margin margin = info.getMargin();
         DimensionD size = info.getSize();

         model = VSLayoutModel.builder()
            .name(Catalog.getCatalog().getString("Print Layout"))
            .objects(getObjects(layout.getVSAssemblyLayouts(), rvs))
            .printLayout(true)
            .unit(info.getUnit())
            .marginTop(margin.top)
            .marginBottom(margin.bottom)
            .marginLeft(margin.left)
            .marginRight(margin.right)
            .headerFromEdge(info.getHeaderFromEdge())
            .footerFromEdge(info.getFooterFromEdge())
            .width(size.getWidth())
            .height(size.getHeight())
            .headerObjects(getObjects(printLayout.getHeaderLayouts(), rvs))
            .footerObjects(getObjects(printLayout.getFooterLayouts(), rvs))
            .horizontal(printLayout.isHorizontalScreen())
            .runtimeID(rvs.getID())
            .build();
      }

      ChangeCurrentLayoutCommand command = new ChangeCurrentLayoutCommand(model);
      dispatcher.sendCommand(command);
   }

   public final VSLayoutObjectModel createObjectModel(RuntimeViewsheet rvs,
                                                      VSAssemblyLayout assemblyLayout,
                                                      VSObjectModelFactoryService objectModelService)
   {
      String name = assemblyLayout.getName();
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly0 = (VSAssembly) vs.getAssembly(name);
      VSAssembly assembly = assembly0 != null ? (VSAssembly) assembly0.clone() : null;
      List<VSObjectModel> childModels = new ArrayList<>();

      boolean editable = false;

      if(assemblyLayout instanceof VSEditableAssemblyLayout) {
         VSAssemblyInfo info = ((VSEditableAssemblyLayout) assemblyLayout).getInfo();

         if(info instanceof TextVSAssemblyInfo) {
            assembly = new TextVSAssembly(vs, assemblyLayout.getName());
            editable = true;
         }
         else if(info instanceof PageBreakVSAssemblyInfo) {
            assembly = new PageBreakVSAssembly(vs, assemblyLayout.getName());
         }
         else {
            assembly = new ImageVSAssembly(vs, assemblyLayout.getName());
            editable = true;
         }

         assembly.setVSAssemblyInfo(info);
      }

      VSObjectModel objectModel = null;

      if(assembly != null) {
         assembly.getInfo().setVisible(true);
         rvs.getViewsheet().getLayoutInfo().getPrintLayout();
         objectModel = objectModelService.createModel(assembly, rvs);

         if(assembly instanceof TabVSAssembly ||
            assembly instanceof GroupContainerVSAssembly)
         {
            // get child assemblies of layout object to render them only within the
            // layout object and not as editable layout objects
            getChildAssemblies((ContainerVSAssembly) assembly, rvs,
                               childModels, objectModelService);
         }
      }

      return VSLayoutObjectModel.builder()
         .editable(editable)
         .objectModel(objectModel)
         .childModels(childModels)
         .name(assemblyLayout.getName())
         .width(assemblyLayout.getSize().width)
         .height(assemblyLayout.getSize().height)
         .left(assemblyLayout.getPosition().x)
         .top(assemblyLayout.getPosition().y)
         .tableLayout(assemblyLayout.getTableLayout())
         .supportTableLayout(supportTableLayout(assembly))
         .build();
   }

   /**
    * Check if current assembly support editing tablelayout in printlayout.
    */
   private boolean supportTableLayout(VSAssembly assembly) {
      if(assembly == null) {
         return false;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

      if(!info.isVisible(true) || info.isEmbedded() && !info.isPrimary()) {
         return false;
      }

      Viewsheet vs = assembly.getViewsheet();

      if(assembly instanceof TabVSAssembly) {
         String selected = ((TabVSAssembly) assembly).getSelected();
         return supportTableLayout(vs.getAssembly(selected));
      }

      if(assembly instanceof Viewsheet) {
         Viewsheet embedded = (Viewsheet) assembly;
         Assembly[] assemblies = embedded.getAssemblies(true);

         return Arrays.stream(assemblies).filter(item -> supportTableLayout((VSAssembly) item))
            .findAny()
            .isPresent();
      }

      if(assembly instanceof GroupContainerVSAssembly) {
         String[] names = ((GroupContainerVSAssembly) assembly).getAssemblies();

         return Arrays.stream(names).filter(name -> supportTableLayout(vs.getAssembly(name)))
            .findAny()
            .isPresent();
      }

      return assembly instanceof TableDataVSAssembly;
   }

   /**
    * Sort the layout objects by ascending in y.
    */
   public void sortAssemblyLayouts(List<VSAssemblyLayout> layouts) {
      VSAssemblyLayoutComparator comparator = new VSAssemblyLayoutComparator();
      Collections.sort(layouts, comparator);
   }

   public List<VSAssemblyLayout> getSortAssemblyLayouts(List<VSAssemblyLayout> list) {
      List<VSAssemblyLayout> copy = new ArrayList<>();
      copy.addAll(list);
      Collections.sort(copy, new VSAssemblyLayoutComparator());
      return copy;
   }

   /**
    * Refresh position for assembly layouts of print layout after page size changed,
    * to make sure each page break works well.
    */
   public void refrestPrintLayoutObjs(Viewsheet vs, PrintLayout pLayout) {
      if(vs == null || pLayout == null) {
         return;
      }

      List<VSAssemblyLayout> layouts = pLayout.getVSAssemblyLayouts();
      List<VSAssemblyLayout> sortedLayouts = getSortAssemblyLayouts(layouts);
      List<VSAssemblyLayout> pageBreaks = getSortedPageBreaks(vs, sortedLayouts);

      if(pageBreaks == null || pageBreaks.size() == 0) {
         return;
      }

      for(int i = 0; i < pageBreaks.size(); i++) {
         fixAssemblyLayoutsPosition(vs, pLayout, sortedLayouts, pageBreaks.get(i), MOVE_ACTION);
      }
   }

   /**
    * Refresh the layout objects position after adding new assembly layout.
    * @param vs
    * @param pLayout        the current print layout.
    * @param addedLayout    the new added assembly layout.
    * @return true if the layout position be refreshed, else false.
    */
   public boolean fixAssemblyLayoutsPosition(Viewsheet vs, PrintLayout pLayout,
                                             List<VSAssemblyLayout> sortedLayouts,
                                             VSAssemblyLayout addedLayout, int action)
   {
      if(vs == null || pLayout == null || addedLayout == null ||
         isShapeLayoutObj(vs, addedLayout))
      {
         return false;
      }

      if(isPageBreak(addedLayout)) {
         return fixAssemblyLayoutsPosition0(vs, pLayout, sortedLayouts, addedLayout, action);
      }
      else {
         return fixAssemblyLayoutPosition(vs, pLayout, sortedLayouts, addedLayout, action);
      }
   }

   /**
    * Refresh position for layout objects which below the target page break.
    * @return true if the layout position be refreshed, else false.
    */
   private boolean fixAssemblyLayoutsPosition0(Viewsheet vs, PrintLayout pLayout,
                                               List<VSAssemblyLayout> sortedLayouts,
                                               VSAssemblyLayout pageBreak, int action)
   {
      Dimension pageSize = getPrintPageSize(pLayout);
      Rectangle forceBlankRegion = getForceBlankRegion(pageSize, pageBreak);
      int pageNum = getPageNumber(pageBreak, pageSize);
      boolean hasIntersect = false;
      int pushDownHeight = 0;

      for(int i = 0; i < sortedLayouts.size(); i++) {
         if(Tool.equals(sortedLayouts.get(i), pageBreak)) {
            continue;
         }

         if(isShapeLayoutObj(vs, sortedLayouts.get(i))) {
            continue;
         }

         int page = getPageNumber(sortedLayouts.get(i), pageSize);

         if(!hasIntersect && page > pageNum) {
            break;
         }

         // if page break is added under a page break, should push down the added page break.
         if(page == pageNum && isPageBreak(sortedLayouts.get(i)) &&
            sortedLayouts.get(i).getPosition().y <= pageBreak.getPosition().y)
         {
            return fixAssemblyLayoutPosition(vs, pLayout, sortedLayouts, pageBreak, action);
         }

         Rectangle rect = getAssemblyLayoutBounds(sortedLayouts.get(i));

         if(VSUtil.isIntersecting(forceBlankRegion, rect) && !hasIntersect) {
            hasIntersect = true;
            pushDownHeight = pushDownToNextPage(sortedLayouts.get(i), pageSize, MOVE_ACTION);
         }
         else if(hasIntersect) {
            pushDown(vs, sortedLayouts.get(i), pageSize, pushDownHeight);
         }

         // recursive process for the push down page breaks.
         if(hasIntersect && isPageBreak(sortedLayouts.get(i)) && i < sortedLayouts.size() - 1) {
            forcePushDown(vs, sortedLayouts, i + 1, pushDownHeight, pageSize);
            fixAssemblyLayoutsPosition(vs, pLayout, sortedLayouts, sortedLayouts.get(i), ADD_ACTION);
            break;
         }
      }

      return hasIntersect;
   }

   /**
    * Simple push down the target layouts by the specific y distance.
    */
   private void forcePushDown(Viewsheet vs, List<VSAssemblyLayout> sortedLayouts, int index,
                              int pushDownHeight, Dimension pageSize)
   {
      for(int i = index; i < sortedLayouts.size(); i++) {
         pushDown(vs, sortedLayouts.get(i), pageSize, pushDownHeight);
      }
   }

   /**
    * Fix the target layout object position, move down if it was intersect with page break caused
    * force blank page area, the move down will cause all the elements below this element move
    * down by specific y distance, and the y distance maybe update if any layout object need to
    * move down more when it cross two page after moving down.
    *
    *
    * @param vs
    * @param pLayout          the current print layout.
    * @param sortedLayouts    the vs assembly layouts which sorted by ascending in y.
    * @param currentLayout    the target assembly layout to fix position.
    * @return true if the layout position be refreshed, else false.
    */
   private boolean fixAssemblyLayoutPosition(Viewsheet vs, PrintLayout pLayout,
                                             List<VSAssemblyLayout> sortedLayouts,
                                             VSAssemblyLayout currentLayout, int action)
   {
      Dimension pageSize = getPrintPageSize(pLayout);
      Rectangle rect = getAssemblyLayoutBounds(currentLayout);

      if(isPageBreak(currentLayout)) {
         rect = getForceBlankRegion(pageSize, currentLayout);
      }

      int pageNum = getPageNumber(currentLayout, pageSize);
      List<VSAssemblyLayout> pageBreaks = getSortedPageBreaks(vs, sortedLayouts);
      Rectangle pRect = null;

      for(int i = 0; i < pageBreaks.size(); i++) {
         if(getPageNumber(pageBreaks.get(i), pageSize) != pageNum ||
            Tool.equals(pageBreaks.get(i), currentLayout))
         {
            continue;
         }

         pRect = getForceBlankRegion(pageSize, pageBreaks.get(i));

         if(!VSUtil.isIntersecting(pRect, rect)) {
            continue;
         }

         if(action == RESIZE_ACTION) {
            int pushHeight = currentLayout.getPosition().y + currentLayout.getSize().height
               + GAP - pageBreaks.get(i).getPosition().y;
            pushDown(vs, pageBreaks.get(i), pageSize, pushHeight);
            fixAssemblyLayoutsPosition0(vs, pLayout, sortedLayouts, pageBreaks.get(i), action);
         }
         else {
            pushDownCurrentLayout(vs, pLayout, sortedLayouts, pageBreaks,
               currentLayout, i, ADD_ACTION);
         }

         return true;
      }

      return false;
   }

   public void pushDownCurrentLayout(Viewsheet vs, PrintLayout pLayout,
                                     List<VSAssemblyLayout> sortedLayouts,
                                     List<VSAssemblyLayout> sortedPageBreaks,
                                     VSAssemblyLayout currentLayout,
                                     int intersectPageBreakIdx, int action)
   {
      Dimension pageSize = getPrintPageSize(pLayout);
      int pushDownHeight = pushDownToNextPage(currentLayout, pageSize, action);
      int idx = sortedLayouts.indexOf(currentLayout);

      if(idx == sortedLayouts.size() - 1) {
         return;
      }

      Rectangle curr_rect = getAssemblyLayoutBounds(currentLayout);
      boolean intersect = false;

      for(int j = idx + 1; j < sortedLayouts.size(); j++) {
         Rectangle rect = getAssemblyLayoutBounds(sortedLayouts.get(j));

         // if the other layouts already below the current layout, then stop the push down
         if(!intersect && curr_rect.y < rect.y && !VSUtil.isIntersecting(curr_rect, rect)) {
            break;
         }

         if(Tool.equals(sortedLayouts.get(j), sortedPageBreaks.get(intersectPageBreakIdx))) {
            continue;
         }

         pushDown(vs, sortedLayouts.get(j), pageSize, pushDownHeight);
         intersect = true;
      }

      if(intersectPageBreakIdx == sortedPageBreaks.size() - 1) {
         return;
      }

      for(int k = intersectPageBreakIdx + 1; k < sortedPageBreaks.size(); k++) {
         fixAssemblyLayoutsPosition0(vs, pLayout, sortedLayouts, sortedPageBreaks.get(k), action);
      }
   }

   /**
    * @return page breaks sorted by ascending in y.
    */
   public List<VSAssemblyLayout> getSortedPageBreaks(Viewsheet vs,
                                                     List<VSAssemblyLayout> sortedLayouts)
   {
      List<VSAssemblyLayout> list = new ArrayList<>();

      for(int i = 0; i < sortedLayouts.size(); i++) {
         if(isPageBreak(sortedLayouts.get(i))) {
            list.add(sortedLayouts.get(i));
         }
      }

      return list;
   }

   /**
    * Push target assembly to next print page.
    */
   public int pushDownToNextPage(VSAssemblyLayout layout, Dimension pageSize, int action) {
      int pageNum = getPageNumber(layout, pageSize);
      int targetY = (pageNum + 1) * pageSize.height + GAP;
      int old_y = layout.getPosition().y;
      layout.getPosition().y = targetY;
      int pushDownHeight = targetY - old_y;

      if(action == ADD_ACTION) {
         return layout.getSize().height + GAP;
      }

      return pushDownHeight;
   }

   /**
    * Push down the target layout by the target pushDownHeight, if target position cross two pages
    * continue to push down it to the next page and return the new pushDownHeight, and layout
    * objects below the current layout will use the new pushDownHeight to fix postion.
    *
    * @param layout         the target layout to push down.
    * @param pageSize       the print layout page size.
    * @param pushDownHeight the height to push down.
    * @return the new push down height.
    */
   public int pushDown(Viewsheet vs, VSAssemblyLayout layout,
                       Dimension pageSize, int pushDownHeight)
   {
      if(isShapeLayoutObj(vs, layout)) {
         return pushDownHeight;
      }

      Point pos = layout.getPosition();
      Dimension size = layout.getSize();
      int pageHeight = pageSize.height;

      int targetY = pos.y + pushDownHeight;
      int page1 = getPageNumber(targetY, pageHeight);
      int page2 = getPageNumber(targetY + size.height, pageHeight);

      if(isPageBreak(layout) && page2 > page1) {
         targetY = page2 * pageHeight + GAP;
         pushDownHeight = targetY - pos.y;
      }

      layout.getPosition().y = targetY;

      return pushDownHeight;
   }

   /**
    * @param y            the y position in print layout pane.
    * @param pageHeight   the print layout page height.
    * @return the page number of the target y.
    */
   public int getPageNumber(int y, int pageHeight) {
      if(pageHeight == 0) {
         return -1;
      }

      return (int) Math.ceil(y / pageHeight);
   }

   /**
    * @param layout   the target assembly layout.
    * @param pageSize the print layout page size.
    * @return the page number of the target assembly layout.
    */
   public int getPageNumber(VSAssemblyLayout layout, Dimension pageSize) {
      if(pageSize == null || pageSize.height == 0 || layout == null) {
         return -1;
      }

      Point pos = layout.getPosition();
      return (int) Math.ceil(pos.y / pageSize.height);
   }

   /**
    * @return if the target layout object is page break assembly.
    */
   public boolean isPageBreak(VSAssemblyLayout layout) {
      return layout instanceof VSEditableAssemblyLayout &&
         ((VSEditableAssemblyLayout) layout).getInfo() instanceof PageBreakVSAssemblyInfo;
   }

   public boolean isShapeLayoutObj(Viewsheet vs, VSAssemblyLayout layout) {
      return vs != null && layout != null &&
         vs.getAssembly(layout.getName()) instanceof ShapeVSAssembly;
   }

   /**
    * @return bounds for the target assembly layout.
    */
   public Rectangle getAssemblyLayoutBounds(VSAssemblyLayout layout) {
      if(layout == null) {
         return null;
      }

      Point pos = layout.getPosition();
      Dimension size = layout.getSize();

      return new Rectangle(pos.x, pos.y, size.width, size.height);
   }

   /**
    * @return the region bounds below the page break object in current page.
    */
   public Rectangle getForceBlankRegion(Dimension pageSize, VSAssemblyLayout pageBreak) {
      Point pos = pageBreak.getPosition();
      int pageNum = getPageNumber(pageBreak, pageSize);
      int height = (pageNum + 1) * pageSize.height - pos.y;

      return new Rectangle(pos.x, pos.y, pageSize.width, height);
   }

   private List<VSLayoutObjectModel> getObjects(List<VSAssemblyLayout> layouts,
                                                RuntimeViewsheet rvs)
   {
      if(layouts == null) {
         return new ArrayList<>();
      }

      return layouts.stream()
         .map(assembly -> createObjectModel(rvs, assembly, objectModelService))
         .collect(Collectors.toList());
   }

   private void getChildAssemblies(ContainerVSAssembly assembly, RuntimeViewsheet rvs,
                                   List<VSObjectModel> childModels,
                                   VSObjectModelFactoryService objectModelService)
   {
      Viewsheet vs = rvs.getViewsheet();
      String[] names = assembly.getAbsoluteAssemblies();

      for(String assemblyName : names) {
         VSAssembly childAssembly = (VSAssembly) vs.getAssembly(assemblyName);
         childModels.add(objectModelService.createModel(childAssembly, rvs));

         if(childAssembly instanceof TabVSAssembly ||
            childAssembly instanceof GroupContainerVSAssembly)
         {
            getChildAssemblies((ContainerVSAssembly) childAssembly, rvs,
                               childModels, objectModelService);
         }
      }
   }

   private List<VSAssemblyLayout> getUpdatedVSAssemblyLayouts(
      Viewsheet viewsheet,
      List<VSAssemblyLayout> vsAssemblyLayouts)
   {
      return vsAssemblyLayouts.stream()
         .filter(l -> viewsheet.getAssembly(l.getName()) != null &&
            ((VSAssembly) viewsheet.getAssembly(l.getName())).getContainer() == null ||
            l instanceof VSEditableAssemblyLayout)
         .collect(Collectors.toList());
   }

   public static Dimension getPrintPageSize(PrintLayout layout) {
      if(layout == null || layout.getPrintInfo() == null) {
         return null;
      }

      Margin margin = layout.getPrintInfo().getMargin();

      if(margin == null) {
         margin = new Margin();
      }

      Dimension pageSize = new Dimension();
      boolean horizontal = layout.isHorizontalScreen();
      String unit = layout.getPrintInfo().getUnit();
      double width = getPLayoutSize(layout.getPrintInfo().getSize().getWidth(), unit);
      double height = getPLayoutSize(layout.getPrintInfo().getSize().getHeight(), unit);
      double top = getPLayoutSize(margin.top, "inches");
      double left = getPLayoutSize(margin.left, "inches");
      double right = getPLayoutSize(margin.right, "inches");
      double bottom = getPLayoutSize(margin.bottom, "inches");

      width -= !horizontal ? left + right : top + bottom;
      height -= !horizontal ? top + bottom : left + right;

      if(horizontal) {
         pageSize.setSize(height, width);
      }
      else {
         pageSize.setSize(width, height);
      }

      return pageSize;
   }

   public static double getPLayoutSize(double asize, String unit) {
      double psize = asize;

      switch(unit) {
         case "inches":
            psize = asize * INCH_POINT;
            break;
         case "mm":
            psize = asize / INCH_MM * INCH_POINT;
            break;
         default:
            break;
      }

      return psize;
   }

   public void makeUndoable(RuntimeSheet rs, CommandDispatcher dispatcher,
                            String focusedLayoutName)
   {
      if(rs.isDisposed()) {
         return;
      }

      if(rs instanceof RuntimeViewsheet && focusedLayoutName != null &&
         !Catalog.getCatalog().getString("Master").equals(focusedLayoutName)) {
         RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
         LayoutInfo info = rvs.getViewsheet().getLayoutInfo();
         AbstractLayout abstractLayout;

         if(Catalog.getCatalog().getString("Print Layout").equals(focusedLayoutName)) {
            abstractLayout =  info.getPrintLayout();
         }
         else {
            abstractLayout = info.getViewsheetLayouts()
               .stream()
               .filter(l -> l.getName().equals(focusedLayoutName))
               .findFirst()
               .orElse(null);
         }

         rvs.addLayoutCheckPoint(abstractLayout);

         UpdateLayoutUndoStateCommand command = new UpdateLayoutUndoStateCommand();
         command.setLayoutPoint(rvs.getLayoutPoint());
         command.setLayoutPoints(rvs.getLayoutPointsSize());
         command.setId(rs.getID());
         dispatcher.sendCommand(command);
      }
      else {
         rs.addCheckpoint(rs.getSheet().prepareCheckpoint());

         UpdateUndoStateCommand command = new UpdateUndoStateCommand();
         command.setPoints(rs.size());
         command.setCurrent(rs.getCurrent());
         command.setSavePoint(rs.getSavePoint());
         command.setId(rs.getID());
         dispatcher.sendCommand(command);
      }
   }

   public void removeLayoutAssembly(Viewsheet vs, String aname) {
      LayoutInfo layoutInfo = vs.getLayoutInfo();
      PrintLayout printLayout = layoutInfo.getPrintLayout();
      List<ViewsheetLayout> viewsheetLayouts = layoutInfo.getViewsheetLayouts();
      Predicate<VSAssemblyLayout> assemblyLayoutPredicate = v -> v.getName().equals(aname);

      if(printLayout != null) {
         List<VSAssemblyLayout> headerLayouts = printLayout.getHeaderLayouts();
         List<VSAssemblyLayout> footerLayouts = printLayout.getFooterLayouts();
         List<VSAssemblyLayout> printVSAssemblyLayouts = printLayout.getVSAssemblyLayouts();

         Stream.<List<VSAssemblyLayout>>builder()
            .add(headerLayouts).add(footerLayouts).add(printVSAssemblyLayouts).build()
            .forEach(l -> l.removeIf(assemblyLayoutPredicate));
      }

      if(viewsheetLayouts != null) {
         for(ViewsheetLayout viewsheetLayout : viewsheetLayouts) {
            List<VSAssemblyLayout> vsAssemblyLayouts = viewsheetLayout.getVSAssemblyLayouts();

            if(vsAssemblyLayouts != null) {
               vsAssemblyLayouts.removeIf(assemblyLayoutPredicate);
            }
         }
      }
   }

   private final VSObjectModelFactoryService objectModelService;
   private static final int GAP = 20;
   public static final int HEADER = 0;
   public static final int CONTENT = 1;
   public static final int FOOTER = 2;
   public static final int PAGE_BREAK_HEIGHT = 20;
   private static final double INCH_POINT = 72;
   private static final double INCH_MM = 25.4;
   public static final int ADD_ACTION = 0;
   public static final int MOVE_ACTION = 1;
   public static final int RESIZE_ACTION = 2;
}
