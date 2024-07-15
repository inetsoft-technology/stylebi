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
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSRenameColumnEvent;
import inetsoft.web.composer.ws.event.WSRenameColumnEventValidator;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

/**
 * Rename table column controller.
 *
 * @author InetSoft Technology Corp
 * @version 12.3
 */
@Controller
public class RenameColumnController extends WorksheetController {
   /**
    * Rename column.
    */
   public static boolean renameColumn(
      CommandDispatcher commandDispatcher, TableAssembly table,
      ColumnRef ocolumn, String alias)
   {
      ColumnSelection columns = table.getColumnSelection(false);
      int index = columns.indexOfAttribute(ocolumn);

      if(index < 0) {
         if(columns.getAttribute(ocolumn.getAttribute()) != null) {
            index = columns.indexOfAttribute(
               columns.getAttribute(ocolumn.getAttribute()));
         }
         else {
            return false;
         }
      }

      ColumnRef column = (ColumnRef) columns.getAttribute(index);

      if(Tool.equals(alias, ocolumn.getAlias())) {
         return false;
      }

      String name = column.getAlias();
      name = name == null || name.length() == 0 ? column.getAttribute() : name;
      boolean eq = alias.equals(name);
      ColumnRef conflictingColumn = null;

      if(!eq) {
         if(alias.equalsIgnoreCase(column.getAttribute())) {
            conflictingColumn = AssetUtil.findColumnConflictingWithAlias(
               columns, column, alias, false);
         }
         else {
            conflictingColumn = AssetUtil.findColumnConflictingWithAlias(
               columns, column, alias, true);
         }
      }

      if(conflictingColumn != null) {
         MessageCommand command = new MessageCommand();
         command.setMessage(createColumnConflictErrorMessage(alias, conflictingColumn));
         command.setType(MessageCommand.Type.ERROR);
         command.setAssemblyName(table.getName());
         commandDispatcher.sendCommand(command);
         return true;
      }

      ocolumn = column.clone();
      DataRef ref = column.getDataRef();
      String oref;
      String nref;

      Function<String, Boolean> acceptFunc = null;

      if(table instanceof AbstractTableAssembly) {
         SourceInfo sourceInfo = ((AbstractTableAssembly) table).getSourceInfo();

         if(sourceInfo != null && sourceInfo.getSource() != null) {
            acceptFunc = (script) -> {
               return script != null && !script.endsWith(sourceInfo.getSource() + ".");
            };
         }
      }

      // expression? let's change the expression
      if(ref instanceof ExpressionRef) {
         ExpressionRef eref = (ExpressionRef) ref;

         if(alias == null || alias.length() == 0 ||
            alias.equals(eref.getName()))
         {
            return false;
         }

         oref = eref.getName();
         eref.setName(alias);
         nref = alias;
         column.setDataRef(eref);

         // @by davidd 2009-02-12 When renaming expression columns, make sure
         // to carry forward the adhoc.edit property of the renamed column.
         if("true".equals(table.getProperty("adhoc.edit." + oref))) {
            table.clearProperty("adhoc.edit." + oref);
            table.setProperty("adhoc.edit." + alias, "true");
         }
      }
      // normal table and normal column? let's change the alias
      else {
         String oalias = column.getAlias();
         oref = oalias == null || oalias.length() == 0 ? column.getAttribute() : oalias;
         column.setAlias(alias);
         String nalias = column.getAlias();
         nref = nalias == null || nalias.length() == 0 ? column.getAttribute() : nalias;
      }

      if(table instanceof UnpivotTableAssembly) {
         UnpivotTableAssembly unpivot = (UnpivotTableAssembly) table;
         UnpivotTableAssemblyInfo uInfo = (UnpivotTableAssemblyInfo) unpivot.getInfo();
         String type = uInfo.getChangedColType(oref);
         XFormatInfo fmt = uInfo.getChangedTypeColumnFormatInfo(oref);
         uInfo.setColumnType(column, type, fmt, true);
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) columns.getAttribute(i);

         if(!tcolumn.isExpression()) {
            continue;
         }

         ExpressionRef exp = (ExpressionRef) tcolumn.getDataRef();

         if(exp instanceof RangeRef) {
            RangeRef range = (RangeRef) exp;

            if(range.getDataRef().equals(ocolumn.getDataRef())) {
               range.setDataRef((DataRef) column.getDataRef().clone());

               if(range instanceof DateRangeRef && column.getDataRef() instanceof ExpressionRef) {
                  DateRangeRef drange = (DateRangeRef) range;
                  int dgroup = drange.getDateOption();
                  drange.setName(DateRangeRef.getName(column.getAttribute(), dgroup));
               }
            }
         }
         else {
            renameExpression(exp, ocolumn.getName(), nref, acceptFunc);
         }
      }

