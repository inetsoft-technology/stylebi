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

import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
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
   public RenameColumnController(RenameColumnServiceProxy renameColumnService) {
      this.renameColumnService = renameColumnService;
   }

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
         // commandDispatcher is null when this is called from a caller with no live UI session
         // to report to (e.g. the wiz agent bridge, Bug #77005) -- such a caller pre-checks the
         // identical conflict via findRenameConflict() below and reports it its own way, so there
         // is nothing left to dispatch a MessageCommand to here.
         if(commandDispatcher != null) {
            MessageCommand command = new MessageCommand();
            command.setMessage(createColumnConflictErrorMessage(alias, conflictingColumn));
            command.setType(MessageCommand.Type.ERROR);
            command.setAssemblyName(table.getName());
            commandDispatcher.sendCommand(command);
         }

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

      // pre, post, ranking (@by davyc, why not rename ranking condition? see
      // bug1074735977535) and the four MV lists (Bug #78234 / WBS-107)
      for(ConditionListWrapper conds : conditionLists(table)) {
         renameConditionList(conds, ocolumn, column);
      }

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
    * Finds the existing column on {@code table} that would collide with renaming {@code ocolumn}
    * to {@code alias}, mirroring the same conflict check {@link #renameColumn(CommandDispatcher,
    * TableAssembly, ColumnRef, String)} runs before mutating anything (lines 60-93 there) --
    * duplicated here, read-only, rather than extracted out of that already-tested method, so a
    * caller with no {@link CommandDispatcher} (Bug #77005, the wiz agent bridge) can pre-check
    * the identical conflict and report it its own way (a {@link
    * inetsoft.web.wiz.pairing.PairingException}, not a {@link MessageCommand}) before calling
    * {@link #renameColumn(Worksheet, CommandDispatcher, TableAssembly, ColumnRef, String)} with a
    * {@code null} dispatcher.
    *
    * @return the conflicting column, or {@code null} if {@code ocolumn} could not be found, the
    *         rename is a no-op (unchanged alias), or there is no conflict
    */
   public static ColumnRef findRenameConflict(TableAssembly table, ColumnRef ocolumn, String alias) {
      ColumnSelection columns = table.getColumnSelection(false);
      int index = columns.indexOfAttribute(ocolumn);

      if(index < 0) {
         if(columns.getAttribute(ocolumn.getAttribute()) != null) {
            index = columns.indexOfAttribute(columns.getAttribute(ocolumn.getAttribute()));
         }
         else {
            return null;
         }
      }

      ColumnRef column = (ColumnRef) columns.getAttribute(index);

      if(Tool.equals(alias, ocolumn.getAlias())) {
         return null;
      }

      String name = column.getAlias();
      name = name == null || name.length() == 0 ? column.getAttribute() : name;

      if(alias.equals(name)) {
         return null;
      }

      if(alias.equalsIgnoreCase(column.getAttribute())) {
         return AssetUtil.findColumnConflictingWithAlias(columns, column, alias, false);
      }

      return AssetUtil.findColumnConflictingWithAlias(columns, column, alias, true);
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
                  // Bug #78234 / WBS-103: a composed table with more than one source (concat,
                  // join, merge join, cross join) can hold several columns with the same bare
                  // name, from different sources -- so the propagated column is identified by
                  // its SOURCE (entity) there, never by bare name. A mirror is routed the same way
                  // (Bug #77005 / WBS-102): its column may carry its OWN alias, which is its
                  // output name and which the bare-name lookup below can neither find nor keep.
                  // Rotate and unpivot keep the original bare-name path below unchanged.
                  if(isMultiSource(composedTable) || composedTable instanceof MirrorTableAssembly) {
                     ColumnRef sourceColumn = findSourceColumn(ocolumn, table, composedTable);

                     if(sourceColumn != null) {
                        renameMultiSourceColumn(
                           ws, table, composedTable, ocolumn, ncolumn, sourceColumn);
                     }

                     continue;
                  }

                  final DataRef column = findColumn(ocolumn, composedTable);

                  // Bug #77005 / WBS-094: capture composedTable's OWN pre-rename BARE local
                  // attribute name (never entity-qualified -- field['Amount'] addresses a
                  // column by its bare attribute, the same identity getAttribute() returns)
                  // now, before renameTableColumn0 runs below. renameMirrorConditionList
                  // (called from within it) can mutate THIS EXACT ColumnRef object in place
                  // via setDataRef -- when a downstream condition's own attribute happens to
                  // be this same object, e.g. because addFilter reuses the live column
                  // reference rather than cloning it -- so reading its name AFTER
                  // renameTableColumn0 runs would already see the NEW name, not the old one
                  // this scan needs to search for.
                  String oldLocalName =
                     column instanceof ColumnRef ? ((ColumnRef) column).getAttribute() : null;
                  String oldLocalEntity =
                     column instanceof ColumnRef ? ((ColumnRef) column).getEntity() : null;

                  if(column instanceof ColumnRef) {
                     renameTableColumn(ws, composedTable, (ColumnRef) column, ncolumn);
                  }

                  renameTableColumn0(ws, table, composedTable, ocolumn, ncolumn);

                  // Bug #77005 / WBS-094: composedTable's OWN expression columns may reference
                  // the renamed column purely by ITS OWN (pre-rename) local identity --
                  // field['Amount'], with no cross-table qualifier at all -- which
                  // getExpressionDependeds (below) can never detect, since that mechanism only
                  // recognizes a script referencing ANOTHER assembly BY NAME, not a same-table
                  // local field access.
                  if(oldLocalName != null) {
                     String newLocalName = ncolumn.getAlias() != null ?
                        ncolumn.getName() : ncolumn.getAttribute();
                     renameComposedTableOwnExpressions(
                        composedTable, oldLocalEntity, oldLocalName, newLocalName);
                  }
               }
            }

            renameJoinOperators(composedTable, tableName, ocolumn, ncolumn);
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

   /**
    * Bug #78234 / WBS-103: whether a composed table draws its columns from more than one source
    * table, so a bare column name may not identify which source a column comes from.
    */
   private static boolean isMultiSource(ComposedTableAssembly composedTable) {
      String[] names = composedTable.getTableNames();
      return names != null && names.length > 1;
   }

   /**
    * Bug #78234 / WBS-103: finds the column of a multi-source composed table that comes FROM
    * {@code source}'s column {@code ocolumn}. Composed tables build each column as
    * {@code AssetUtil.getOuterAttribute(sourceName, sourceColumn)} -- entity = the source table,
    * attribute = the source column's output name (alias, else attribute) -- so a column belongs to
    * the renamed source iff its entity is that source and its attribute is the source column's old
    * output name. The alias is tried against every column before the attribute: after an earlier
    * rename (alias Item over attribute Product) a different column can carry the attribute Product.
    * A concat's columns all carry its FIRST source as entity, so a non-first source never matches
    * and the concat is left alone, which is correct: its output does not follow that source.
    *
    * @return the matching column, or {@code null} when the rename does not reach this table.
    */
   private static ColumnRef findSourceColumn(ColumnRef ocolumn, TableAssembly source,
                                             ComposedTableAssembly composedTable)
   {
      ColumnSelection columns = composedTable.getColumnSelection();
      String sourceName = source.getName();
      List<String> names = new ArrayList<>();

      if(!Tool.isEmptyString(ocolumn.getAlias())) {
         names.add(ocolumn.getAlias());
      }

      if(!Tool.isEmptyString(ocolumn.getAttribute()) && !names.contains(ocolumn.getAttribute())) {
         names.add(ocolumn.getAttribute());
      }

      for(String name : names) {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            if(columns.getAttribute(i) instanceof ColumnRef col &&
               Tool.equals(sourceName, col.getEntity()) && name.equals(col.getAttribute()))
            {
               return col;
            }
         }
      }

      // A legacy column with no entity at all cannot be attributed to a source; match it by name
      // rather than silently skipping it. Never the composed table's own expression columns.
      for(String name : names) {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            if(columns.getAttribute(i) instanceof ColumnRef col &&
               Tool.isEmptyString(col.getEntity()) && !col.isExpression() &&
               name.equals(col.getAttribute()))
            {
               return col;
            }
         }
      }

      return null;
   }

   /**
    * Bug #78234 / WBS-103: carries a source column rename into a multi-source composed table:
    * re-points exactly {@code column} (the column that comes from the renamed source) and the
    * table's own conditions/sort/aggregates/expressions on it, then -- only if that column's
    * OUTPUT name actually changed -- carries the rename on to the composed table's own
    * consumers, under the composed column's own old/new identity.
    */
   private static void renameMultiSourceColumn(Worksheet ws, TableAssembly source,
                                               ComposedTableAssembly composedTable,
                                               ColumnRef ocolumn, ColumnRef ncolumn,
                                               ColumnRef column)
   {
      // Snapshot BEFORE anything is renamed: a condition can share this exact ColumnRef object
      // and renameMirrorConditionList renames it in place (Bug #77005 / WBS-094).
      ColumnRef oldColumn = (ColumnRef) column.clone();
      ColumnRef newColumn =
         renameTableColumn0(ws, source, composedTable, ocolumn, ncolumn, column, true);
      String oldOutput = outputName(oldColumn);
      String newOutput = outputName(newColumn);

      // The table's own expression columns: field['<output name>'] follows the output name, and
      // the qualified field['<source>.<attribute>'] follows the attribute.
      Map<String, String> renames = new HashMap<>();

      if(!Tool.equals(oldOutput, newOutput)) {
         renames.put(oldOutput, newOutput);
      }

      if(!Tool.isEmptyString(oldColumn.getEntity()) &&
         !Tool.equals(oldColumn.getAttribute(), newColumn.getAttribute()))
      {
         renames.put(oldColumn.getEntity() + "." + oldColumn.getAttribute(),
                     newColumn.getEntity() + "." + newColumn.getAttribute());
      }

      renameOwnExpressions(composedTable, renames);

      // A kept alias (e.g. a join's Product_1) means the composed table's output name did not
      // change, so nothing downstream of it refers to anything that moved.
      if(Tool.equals(oldOutput, newOutput)) {
         return;
      }

      String sourceNewName = ncolumn.getAlias() != null ? ncolumn.getName() : ncolumn.getAttribute();
      ColumnRef next = ncolumn;

      // The composed column's final name differs from the source's new name when it had to be
      // aliased to stay unique; downstream must follow that final name, not the source's.
      if(!Tool.equals(newOutput, sourceNewName)) {
         next = (ColumnRef) ncolumn.clone();
         next.setAlias(newOutput);
      }

      renameTableColumn(ws, composedTable, oldColumn, next);
   }

   /** A column's name as the table exposes it: its alias, else its attribute. */
   private static String outputName(ColumnRef column) {
      return !Tool.isEmptyString(column.getAlias()) ? column.getAlias() : column.getAttribute();
   }

   /**
    * Bug #78234 / WBS-103: a unique alias for a column renamed to {@code name} inside a composed
    * table where a sibling already exposes that name -- the same {@code name_i} shape
    * {@link AssetUtil#fixAlias} gives a duplicate, but assigned to the renamed column ONLY.
    * Re-running the positional fixAlias over the whole selection could instead re-alias a column
    * that was never renamed, silently changing its output name under its own consumers.
    *
    * @return {@code null} when {@code name} does not collide.
    */
   private static String uniqueAlias(ColumnSelection columns, int self, String name) {
      if(!outputNameTaken(columns, self, name)) {
         return null;
      }

      for(int i = 1; ; i++) {
         String alias = name + "_" + i;

         if(!outputNameTaken(columns, self, alias)) {
            return alias;
         }
      }
   }

   private static boolean outputNameTaken(ColumnSelection columns, int self, String name) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(i != self && columns.getAttribute(i) instanceof ColumnRef col &&
            name.equalsIgnoreCase(outputName(col)))
         {
            return true;
         }
      }

      return false;
   }

   private static ColumnRef createColumnRefForVariable(ColumnRef ncolumn, ColumnRef ocolumn) {
      String name = ncolumn.getAlias() != null ? ncolumn.getName() : ncolumn.getAttribute();
      AttributeRef attr = new AttributeRef(ocolumn.getEntity(), name);
      return new ColumnRef(attr);
   }

   /**
    * Renames {@code ocolumn} in {@code table}'s join operators. An expression column (a range
    * column included) is matched on the operator side of {@code sourceName}, the table it was
    * renamed on, only: another table's same-named expression key is a different column (Bug
    * #78527, an unrelated join re-keyed on both sides by a date range re-level).
    */
   private static void renameJoinOperators(ComposedTableAssembly table, String sourceName,
                                           ColumnRef ocolumn, ColumnRef ncolumn)
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

         boolean expression = ocolumn.getDataRef().isExpression();

         for(int i = ops.length - 1; i >= 0 ; i--) {
            if((Tool.equals(table.getName(), ops[i].getLeftTable()) ||
               expression && Tool.equals(sourceName, ops[i].getLeftTable())) &&
               Tool.equals(ocolumn, ops[i].getLeftAttribute()))
            {
               ops[i].setLeftAttribute(ncolumn);
            }

            if((Tool.equals(table.getName(), ops[i].getRightTable()) ||
               expression && Tool.equals(sourceName, ops[i].getRightTable())) &&
               Tool.equals(ocolumn, ops[i].getRightAttribute()))
            {
               ops[i].setRightAttribute(ncolumn);
            }
         }
      }
   }

   /**
    * Bug #77005 / WBS-094: rewrites {@code composedTable}'s OWN expression-column script text
    * that references {@code oldLocalName} under composedTable's OWN local column identity (e.g.
    * a mirror's own {@code field['Amount']} expression, addressing its own mirrored "Amount"
    * column). This is the SAME rewrite {@link Util#renameScriptRefDepended} already applies for
    * a table renaming ITS OWN expression columns ({@link #renameColumn(CommandDispatcher,
    * TableAssembly, ColumnRef, String)}, the {@code exprs} loop above) -- applied here to a
    * DOWNSTREAM table's own expressions instead, unconditional on {@link
    * AbstractTableAssembly#getExpressionDependeds}, which answers a different question ("does
    * this script textually reference ANOTHER assembly by name") that a same-table local field
    * access never triggers, and so would never gate this rewrite into running at all.
    */
   private static void renameComposedTableOwnExpressions(
      TableAssembly composedTable, String oldEntity, String oldLocalName, String newLocalName)
   {
      if(oldLocalName == null || newLocalName == null || oldLocalName.equals(newLocalName)) {
         return;
      }

      // Bug #78234 / WBS-104: one token-based matcher (FieldReferenceMatcher) for every syntax
      // the script engine accepts -- field['x'], field["x"], field.x -- plus the qualified
      // field['T.x'] form, instead of a literal "['x']" substring replace that only knew the
      // single-quote bracket form.
      Map<String, String> renames = new HashMap<>();
      renames.put(oldLocalName, newLocalName);

      if(oldEntity != null && !oldEntity.isEmpty()) {
         renames.put(oldEntity + "." + oldLocalName, oldEntity + "." + newLocalName);
      }

      renameOwnExpressions(composedTable, renames);
   }

   /**
    * Rewrites {@code composedTable}'s own expression-column field references per {@code renames}
    * (old reference name to new) with {@link FieldReferenceMatcher}.
    */
   private static void renameOwnExpressions(TableAssembly composedTable,
                                            Map<String, String> renames)
   {
      if(renames.isEmpty()) {
         return;
      }

      ColumnSelection columns = composedTable.getColumnSelection();
      Set<String> outputs = new HashSet<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         if(columns.getAttribute(i) instanceof ColumnRef col) {
            outputs.add(outputName(col));
         }
      }

      outputs.removeAll(renames.keySet());

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef column = columns.getAttribute(i);

         if(!(column instanceof ColumnRef)) {
            continue;
         }

         DataRef ref = ((ColumnRef) column).getDataRef();

         // A RangeRef's expression is generated from its own base ref (re-pointed by
         // renameTableColumn0), never read from the stored text -- nothing to rewrite here.
         if(ref instanceof ExpressionRef && !(ref instanceof RangeRef)) {
            ExpressionRef exp = (ExpressionRef) ref;
            String expr = exp.getExpression();
            String renamed = FieldReferenceMatcher.rename(expr, renames, outputs);

            if(renamed != expr) {
               exp.setExpression(renamed);
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
            token.val.equals(ocolumn.getName()) && (cref == null || !"[".equals(cref.val)))
         {
            sb.append(new ScriptIterator.Token(token.type, ncolumn.getName(), token.length));
         }
         // Bug #78234: the former TEXT-token "['x']" substring branch is retired. A real
         // field['x'] access is tokenized as a ref (never TEXT), so that branch only ever fired on
         // text inside a string literal; and field['x'] on THIS table addresses its own column,
         // not changedTable's. A downstream composed table's own field references are rewritten
         // by renameComposedTableOwnExpressions via FieldReferenceMatcher.
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

      renameTableColumn0(ws, base, mirror, ocolumn, ncolumn, (ColumnRef) ref, false);
   }

   /**
    * @param column     the column of {@code mirror} that follows the renamed source column.
    * @param byIdentity Bug #78234 / WBS-103: true for a multi-source composed table -- replace
    *                   exactly {@code column} (never every column with the same bare name, which
    *                   can belong to another source), keep its existing alias (it is the composed
    *                   table's output name), and alias it uniquely if the new name collides with
    *                   a sibling. False keeps the original single-source (mirror) behaviour.
    * @return the composed table's new column.
    */
   private static ColumnRef renameTableColumn0(Worksheet ws, TableAssembly base,
                                               TableAssembly mirror, ColumnRef ocolumn,
                                               ColumnRef ncolumn, ColumnRef column,
                                               boolean byIdentity)
   {
      String name = ncolumn.getAlias() != null ? ncolumn.getName() : ncolumn.getAttribute();
      AttributeRef newAttr = new AttributeRef(column.getEntity(), name);
      ColumnRef newMirrorCol = new ColumnRef(newAttr);
      newMirrorCol.setOldName(ocolumn.getOldName());
      newMirrorCol.setLastOldName(ocolumn.getLastOldName());
      // Match every consumer against a snapshot of the pre-rename identity: a condition can
      // share this exact ColumnRef object and renameMirrorConditionList renames it in place,
      // after which the live object no longer matches the lists processed later.
      ColumnRef oldMirrorCol = (ColumnRef) column.clone();
      ColumnSelection cols = mirror.getColumnSelection();
      ColumnSelection pcols = mirror.getColumnSelection(true);
      int index = -1;
      // A condition's attribute is rebuilt from ncol.getDisplayName(), so it is handed the
      // unaliased new attribute even when the composed column keeps an alias.
      ColumnRef conditionCol = newMirrorCol;
      ColumnRef sortCol = ncolumn;

      if(byIdentity) {
         index = indexOfColumn(cols, column);

         if(!Tool.isEmptyString(column.getAlias())) {
            newMirrorCol.setAlias(column.getAlias());
         }
         else {
            newMirrorCol.setAlias(uniqueAlias(cols, index, name));
         }

         newMirrorCol.setDataType(column.getDataType());
         conditionCol = new ColumnRef(newAttr);
         sortCol = newMirrorCol;
      }

      for(ConditionListWrapper conds : conditionLists(mirror)) {
         renameMirrorConditionList(conds, oldMirrorCol, conditionCol);
      }

      renameSortInfo(mirror.getSortInfo(), oldMirrorCol, sortCol);
      renameAggregateInfo(ws, mirror, mirror.getAggregateInfo(), oldMirrorCol, newMirrorCol);

      if(mirror instanceof CompositeTableAssembly) {
         CompositeTableAssembly composite = (CompositeTableAssembly) mirror;
         String[] tableNames = composite.getTableNames();

         // NOTE: the bound here counts OPERATORS while the indices address TABLES, of which there
         // is one more -- so the last table is never reached, and with two tables (count 1, j
         // starting at 1) the body does not run at all. Deliberately left as-is: disabling this
         // loop entirely changes no observable behaviour, because renaming already propagates to
         // join operators by another path (see RenameColumnControllerTest). Correcting the bound
         // would start executing code that has effectively never run, on a rename path with no
         // test coverage of its own -- a bigger risk than the wrong bound. Pinned by tests so the
         // behaviour cannot regress silently if this is ever revisited.
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

      if(mirror instanceof RelationalJoinTableAssembly) {
         for(int i = 0; i < pcols.getAttributeCount(); i ++) {
            boolean match = byIdentity ? sameColumn(pcols.getAttribute(i), oldMirrorCol) :
               pcols.getAttribute(i).getAttribute().equals(ocolumn.getDisplayName());

            if(match) {
               pcols.setAttribute(i, newMirrorCol);
            }
         }
      }

      for(int i = 0; i < cols.getAttributeCount(); i ++) {
         boolean match = byIdentity ? i == index :
            cols.getAttribute(i).getAttribute().equals(ocolumn.getDisplayName());

         if(match) {
            cols.setAttribute(i, newMirrorCol);
         }
         else if(cols.getAttribute(i) instanceof ColumnRef) {
            ColumnRef col = (ColumnRef) cols.getAttribute(i);

            // Bug #78234 / WBS-105: any RangeRef (date AND numeric). A NumericRangeRef is also
            // an ExpressionRef, but its expression is generated from this base ref on every
            // read, so the ExpressionRef text rewrite below never reached it -- the base ref
            // itself has to be re-pointed.
            if(col.getDataRef() instanceof RangeRef) {
               RangeRef rangeRef = (RangeRef) col.getDataRef();

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
      return newMirrorCol;
   }

   /** Position of exactly {@code column} (same object, else same entity and attribute). */
   private static int indexOfColumn(ColumnSelection cols, ColumnRef column) {
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(cols.getAttribute(i) == column) {
            return i;
         }
      }

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(sameColumn(cols.getAttribute(i), column)) {
            return i;
         }
      }

      return -1;
   }

   private static boolean sameColumn(DataRef ref, ColumnRef column) {
      return ref instanceof ColumnRef col && !col.isExpression() &&
         Tool.equals(col.getEntity(), column.getEntity()) &&
         Tool.equals(col.getAttribute(), column.getAttribute());
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

         renameMirrorConditionValues(item.getXCondition(), ocol, ncol);
      }
   }

   /**
    * Bug #78234 / WBS-106: a condition whose VALUE is another column ({@code Budget > [field
    * Amount]}) holds that column as a {@link DataRef} in its value slot. Re-points it the same way
    * the condition's own attribute is re-pointed. The value is replaced by a renamed copy, never
    * mutated in place: it is usually the live column object from the table's selection.
    */
   private static void renameMirrorConditionValues(XCondition xcond, ColumnRef ocol,
                                                   ColumnRef ncol)
   {
      if(!(xcond instanceof Condition)) {
         return;
      }

      Condition cond = (Condition) xcond;

      for(int v = 0; v < cond.getValueCount(); v++) {
         Object value = cond.getValue(v);

         if(!(value instanceof ColumnRef) || !value.equals(ocol)) {
            continue;
         }

         ColumnRef valueCol = (ColumnRef) value;

         if(valueCol.getDataRef() instanceof AttributeRef) {
            ColumnRef renamed = (ColumnRef) valueCol.clone();
            renamed.setDataRef(new AttributeRef(valueCol.getEntity(), ncol.getDisplayName()));
            renamed.setView(renamed.getName());
            cond.setValue(v, renamed);
         }
         else {
            cond.setValue(v, ncol.clone());
         }
      }
   }

   /**
    * Same as {@link #renameMirrorConditionValues} for a table renaming its OWN column, where
    * {@code ncol} is the table's live, renamed column.
    */
   private static void renameOwnConditionValues(XCondition xcond, ColumnRef ocol,
                                                ColumnRef ncol)
   {
      if(!(xcond instanceof Condition)) {
         return;
      }

      Condition cond = (Condition) xcond;

      for(int v = 0; v < cond.getValueCount(); v++) {
         Object value = cond.getValue(v);

         if(value instanceof ColumnRef && value.equals(ocol)) {
            cond.setValue(v, ncol);
         }
      }
   }

   /**
    * Every condition list a column rename has to carry through: pre, post, ranking, and (Bug
    * #78234 / WBS-107) the four MV incremental-refresh lists, which nothing else re-validates,
    * so a missed rename leaves them silently stale.
    */
   static List<ConditionListWrapper> conditionLists(TableAssembly table) {
      return Arrays.asList(
         table.getPreConditionList(), table.getPostConditionList(),
         table.getRankingConditionList(),
         table.getMVUpdatePreConditionList(), table.getMVUpdatePostConditionList(),
         table.getMVDeletePreConditionList(), table.getMVDeletePostConditionList());
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
      return renameColumnService.validateOpen(runtimeId, event, principal);
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
      renameColumnService.renameColumn(getRuntimeId(), event, principal, commandDispatcher);
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

         renameOwnConditionValues(xCondition, ocol, ncol);
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
      column.setExpression(Util.renameScriptRefDepended(oname, nname, exp, acceptFunc));
   }

   private final RenameColumnServiceProxy renameColumnService;
}
