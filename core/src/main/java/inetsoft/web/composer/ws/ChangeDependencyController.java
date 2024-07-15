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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.MirrorVariableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSChangeDependencyEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChangeDependencyController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/change-dependency")
   public void changeDependency(
      @Payload WSChangeDependencyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String oldDepended = event.oldDepended();
      String target = event.target();
      String newDepended = event.newDepended();
      Assembly oldDependedAssembly = ws.getAssembly(oldDepended);
      Assembly targetAssembly = ws.getAssembly(target);
      Assembly newDependedAssembly = ws.getAssembly(newDepended);

      if(!isChangeable(targetAssembly, oldDependedAssembly, newDependedAssembly)) {
         return;
      }

      if(oldDependedAssembly instanceof VariableAssembly ||
         oldDependedAssembly instanceof DateRangeAssembly ||
         oldDependedAssembly instanceof NamedGroupAssembly ||
         oldDependedAssembly instanceof ConditionAssembly)
      {
         if(replaceAssemblies(ws, targetAssembly, oldDependedAssembly,
                              newDependedAssembly))
         {
            // fix bug1282113746872, if change dependency by named group
            // assembly, refresh the source table immediately.
            if(oldDependedAssembly instanceof NamedGroupAssembly &&
               WorksheetEventUtil.refreshVariables(
               rws, super.getWorksheetEngine(), oldDepended, commandDispatcher))
            {
               return;
            }

            WorksheetEventUtil.refreshColumnSelection(rws, target, true);
            // NOTE: load before refresh is different from old version.
            WorksheetEventUtil.loadTableData(rws, target, true, true);
            WorksheetEventUtil.refreshAssembly(rws, target, true, commandDispatcher, principal);
            WorksheetEventUtil.layout(rws, commandDispatcher);
            AssetEventUtil.refreshTableLastModified(ws, target, true);
         }

         return;
      }

      TableAssembly targetTable = (TableAssembly) ws.getAssembly(target);
      TableAssembly oldDependedTable = (TableAssembly) ws.getAssembly(oldDepended);
      TableAssembly newDependedTable = (TableAssembly) ws.getAssembly(newDepended);

      if(targetTable instanceof ComposedTableAssembly && oldDependedTable != null &&
         newDependedTable != null)
      {
         ComposedTableAssembly ctbl = (ComposedTableAssembly) targetTable;

         if(ctbl instanceof CompositeTableAssembly) {
            if(ctbl instanceof ConcatenatedTableAssembly) {
               ConcatenateTablesController.ConcatenationCompatibility compatibility =
                  ConcatenateTablesController.getTableColumnCompatibility(
                  oldDependedTable, newDependedTable);

               if(!compatibility.isCompatible()) {
                  if(compatibility.getCompatibilityFailureMessage() != null) {
                     MessageCommand messageCommand = new MessageCommand();
                     messageCommand.setMessage(compatibility.getCompatibilityFailureMessage());
                     messageCommand.setType(MessageCommand.Type.ERROR);
                     commandDispatcher.sendCommand(messageCommand);
                  }

                  return;
               }
            }
         }

         // do not replace self
         TableAssembly[] subTables = ctbl.getTableAssemblies(false);

         for(TableAssembly subTable : subTables) {
            if(subTable.getName().equals(newDepended)) {
               return;
            }
         }

         // Cyclical dependency check
         if(newDependedTable instanceof ComposedTableAssembly) {
            ComposedTableAssembly compNewDependedTable = (ComposedTableAssembly) newDependedTable;
            TableAssembly[] depArr = compNewDependedTable.getTableAssemblies(false);

            for(TableAssembly aDepArr : depArr) {
               if(aDepArr.getName().equals(target)) {
                  throw new InvalidDependencyException(Catalog.getCatalog().getString(
                     "common.dependencyCycle"));
               }
            }
         }

         for(int i = 0; i < subTables.length; i++) {
            if(subTables[i].getName().equals(oldDepended)) {
               subTables[i] = newDependedTable;
               break;
            }
         }

         if(ctbl instanceof CompositeTableAssembly) {
            ctbl.renameDepended(oldDepended, newDepended);
         }

         ctbl.setTableAssemblies(subTables);

         try {
            ctbl.checkValidity();
         }
         catch(Exception ex) {
            LOG.warn("Failed to check the validity of table assembly: " + ctbl.getName(),
               ex);
         }

         syncColumnSelection(ctbl);
         WorksheetEventUtil.refreshColumnSelection(rws, target, true);
         WorksheetEventUtil.loadTableData(rws, target, true, true);
         WorksheetEventUtil.refreshAssembly(rws, target, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         AssetEventUtil.refreshTableLastModified(ws, target, true);
      }
   }

   /**
    * Column selection may have changed. Make sure references to other columns
    * are still correct.
    */
   private static void syncColumnSelection(TableAssembly tbl) {
      ColumnSelection[] csels = { tbl.getColumnSelection(true),
                                  tbl.getColumnSelection(false) };

      for(ColumnSelection csel : csels) {
         for(int i = 0; i < csel.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) csel.getAttribute(i);
            DataRef ref = column.getDataRef();

            if(ref instanceof NumericRangeRef) {
               NumericRangeRef nref = (NumericRangeRef) ref;
               DataRef baseref = nref.getDataRef();

               // if the base table is changed, the numeric range reference
               // should not use the fully qualified name since the base table
               // is no longer there. The best case is the new table has
               // columns with same name (but different entity/table name)
               nref.setDataRef(new AttributeRef(null, baseref.getAttribute()));
            }
         }
      }
   }

   /**
    * Replace assemblies from the table.
    */
   private boolean replaceAssemblies(Worksheet ws, Assembly src, Assembly target,
                                     Assembly ntarget)
   {
      if(target instanceof VariableAssembly) {
         VariableAssembly var = (VariableAssembly) target;
         VariableAssembly nvar = (VariableAssembly) ntarget;
         UserVariable uvar = (UserVariable) nvar.getVariable().clone();
         uvar.setAlias(nvar.getName());

         if(src instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) src;
            replaceVariable(table.getPreConditionList(), var, uvar);
            replaceVariable(table.getPostConditionList(), var, uvar);
            replaceVariable(table.getRankingConditionList(), var, uvar);
         }
         else if(src instanceof ConditionAssembly) {
            ConditionAssembly cond = (ConditionAssembly) src;
            replaceVariable(cond.getConditionList(), var, uvar);
         }
         else if(src instanceof NamedGroupAssembly) {
            NamedGroupInfo info = ((NamedGroupAssembly) src).getNamedGroupInfo();

            for(int i = 0; i < info.getGroups().length; i++) {
               ConditionList cond = info.getGroupCondition(info.getGroups()[i]);
               replaceVariable(cond, var, uvar);
            }
         }
      }
      else if(target instanceof DateRangeAssembly) {
         DateRangeAssembly var = (DateRangeAssembly) target;
         DateRangeAssembly nvar = (DateRangeAssembly) ntarget.clone();

         if(src instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) src;
            replaceDateRange(table.getPreConditionList(), var, nvar);
            replaceDateRange(table.getPostConditionList(), var, nvar);
            replaceDateRange(table.getRankingConditionList(), var, nvar);
         }
         else if(src instanceof ConditionAssembly) {
            ConditionAssembly cond = (ConditionAssembly) src;
            replaceDateRange(cond.getConditionList(), var, nvar);
         }
         else if(src instanceof NamedGroupAssembly) {
            NamedGroupInfo info = ((NamedGroupAssembly) src).getNamedGroupInfo();

            for(int i = 0; i < info.getGroups().length; i++) {
               ConditionList cond = info.getGroupCondition(info.getGroups()[i]);
               replaceDateRange(cond, var, nvar);
            }
         }
      }
      else if(target instanceof NamedGroupAssembly) {
         TableAssembly table = (TableAssembly) src;
         NamedGroupAssembly group = (NamedGroupAssembly) target;
         NamedGroupAssembly ngroup = (NamedGroupAssembly) ntarget.clone();
         replaceGroupAssemblies(table, group, ngroup);
      }
      else if(target instanceof ConditionAssembly) {
         TableAssembly table = (TableAssembly) src;
         ConditionAssembly var = (ConditionAssembly) target;
         ConditionAssembly nvar = (ConditionAssembly) ntarget.clone();
         return replaceConditionAssemblies(table, var, nvar);
      }

      return true;
   }

   /**
    * Replace the user variable in condition list.
    */
   private void replaceVariable(ConditionListWrapper wrapper,
                                VariableAssembly var, UserVariable nvar)
   {
      ConditionList condition = wrapper.getConditionList();

      for(int i = 0; i < condition.getSize(); i++) {
         XCondition xcond = condition.getXCondition(i);

         if(xcond instanceof RankingCondition) {
            RankingCondition rcond = (RankingCondition) xcond;
            Object obj = rcond.getN();

            if(obj instanceof UserVariable &&
               ((UserVariable) obj).getName().equals(var.getName()))
            {
               rcond.setN(nvar);
            }
         }
         else if(xcond instanceof Condition) {
            Condition cond = (Condition) xcond;

            for(int j = 0; j < cond.getValueCount(); j++) {
               Object obj = cond.getValue(j);

               if(obj != null && obj instanceof UserVariable &&
                  ((UserVariable) obj).getName().equals(getVariableName(var)))
               {
                  cond.setValue(j, nvar);
               }
            }
         }
      }
   }

   /**
    * Get variable name.
    */
   private String getVariableName(VariableAssembly assembly) {
      if(assembly instanceof DefaultVariableAssembly) {
         return assembly.getName();
      }

      while(assembly != null && assembly instanceof MirrorVariableAssembly) {
         MirrorVariableAssemblyInfo info = (MirrorVariableAssemblyInfo)
            assembly.getInfo();
         assembly = (VariableAssembly) info.getImpl().getAssembly();
      }

      return assembly.getVariable().getName();
   }

   /**
    * Replace the date range object in condition list.
    */
   private void replaceDateRange(ConditionListWrapper wrapper,
                                 DateRangeAssembly var, DateRangeAssembly nvar)
   {
      ConditionList condition = wrapper.getConditionList();

      for(int i = 0; i < condition.getSize(); i++) {
         ConditionItem item = condition.getConditionItem(i);

         if(item != null && item.getXCondition() instanceof DateRangeAssembly) {
            DateRangeAssembly range = (DateRangeAssembly) item.getXCondition();

            if(range.getName().equals(var.getName())) {
               nvar.setOperation(range.getOperation());
               nvar.setType(range.getType());
               condition.setConditionItem(i, item.getAttribute(), nvar);
            }
         }
      }
   }

   /**
    * Replace the group assemblies in table assembly.
    */
   private void replaceGroupAssemblies(TableAssembly src,
                                       NamedGroupAssembly group, NamedGroupAssembly ngroup)
   {
      AggregateInfo info = src.getAggregateInfo();
      String name = group.getName();
      String nname = ngroup.getName();

      for(int i = 0; i < info.getGroups().length; i++) {
         GroupRef ref = info.getGroups()[i];

         if(ref.getNamedGroupAssembly() != null &&
            ref.getNamedGroupAssembly().equals(name))
         {
            ref.setNamedGroupAssembly(nname);
         }
      }
   }

   /**
    * Replace condition assemblies in table assembly.
    */
   private boolean replaceConditionAssemblies(TableAssembly src,
                                              ConditionAssembly cond, ConditionAssembly ncond)
   {
      if(!(src instanceof BoundTableAssembly) ||
         !cond.getAttachedSource().equals(ncond.getAttachedSource()))
      {
         return false;
      }

      BoundTableAssembly table = (BoundTableAssembly) src;

      for(int i = 0; i < table.getConditionAssemblyCount(); i++) {
         if(table.getConditionAssembly(i).equals(cond)) {
            table.renameDepended(cond.getName(), ncond.getName());
            break;
         }
      }

      return true;
   }

   /**
    * Check the change dependency operation is supported or not.
    */
   private boolean isChangeable(Assembly src, Assembly target, Assembly ntarget)
   {
      if(target instanceof VariableAssembly &&
         ntarget instanceof VariableAssembly)
      {
         return true;
      }
      else if(target instanceof DateRangeAssembly &&
         ntarget instanceof DateRangeAssembly) {
         return true;
      }
      else if(target instanceof ConditionAssembly &&
         ntarget instanceof ConditionAssembly)
      {
         return true;
      }
      else if(target instanceof NamedGroupAssembly &&
         ntarget instanceof NamedGroupAssembly)
      {
         return true;
      }
      else if(src instanceof TableAssembly &&
         target instanceof TableAssembly &&
         ntarget instanceof TableAssembly)
      {
         return true;
      }

      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ChangeDependencyController.class);
}