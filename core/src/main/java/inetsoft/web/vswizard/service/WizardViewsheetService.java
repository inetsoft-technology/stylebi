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
package inetsoft.web.vswizard.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import inetsoft.web.vswizard.command.RefreshNewObjectPositionCommand;
import inetsoft.web.vswizard.command.SetWizardGridCommand;
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Service
public class WizardViewsheetService {
   @Autowired
   public WizardViewsheetService(ViewsheetService viewsheetService,
                                 PlaceholderService placeholderService)
   {
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
   }

   public void refreshWizardViewsheet(String runtimeId, String linkUri, Principal principal,
                                      CommandDispatcher commandDispatcher)
           throws Exception
   {
      RuntimeViewsheet rtv = this.viewsheetService.getViewsheet(runtimeId, principal);

      if(rtv == null) {
         return;
      }

      ChangedAssemblyList clist = placeholderService.createList(true, commandDispatcher, rtv,
         linkUri);
      placeholderService.refreshViewsheet(rtv, rtv.getID(), linkUri, commandDispatcher, false,
         true, true, clist);
   }

   public void refreshWizardViewsheet(RuntimeViewsheet rvs, Viewsheet ovs,
                                      List<Assembly> editedObjs, String linkUri,
                                      Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      if(rvs == null) {
         return;
      }

      if(editedObjs == null) {
         editedObjs = new ArrayList<>();
      }

      Viewsheet vs = rvs.getViewsheet();
      Assembly[] assemblies = vs.getAssemblies();

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      for(Assembly assembly : assemblies) {
         Assembly oassembly = ovs != null ? ovs.getAssembly(assembly.getAbsoluteName()) : null;
         Point opoint = oassembly != null ? oassembly.getPixelOffset() : null;
         Dimension osize = oassembly != null ? oassembly.getPixelSize() : null;

         // don't need to refresh the uninfluenced assemblies.
         if(oassembly == null || !Tool.equals(osize, assembly.getPixelSize()) ||
            (!Tool.equals(opoint, assembly.getPixelOffset()) && !editedObjs.contains(assembly)))
         {
            placeholderService.refreshVSAssembly(rvs, (VSAssembly) assembly, commandDispatcher);
         }
      }
   }

   public Assembly[] getAssemblies(RuntimeViewsheet rvs, boolean sort) {
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly[] assemblies = viewsheet.getAssemblies();

      if(sort) {
         Arrays.sort(assemblies, new PositionComparator());
      }

      return assemblies;
   }

   public Point updateGridRowsAndNewBlock(Assembly[] assemblies, CommandDispatcher commandDispatcher)
   {
      if(assemblies == null) {
         return new Point(-1, -1);
      }

      int maxY = 0;
      int maxX = 0;

      for(Assembly assembly : assemblies) {
         Rectangle bounds = assembly.getBounds();

         maxY = Math.max(maxY, bounds.y + bounds.height);
         maxX = Math.max(maxX, bounds.x + bounds.width);
      }

      int maxRows = Math.max(GRID_ROW, maxY / GRID_CELL_HEIGHT);
      int maxCols = Math.max(GRID_COLUMN, maxX / GRID_CELL_WIDTH);
      Point newPosition = updateNewBlockPosition(assemblies, maxRows, maxCols, commandDispatcher);

      if(newPosition.y + NEW_BLOCK_ROW_COUNT > maxRows) {
         maxRows = newPosition.y + NEW_BLOCK_ROW_COUNT;
      }

      SetWizardGridCommand command = new SetWizardGridCommand();
      command.setGridRowCount(maxRows);
      command.setGridColCount(maxCols);
      commandDispatcher.sendCommand(command);

      return new Point(maxCols, maxRows);
   }

   /**
    * update the layout of viewsheet.
    * @param rvs runtime viewsheet.
    * @param obj assembly that causes overwrite
    * @param allObjs all assemblies.
    * @param rowCounts grid row count.
    * @param commandDispatcher
    * @throws Exception
    */
   public void layoutWizardViewsheet(RuntimeViewsheet rvs, Assembly obj, Assembly[] allObjs,
                                     int rowCounts, int colCount, Rectangle newRectangle,
                                     boolean autoLayoutH, boolean moveRowOrCol, String linkUri,
                                     Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet ovs = rvs.getViewsheet().clone();
      editAssembly(rvs, obj, allObjs, new ArrayList<>(), rowCounts, colCount, newRectangle,
         autoLayoutH, moveRowOrCol, linkUri, principal, commandDispatcher);
      refreshWizardViewsheet(rvs, ovs, null, linkUri, principal, commandDispatcher);
   }

