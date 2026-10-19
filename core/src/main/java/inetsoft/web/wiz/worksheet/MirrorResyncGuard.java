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
import inetsoft.web.wiz.pairing.PairingException;

import java.security.Principal;
import java.util.*;

/**
 * Bug #78814: what re-syncing an outer (cross-worksheet) mirror with its saved source worksheet
 * would break. The re-sync replaces the mirror's columns with the source primary's current ones,
 * and the post-edit refresh then silently drops every condition, ranking, expression, range and
 * aggregate that read a column that is gone, so it is checked before anything is mutated, on a
 * scratch copy of the worksheet.
 *
 * <p>Every outer mirror of the worksheet that shares the mirror's source copies is re-synced with
 * it, so each one is checked. Join keys, a key whose type changes, a concatenation fed by a
 * changed column list and a user table reading a replaced source copy directly are never
 * confirmable; the mirror's own and its dependents' conditions, rankings, expressions, ranges and
 * aggregates are refused unless {@code confirmed}, as {@code remove_column} does.</p>
 */
final class MirrorResyncGuard {
   private MirrorResyncGuard() {
   }

   static void requireResyncKeepsDependents(
      Worksheet ws, String name, MirrorAssembly mirror, AssetRepository repository,
      Principal user, boolean confirmed) throws Exception
   {
      if(!mirror.isOuterMirror()) {
         throw new PairingException(
            "update_mirror does nothing on \"" + name + "\": its source is in this worksheet, " +
            "and such a mirror always tracks its source. Only a mirror of an asset in another " +
            "worksheet is re-synced.");
      }

      if(!(mirror instanceof MirrorTableAssembly table)) {
         return;
      }

      List<MirrorTableAssembly> sharing = sharingMirrors(ws, table);
      Set<String> replaced = ws.getReplacedOuterCopies(table);
      Worksheet scratch = (Worksheet) ws.clone();
      ((MirrorAssembly) scratch.getAssembly(name)).updateMirror(repository, user);
      List<String> never = new ArrayList<>();
      List<String> confirmable = new ArrayList<>();

      for(MirrorTableAssembly live : sharing) {
         MirrorTableAssembly fresh = (MirrorTableAssembly) scratch.getAssembly(live.getName());
         fresh.updateColumnSelection();
         checkMirror(ws, live, fresh, confirmed, never, confirmable);
      }

      checkDirectDependents(ws, scratch, sharing, replaced, never);

      if(!never.isEmpty()) {
         throw new PairingException(String.join(" ", never));
      }

      if(!confirmable.isEmpty()) {
         throw new PairingException(String.join(" ", confirmable));
      }
   }

