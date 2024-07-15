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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XUtil;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSInsertColumnsEvent;
import inetsoft.web.composer.ws.event.WSInsertColumnsEventValidator;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

@Controller
public class WorksheetController {
   protected RuntimeWorksheet getRuntimeWorksheet(Principal principal) throws Exception {
      WorksheetService engine = getWorksheetEngine();
      return engine.getWorksheet(getRuntimeId(), principal);
   }

   protected WorksheetService getWorksheetEngine() {
      return wsEngine;
   }

   public String getRuntimeId() {
      return runtimeViewsheetRef.getRuntimeId();
   }

   protected RuntimeViewsheetRef getRuntimeViewsheetRef() {
      return runtimeViewsheetRef;
   }

   @Autowired
   protected void setRuntimeViewsheetRef(RuntimeViewsheetRef runtimeViewsheetRef) {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Autowired
   protected void setWsEngine(ViewsheetService viewsheetService) {
      this.wsEngine = viewsheetService;
   }

   /**
    * Check if allows deletion.
    */
   protected boolean allowsDeletion(Worksheet ws, TableAssembly assembly, ColumnRef ref) {
      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());

      for(AssemblyRef assemblyRef : arr) {
         String assemblyName = assemblyRef.getEntry().getName();
         Assembly tmp = ws.getAssembly(assemblyName);

         if(tmp instanceof CompositeTableAssembly) {
            CompositeTableAssembly table = (CompositeTableAssembly) tmp;

            if(table.isColumnUsed(assembly, ref)) {
               return false;
            }
         }

         if(tmp instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) tmp;
            DataRef dataRef = AssetUtil.getOuterAttribute(assemblyName, ref);
            ColumnRef ref2 =
               AssetUtil.getColumnRefFromAttribute(table.getColumnSelection(), dataRef);

            if(ref2 != null && !allowsDeletion(ws, table, ref2)) {
               return false;
            }
         }
      }

      return true;
   }

   protected boolean isBeDepend(ColumnSelection columns, DataRef target) {
      boolean depend = false;

      for(int j = 0; j < columns.getAttributeCount(); j++) {
         ColumnRef col = (ColumnRef) columns.getAttribute(j);
         DataRef baseRef = AssetUtil.getBaseAttribute(col);

         if(baseRef instanceof RangeRef) {
            RangeRef dependRef = (RangeRef) baseRef;

            if(dependRef != null && target.equals(dependRef.getDataRef())) {
               depend = true;
               break;
            }
         }
      }

      return depend;
   }

   protected WSInsertColumnsEventValidator validateInsertColumns0(
      RuntimeWorksheet rws,
      WSInsertColumnsEvent event,
      Principal principal) throws Exception
   {
      WSInsertColumnsEventValidator.Builder builder = WSInsertColumnsEventValidator.builder();

      Worksheet ws = rws.getWorksheet();
      String name = event.name();
      int index = event.index();
      TableAssembly assembly = (TableAssembly) ws.getAssembly(name);

      if(assembly == null) {
         return null;
      }

      ColumnSelection columns =
         (ColumnSelection) assembly.getColumnSelection().clone(true);
      BoundTableAssembly boundTable;
      SourceInfo source;

      if(assembly instanceof BoundTableAssembly) {
         boundTable = (BoundTableAssembly) assembly;
         BoundTableAssembly otable = (BoundTableAssembly) boundTable.clone();
         source = boundTable.getSourceInfo();
         XLogicalModel lmodel = XUtil.getLogicModel(source, rws.getUser());

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         int mode = AssetEventUtil.getMode(assembly);
         XTable data = box.getTableLens(name, mode);

         if(index != 0) {
            ColumnRef leftNeighbor = AssetUtil.findColumn(data, index - 1, columns);
            index = columns.indexOfAttribute(leftNeighbor) + 1;
         }
         else if(data.getColCount() > 0) {
            ColumnRef rightNeighbor = AssetUtil.findColumn(data, 0, columns);
            index = columns.indexOfAttribute(rightNeighbor);
         }
         else {
            index = 0;
         }

         for(AssetEntry entry : event.entries()) {
            String esrc = entry.getProperty("source");
            String entity = entry.getProperty("entity");
            String attr = entry.getProperty("attribute");
            attr = AssetUtil.trimEntity(attr, entity);

            if(source != null && source.getSource() != null &&
               source.getSource().equals(esrc) && attr != null)
            {
               AttributeRef attributeRef = new AttributeRef(entity, attr);

               if(entry.getProperty("caption") != null) {
                  attributeRef.setCaption(entry.getProperty("caption"));
               }

               if(entry.getProperty("refType") != null) {
                  attributeRef.setRefType(
                     Integer.parseInt(entry.getProperty("refType")));
               }

               if(index < 0 || index > columns.getAttributeCount()) {
                  return null;
               }

               ColumnRef ref = new ColumnRef(attributeRef);
               ref.setDataType(entry.getProperty("dtype"));
               int oldCount = columns.getAttributeCount();
               columns.addAttribute(index, ref);

               // Column was successfully added.
               if(oldCount < columns.getAttributeCount()) {
                  index++;
               }

               // if logic model, keep ref type
               if(lmodel != null) {
                  XEntity xentity = lmodel.getEntity(entity);
                  XAttribute xattr = xentity.getAttribute(attr);
                  attributeRef.setRefType(xattr.getRefType());
                  attributeRef.setDefaultFormula(xattr.getDefaultFormula());
                  ref.setDescription(xattr.getDescription());
               }
            }
         }

         if(otable != null) {
            assembly.setColumnSelection(columns);
            WSModelTrapContext context =
               new WSModelTrapContext(boundTable, principal);

            if(context.isCheckTrap() &&
               context.checkTrap(otable, boundTable).showWarning())
            {
               String msg = context.getTrapCondition();
               builder.trap(msg);
            }

            assembly.setColumnSelection(otable.getColumnSelection());
         }
      }

      return builder.build();
   }

   protected void insertColumns0(
      String name, int index, AssetEntry[] entries,
      ColumnSelection columns, RuntimeWorksheet rws,  TableAssembly assembly) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      BoundTableAssembly boundTable;
      SourceInfo source;

      boundTable = (BoundTableAssembly) assembly;