   /**
    * update the layout of viewsheet.
    * @param rvs runtime viewsheet.
    * @param objs assembly that causes overwrite
    * @param allObjs all assemblies.
    * @param rowCounts grid row count.
    * @param commandDispatcher
    * @throws Exception
    */
   public void layoutWizardViewsheet(RuntimeViewsheet rvs, List<Assembly> objs, Assembly[] allObjs,
                                     int rowCounts, int colCounts,
                                     Map<Assembly, Rectangle> newRectangles, boolean autoLayoutH,
                                     boolean moveRowOrCol, String linkUri, Principal principal,
                                     CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet ovs = rvs.getViewsheet().clone();
      objs.sort(new PositionComparator());
      int currentRow = rowCounts;

      if(objs.size() > 1) {
         moveRowOrCol = false;
      }

      for(int i = 0; i < objs.size(); i++) {
         Assembly obj = objs.get(i);
         Rectangle oldBounds = new Rectangle(obj.getBounds());
         Rectangle newRectangle = newRectangles.get(obj);
         currentRow = editAssembly(rvs, obj, allObjs, objs, currentRow, colCounts, newRectangle,
            autoLayoutH, moveRowOrCol, linkUri, principal, commandDispatcher);
      }

      refreshWizardViewsheet(rvs, ovs, objs, linkUri, principal, commandDispatcher);
   }

   public int editAssembly(RuntimeViewsheet rvs, Assembly obj, Assembly[] allObjs,
                           List<Assembly> keeps, int rowCounts, int colCounts, Rectangle newRectangle,
                           boolean autoLayoutH, boolean moveRowOrCol, String linkUri,
                           Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      Rectangle oldBounds = new Rectangle(obj.getBounds());
      PositionComparator comparator = new PositionComparator();
      final Map<Assembly, Point> fixPositionMap = new HashMap<>();
      boolean containsBase = true;

      if(newRectangle != null) {
         if(newRectangle.width == 0 || newRectangle.height == 0) {
            deleteAssembly(rvs, obj, rowCounts, newRectangle, fixPositionMap);
            containsBase = false;
         }
         else if(!obj.getPixelOffset().equals(newRectangle.getLocation())) {
            if(moveRowOrCol) {
               moveAllRowOrCol(rvs, obj, rowCounts, colCounts, newRectangle, autoLayoutH, linkUri, principal,
                  dispatcher);
            }
            else {
               Point newRowAndCol = moveAssembly(rvs, obj, rowCounts, colCounts, newRectangle, keeps, autoLayoutH,
                  fixPositionMap);
               rowCounts = newRowAndCol.y;
               colCounts = newRowAndCol.x;

            }
         }
         else if(!obj.getPixelSize().equals(newRectangle.getSize())){
            Point newRowAndCol = resizeAssembly(rvs, obj, rowCounts, colCounts, newRectangle,
               autoLayoutH, fixPositionMap);
            rowCounts = newRowAndCol.y;
            colCounts = newRowAndCol.x;
         }

         comparator.setAssembly(obj);
         comparator.setDy(newRectangle.y - oldBounds.y);
         allObjs = sortAssemblies(allObjs, obj, containsBase, comparator);
      }
      else {
         Arrays.sort(allObjs, comparator);
      }

      // reorder assemblies the to layout them.
      adjustAssembliesPosition(rvs, allObjs, keeps, rowCounts, colCounts, fixPositionMap);

      return rowCounts;
   }

