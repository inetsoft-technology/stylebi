/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.wiz.pairing.PairingException;

import java.util.*;

import static inetsoft.web.wiz.worksheet.MirrorResyncGuard.refuse;

/**
 * Bug #78846: what reordering the subtables of a concatenation would break. A concatenation takes
 * its column names, and the table every column is attributed to, from its FIRST subtable, so a
 * reorder that changes the first subtable re-points every column of the concatenation, and the
 * refresh that follows silently drops the conditions, rankings, aliases and hidden flags the
 * concatenation holds on them, and what it and its dependents hold on a column whose name
 * changes. Its sort, aggregate, expression and range columns follow a column by name, so they
 * are lost only with the name. It is checked before anything is mutated.
 *
 * <p>A reorder that keeps the first subtable loses no column state. Join keys, a key whose type
 * changes and a concatenation fed by a changed column list are never confirmable; the other
 * losses are refused unless {@code confirmed}, as {@code remove_column} does. Operators are
 * carried by position, so a mixed UNION/MINUS/INTERSECT concatenation changes which rows survive
 * whatever the first subtable is; no column check sees that.</p>
 *
 * <p>Bug #78879: removing the first subtable makes the next one first, with the same effect, so
 * {@code remove_concat_subtable} runs the same check against the subtables that remain.</p>
 */
final class ConcatReorderGuard {
   private static final String CONFIRM =
      "Confirm with the user before retrying with confirmed:true -- this changes what " +
      "those OTHER tables show, not just this one. Nothing was changed.";

   private ConcatReorderGuard() {
   }

   /**
    * Resolves {@code names} to the subtables of {@code table}, in that order, failing loudly when
    * the list is not exactly a permutation of the current subtables.
    */
   static TableAssembly[] resolvePermutation(Worksheet ws, ConcatenatedTableAssembly table,
                                             List<String> names) throws PairingException
   {
      List<String> current = Arrays.asList(table.getTableNames());
      Set<String> seen = new HashSet<>();
      TableAssembly[] resolved = new TableAssembly[names.size()];

      for(int i = 0; i < resolved.length; i++) {
         String name = names.get(i);

         if(!seen.add(name)) {
            throw notPermutation(table, current, "\"" + name + "\" is listed more than once");
         }

         if(!current.contains(name)) {
            throw notPermutation(table, current, "\"" + name + "\" is not a subtable of it");
         }

         if(!(ws.getAssembly(name) instanceof TableAssembly sub)) {
            throw new PairingException("Subtable not found in worksheet: " + name);
         }

         resolved[i] = sub;
      }

      if(resolved.length != current.size()) {
         throw notPermutation(
            table, current, "it lists " + resolved.length + " subtable(s) but the table has " +
            current.size());
      }

      return resolved;
   }

   private static PairingException notPermutation(TableAssembly table, List<String> current,
                                                  String why)
   {
      return new PairingException(
         "subtables must list every subtable of '" + table.getName() + "' exactly once, in the " +
         "new order: " + why + ". Its subtables are " + String.join(", ", current) +
         ". Nothing was changed.");
   }

   static void requireReorderKeepsState(Worksheet ws, ConcatenatedTableAssembly table,
                                        TableAssembly[] reordered, boolean confirmed)
      throws PairingException
   {
      requireFirstKeepsState(
         ws, table, reordered,
         "Putting '" + reordered[0].getName() + "' first in '" + table.getName() + "'",
         "keep the current first subtable", confirmed);
   }

   /**
    * Removing {@code removed} from {@code table} leaves {@code remaining}, in order, which is
    * checked the same way as a reorder to that list. Only called when at least 2 subtables remain
    * (with fewer the concatenation itself is deleted).
    */
   static void requireRemovalKeepsState(Worksheet ws, ConcatenatedTableAssembly table,
                                        String removed, TableAssembly[] remaining,
                                        boolean confirmed) throws PairingException
   {
      requireFirstKeepsState(
         ws, table, remaining,
         "Removing '" + removed + "' from '" + table.getName() + "' (which makes '" +
         remaining[0].getName() + "' its first subtable)",
         "remove a subtable other than the first", confirmed);
   }

   private static void requireFirstKeepsState(Worksheet ws, ConcatenatedTableAssembly table,
                                              TableAssembly[] subtables, String lead,
                                              String alternative, boolean confirmed)
      throws PairingException
   {
      String[] current = table.getTableNames();

      // the entity every column is attributed to comes from the first subtable only
      if(current.length == 0 || Tool.equals(current[0], subtables[0].getName())) {
         return;
      }

      String name = table.getName();
      List<ColumnRef> oldColumns = MirrorResyncGuard.sourceColumns(table.getColumnSelection());
      ColumnSelection fresh = ConcatenatedTableAssembly.getDefaultColumnSelection(subtables);
      Set<String> freshNames = new HashSet<>();
      ColumnSelection before = new ColumnSelection();
      ColumnSelection after = new ColumnSelection();
      String entity = oldColumns.isEmpty() ? name : oldColumns.get(0).getEntity();

      // the refresh keeps the table's own expression and named-group columns, and resets every
      // other column to the default one: no alias, visible
      ColumnSelection all = table.getColumnSelection();

      for(int i = 0; i < all.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) all.getAttribute(i);

         if(column.isExpression() || column.getDataRef() instanceof NamedRangeRef) {
            freshNames.add(column.getDisplayName());
         }
      }