//         BoundTableAssembly otable =
//            (BoundTableAssembly) (confirmed ? null : boundTable.clone());
      source = boundTable.getSourceInfo();
      XLogicalModel lmodel = XUtil.getLogicModel(source, rws.getUser());

      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      int mode = AssetEventUtil.getMode(assembly);
      XTable data = box.getTableLens(name, mode);

      if(index != 0 && columns.getAttributeCount() > 0) {
         ColumnRef leftNeighbor = AssetUtil.findColumn(data, index - 1, columns);
         index = columns.indexOfAttribute(leftNeighbor) + 1;
      }
      else if(data.getColCount() > 0 && columns.getAttributeCount() > 0) {
         ColumnRef rightNeighbor = AssetUtil.findColumn(data, 0, columns);
         index = columns.indexOfAttribute(rightNeighbor);
      }
      else {
         index = 0;
      }

      for(AssetEntry entry : entries) {
         String esrc = entry.getProperty("source");
         String entity = entry.getProperty("entity");
         String attr = entry.getProperty("attribute");
         attr = AssetUtil.trimEntity(attr, entity);

         if(source != null && source.getSource() != null &&
            source.getSource().equals(esrc) && attr != null)
         {
            AttributeRef attributeRef = new AttributeRef(entity, attr);

            if(entry.getProperty("caption") != null) {
               attributeRef.setCaption(entry.getProperty("caption"));
            }

            if(entry.getProperty("refType") != null) {
               attributeRef.setRefType(
                  Integer.parseInt(entry.getProperty("refType")));
            }

            if(index < 0 || index > columns.getAttributeCount()) {
               return;
            }

            ColumnRef ref = new ColumnRef(attributeRef);
            ref.setDataType(entry.getProperty("dtype"));
            int oldCount = columns.getAttributeCount();
            columns.addAttribute(index, ref);

            // Column was successfully added.
            if(oldCount < columns.getAttributeCount()) {
               index++;
            }

            // if logic model, keep ref type
            if(lmodel != null) {
               XEntity xentity = lmodel.getEntity(entity);
               XAttribute xattr = xentity.getAttribute(attr);
               attributeRef.setRefType(xattr.getRefType());
               attributeRef.setDefaultFormula(xattr.getDefaultFormula());
               ref.setDescription(xattr.getDescription());
            }
         }
      }

      assembly.setColumnSelection(columns);
      // Handled by validator