   private Point resizeAssembly(RuntimeViewsheet rvs, Assembly obj, int rowCounts, int colCounts,
                              Rectangle newRectangle, boolean autoLayoutH,
                              Map<Assembly, Point> fixPositionMap)
   {
      Assembly[] allObjs =  getAssemblies(rvs, true);
      Rectangle oldBounds = new Rectangle(obj.getBounds());
      obj.setBounds(newRectangle);
      int dw = newRectangle.width - oldBounds.width;
      int dh = newRectangle.height - oldBounds.height;

      if(dw > 0) {
         // pull right the covered assemblies if the right space is enough
         Assembly[] xSorted = getAssemblies(rvs, false);
         Comparator comparator = new PositionComparator(true);
         Arrays.sort(xSorted, comparator);
         Rectangle influencingArea = new Rectangle(oldBounds.x + oldBounds.width, oldBounds.y,
             dw, oldBounds.height);

         if(autoLayoutH) {
            Assembly firstCover = getFirstCover(xSorted, obj, new ArrayList<>(), influencingArea);

            if(firstCover != null) {
               colCounts += (oldBounds.width + newRectangle.width) / GRID_CELL_WIDTH;
               int dWidth = newRectangle.x + newRectangle.width - firstCover.getBounds().x;
               fixXInfluencingAssemblies(xSorted, obj, dWidth, fixPositionMap, influencingArea, true);
            }
         }
      }

      if(dh > 0) {
         Rectangle influencingArea = new Rectangle(oldBounds.x, oldBounds.y + oldBounds.height,
            newRectangle.width, dh);
         Assembly firstCover = getFirstCover(allObjs, obj, new ArrayList<>(), influencingArea);

         if(firstCover != null) {
            int dHeight = newRectangle.y + newRectangle.height - firstCover.getBounds().y;
            rowCounts += (oldBounds.height + newRectangle.height) / GRID_CELL_HEIGHT;
            fixYInfluencingAssemblies(allObjs, obj, dHeight, fixPositionMap, influencingArea, null,
               false);
         }
      }

      return new Point(colCounts, rowCounts);
   }

   private void deleteAssembly(RuntimeViewsheet rvs, Assembly obj, int rowCounts,
                               Rectangle newRectangle,  Map<Assembly, Point> fixPositionMap)
   {
//      Assembly allObjs[] =  getAssemblies(rvs, true);
//      Rectangle oldBounds = new Rectangle(obj.getBounds());
//      Rectangle emptyInfluencingArea = new Rectangle(oldBounds.x, oldBounds.y,
//         oldBounds.width, rowCounts * GRID_CELL_HEIGHT);
//      int dh = -obj.getBounds().height;
//      fixYInfluencingAssemblies(allObjs, obj, dh, fixPositionMap, emptyInfluencingArea, null,
//         false);
   }

   private Point moveAssembly(RuntimeViewsheet rvs, Assembly obj, int rowCounts, int colCounts,
                             Rectangle newRectangle, List<Assembly> keeps, boolean autoLayoutH,
                             Map<Assembly, Point> fixPositionMap)
   {
      Rectangle oldBounds = new Rectangle(obj.getBounds());
      obj.setBounds(new Rectangle(newRectangle));
      Assembly[] allObjs =  getAssemblies(rvs, true);
      int maxX = colCounts * GRID_CELL_WIDTH;

      if(autoLayoutH) {
         Assembly[] xSorted = getAssemblies(rvs, false);
         Comparator comparator = new PositionComparator(true);
         Arrays.sort(xSorted, comparator);
         Rectangle influencingArea = new Rectangle(newRectangle.x, newRectangle.y,
            maxX - newRectangle.width, oldBounds.height);

         xSorted = Arrays.stream(xSorted)
            .filter(assembly -> {
               Rectangle bounds = assembly.getBounds();
               // fit horizontal
               boolean hor = newRectangle.x < bounds.x || newRectangle.y > bounds.y;

               return isIntersecting(newRectangle, bounds) && hor || assembly.equals(obj);
            })
            .toArray(Assembly[]::new);

         Assembly firstCover = getFirstCover(allObjs, obj, keeps, newRectangle);

         if(firstCover != null) {
            Rectangle bounds = new Rectangle(firstCover.getBounds());
            colCounts += (oldBounds.width + newRectangle.width) / GRID_CELL_WIDTH;
            bounds.x = newRectangle.x + newRectangle.width;
            int dWidth = bounds.x - firstCover.getBounds().x;
            fixXInfluencingAssemblies(xSorted, obj, dWidth, fixPositionMap, influencingArea, true);
         }
      }

      // fix assemblies affected by the cover area
      Assembly firstCover = getFirstCover(allObjs, obj, keeps, newRectangle);

      if(firstCover != null) {
         rowCounts += (oldBounds.height + newRectangle.height) / GRID_CELL_HEIGHT;
         Rectangle bounds = new Rectangle(firstCover.getBounds());
         bounds.y = newRectangle.y + newRectangle.height;
         int dh = bounds.y - firstCover.getBounds().y;
         Rectangle coverInfluencingArea = new Rectangle(newRectangle.x, newRectangle.y,
            newRectangle.width, rowCounts * GRID_CELL_HEIGHT);
         fixYInfluencingAssemblies(allObjs, obj, dh, fixPositionMap, coverInfluencingArea, null,
            true);
      }

      return new Point(colCounts, rowCounts);
   }

