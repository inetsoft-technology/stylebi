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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.model.vs.LayoutOptionDialogModel;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSTableService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

@Service
@ClusterProxy
public class LayoutOptionDialogService {
   /**
    * The item is to be placed into the current selection component.
    */
   public int SELECTION = 1;

   public LayoutOptionDialogService(GroupingService groupingService,
                                    VSObjectTreeService vsObjectTreeService,
                                    ViewsheetService engine,
                                    VSTableService vsTableService,
                                    CoreLifecycleService coreLifecycleService)
   {
      this.groupingService = groupingService;
      this.vsObjectTreeService = vsObjectTreeService;
      this.engine = engine;
      this.vsTableService = vsTableService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setLayoutOptionDialogModel(@ClusterProxyKey String runtimeId,
                                          LayoutOptionDialogModel model,
                                          Principal principal, String linkUri,
                                          CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly object = (VSAssembly) viewsheet.getAssembly(model.getObject());
      VSAssembly target = (VSAssembly) viewsheet.getAssembly(model.getTarget());
      int selected = model.getSelectedValue();
      boolean selection = selected == SELECTION;

      // If dropping straight from toolbox into container component, make new object.
      if(model.getNewObjectType() > 0) {
         // Embedded Viewsheet
         if(model.getVsEntry() != null) {
            AssetRepository engine = this.engine.getAssetRepository();
            Viewsheet assembly = (Viewsheet)
               engine.getSheet(model.getVsEntry(), principal, true, AssetContent.ALL);
            VSEventUtil.syncEmbeddedTableVSAssembly(assembly);
            String name = AssetUtil.getNextName(viewsheet, assembly.getAssemblyType());
            assembly = assembly.createVSAssembly(name);
            assembly.setEntry(model.getVsEntry());
            viewsheet.addAssembly(assembly);
            rvs.initViewsheet(assembly, false);

            object = assembly;
            coreLifecycleService.addDeleteVSObject(rvs, object, dispatcher);
         }
         else if("column".equalsIgnoreCase(model.getObject())) {
            object = getNewSelectionAssemblyFromBindings(model.getColumns(), rvs, principal);
            viewsheet.addAssembly(object);
         }
         else if("table".equalsIgnoreCase(model.getObject())) {
            object = vsTableService.createTable(rvs, engine, model.getColumns().get(0), 0, 0);
            viewsheet.addAssembly(object);
         }
         else {
            object = VSEventUtil.createVSAssembly(rvs, model.getNewObjectType());
         }
      }

      if(object == null || target == null) {
         return null;
      }

      if(object.getContainer() instanceof GroupContainerVSAssembly) {
         object = object.getContainer();
      }

      if(target.getContainer() instanceof GroupContainerVSAssembly) {
         target = target.getContainer();
      }

      this.groupingService.groupComponents(rvs, target, object, selection, linkUri, dispatcher);
      VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
      PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
      dispatcher.sendCommand(treeCommand);

      return null;
   }

   private VSAssembly getNewSelectionAssemblyFromBindings(List<AssetEntry> bindings,
                                                         RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      int type;
      ColumnSelection columns = new ColumnSelection();
      Viewsheet viewsheet = rvs.getViewsheet();

      AssetEntry binding = bindings.get(0);
      DataRef ref = createDataRef(binding);

      String table = binding.getProperty("assembly");
      String dtype = binding.getProperty("dtype");

      if(bindings.size() > 1) {
         type = AbstractSheet.SELECTION_TREE_ASSET;

         String newTable = table;

         for(int i = 0; i < bindings.size(); i++) {
            String refTable = bindings.get(i).getProperty("assembly");

            if(!newTable.equals(refTable)) {
               if(i == 0) {
                  newTable = refTable;
                  columns = new ColumnSelection();
               }
               else {
                  continue;
               }
            }

            columns.addAttribute(createDataRef(bindings.get(i)));
         }

         table = newTable;
      }
      else if(XSchema.isNumericType(dtype)) {
         type = AbstractSheet.TIME_SLIDER_ASSET;
         columns.addAttribute(ref);
      }
      else {
         type = AbstractSheet.SELECTION_LIST_ASSET;
         columns.addAttribute(ref);
      }

      String name = AssetUtil.getNextName(viewsheet, type);
      final List<String> tables = Collections.singletonList(table);
      VSAssembly vsassembly = vsTableService.createSelectionVSAssembly(viewsheet, type, dtype,
                                                                       name, tables,
                                                                       columns);
      vsassembly.initDefaultFormat();
      Point offsetPixel = new Point(0, 0);
      vsassembly.getInfo().setPixelOffset(offsetPixel);

      return vsassembly;
   }

   private DataRef createDataRef(AssetEntry entry) {
      if(!entry.isColumn()) {
         return null;
      }

      String entity = entry.getProperty("entity");
      String attr = entry.getProperty("attribute");
      String refType = entry.getProperty("refType");
      String caption = entry.getProperty("caption");
      String dtype = entry.getProperty("dtype");
      AttributeRef ref = new AttributeRef(entity, attr);

      if(refType != null) {
         ref.setRefType(Integer.parseInt(refType));
      }

      ref.setCaption(caption);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(dtype);

      return col;
   }

   private final GroupingService groupingService;
   private final VSObjectTreeService vsObjectTreeService;
   private final ViewsheetService engine;
   private final VSTableService vsTableService;
   private final CoreLifecycleService coreLifecycleService;
}