//         if(otable != null) {
//            WSModelTrapContext context =
//               new WSModelTrapContext(boundTable, principal);
//            AbstractModelTrapContext.TrapInfo info = null;
//
//            if(context.isCheckTrap() &&
//               (info = context.checkTrap(otable, boundTable)).showWarning()) {
//               assembly.setColumnSelection(otable.getColumnSelection());
//               String msg = context.getTrapCondition();
//               WSTrapMessageCommand cmd =
//                  new WSTrapMessageCommand(msg, MessageCommand.CONFIRM);
//               MessageCommand command = new MessageCommand();
//               command.setMessage(msg);
//               command.setType(MessageCommand.Type.CONFIRM);
//               commandDispatcher.sendCommand(command);
//               cmd.addEvent(this);
//               command.addCommand(cmd);
//               return;
//            }
//         }
   }

   protected void setColumnIndex0(String tname, int newIndex,
                                  RuntimeWorksheet rws, int[] oldIndices, Boolean replaceColumn) throws Exception
   {
      Worksheet ws = rws.getWorksheet();

      if(oldIndices.length == 0) {
         return;
      }

      Arrays.sort(oldIndices);
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table == null) {
         return;
      }

//      command.put("linkUri", getLinkURI()); TODO
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      int mode = WorksheetEventUtil.getMode(table);
      XTable data = box.getTableLens(tname, mode);
      boolean lastCol = false;
      boolean isCrosstab = table.getAggregateInfo().isCrosstab();

      if(newIndex == data.getColCount()) {
         lastCol = true;
         newIndex--;
      }

      if(isCrosstab && table.isAggregate() && newIndex == table.getAggregateInfo().getGroupCount() - 1) {
         newIndex--;
      }

      if(newIndex < 0 || newIndex >= data.getColCount()) {
         return;
      }

      ColumnSelection columns = table.getColumnSelection();
      ColumnSelection columns2 = table.getColumnSelection(true);
      ColumnRef to = AssetUtil.findColumn(data, newIndex, columns);
      ArrayList<ColumnRef> columnsInsertedLeftOfNewIndex = new ArrayList<>();
      ArrayList<ColumnRef> columnsInsertedRightOfNewIndex = new ArrayList<>();

      if(to == null) {
         return;
      }

      boolean oldIndexIsNewIndex = false;

      for(int oldIndex : oldIndices) {
         ColumnRef from = AssetUtil.findColumn(data, oldIndex, columns);

         if(from == null) {
            return;
         }

         if(oldIndex == newIndex) {
            oldIndexIsNewIndex = true;
         }
         else if(oldIndexIsNewIndex && oldIndex > newIndex) {
            columnsInsertedRightOfNewIndex.add(from);
         }
         else {
            columnsInsertedLeftOfNewIndex.add(from);
         }
      }

      for(ColumnRef from : columnsInsertedLeftOfNewIndex) {
         columns.removeAttribute(from);
         newIndex = columns.indexOfAttribute(to);

         if(lastCol) {
            columns.addAttribute(from);
         }
         else {
            columns.addAttribute(newIndex, from);
         }

         if(columns2.containsAttribute(from) && columns2.containsAttribute(to)) {
            int findex = columns2.indexOfAttribute(from);
            int tindex = columns2.indexOfAttribute(to);

            from = (ColumnRef) columns2.getAttribute(findex);
            columns2.removeAttribute(from);

            if(lastCol) {
               columns2.addAttribute(from);
            }
            else {
               columns2.addAttribute(tindex, from);
            }
         }
      }

      for(ColumnRef from : columnsInsertedRightOfNewIndex) {
         columns.removeAttribute(from);
         newIndex = columns.indexOfAttribute(to) + 1;

         if(lastCol) {
            columns.addAttribute(from);
         }
         else {
            columns.addAttribute(newIndex, from);
         }

         if(columns2.containsAttribute(from) && columns2.containsAttribute(to)) {
            int findex = columns2.indexOfAttribute(from);
            int tindex = columns2.indexOfAttribute(to) + 1;

            from = (ColumnRef) columns2.getAttribute(findex);
            columns2.removeAttribute(from);

            if(lastCol) {
               columns2.addAttribute(from);
            }
            else {
               columns2.addAttribute(tindex, from);
            }
         }
      }

      //if it is replace column, we should remove column 'to'
      if(replaceColumn) {
         columns.removeAttribute(to);
         columns2.removeAttribute(to);
      }

      table.setColumnSelection(columns2, true);
      table.setColumnSelection(columns, false);
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private WorksheetService wsEngine;

   protected static final int ROW_LIMIT = 10000;
   protected static final int COL_LIMIT = 1000;
}