   private void moveAllRowOrCol(RuntimeViewsheet rvs, Assembly obj, int rowCounts, int colCounts,
                                Rectangle newRectangle, boolean autoLayoutH, String linkUri,
                                Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      int maxX = colCounts * GRID_CELL_WIDTH;
      int maxY = rowCounts * GRID_CELL_HEIGHT;
      Rectangle oldBounds = new Rectangle(obj.getBounds());
      int dx = newRectangle.x - oldBounds.x;
      int dy = newRectangle.y - oldBounds.y;
      boolean isXDirection = Math.abs(dx) > Math.abs(dy);
      Assembly[] allObjs =  getAssemblies(rvs, false);
      PositionComparator comparator = new PositionComparator(isXDirection);
      Arrays.sort(allObjs, comparator);

      Rectangle influencingArea;

      if(isXDirection) {
         influencingArea = new Rectangle(oldBounds.x, oldBounds.y, maxX - oldBounds.y,
            oldBounds.height);
      }
      else {
         influencingArea = new Rectangle(oldBounds.x, oldBounds.y, oldBounds.width,
            maxY - oldBounds.y);
      }

      List<Assembly> influencingAssemblies =
         getInfluencingAssemblies(allObjs, obj, influencingArea, isXDirection);
      influencingAssemblies.add(obj);
      Map<Assembly, Rectangle> newBoundsMap = new HashMap<>();

      influencingAssemblies.forEach(influencing -> {
         Rectangle newBounds = new Rectangle(influencing.getBounds());
         newBounds.x = newBounds.x + dx < 0 ? 0 : newBounds.x + dx;
         newBounds.y = newBounds.y + dy < 0 ? 0 : newBounds.y + dy;
         newBoundsMap.put(influencing, newBounds);
      });

      layoutWizardViewsheet(rvs, influencingAssemblies, allObjs, rowCounts, colCounts,
         newBoundsMap, autoLayoutH, false, linkUri, principal, dispatcher);
   }

   private List<Assembly> getInfluencingAssemblies(Assembly[] allObjs, Assembly obj,
                                                   Rectangle influencingArea, boolean isXDirection)
   {
      List<Assembly> influencingAssemblies = new ArrayList<>();

      Arrays.stream(allObjs).forEach(assembly -> {
         if(assembly.equals(obj)) {
            return;
         }

         if(isIntersecting(assembly.getBounds(), influencingArea)) {
            influencingAssemblies.add(assembly);
            enLargeInfluencingArea(influencingArea, assembly.getBounds(), isXDirection);
         }
      });

      return influencingAssemblies;
   }

   private void enLargeInfluencingArea(Rectangle influencingArea, Rectangle bounds,
                                       boolean isXDirection)
   {
      if(isXDirection) {
         int minY = bounds.y < influencingArea.y ? bounds.y : influencingArea.y;
         int maxHeight = (bounds.y + bounds.height > influencingArea.y + influencingArea.height ?
            bounds.y + bounds.height : influencingArea.y + influencingArea.height) - minY;
         influencingArea.y = minY;
         influencingArea.height = maxHeight;
      }
      else {
         int minX = bounds.x < influencingArea.x ? bounds.x : influencingArea.x;
         int maxWidth = (bounds.x + bounds.width > influencingArea.x + influencingArea.width ?
            bounds.x + bounds.width : influencingArea.x + influencingArea.width) - minX;
         influencingArea.x = minX;
         influencingArea.width = maxWidth;
      }

   }



   private Assembly getFirstCover(Assembly[] allObjs, Assembly obj, List<Assembly> ignores,
                                  Rectangle newRectangle)
   {
      for(Assembly assembly : allObjs) {
         if(assembly.equals(obj) || ignores.contains(assembly)) {
            continue;
         }

         if(isIntersecting(newRectangle, assembly.getBounds())) {
            return assembly;
         }
      }

      return null;
   }