      for(int i = 0; fresh != null && i < fresh.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) fresh.getAttribute(i);
         freshNames.add(column.getAttribute());
         ColumnRef carried = new ColumnRef(new AttributeRef(entity, column.getAttribute()));
         carried.setDataType(column.getDataType());
         after.addAttribute(carried);
      }

      Set<String> keptNames = new HashSet<>();

      // the stored type is the first subtable's; what a join key sees today is the merged one
      ColumnSelection merged = WorksheetMutationSupport.mergedConcatSelection(table);

      for(ColumnRef column : oldColumns) {
         String type = WorksheetMutationSupport.effectiveType(merged, column);

         if(Tool.equals(type, column.getDataType())) {
            before.addAttribute(column);
         }
         else {
            ColumnRef retyped = (ColumnRef) column.clone();
            retyped.setDataType(type);
            before.addAttribute(retyped);
         }

         // a dependent reads the column by the name it is displayed under
         if(freshNames.contains(column.getDisplayName())) {
            keptNames.add(column.getName());
         }
      }

      String remedy = "Remove or edit the join first, or " + alternative + ". Nothing was changed.";
      List<String> never = new ArrayList<>();
      List<String> confirmable = new ArrayList<>();

      for(ColumnRef column : oldColumns) {
         if(!keptNames.contains(column.getName())) {
            refuse(never, () -> ColumnLossReport.requireNotJoinKey(
               ws, table, column, lead + " would drop \"" + column.getDisplayName() + "\"",
               remedy));
         }
      }

      refuse(never, () -> JoinKeyTypeGuard.throwIfAny(
         JoinKeyTypeGuard.findSelectionBreaks(ws, table, before, after), name,
         lead + " would change the type of",
         remedy));

      if(!sameNames(oldColumns, fresh)) {
         refuse(never, () -> MirrorResyncGuard.requireNoConcatenationFed(ws, table, lead));
      }

      if(!never.isEmpty()) {
         throw new PairingException(String.join(" ", never));
      }

      if(!confirmed) {
         refuse(confirmable, () -> ColumnLossReport.requireNoDependentLoss(
            ws, table, name, oldColumns, keptNames, ColumnLossReport.Scope.RESYNC, lead,
            CONFIRM));
         refuse(confirmable, () -> requireOwnStateKept(table, oldColumns, keptNames, freshNames, lead));
      }

      if(!confirmable.isEmpty()) {
         throw new PairingException(String.join(" ", confirmable));
      }
   }

   private static boolean sameNames(List<ColumnRef> oldColumns, ColumnSelection fresh) {
      int count = fresh == null ? 0 : fresh.getAttributeCount();

      if(oldColumns.size() != count) {
         return false;
      }

      for(int i = 0; i < count; i++) {
         if(!Tool.equals(oldColumns.get(i).getDisplayName(), fresh.getAttribute(i).getAttribute())) {
            return false;
         }
      }

      return true;
   }

   /**
    * The table's own state the reorder drops without a dependent noticing: on a column whose name
    * survives, a condition or ranking (the column is still there but attributed to the new first
    * subtable); on any column, its alias and its hidden flag; and an aggregate or group column
    * the new first subtable does not have. (Sort, expression and range columns, and an aggregate
    * on a surviving name, follow the column by name; state on a column whose name leaves is
    * reported with the dependents.)
    */
   private static void requireOwnStateKept(ConcatenatedTableAssembly table,
                                           List<ColumnRef> oldColumns, Set<String> keptNames,
                                           Set<String> freshNames, String lead)
      throws PairingException
   {
      Set<String> lost = new LinkedHashSet<>();

      for(ColumnRef column : oldColumns) {
         if(keptNames.contains(column.getName())) {
            lost.addAll(WorksheetControllerService.findOwnConditionReferences(table, column));
         }

         if(column.getAlias() != null && !column.getAlias().isEmpty()) {
            lost.add("alias of column '" + column.getAttribute() + "'");
         }

         if(!column.isVisible()) {
            lost.add("hidden column '" + column.getDisplayName() + "'");
         }
      }

      if(ColumnLossReport.aggregateLosesColumn(
         table.getAggregateInfo(), attr -> !freshNames.contains(attr)))
      {
         lost.add("aggregate/group-by");
      }

      if(!lost.isEmpty()) {
         throw new PairingException(
            lead + " changes the source its columns come from, so table '" + table.getName() +
            "' itself would lose " + String.join(", ", lost) + ". " + CONFIRM);
      }
   }
}
