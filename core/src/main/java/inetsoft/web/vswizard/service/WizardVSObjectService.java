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
package inetsoft.web.vswizard.service;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.vs.objects.command.ForceEditModeCommand;
import inetsoft.web.composer.vs.objects.event.*;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import inetsoft.web.vswizard.command.UploadImageCommand;
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;

@Service
public class WizardVSObjectService {
   @Autowired
   public WizardVSObjectService(PlaceholderService placeholderService,
                                WizardViewsheetService wizardVSService,
                                VSObjectModelFactoryService objectModelService)
   {
      this.wizardVSService = wizardVSService;
      this.placeholderService = placeholderService;
      this.objectModelService = objectModelService;
   }

   public Assembly addVsObject(RuntimeViewsheet rtv, VSAssembly assembly, int currentGridRow,
                               int currentGridCol, String linkUri, Principal principal,
                               CommandDispatcher commandDispatcher)
      throws Exception
   {
      return addVsObject(rtv, assembly, currentGridRow, currentGridCol, false, linkUri,
         principal, commandDispatcher);
   }

   public Assembly addVsObject(RuntimeViewsheet rtv, VSAssembly assembly, int currentGridRow,
                               int currentGridCol, boolean edit, String linkUri, Principal principal,
                               CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rtv.getViewsheet();

      if(viewsheet == null) {
         return null;
      }

      Assembly[] assemblies = wizardVSService.getAssemblies(rtv, false);
      Rectangle bounds = assembly.getBounds();
      int enLargeRowCount = currentGridRow + bounds.height / VSWizardConstants.GRID_CELL_HEIGHT;
      int enLargeColCount = currentGridCol + bounds.width / VSWizardConstants.GRID_CELL_WIDTH;

      /* now insertion location is fixed, moving the newly inserted vsobj feels strange.
         just leave it there and let the user move it back
      boolean[][] grid = wizardVSService.getGridMatrix(enLargeRowCount, assemblies);
      Rectangle oldBounds = new Rectangle(bounds);
      Rectangle gridRec = wizardVSService.getGridRectangle(bounds);

      // place the new assembly to a empty space when the width is too wide
      if(gridRec.x + gridRec.width > grid[0].length) {
         boolean placed =  false;

         FINISH:
         for(int row = 0; row < grid.length; row++) {
            if(row <= gridRec.y) {
               continue;
            }

            for(int col = 0; col < grid[row].length; col++) {
               if(grid[row][col]) {
                  continue;
               }

               gridRec.x = col;
               gridRec.y = row;
               placed = wizardVSService.canPlace(gridRec, grid);

               if(placed) {
                  break FINISH;
               }
            }
         }

         if(!placed) {
            gridRec.x = 0;
            gridRec.y = currentGridRow;
         }

         bounds = wizardVSService.getPixelRectangle(gridRec);
      }
      */

      viewsheet.addAssembly(assembly);
      assemblies = wizardVSService.getAssemblies(rtv, false);
      Point virtualPosition = new Point(0,currentGridRow * VSWizardConstants.GRID_CELL_HEIGHT);
      assembly.setPixelOffset(virtualPosition);
      this.wizardVSService.editAssembly(rtv, assembly, assemblies, new ArrayList<>(),
         enLargeRowCount, enLargeColCount, bounds, true, false, linkUri, principal,
         commandDispatcher);
//      addPaddingToNewAssembly(rtv, assembly, enLargeRowCount, assemblies, linkUri, edit,
//         !oldBounds.getLocation().equals(bounds.getLocation()), principal, commandDispatcher);
      this.wizardVSService.updateGridRowsAndNewBlock(viewsheet.getAssemblies(), commandDispatcher);

      if(assembly instanceof ImageVSAssembly) {
         ImageVSAssembly image = (ImageVSAssembly) assembly;
         ImageVSAssemblyInfo info = (ImageVSAssemblyInfo) image.getVSAssemblyInfo();
         info.setScaleImageValue(true);
         info.setMaintainAspectRatio(true);
         image.setVSAssemblyInfo(info);
         UploadImageCommand command = new UploadImageCommand();
         command.setUploadObject(objectModelService.createModel(assembly, rtv));
         commandDispatcher.sendCommand(command);
      }
      else if(assembly instanceof TextVSAssembly) {
         ForceEditModeCommand forceEditModeCommand = ForceEditModeCommand
            .builder()
            .select(true)
            .editMode(true)
            .build();
         commandDispatcher.sendCommand(assembly.getAbsoluteName(), forceEditModeCommand);
      }

      wizardVSService.refreshWizardViewsheet(rtv, null, null, linkUri,
         principal, commandDispatcher);

      return assembly;
   }