   private static List<MirrorTableAssembly> sharingMirrors(Worksheet ws, MirrorTableAssembly mirror)
   {
      List<MirrorTableAssembly> sharing = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof MirrorTableAssembly other && other.isOuterMirror() &&
            Tool.equals(mirror.getAssemblyName(), other.getAssemblyName()))
         {
            sharing.add(other);
         }
      }

      return sharing;
   }

   private static void checkMirror(Worksheet ws, MirrorTableAssembly live,
                                   MirrorTableAssembly fresh, boolean confirmed,
                                   List<String> never, List<String> confirmable)
   {
      String name = live.getName();
      List<ColumnRef> oldColumns = sourceColumns(live.getColumnSelection());

      if(oldColumns.isEmpty()) {
         return;
      }

      // the source copies are renamed when the source gains or loses an upstream table, so the
      // fresh columns are compared by attribute and carried under the live copy's entity
      String entity = oldColumns.get(0).getEntity();
      ColumnSelection fresher = new ColumnSelection();

      for(ColumnRef column : sourceColumns(fresh.getColumnSelection())) {
         ColumnRef carried = new ColumnRef(new AttributeRef(entity, column.getAttribute()));
         carried.setDataType(column.getDataType());
         fresher.addAttribute(carried);
      }

      ColumnSelection before = new ColumnSelection();
      Set<String> keptNames = new HashSet<>();

      for(ColumnRef column : oldColumns) {
         before.addAttribute(column);

         if(fresher.containsAttribute(column)) {
            keptNames.add(column.getName());
         }
      }

      String lead = "Re-syncing '" + name + "' with its source";

      for(ColumnRef column : oldColumns) {
         if(!keptNames.contains(column.getName())) {
            refuse(never, () -> ColumnLossReport.requireNotJoinKey(
               ws, live, column, lead + " would drop \"" + column.getAttribute() + "\"",
               "Remove or edit the join first, or keep the column in the source worksheet. " +
               "Nothing was changed."));
         }
      }

      refuse(never, () -> JoinKeyTypeGuard.throwIfAny(
         JoinKeyTypeGuard.findSelectionBreaks(ws, live, before, fresher), name,
         lead + " would change the type of",
         "Remove or edit the join first, or keep the column type in the source worksheet. " +
         "Nothing was changed."));

      if(shapeChanged(oldColumns, sourceColumns(fresh.getColumnSelection()))) {
         refuse(never, () -> requireNoConcatenationFed(ws, live, lead));
      }

      if(!confirmed) {
         refuse(confirmable, () -> ColumnLossReport.requireNoDependentLoss(
            ws, live, name, oldColumns, keptNames, ColumnLossReport.Scope.RESYNC, lead,
            "Confirm with the user before retrying with confirmed:true -- this changes what " +
            "those OTHER tables show, not just this one. Nothing was changed."));
      }
   }

   /**
    * The columns that come from the source: the mirror's own expression and named-group columns
    * are kept (or not) by the refresh, not by the re-sync.
    */
   private static List<ColumnRef> sourceColumns(ColumnSelection selection) {
      List<ColumnRef> columns = new ArrayList<>();

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) selection.getAttribute(i);

         if(!column.isExpression() && !(column.getDataRef() instanceof NamedRangeRef)) {
            columns.add(column);
         }
      }

      return columns;
   }

   private static boolean shapeChanged(List<ColumnRef> before, List<ColumnRef> after) {
      if(before.size() != after.size()) {
         return true;
      }

      for(int i = 0; i < before.size(); i++) {
         if(!Tool.equals(before.get(i).getAttribute(), after.get(i).getAttribute()) ||
            !Tool.equals(before.get(i).getDataType(), after.get(i).getDataType()))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * A concatenation pairs its sources by position and takes its names from the first one, so a
    * changed column list of any table feeding it, directly or through other tables, re-pairs
    * columns without any error.
    */
   private static void requireNoConcatenationFed(Worksheet ws, TableAssembly table, String lead)
      throws PairingException
   {
      List<String> fed = new ArrayList<>();
      Set<String> visited = new HashSet<>();
      Deque<AssemblyEntry> queue = new ArrayDeque<>();
      queue.add(table.getAssemblyEntry());

      while(!queue.isEmpty()) {
         for(AssemblyRef ref : ws.getDependings(queue.poll())) {
            String dependent = ref.getEntry().getName();

            if(visited.add(dependent)) {
               Assembly assembly = ws.getAssembly(dependent);

               if(assembly instanceof ConcatenatedTableAssembly) {
                  fed.add("'" + dependent + "'");
               }

               if(assembly != null) {
                  queue.add(assembly.getAssemblyEntry());
               }
            }
         }
      }

      if(!fed.isEmpty()) {
         throw new PairingException(
            lead + " would change its column list, and '" + table.getName() + "' feeds " +
            "concatenation(s) " + String.join(", ", fed) + ". Sources are combined by POSITION, " +
            "so adding, removing or reordering a column shifts every later column onto a " +
            "different source column -- values then arrive under a name that no longer " +
            "describes them, and nothing reports it. Nothing was changed.");
      }
   }

   /**
    * A user table that reads a replaced source copy directly is not reached from the mirror: the
    * copy is removed and recreated, so when it comes back under another name or with another
    * column list that table loses its source or the columns it was built on.
    */
   private static void checkDirectDependents(Worksheet ws, Worksheet scratch,
                                             List<MirrorTableAssembly> sharing,
                                             Set<String> replaced, List<String> never)
   {
      Set<String> sharingNames = new HashSet<>();

      for(MirrorTableAssembly mirror : sharing) {
         sharingNames.add(mirror.getName());
      }

      for(String copy : new TreeSet<>(replaced)) {
         Assembly source = ws.getAssembly(copy);

         if(!(source instanceof TableAssembly table) || !copyChanges(table, scratch, copy)) {
            continue;
         }

         List<String> direct = new ArrayList<>();

         for(AssemblyRef ref : ws.getDependings(source.getAssemblyEntry())) {
            String name = ref.getEntry().getName();
            Assembly dependent = ws.getAssembly(name);

            if(dependent instanceof WSAssembly wsa && !wsa.isOuter() &&
               !sharingNames.contains(name))
            {
               direct.add("'" + name + "'");
            }
         }

         if(!direct.isEmpty()) {
            never.add("Re-syncing the mirror would replace its source copy '" + copy +
                      "' with one that has other columns, and " + String.join(", ", direct) +
                      " reads it directly, so " +
                      (direct.size() == 1 ? "that table" : "those tables") + " would lose " +
                      "its source. Point " + (direct.size() == 1 ? "it" : "them") +
                      " at the mirror instead. Nothing was changed.");
         }
      }
   }

   private static boolean copyChanges(TableAssembly copy, Worksheet scratch, String name) {
      return !(scratch.getAssembly(name) instanceof TableAssembly fresh) ||
         shapeChanged(sourceColumns(copy.getColumnSelection()),
                      sourceColumns(fresh.getColumnSelection()));
   }

   private interface Check {
      void run() throws PairingException;
   }

   private static void refuse(List<String> out, Check check) {
      try {
         check.run();
      }
      catch(PairingException ex) {
         out.add(ex.getMessage());
      }
   }
}