   private void fixYInfluencingAssemblies(Assembly[] allObjs, Assembly obj, int dh,
                                          Map<Assembly, Point> fixPositionMap,
                                          Rectangle influencingArea, Rectangle ignoreArea,
                                          boolean fixDirectly)
   {
      for(Assembly assembly : allObjs) {
         if(assembly.equals(obj)) {
            continue;
         }

         Rectangle bounds = new Rectangle(assembly.getBounds());

         if(isIntersecting(influencingArea, assembly.getBounds()) &&
            fixPositionMap.get(assembly) == null)
         {
            if(ignoreArea == null || !isIntersecting(ignoreArea, assembly.getBounds())) {
               bounds.y += dh;

               if(fixPositionMap.get(assembly) == null) {
                  fixPositionMap.put(assembly, new Point());
               }

               if(fixDirectly) {
                  assembly.setBounds(bounds);
               }
               else {
                  fixPositionMap.get(assembly).y = dh;
               }
            }

            enLargeInfluencingArea(influencingArea, bounds, false);
         }

         if(ignoreArea != null && isIntersecting(ignoreArea, assembly.getBounds())) {
            enLargeInfluencingArea(ignoreArea, bounds, false);
         }
      }
   }

   /**
    * Move objects that overlaps with influencingArea to the right.
    */
   private void fixXInfluencingAssemblies(Assembly[] allObjs, Assembly obj, int dw,
                                          Map<Assembly, Point> fixPositionMap,
                                          Rectangle influencingArea, boolean fixDirectly)
   {
      for(Assembly assembly : allObjs) {
         if(assembly.equals(obj)) {
            continue;
         }

         Rectangle bounds = new Rectangle(assembly.getBounds());

         if(isIntersecting(influencingArea, assembly.getBounds()) &&
            fixPositionMap.get(assembly) == null)
         {
            bounds.x += dw;

            if(fixPositionMap.get(assembly) == null) {
               fixPositionMap.put(assembly, new Point());
            }

            if(fixDirectly) {
               assembly.setBounds(bounds);
            }
            else {
               fixPositionMap.get(assembly).x = dw;
            }

            enLargeInfluencingArea(influencingArea, bounds, true);
         }
      }
   }

   private int adjustAssembliesPosition(RuntimeViewsheet rvs, Assembly[] allObjs,
                                        List<Assembly> keeps, int gridRows, int gridCols,
                                        Map<Assembly, Point> fixPositionMap)
   {
      boolean[][] grid = getGridMatrix(gridRows, gridCols, GRID_CELL_WIDTH, GRID_CELL_HEIGHT,
         new Assembly[0]);
      int maxRow = 0;

      for(int i = 0; i < allObjs.length; i++) {
         VSAssembly assembly = (VSAssembly) allObjs[i];
         assembly.setZIndex(allObjs.length - i);

         // keep the position of all moved assemblies.
         if(keeps.contains(assembly)) {
            continue;
         }

         Rectangle rectangle = assembly.getBounds();
         Rectangle gridRec = getGridRectangle(rectangle, GRID_CELL_WIDTH, GRID_CELL_HEIGHT);
         Point dPoint = fixPositionMap.get(assembly);
         int upRows = getPullUpRows(grid, assembly.getBounds(),
            dPoint == null ? 0 : dPoint.y, true, GRID_CELL_WIDTH, GRID_CELL_HEIGHT,
            gridRows);

         if(upRows != 0) {
            rectangle.y += (upRows * GRID_CELL_HEIGHT);
            gridRec = getGridRectangle(rectangle, GRID_CELL_WIDTH, GRID_CELL_HEIGHT);
         }

         maxRow = gridRec.y + gridRec.height > maxRow ? gridRec.y + gridRec.height : maxRow;

         fillGrid(grid, gridRec);
         assembly.setBounds(rectangle);
      }

      Comparator comparator = new PositionComparator(true);
      Arrays.sort(allObjs, comparator);
      grid = getGridMatrix(gridRows, gridCols, GRID_CELL_WIDTH, GRID_CELL_HEIGHT,
         new Assembly[0]);

      for(int i = 0; i < allObjs.length; i++) {
         VSAssembly assembly = (VSAssembly) allObjs[i];

         // keep the position of all moved assemblies.
         if(keeps.contains(assembly)) {
            continue;
         }

         Rectangle rectangle = assembly.getBounds();
         Rectangle gridRec = getGridRectangle(rectangle, GRID_CELL_WIDTH, GRID_CELL_HEIGHT);
         Point dPoint = fixPositionMap.get(assembly);
         int leftCols = getKeepFlowLeftCols(grid, assembly.getBounds(),
            dPoint == null ? 0 : dPoint.x, true, GRID_CELL_WIDTH, GRID_CELL_HEIGHT,
            gridCols);

         if(leftCols != 0) {
            rectangle.x += (leftCols * GRID_CELL_WIDTH);
            gridRec = getGridRectangle(rectangle, GRID_CELL_WIDTH, GRID_CELL_HEIGHT);
         }

         fillGrid(grid, gridRec);
         assembly.setBounds(rectangle);
      }

      return maxRow > GRID_ROW ? maxRow : GRID_ROW;
   }