      renameConditionList(table.getPreConditionList(), ocolumn, column);
      renameConditionList(table.getPostConditionList(), ocolumn, column);
      // @by davyc, why not rename ranking condition? see bug1074735977535
      renameConditionList(table.getRankingConditionList(), ocolumn, column);
      renameSortInfo(table.getSortInfo(), ocolumn, column);
      renameAggregateInfo(null, table, table.getAggregateInfo(), ocolumn, column);

      if(table instanceof UnpivotTableAssembly) {
         DataRef ncol = columns.getAttribute(nref);

         if(ncol instanceof ColumnRef) {
            ((ColumnRef) ncol).setDataType(ocolumn.getDataType());
         }
      }

      table.setColumnSelection(columns);
      return false;
   }

   private boolean canRenameColumn(Worksheet ws, TableAssembly table, ColumnRef column,
                                   CommandDispatcher dispatcher)
   {
      if(!allowsDeletion(ws, table, column)) {
         MessageCommand command = new MessageCommand();
         command.setMessage(Catalog.getCatalog().getString(
            "common.columnDependency", column.getAttribute()));
         command.setType(MessageCommand.Type.WARNING);
         command.setAssemblyName(table.getName());
         dispatcher.sendCommand(command);
         return false;
      }

      return true;
   }

   public static String createColumnConflictErrorMessage(String name, ColumnRef conflictingColumn) {
      if(name == null) {
         return null;
      }

      String message;

      if(name.equalsIgnoreCase(conflictingColumn.getAlias())) {
         message = Catalog.getCatalog().getString("common.duplicateColumnAlias", name);
      }
      else if(name.equalsIgnoreCase(conflictingColumn.getAttribute())) {
         String columnName = conflictingColumn.getAlias() != null ? conflictingColumn.getAlias() :
            conflictingColumn.getAttribute();
         message = Catalog.getCatalog()
            .getString("common.conflictingColumnAttribute", columnName);
      }
      else {
         message = Catalog.getCatalog().getString("common.duplicateName");
      }

      return message;
   }

   /**
    * Rename column.
    */
   public static boolean renameColumn(
      Worksheet ws, CommandDispatcher commandDispatcher,
      TableAssembly table, ColumnRef ocolumn, String alias)
   {
      final ColumnRef originalCol = (ColumnRef) Tool.clone(ocolumn);
      ColumnSelection columns = table.getColumnSelection(false);
      int index = columns.indexOfAttribute(ocolumn);

      if(index < 0) {
         if(columns.getAttribute(ocolumn.getAttribute()) != null) {
            index = columns.indexOfAttribute(
               columns.getAttribute(ocolumn.getAttribute()));
         }
      }

      boolean failed = renameColumn(commandDispatcher, table, ocolumn, alias);

      if(index >= 0) {
         ocolumn = (ColumnRef) columns.getAttribute(index);
      }

      if(!failed) {
         renameTableColumn(ws, table, originalCol, ocolumn);
      }

      return failed;
   }

   /**
    * Rename mirror table column name.
    * @param ws    the used worksheet.
    * @param table the originally table .
    * @param ocolumn column in mirror to rename
    * @param ncolumn column ref with new name
    */
   public static void renameTableColumn(Worksheet ws, TableAssembly table,
                                        ColumnRef ocolumn, ColumnRef ncolumn)
   {
      Assembly[] assemblies = ws.getAssemblies();
      String tableName = table.getName();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof ComposedTableAssembly) {
            ComposedTableAssembly composedTable = (ComposedTableAssembly) assembly;
            String[] tableAssemblies = composedTable.getTableNames();

            for(String tableAssembly : tableAssemblies) {
               if(Tool.equals(table.getName(), tableAssembly)) {
                  final DataRef column = findColumn(ocolumn, composedTable);

                  if(column instanceof ColumnRef) {
                     renameTableColumn(ws, composedTable, (ColumnRef) column, ncolumn);
                  }

                  renameTableColumn0(ws, table, composedTable, ocolumn, ncolumn);
               }
            }

            renameJoinOperators(composedTable, ocolumn, ncolumn);
         }

         if(assembly instanceof AbstractTableAssembly) {
            Set<AssemblyRef> expressionDeps = new HashSet<>();
            ((AbstractTableAssembly) assembly).getExpressionDependeds(expressionDeps);
            Optional<AssemblyEntry> depOp = expressionDeps.stream()
               .map(AssemblyRef::getEntry)
               .filter(entry -> Tool.equals(entry, table.getAssemblyEntry()))
               .findFirst();

            if(depOp.isPresent()) {
               renameTableExpressionColumn(table, ocolumn, ncolumn, (TableAssembly) assembly);
            }
         }

         if(assembly instanceof DefaultVariableAssembly) {
            DefaultVariableAssembly variable = (DefaultVariableAssembly) assembly;
            AssetVariable var = variable.getVariable();

            if(!Tool.equals(tableName, var.getTableName())) {
               continue;
            }

            ColumnRef oldVariableRef = createColumnRefForVariable(ocolumn, ocolumn);
            DataRef lref = var.getLabelAttribute();

            if(Tool.equals(lref, ocolumn) || Tool.equals(lref, oldVariableRef)) {
               var.setLabelAttribute(createColumnRefForVariable(ncolumn, ocolumn));
            }

            DataRef vref = var.getValueAttribute();

            if(Tool.equals(vref, ocolumn) || Tool.equals(vref, oldVariableRef)) {
               var.setValueAttribute(createColumnRefForVariable(ncolumn, ocolumn));
            }
         }
      }
   }

   private static ColumnRef createColumnRefForVariable(ColumnRef ncolumn, ColumnRef ocolumn) {
      String name = ncolumn.getAlias() != null ? ncolumn.getName() : ncolumn.getAttribute();
      AttributeRef attr = new AttributeRef(ocolumn.getEntity(), name);
      return new ColumnRef(attr);
   }

   private static void renameJoinOperators(ComposedTableAssembly table, ColumnRef ocolumn,
                                    ColumnRef ncolumn)
   {
      if(!(table.getTableInfo() instanceof CompositeTableAssemblyInfo)) {
         return;
      }

      CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo)
         table.getTableInfo();
      Enumeration<?> iter = info.getOperatorTables();

      while(iter.hasMoreElements()) {
         String[] pair = (String[]) iter.nextElement();
         TableAssemblyOperator op = info.getOperator(pair[0], pair[1]);
         TableAssemblyOperator.Operator[] ops = op.getOperators();

         if(ops == null) {
            continue;
         }

         for(int i = ops.length - 1; i >= 0 ; i--) {
            if((Tool.equals(table.getName(), ops[i].getLeftTable()) ||
               ocolumn.getDataRef().isExpression()) &&
               Tool.equals(ocolumn, ops[i].getLeftAttribute()))
            {
               ops[i].setLeftAttribute(ncolumn);
            }

            if((Tool.equals(table.getName(), ops[i].getRightTable()) ||
               ocolumn.getDataRef().isExpression()) &&
               Tool.equals(ocolumn, ops[i].getRightAttribute()))
            {
               ops[i].setRightAttribute(ncolumn);
            }
         }
      }
   }

   private static void renameTableExpressionColumn(TableAssembly namedColtable,
                                            ColumnRef ocolumn,
                                            ColumnRef ncolumn,
                                            TableAssembly changeColtable)
   {
      final ColumnSelection columns = changeColtable.getColumnSelection();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef)) {
            continue;
         }

         DataRef ref = ((ColumnRef) column).getDataRef();

         if(ref instanceof ExpressionRef) {
            renameTableColumnExpression((ExpressionRef) ref, namedColtable.getName(), ocolumn,
               ncolumn);
         }
      }
   }

   private static void renameTableColumnExpression(ExpressionRef ref, String changedTable,
                                              ColumnRef ocolumn, ColumnRef ncolumn)
   {
      final StringBuilder sb = new StringBuilder();

      ScriptIterator.ScriptListener listener = (ScriptIterator.Token token, ScriptIterator.Token pref, ScriptIterator.Token cref) -> {
         if(pref != null && Tool.equals(pref.val, changedTable) && token.isRef() &&
            token.val.equals(ocolumn.getName()) && (cref == null || !"[".equals(cref)))
         {
            sb.append(new ScriptIterator.Token(token.type, ncolumn.getName(), token.length));
         }
         else {
            sb.append(token);
         }
      };

      ScriptIterator iterator = new ScriptIterator(ref.getExpression());
      iterator.addScriptListener(listener);
      iterator.iterate();
      ref.setExpression(sb.toString());
   }

   private static DataRef findColumn(ColumnRef ocolumn, TableAssembly composedTable) {
      return findColumn(ocolumn, composedTable, false);
   }

   private static DataRef findColumn(ColumnRef ocolumn, TableAssembly composedTable, boolean mirror) {
      final ColumnSelection columns = composedTable.getColumnSelection();
      DataRef attribute = columns.getAttribute(mirror && !Tool.isEmptyString(ocolumn.getAlias()) ? ocolumn.getAlias()
         : ocolumn.getAttribute());

      if(attribute == null) {
         attribute = columns.getAttribute(ocolumn.getName());
      }

      return attribute;
   }

   /**
    * Rename mirror table column.
    * @param mirror the table .
    * @param ocolumn original column
    * @param ncolumn new column
    */
   private static void renameTableColumn0(Worksheet ws, TableAssembly base, TableAssembly mirror,
                                         ColumnRef ocolumn, ColumnRef ncolumn)
   {
      final DataRef ref = findColumn(ocolumn, mirror, true);

      if(!(ref instanceof ColumnRef)) {
         return;
      }

      ColumnRef column = (ColumnRef) ref;
      String name = ncolumn.getAlias() != null ? ncolumn.getName() : ncolumn.getAttribute();
      AttributeRef newAttr = new AttributeRef(column.getEntity(), name);
      ColumnRef newMirrorCol = new ColumnRef(newAttr);
      newMirrorCol.setOldName(ocolumn.getOldName());
      newMirrorCol.setLastOldName(ocolumn.getLastOldName());
      renameMirrorConditionList(mirror.getPreConditionList(), column, newMirrorCol);
      renameMirrorConditionList(mirror.getPostConditionList(), column, newMirrorCol);
      renameMirrorConditionList(mirror.getRankingConditionList(), column, newMirrorCol);
      renameSortInfo(mirror.getSortInfo(), column, ncolumn);
      renameAggregateInfo(ws, mirror, mirror.getAggregateInfo(), column, newMirrorCol);

      if(mirror instanceof CompositeTableAssembly) {
         CompositeTableAssembly composite = (CompositeTableAssembly) mirror;
         String[] tableNames = composite.getTableNames();

         for(int i = 0; i < composite.getOperatorCount(); i++) {
            for(int j = i + 1; j < composite.getOperatorCount(); j++) {
               TableAssemblyOperator tableOperator = composite.getOperator(tableNames[i], tableNames[j]);

               if(tableOperator == null) {
                  continue;
               }

               for(int k = 0; k < tableOperator.getOperatorCount(); k++) {
                  TableAssemblyOperator.Operator operator = tableOperator.getOperator(k);

                  if(Objects.equals(ocolumn, operator.getLeftAttribute())) {
                     operator.setLeftAttribute(ncolumn);
                  }

                  if(Objects.equals(ocolumn, operator.getRightAttribute())) {
                     operator.setRightAttribute(ncolumn);
                  }
               }
            }
         }
      }

      ColumnSelection cols = mirror.getColumnSelection();
      ColumnSelection pcols = mirror.getColumnSelection(true);

      if(mirror instanceof RelationalJoinTableAssembly) {
         for(int i = 0; i < pcols.getAttributeCount(); i ++) {
            if(pcols.getAttribute(i).getAttribute().equals(ocolumn.getDisplayName())) {
               pcols.setAttribute(i, newMirrorCol);
            }
         }
      }

      for(int i = 0; i < cols.getAttributeCount(); i ++) {
         if(cols.getAttribute(i).getAttribute().equals(ocolumn.getDisplayName())) {
            cols.setAttribute(i, newMirrorCol);
         }
         else if(cols.getAttribute(i) instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) cols.getAttribute(i);

            if(col.getDataRef() instanceof DateRangeRef) {
               DateRangeRef rangeRef = (DateRangeRef) col.getDataRef();

               if(rangeRef.getDataRef() instanceof AttributeRef) {
                  AttributeRef aref = (AttributeRef) rangeRef.getDataRef();

                  if(Tool.equals(base.getName(), aref.getEntity()) &&
                     Tool.equals(ocolumn.getAttribute(), aref.getAttribute()))
                  {
                     rangeRef.setDataRef(newAttr);
                  }
               }
            }
            else if(col.getDataRef() instanceof ExpressionRef) {
               String oldName = ocolumn.getName();
               String newName = ncolumn.getName();

               if(ocolumn.getEntity() != null && Tool.isEmptyString(ocolumn.getAlias()) &&
                  oldName.startsWith(ocolumn.getEntity() + "."))
               {
                  oldName = oldName.substring(ocolumn.getEntity().length() + 1);
               }

               if(ncolumn.getEntity() != null && Tool.isEmptyString(ncolumn.getAlias()) &&
                  newName.startsWith(ncolumn.getEntity() + "."))
               {
                  newName = newName.substring(ncolumn.getEntity().length() + 1);
               }

               renameExpression((ExpressionRef) col.getDataRef(),
                  base.getName() + "." + oldName,
                  base.getName() + "." + newName, null);
            }
         }
      }

      mirror.setColumnSelection(cols);
      mirror.setColumnSelection(pcols, true);
   }

   /**
    * Rename a condition list.
    */
   private static void renameMirrorConditionList(
      ConditionListWrapper wrapper,
      ColumnRef ocol, ColumnRef ncol)
   {
      if(!(wrapper instanceof ConditionList)) {
         return;
      }

      ConditionList list = (ConditionList) wrapper;

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);
         DataRef ref = item.getAttribute();

         if(ref instanceof AggregateRef) {
            ref = ((AggregateRef) ref).getDataRef();
         }

         if((ref instanceof ColumnRef) && ref.equals(ocol)) {
            final DataRef dataRef = ((ColumnRef) ref).getDataRef();

            if(dataRef instanceof AttributeRef) {
               ((ColumnRef) ref).setDataRef(new AttributeRef(ref.getEntity(),
                                                             ncol.getDisplayName()));
               ((ColumnRef) ref).setView(ref.getName());
            }
            else {
               item.setAttribute(ncol);
            }
         }
         else if(ref instanceof GroupRef && ((GroupRef) ref).getDataRef() instanceof ColumnRef) {
            ColumnRef col = (ColumnRef)((GroupRef) ref).getDataRef();

            if(col.getDataRef() instanceof DateRangeRef) {
               DateRangeRef rangeRef = (DateRangeRef) col.getDataRef();

               if(rangeRef.getDataRef() instanceof AttributeRef) {
                  String name = ncol.getAlias() != null ? ncol.getName() : ncol.getAttribute();
                  AttributeRef newAttr = new AttributeRef(null, name);
                  AttributeRef aref = (AttributeRef) rangeRef.getDataRef();

                  if(Tool.equals(ocol.getAttribute(), aref.getAttribute())) {
                     rangeRef.setDataRef(newAttr);
                     rangeRef.setName(DateRangeRef.getName(rangeRef.getDataRef().getName(),
                        rangeRef.getDateOption()));
                     col.setView(rangeRef.toView());
                  }
               }
            }
            else if(ref.equals(ocol)) {
               ((GroupRef) ref).setDataRef(ncol);
            }
         }

         if(item.getXCondition() instanceof RankingCondition) {
            RankingCondition rcond = (RankingCondition) item.getXCondition();
            DataRef rref = rcond.getDataRef();
            renameConditionValue(rref, ocol, ncol);
         }
      }
   }

   private static void renameConditionValue(DataRef ref, ColumnRef ocol, ColumnRef ncol) {
      if(ref instanceof AggregateRef) {
         ref = ((AggregateRef) ref).getDataRef();
      }

      if((ref instanceof ColumnRef) && ref.equals(ocol)) {
         final DataRef dataRef = ((ColumnRef) ref).getDataRef();

         if(dataRef instanceof AttributeRef) {
            ((ColumnRef) ref).setDataRef(new AttributeRef(ref.getEntity(),
               ncol.getDisplayName()));
            ((ColumnRef) ref).setView(ref.getName());
         }
      }
      else if(ref instanceof GroupRef && ((GroupRef) ref).getDataRef() instanceof ColumnRef) {
         ColumnRef col = (ColumnRef)((GroupRef) ref).getDataRef();

         if(col.getDataRef() instanceof DateRangeRef) {
            DateRangeRef rangeRef = (DateRangeRef) col.getDataRef();

            if(rangeRef.getDataRef() instanceof AttributeRef) {
               String name = ncol.getAlias() != null ? ncol.getName() : ncol.getAttribute();
               AttributeRef newAttr = new AttributeRef(null, name);
               AttributeRef aref = (AttributeRef) rangeRef.getDataRef();

               if(Tool.equals(ocol.getAttribute(), aref.getAttribute())) {
                  rangeRef.setDataRef(newAttr);
                  rangeRef.setName(DateRangeRef.getName(rangeRef.getDataRef().getName(),
                     rangeRef.getDateOption()));
                  col.setView(rangeRef.toView());
               }
            }
         }
      }
   }

   @RequestMapping(
      value = "api/composer/worksheet/rename-column/{runtimeId}",
      method = RequestMethod.POST)
   @ResponseBody
   public WSRenameColumnEventValidator validateOpen(
      @RequestBody WSRenameColumnEvent event,
      @PathVariable("runtimeId") String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      HashSet<String> nameset = new HashSet<>();
      nameset.add(tname);

      if(table != null &&
         AssetEventUtil.hasDependent(table, ws, nameset))
      {
         String message =
            Catalog.getCatalog().getString("assembly.rename.dependency");

         return WSRenameColumnEventValidator.builder()
            .modifyDependencies(message)
            .build();
      }

      return null;
   }

   /**
    * Process rename column event.
    */
   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/rename-column")
   public void renameColumn(
      @Payload WSRenameColumnEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.tableName();
      String alias = event.newAlias();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnRef column = (ColumnRef) table.getColumnSelection()
         .getAttribute(event.columnName());

      if(column == null) {
         return;
      }

      HashSet<String> nameset = new HashSet<>();
      nameset.add(tname);

      // Choose to not modify dependent columns
      if(!event.modifyDependencies() && AssetEventUtil.hasDependent(table, ws, nameset)) {
         return;
      }

      if(!canRenameColumn(ws, table, column, commandDispatcher)) {
         return;
      }

      renameColumn(ws, commandDispatcher, table, column, alias);
      WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
      WorksheetEventUtil.loadTableData(rws, tname, true, true);
      WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);

      AssetEventUtil.refreshTableLastModified(ws, tname, true);
   }

   /**
    * Rename sort info.
    */
   private static void renameSortInfo(SortInfo sinfo, ColumnRef ocol, ColumnRef ncol) {
      SortRef[] sorts = sinfo.getSorts();

      for(int i = 0; i < sorts.length; i++) {
         DataRef ref = sorts[i].getDataRef();

         if(ref.equals(ocol)) {
            sorts[i].setDataRef(ncol);
         }
      }
   }

   /**
    * Rename aggregate info.
    */
   private static void renameAggregateInfo(Worksheet ws, TableAssembly mirror,
      AggregateInfo ainfo, ColumnRef ocol,
      ColumnRef ncol)
   {
      GroupRef[] groups = ainfo.getGroups();

      for(int i = 0; i < groups.length; i++) {
         DataRef ref = groups[i].getDataRef();

         if(ref.equals(ocol)) {
            groups[i].setDataRef(ncol);
         }
         else if(ref instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) ref;

            if(col.getDataRef() instanceof DateRangeRef) {
               DateRangeRef dateRangeRef = (DateRangeRef) col.getDataRef();

               if(Tool.equals(dateRangeRef.getDataRef(), ocol.getDataRef())) {
                  DataRef nref = ncol.getDataRef();
                  int option = dateRangeRef.getDateOption();
                  String name = DateRangeRef.getName(nref.getAttribute(), option);
                  DateRangeRef nrange = new DateRangeRef(name, nref, option);
                  nrange.setOriginalType(dateRangeRef.getOriginalType());
                  col.setDataRef(nrange);
                  renameMirrorColumn(ws, mirror, dateRangeRef, nrange);
               }
            }
         }
      }

      AggregateRef[] aggrs = ainfo.getAggregates();

      for(int i = 0; i < aggrs.length; i++) {
         DataRef ref = aggrs[i].getDataRef();

         if(ref.equals(ocol)) {
            aggrs[i].setDataRef(ncol);
         }

         ref = aggrs[i].getSecondaryColumn();

         if(ref != null && ref.equals(ocol)) {
            aggrs[i].setSecondaryColumn(ncol);
         }
      }
   }

   // rename using current mirror query's date range ref column.
   private static void renameMirrorColumn(Worksheet ws, TableAssembly base, DateRangeRef oref,
                                          DateRangeRef nref)
   {
      if(ws == null) {
         return;
      }

      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof MirrorTableAssembly) {
            MirrorTableAssembly mirror = (MirrorTableAssembly) assembly;

            if(Tool.equals(base.getName(), mirror.getAssemblyName())) {
               AggregateInfo ainfo = mirror.getAggregateInfo();

               for(int i = 0; i < ainfo.getGroupCount(); i++) {
                  if(!(ainfo.getGroup(i).getDataRef() instanceof ColumnRef)) {
                     continue;
                  }

                  ColumnRef columnRef = (ColumnRef) ainfo.getGroup(i).getDataRef();

                  if(columnRef.getDataRef() instanceof DateRangeRef) {
                     DateRangeRef range = (DateRangeRef) columnRef.getDataRef();
                     AttributeRef attr = (AttributeRef) range.getDataRef();

                     if(Tool.equals(attr.getAttribute(), oref.getAttribute()) &&
                        Tool.equals(attr.getEntity(), base.getName()))
                     {
                        AttributeRef nattr = new AttributeRef(base.getName(), nref.getAttribute());
                        range.setDataRef(nattr);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Rename a condition list.
    */
   private static void renameConditionList(
      ConditionListWrapper wrapper,
      ColumnRef ocol, ColumnRef ncol)
   {
      if(!(wrapper instanceof ConditionList)) {
         return;
      }

      ConditionList list = (ConditionList) wrapper;

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem item = list.getConditionItem(i);
         DataRef ref = item.getAttribute();

         if((ref instanceof ColumnRef) && ref.equals(ocol)) {
            item.setAttribute(ncol);
         }
         else if(ref instanceof GroupRef && ((GroupRef) ref).getDataRef() instanceof ColumnRef) {
            ColumnRef col = (ColumnRef)((GroupRef) ref).getDataRef();

            if(Tool.equals(ocol, col)) {
               item.setAttribute(ncol);
            }
            else if(col.getDataRef() instanceof DateRangeRef) {
               DateRangeRef rangeRef = (DateRangeRef) col.getDataRef();

               if(rangeRef.getDataRef() instanceof AttributeRef) {
                  String name = ncol.getAlias() != null ? ncol.getName() : ncol.getAttribute();
                  AttributeRef newAttr = new AttributeRef(ocol.getEntity(), name);
                  AttributeRef aref = (AttributeRef) rangeRef.getDataRef();

                  if(Tool.equals(ocol.getEntity(), aref.getEntity()) &&
                     Tool.equals(ocol.getName(), rangeRef.getName()))
                  {
                     rangeRef.setDataRef(newAttr);
                     rangeRef.setName(ncol.getName());
                     col.setView(ncol.toView());
                  }
               }
            }
            else if(col.equals(ocol)) {
               GroupRef gref = (GroupRef) ref;
               gref.setDataRef(ncol);
            }
         }

         XCondition xCondition = item.getXCondition();

         if(xCondition instanceof RankingCondition) {
            DataRef dataRef = ((RankingCondition) xCondition).getDataRef();

            if(dataRef instanceof AggregateRef && ((AggregateRef) dataRef).getDataRef() instanceof ColumnRef) {
               if(((AggregateRef) dataRef).getDataRef().equals(ocol)) {
                  ((AggregateRef) dataRef).setDataRef(ncol);
               }
            }
         }
      }
   }

   /**
    * Rename an expression.
    *
    * @param column the specified expression column.
    * @param oname  the specified original name.
    * @param nname  the specified new name.
    */
   private static void renameExpression(ExpressionRef column, String oname, String nname,
                                        Function<String, Boolean> acceptFunc)
   {
      String exp = column.getExpression();
      column.setExpression(Util.renameScriptDepended(oname, nname, exp, acceptFunc));
   }
}