   private Assembly getDescriptionAssembly(Viewsheet vs, VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info instanceof DescriptionableAssemblyInfo) {
         String desName = ((DescriptionableAssemblyInfo) info).getDescriptionName();

         if(!StringUtils.isEmpty(desName)) {
            return vs.getAssembly(desName);
         }
      }

      return null;
   }

   public Assembly insertVsObject(RuntimeViewsheet rtv, AddNewVSObjectEvent event,
                                  String linkUri, Principal principal,
                                  CommandDispatcher commandDispatcher)
      throws Exception
   {

      VSAssembly assembly = VSEventUtil.createVSAssembly(rtv, event.getType());
      assert assembly != null;
      Point position = new Point(event.getxOffset(), event.getyOffset());
      assembly.setPixelOffset(position);
      assembly.setPixelSize(wizardVSService.getDefaultAssemblyPixelSize(assembly));

      if(assembly instanceof TextVSAssembly) {
         VSCompositeFormat fmt = assembly.getVSAssemblyInfo().getFormat();
         // use the same default as label in insertVsObject, 40 * 0.6
         final Font defFnt = VSAssemblyInfo.getDefaultFont(Font.PLAIN, 24);
         fmt.getUserDefinedFormat().setFontValue(defFnt);
      }

      addVsObject(rtv, assembly, event.getWizardCurrentGridRow(), event.getWizardCurrentGridCol(),
         linkUri, principal, commandDispatcher);

      return assembly;
   }

   public void deleteVSObject(RuntimeViewsheet rtv, RemoveVSObjectsEvent event, String linkUri,
                              Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rtv.getViewsheet();
      ArrayList<Assembly> deleteAssemblies = new ArrayList<>();
      Map<Assembly, Rectangle> newRectangles = new HashMap<>();
      int minY = -1;

      // 1.delete the assembly.
      for(String objectName: event.objectNames()) {
         Assembly assembly = viewsheet.getAssembly(objectName);

         if(assembly == null) {
            continue;
         }

         if(minY == -1 || minY > assembly.getBounds().y) {
            minY = assembly.getBounds().y;
         }

         //fix description dependency
         fixDescriptionDependency(assembly, viewsheet, commandDispatcher);

         deleteAssemblies.add(assembly);
         newRectangles.put(assembly,
            new Rectangle(assembly.getBounds().x, assembly.getBounds().y, 0, 0));
         placeholderService.removeVSAssembly(rtv, linkUri, ((VSAssembly) assembly), commandDispatcher, false, true);
         // 2.send command to refresh wizard pane.
         RemoveVSObjectCommand removeObjectCmd = new RemoveVSObjectCommand();
         removeObjectCmd.setName(objectName);
         commandDispatcher.sendCommand(removeObjectCmd);
      }

      Assembly[] assemblies = wizardVSService.getAssemblies(rtv, false);
      this.wizardVSService.layoutWizardViewsheet(rtv, deleteAssemblies, assemblies,
         event.wizardGridRows(), event.wizardGridCols(), newRectangles, true,
         false, linkUri, principal,  commandDispatcher);
      this.wizardVSService.updateGridRowsAndNewBlock(viewsheet.getAssemblies(), commandDispatcher);
   }

   public void fixDescriptionDependency(Assembly assembly, Viewsheet viewsheet,
                                         CommandDispatcher commandDispatcher)
   {
      VSAssembly assemblyItem2 = (VSAssembly) assembly;

      if(assemblyItem2 instanceof TextVSAssembly &&
         ((DescriptionableAssemblyInfo) assemblyItem2.getVSAssemblyInfo()).getDescriptionName() == null)
      {
         for(Assembly assemblyItem: viewsheet.getAssemblies()) {
            VSAssembly assembly2 = (VSAssembly) assemblyItem;

            if(!(assembly2.getVSAssemblyInfo() instanceof DescriptionableAssemblyInfo)) {
               continue;
            }

            String desName = ((DescriptionableAssemblyInfo) assembly2.getVSAssemblyInfo())
                    .getDescriptionName();

            if(assemblyItem2.getAbsoluteName().equals(desName)) {
               ((DescriptionableAssemblyInfo) assembly2.getVSAssemblyInfo()).setDescriptionName(null);
            }
         }
      }
      else {
         if(!(assemblyItem2.getVSAssemblyInfo() instanceof DescriptionableAssemblyInfo)) {
            return;
         }

         String desName = ((DescriptionableAssemblyInfo) assemblyItem2.getVSAssemblyInfo())
                 .getDescriptionName();

         if(desName == null) {
            return;
         }

         viewsheet.removeAssembly(desName);
         RemoveVSObjectCommand removeObjectCmd = new RemoveVSObjectCommand();
         removeObjectCmd.setName(desName);
         commandDispatcher.sendCommand(removeObjectCmd);
      }
   }

   public void moveVSObject(RuntimeViewsheet rtv, MoveVSObjectEvent event, String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rtv.getViewsheet();
      Assembly assembly = viewsheet.getAssembly(event.getName());
      Assembly[] assemblies = wizardVSService.getAssemblies(rtv, false);

      if(assembly == null) {
         return;
      }

      Rectangle newRectangle = new Rectangle(assembly.getBounds());
      newRectangle.x = event.getxOffset();
      newRectangle.y = event.getyOffset();

      wizardVSService.layoutWizardViewsheet(rtv, assembly, assemblies, event.getWizardGridRows(),
         event.getWizardGridCols(), newRectangle, false, event.isMoveRowOrCol(),
         linkUri, principal, commandDispatcher);
      wizardVSService.updateGridRowsAndNewBlock(assemblies, commandDispatcher);
   }

   public void moveVSObjects(RuntimeViewsheet rtv, MultiMoveVsObjectEvent events, String linkUri,
                            Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      final ArrayList<Assembly> moveAssemblies = new ArrayList<>();
      final Map<Assembly, Rectangle> newRectangles = new HashMap<>();
      Viewsheet viewsheet = rtv.getViewsheet();
      int gridRows = -1;
      int gridCols = -1;
      boolean autoLayoutH = true;
      boolean moveRowOrCol = false;

      if(events.getEvents() != null && events.getEvents().length != 0) {
         autoLayoutH = events.getEvents()[0].isAutoLayoutHorizontal();
         moveRowOrCol = events.getEvents()[0].isMoveRowOrCol();
      }

      for(MoveVSObjectEvent event : events.getEvents()) {
         Assembly assembly = viewsheet.getAssembly(event.getName());

         if(assembly != null) {
            Rectangle newRectangle = new Rectangle(assembly.getBounds());
            newRectangle.x = event.getxOffset();
            newRectangle.y = event.getyOffset();
            newRectangles.put(assembly, newRectangle);
            moveAssemblies.add(assembly);
         }

         gridRows = Math.max(gridRows, event.getWizardGridRows());
         gridCols = Math.max(gridCols, event.getWizardGridCols());
      }

      Assembly[] assemblies = wizardVSService.getAssemblies(rtv, false);
      this.wizardVSService.layoutWizardViewsheet(rtv, moveAssemblies, assemblies, gridRows,
         gridCols, newRectangles, autoLayoutH, moveRowOrCol, linkUri, principal,  commandDispatcher);
      this.wizardVSService.updateGridRowsAndNewBlock(viewsheet.getAssemblies(), commandDispatcher);
   }

   public void resizeVSObject(RuntimeViewsheet rtv, ResizeVSObjectEvent event, String linkUri,
                              Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rtv.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(event.getName());
      Assembly[] assemblies = viewsheet.getAssemblies();
      Rectangle newRectangle = new Rectangle(assembly.getBounds());
      newRectangle.x = event.getxOffset();
      newRectangle.y = event.getyOffset();
      newRectangle.width = event.getWidth();
      newRectangle.height = event.getHeight();

      if(assembly instanceof TextVSAssembly) {
         VSCompositeFormat fmt = assembly.getVSAssemblyInfo().getFormat();

         if(fmt.getUserDefinedFormat().getFontValue() != null) {
            final Font defFnt = VSAssemblyInfo.getDefaultFont(Font.PLAIN,
                                                              (int) (event.getHeight() * 0.6));
            fmt.getUserDefinedFormat().setFontValue(defFnt);
         }
      }

      wizardVSService.layoutWizardViewsheet(rtv, assembly, assemblies, event.getWizardGridRows(),
         event.getWizardGridCols(), newRectangle, event.isAutoLayoutHorizontal(),
         false,linkUri, principal, commandDispatcher);
      wizardVSService.updateGridRowsAndNewBlock(assemblies, commandDispatcher);
   }

   private final PlaceholderService placeholderService;
   private final WizardViewsheetService wizardVSService;
   private final VSObjectModelFactoryService objectModelService;
}