   private Assembly[] sortAssemblies(Assembly[] allObjs, Assembly baseObj, boolean containsBase,
                                     PositionComparator comparator)
   {
      List<Assembly> smallList = new ArrayList<>();
      List<Assembly> bigList = new ArrayList<>();

      for(Assembly assembly : allObjs) {
         if(assembly == baseObj) {
            continue;
         }

         if(comparator.compare(assembly, baseObj) < 0) {
            smallList.add(assembly);
         }
         else {
            bigList.add(assembly);
         }
      }

      comparator.setAssembly(null);
      Collections.sort(smallList, comparator);
      Collections.sort(bigList, comparator);

      if(baseObj != null && containsBase) {
         smallList.add(baseObj);
      }

      smallList.addAll(bigList);

      return smallList.toArray(new Assembly[0]);
   }

   /**
    * fill the grid with rectangle
    * @param grid
    * @param rectangle
    */
   public boolean fillGrid(boolean[][] grid, Rectangle rectangle) {
      boolean occupied = false;

      for(int row = rectangle.y; row < rectangle.y + rectangle.height; row++) {
         if(row >= grid.length) {
            occupied = true;
            break;
         }

         for(int col = rectangle.x; col < rectangle.x + rectangle.width; col++){
            if(col >= grid[row].length) {
               occupied = true;
               break;
            }
            else {
               grid[row][col] = true;
            }
         }
      }

      return occupied;
   }

   /**
    * Update the new block position.
    * @param assemblies all assemblies in viewsheet.
    * @param gridRows current grid row count.
    * @param commandDispatcher
    * @return
    */
   public Point updateNewBlockPosition(Assembly[] assemblies, int gridRows, int gridCols,
                                     CommandDispatcher commandDispatcher)
   {
      boolean[][] grid = getGridMatrix(gridRows, gridCols, GRID_CELL_WIDTH, GRID_CELL_HEIGHT,
         assemblies);
      Rectangle newBlock = new Rectangle(0, gridRows, NEW_BLOCK_COLUMN_COUNT, NEW_BLOCK_ROW_COUNT);
      Point newPosition = new Point(newBlock.getLocation());

      for(int col = 0; col <= gridCols - newBlock.width; col++) {
         newBlock.x = col;
         int pullUpRows = getPopUpRows(grid, newBlock, false, GRID_CELL_WIDTH, GRID_CELL_HEIGHT);

         if(newBlock.y - pullUpRows < newPosition.y) {
            newPosition.y = newBlock.y - pullUpRows;
            newPosition.x = newBlock.x;
         }
      }

      RefreshNewObjectPositionCommand command = new RefreshNewObjectPositionCommand();
      command.setLeft(newPosition.x * GRID_CELL_WIDTH);
      command.setTop(newPosition.y * GRID_CELL_HEIGHT);
      commandDispatcher.sendCommand(command);

      return newPosition;
   }

   /**
    * Get assembly pixel's size.
    * @param assembly
    * @return
    */
   public Dimension getDefaultAssemblyPixelSize(VSAssembly assembly) {
      //calculate new object size
      if(assembly.getAssemblyType() == Viewsheet.IMAGE_ASSET) {
         return new Dimension(5 * GRID_CELL_WIDTH, 5 * GRID_CELL_HEIGHT);
      }
      else if(assembly.getAssemblyType() == Viewsheet.GAUGE_ASSET) {
         GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) assembly.getInfo();

         if(info.getFace() == 90820) {
            return new Dimension(8 * GRID_CELL_WIDTH, 2 * GRID_CELL_HEIGHT);
         }
         else {
            return new Dimension(5 * GRID_CELL_WIDTH, 5 * GRID_CELL_HEIGHT);
         }
      }
      else if(assembly.getAssemblyType() == Viewsheet.SELECTION_LIST_ASSET ||
         assembly.getAssemblyType() == Viewsheet.SELECTION_TREE_ASSET)
      {
         return new Dimension(5 * GRID_CELL_WIDTH, 6 * GRID_CELL_HEIGHT);
      }
      else if(assembly.getAssemblyType() == Viewsheet.TEXT_ASSET)
      {
         return new Dimension(5 * GRID_CELL_WIDTH, 2 * GRID_CELL_HEIGHT);
      }
      else if(assembly.getAssemblyType() == Viewsheet.SLIDER_ASSET ||
              assembly.getAssemblyType() == Viewsheet.TIME_SLIDER_ASSET )
      {
         return new Dimension(10 * GRID_CELL_WIDTH, 2 * GRID_CELL_HEIGHT);
      }
      else if(assembly.getAssemblyType() == Viewsheet.CALENDAR_ASSET) {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getInfo();

         if(info.getViewModeValue() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE) {
            return new Dimension(30 * GRID_CELL_WIDTH, 10 * GRID_CELL_HEIGHT);
         }
         else {
            return new Dimension(15 * GRID_CELL_WIDTH, 10 * GRID_CELL_HEIGHT);
         }
      }

      return new Dimension(20 * GRID_CELL_WIDTH, 12 * GRID_CELL_HEIGHT);
   }

   /**
    * Translate the pixie rectangle to grid
    * @param rectangle the pixie rectangle
    * @return the grid rectangle
    */
   private Rectangle getGridRectangle(Rectangle rectangle, int cellWidth, int cellHeight) {
      return new Rectangle(rectangle.x / cellWidth, rectangle.y / cellHeight,
         rectangle.width / cellWidth, rectangle.height / cellHeight);
   }

   /**
    * Gets the number of rows that can be moved up in the grid pane.
    * @param grid
    * @param rectangle
    * @return
    */
   private int getPullUpRows(boolean[][] grid, Rectangle rectangle, Integer dh, boolean pixel,
                             int cellWidth, int cellHeight, int rowCount)
   {
      int pullUpRows = 0;
      Rectangle original = new Rectangle(rectangle);
      Rectangle gridBounds = new Rectangle(rectangle);
      dh = dh == null ? 0 : dh;

      if(pixel) {
         gridBounds = getGridRectangle(rectangle, cellWidth, cellHeight);
         original = getGridRectangle(rectangle, cellWidth, cellHeight);
         dh /= GRID_CELL_HEIGHT;
      }

      gridBounds.y = rowCount;

      for(int row = gridBounds.y - 1; row >= 0 && row >= original.y + dh; row--) {
         boolean enoughRow = true;

         for(int col = gridBounds.x;
             col < grid[row].length && col < gridBounds.x + gridBounds.width;
             col++)
         {
            if(grid[row][col]) {
               enoughRow = false;
               break;
            }
         }

         if(enoughRow) {
            pullUpRows++;
         }
         else {
            break;
         }
      }

      return rowCount - pullUpRows - original.y;
   }

   /**
    * Gets the number of rows that can be moved up in the grid pane.
    * @param grid
    * @param rectangle
    * @return
    */
   private int getPopUpRows(boolean[][] grid, Rectangle rectangle, boolean pixel, int cellWidth,
                             int cellHeight) {
      int pullUpRows = 0;
      Rectangle gridBounds = new Rectangle(rectangle);

      if(pixel) {
         gridBounds = getGridRectangle(rectangle, cellWidth, cellHeight);
      }

      for(int row = gridBounds.y - 1; row >= 0; row--) {
         boolean enoughRow = true;

         for(int col = gridBounds.x; col < gridBounds.x + gridBounds.width; col++) {
            if(grid[row][col]) {
               enoughRow = false;
               break;
            }
         }

         if(enoughRow) {
            pullUpRows++;
         }
         else {
            break;
         }
      }

      return pullUpRows;
   }

   private int getKeepFlowLeftCols(boolean[][] grid, Rectangle rectangle, Integer dw, boolean pixel,
                                   int cellWidth, int cellHeight, int colCount)
   {
      int flowLeftCols = 0;
      Rectangle gridBounds = new Rectangle(rectangle);
      Rectangle original = new Rectangle(rectangle);
      dw = dw == null ? 0 : dw;

      if(pixel) {
         gridBounds = getGridRectangle(rectangle, cellWidth, cellHeight);
         original = getGridRectangle(rectangle, cellWidth, cellHeight);
         dw = dw / cellWidth;
      }

      gridBounds.x = colCount;

      for(int col = gridBounds.x - 1; col >= 0 && col >= original.x + dw; col--) {
         boolean enoughRow = true;

         for(int row = gridBounds.y; row < gridBounds.y + gridBounds.height; row++) {
            if(row < grid.length && col < grid[row].length && grid[row][col]) {
               enoughRow = false;
               break;
            }
         }

         if(enoughRow) {
            flowLeftCols++;
         }
         else {
            break;
         }
      }

      return colCount - flowLeftCols - original.x;
   }

   /**
    * Get the grid matrix
    * @param rowCount
    * @param colGrid
    * @return
    */
   private boolean[][] getGridMatrix(int rowCount, int colGrid, int cellWidth, int cellHeight,
                                     Assembly[] assemblies)
   {
      boolean[][] matrix = new boolean[rowCount][colGrid];

      for(int row = 0; row < matrix.length; row++) {
         matrix[row] = new boolean[colGrid];
      }

      // fill the grid.
      for(Assembly assembly : assemblies) {
         Rectangle bounds = assembly.getBounds();
         int startX = bounds.x / cellWidth;
         int startY = bounds.y / cellHeight;
         int endX = (bounds.x + bounds.width) / cellWidth;
         int endY = (bounds.y + bounds.height) / cellHeight;

         for(int i = startX; i < endX; i++) {
            for(int j = startY; j < endY; j++) {
               matrix[j][i] = true;
            }
         }
      }

      return matrix;
   }

   /**
    * Determines whether two rectangles intersect
    * @param rec rectangle1.
    * @param rec2 rectangle2.
    * @return
    */
   private static boolean isIntersecting(Rectangle rec, Rectangle rec2) {
      double xCenterDistance = Math.abs(rec.getCenterX() - rec2.getCenterX());
      double yCenterDistance = Math.abs(rec.getCenterY() - rec2.getCenterY());

      return xCenterDistance < (rec.width + rec2.width) / 2 &&
         yCenterDistance < (rec.height + rec2.height) / 2;
   }

   private static class PositionComparator implements Comparator {
      public PositionComparator() {
      }

      public PositionComparator(boolean preferredX) {
         PositionComparator.this.preferredX = preferredX;
      }

      @Override
      public int compare(Object o1, Object o2) {
         if(o1 instanceof Assembly && o2 instanceof Assembly) {
            Point point1 = ((Assembly) o1).getPixelOffset();
            Point point2 = ((Assembly) o2).getPixelOffset();
            Rectangle originalBounds = null;

            if(assembly != null) {
               Rectangle rectangle = assembly.getBounds();
               originalBounds = new Rectangle(rectangle.x, rectangle.y + dy,
                  rectangle.width, rectangle.height);
            }

            if(o1 == assembly && isIntersecting(((Assembly) o2).getBounds(), assembly.getBounds()))
            {
               return isIntersecting(((Assembly) o2).getBounds(), originalBounds) ? -1 : 1;
            }

            if(o2 == assembly && isIntersecting(((Assembly) o1).getBounds(), assembly.getBounds()))
            {
               return isIntersecting(((Assembly) o1).getBounds(), originalBounds) ? 1 : -1;
            }

            if(preferredX) {
               if(point1.x > point2.x) {
                  return 1;
               }
               else if(point1.x < point2.x) {
                  return -1;
               }
               else {
                  if(point1.y == point2.y) {
                     return 0;
                  }

                  return point1.y > point2.y ? 1 : -1;
               }
            }
            else {
               if(point1.y > point2.y) {
                  return 1;
               }
               else if(point1.y < point2.y) {
                  return -1;
               }
               else {
                  if(point1.x == point2.x) {
                     return 0;
                  }

                  return point1.x > point2.x ? 1 : -1;
               }
            }
         }

         return 0;
      }

      public void setAssembly(Assembly assembly) {
         PositionComparator.this.assembly = assembly;
      }

      public void setDy(int dy) {
         PositionComparator.this.dy = dy;
      }

      private Assembly assembly;
      private boolean preferredX;
      private int dy = 0;
   }

   private static final int GRID_ROW = VSWizardConstants.GRID_ROW;
   private static final int GRID_COLUMN = VSWizardConstants.GRID_COLUMN;
   private static final int GRID_CELL_HEIGHT = VSWizardConstants.GRID_CELL_HEIGHT;
   private static final int GRID_CELL_WIDTH = VSWizardConstants.GRID_CELL_WIDTH;
   private static final int NEW_BLOCK_COLUMN_COUNT = VSWizardConstants.NEW_BLOCK_COLUMN_COUNT;
   private static final int NEW_BLOCK_ROW_COUNT = VSWizardConstants.NEW_BLOCK_ROW_COUNT;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
}
