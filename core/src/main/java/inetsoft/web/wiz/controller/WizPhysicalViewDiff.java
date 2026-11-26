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
package inetsoft.web.wiz.controller;

import inetsoft.util.Tool;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;

import java.util.*;

/**
 * The diff of a submitted physical view against the stored one (design 8.3): the ordered
 * operations that turn the stored view into the desired one, the base-element guard of an
 * extended view (design 8.6), the referential validation of the submitted list, and the
 * read-back comparison (charter C2).
 *
 * <p>Tables are keyed by the name the view references them by; joins by their unordered pair of
 * endpoints. {@code tables} and {@code joins} are complete lists: a stored element that the
 * submitted list lacks is removed, unless the list was not sent at all. The auto aliases of a
 * table follow the same rule one level down: a list replaces the table's set, an empty list
 * clears it, an absent key keeps it.</p>
 */
final class WizPhysicalViewDiff {
   private WizPhysicalViewDiff() {
   }

   /** An alias or inline view renamed, with the submitted table (new name) and the stored one. */
   record Rename(TableDef from, TableDef to) {
   }

   /** A join whose attributes change in place. */
   record JoinChange(JoinDef stored, JoinDef desired) {
   }

   /** The auto aliases of one table that an update sets (replaces) or clears. */
   record AutoAliasChange(String table, List<AutoAliasDef> stored, List<AutoAliasDef> desired) {
   }

   /** The ordered operations of an update (or create) plus the metadata changes. */
   static final class Plan {
      /** Joins that are gone, or whose orientation changed: removed first. */
      final List<JoinDef> removeJoins = new ArrayList<>();
      /** Tables, aliases and views that are gone, or whose kind or source changed. */
      final List<TableDef> removeTables = new ArrayList<>();
      final List<Rename> renames = new ArrayList<>();
      /** Inline views whose SQL changes in place. */
      final List<Rename> updateViews = new ArrayList<>();
      final List<TableDef> addPlain = new ArrayList<>();
      final List<TableDef> addViews = new ArrayList<>();
      /** In dependency order: an alias after the table (or alias) it aliases. */
      final List<TableDef> addAliases = new ArrayList<>();
      final List<JoinDef> addJoins = new ArrayList<>();
      final List<JoinChange> changeJoins = new ArrayList<>();
      /** Applied last: an auto alias is derived from a join, so the joins come first. */
      final List<AutoAliasChange> autoAliasChanges = new ArrayList<>();
      /**
       * Tables (new ones included) whose position is to be set: the submitted table, whose x and y
       * differ from the stored ones. Applied after the tables and joins are in place.
       */
      final List<TableDef> moveTables = new ArrayList<>();
      /** An Auto Layout to run once everything else is in place, with the diagram size to use. */
      AutoLayout autoLayout;
      Viewport viewport;
      boolean descriptionChanged;
      boolean folderChanged;
      boolean connectionChanged;

      boolean structureEmpty() {
         return removeJoins.isEmpty() && removeTables.isEmpty() && renames.isEmpty() &&
            updateViews.isEmpty() && addPlain.isEmpty() && addViews.isEmpty() &&
            addAliases.isEmpty() && addJoins.isEmpty() && changeJoins.isEmpty() &&
            autoAliasChanges.isEmpty() &&
            moveTables.isEmpty() && autoLayout == null;
      }

      boolean isEmpty() {
         return structureEmpty() && !descriptionChanged && !folderChanged && !connectionChanged;
      }

      /** The alias (or view) names renamed in this plan: new name to previous name. */
      Map<String, String> renamedFrom() {
         Map<String, String> result = new LinkedHashMap<>();

         for(Rename rename : renames) {
            result.put(rename.to().name(), rename.from().name());
         }

         for(Rename rename : updateViews) {
            if(!rename.from().name().equals(rename.to().name())) {
               result.put(rename.to().name(), rename.from().name());
            }
         }

         return result;
      }
   }

   /**
    * Plans the update of {@code stored} into {@code desired}.
    *
    * @param creating {@code true} for a create: the base elements of an extended view that the
    *                 submitted list omits are kept (a new extended view declares only its own).
    *
    * @throws DmFailure {@code INVALID} for an inconsistent submitted list, {@code
    *         BASE_ELEMENT_READ_ONLY} for a change of an inherited element.
    */
   static Plan plan(View stored, Desired desired, boolean creating) {
      Plan plan = new Plan();
      View want = desired.view();
      List<Err> errors = new ArrayList<>();
      requireWithinCap(stored, desired);
      Map<String, TableDef> storedTables = byName(stored.tables());
      Map<String, TableDef> wantTables = new LinkedHashMap<>();

      if(desired.hasTables()) {
         validateTables(want, errors, wantTables);
      }
      else {
         wantTables.putAll(storedTables);
      }

      // base elements are read-only: checked before anything is opened
      DmFailure baseFailure = null;

      if(desired.hasTables()) {
         for(TableDef base : stored.tables()) {
            if(!base.baseElement()) {
               continue;
            }

            TableDef now = wantTables.get(base.name());

            if(now == null && creating) {
               wantTables.put(base.name(), base);
               continue;
            }

            if(now == null || !sameTable(base, now, true) ||
               (now.oldName() != null && !now.oldName().equals(now.name())))
            {
               baseFailure = baseFailure != null ? baseFailure :
                  baseReadOnly("tables[" + base.name() + "]");
            }
         }
      }

      if(desired.hasTables()) {
         validateAliases(want, errors, wantTables);
      }

      if(desired.hasJoins()) {
         validateJoins(want, wantTables, errors);
      }

      // an inherited element that is gone also leaves joins and aliases that name it, which
      // are only the consequence: the inherited element is the problem to report
      if(baseFailure != null) {
         throw baseFailure;
      }

      if(!errors.isEmpty()) {
         throw WizPhysicalViewMapper.invalid(errors);
      }

      // tables: removed, renamed, re-sourced, changed
      Set<String> handled = new HashSet<>();
      Map<String, TableDef> renamedTo = new HashMap<>();

      for(TableDef now : wantTables.values()) {
         if(now.oldName() != null && !now.oldName().equals(now.name())) {
            renamedTo.put(now.oldName(), now);
         }
      }

      if(desired.hasTables()) {
         for(TableDef old : stored.tables()) {
            TableDef now = wantTables.get(old.name());
            TableDef renamed = renamedTo.get(old.name());

            if(renamed != null) {
               if(now != null) {
                  errors.add(new Err("tables[" + renamed.name() + "].oldName", "\"" +
                     old.name() + "\" is renamed to \"" + renamed.name() + "\" but a table " +
                     "named \"" + old.name() + "\" is still in the list", "TABLE_INVALID"));
               }
               else if(old.kind() == Kind.PLAIN || renamed.kind() != old.kind()) {
                  errors.add(new Err("tables[" + renamed.name() + "].oldName",
                     "\"" + old.name() + "\" cannot be renamed to a " +
                        renamed.kind().name().toLowerCase(Locale.ROOT) +
                        " (only an alias or an inline view can be renamed, keeping its kind)",
                     "TABLE_INVALID"));
               }
               else if(old.kind() == Kind.ALIAS && !Tool.equals(old.aliasOf(), renamed.aliasOf())) {
                  errors.add(new Err("tables[" + renamed.name() + "]", "an alias cannot be " +
                     "renamed and re-sourced in one update; rename it, then change aliasOf",
                     "TABLE_INVALID"));
               }
               else if(old.baseElement()) {
                  throw baseReadOnly("tables[" + old.name() + "]");
               }
               else if(old.kind() == Kind.VIEW) {
                  plan.updateViews.add(new Rename(old, renamed));
                  handled.add(renamed.name());
               }
               else {
                  plan.renames.add(new Rename(old, renamed));
                  handled.add(renamed.name());
               }

               continue;
            }

            if(now == null) {
               plan.removeTables.add(old);
            }
            else if(!sameKind(old, now) || (old.kind() == Kind.ALIAS &&
               !Tool.equals(old.aliasOf(), now.aliasOf())) ||
               (old.kind() == Kind.PLAIN && !sameTable(old, now, false)))
            {
               plan.removeTables.add(old);
            }
            else if(old.kind() == Kind.VIEW && !Tool.equals(old.sql(), now.sql())) {
               plan.updateViews.add(new Rename(old, now));
               handled.add(now.name());
            }
            else {
               handled.add(now.name());
            }
         }
      }

      if(desired.hasTables()) {
         for(Map.Entry<String, TableDef> entry : renamedTo.entrySet()) {
            if(!storedTables.containsKey(entry.getKey())) {
               errors.add(new Err("tables[" + entry.getValue().name() + "].oldName",
                  "the stored view has no alias or inline view \"" + entry.getKey() + "\"",
                  "OLDNAME_UNKNOWN"));
            }
         }
      }

      if(!errors.isEmpty()) {
         throw WizPhysicalViewMapper.invalid(errors);
      }

      if(desired.hasTables()) {
         Set<String> removed = new HashSet<>();
         plan.removeTables.forEach(t -> removed.add(t.name()));

         for(TableDef now : wantTables.values()) {
            boolean kept = storedTables.containsKey(now.name()) && !removed.contains(now.name());

            if(handled.contains(now.name()) || kept) {
               continue;
            }

            switch(now.kind()) {
            case PLAIN -> plan.addPlain.add(now);
            case VIEW -> plan.addViews.add(now);
            default -> plan.addAliases.add(now);
            }
         }

         orderAliases(plan.addAliases, wantTables);
         planMoves(storedTables, wantTables, plan);
      }

      if(desired.autoLayout() != null) {
         if(!plan.moveTables.isEmpty()) {
            throw WizPhysicalViewMapper.invalid(List.of(new Err(
               "definition.autoLayout", "autoLayout arranges every table itself and cannot be " +
               "combined with explicit table positions (" + plan.moveTables.get(0).name() +
               " has x/y); send one or the other", "TABLE_INVALID")));
         }

         plan.autoLayout = desired.autoLayout();
         plan.viewport = desired.viewport();
      }

      if(desired.hasJoins()) {
         planJoins(stored, want, creating, plan);
      }

      planAutoAliases(stored, desired, creating, storedTables, wantTables, plan, errors);

      if(!errors.isEmpty()) {
         throw WizPhysicalViewMapper.invalid(errors);
      }

      if(desired.hasDescription() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(want.description()),
         WizPhysicalViewMapper.blankToNull(stored.description())))
      {
         plan.descriptionChanged = true;
      }

      if(desired.hasFolder() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(want.folder()),
         WizPhysicalViewMapper.blankToNull(stored.folder())))
      {
         plan.folderChanged = true;
      }

      if(desired.hasConnection() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(want.connection()),
         WizPhysicalViewMapper.blankToNull(stored.connection())))
      {
         plan.connectionChanged = true;
      }

      return plan;
   }

   /**
    * The tables whose submitted position differs from the stored one. A table that is new, or
    * re-created because its kind or source changed, has no stored position to equal. A position
    * of an inherited table is refused: the base view's rectangle is what the extended view uses,
    * so a write there would silently do nothing.
    */
   private static void planMoves(Map<String, TableDef> storedTables,
                                 Map<String, TableDef> wantTables, Plan plan)
   {
      Set<String> removed = new HashSet<>();
      plan.removeTables.forEach(t -> removed.add(t.name()));
      Map<String, String> renamedFrom = plan.renamedFrom();

      for(TableDef now : wantTables.values()) {
         if(!now.hasPosition()) {
            continue;
         }

         String was = renamedFrom.getOrDefault(now.name(), now.name());
         TableDef old = removed.contains(was) ? null : storedTables.get(was);

         if(old != null && old.hasPosition() && old.x().equals(now.x()) &&
            old.y().equals(now.y()))
         {
            continue;
         }

         if(old != null && old.baseElement()) {
            throw baseReadOnly("tables[" + now.name() + "].x");
         }

         plan.moveTables.add(now);
      }
   }

   /**
    * The cap on OWN tables and joins (inherited ones, known from the stored view and not from
    * what the caller flags, do not count). It stops a write from growing a view over the cap; a
    * definition that does not exceed the stored view's own count is never refused for it, so a
    * stored view above the cap can still be fed back unchanged or have its description edited.
    */
   private static void requireWithinCap(View stored, Desired desired) {
      Set<String> baseTables = new HashSet<>();
      Set<String> baseJoins = new HashSet<>();
      stored.tables().stream().filter(TableDef::baseElement).forEach(t -> baseTables.add(t.name()));
      stored.joins().stream().filter(JoinDef::baseElement).forEach(j -> baseJoins.add(j.key()));

      if(desired.hasTables()) {
         long own = desired.view().tables().stream()
            .filter(t -> !baseTables.contains(t.name())).count();
         long before = stored.tables().stream().filter(t -> !t.baseElement()).count();

         if(own > WizPhysicalViewMapper.MAX_TABLES && own > before) {
            throw capFailure("tables", WizPhysicalViewMapper.MAX_TABLES, own);
         }
      }

      if(desired.hasJoins()) {
         long own = desired.view().joins().stream()
            .filter(j -> !baseJoins.contains(j.key())).count();
         long before = stored.joins().stream().filter(j -> !j.baseElement()).count();

         if(own > WizPhysicalViewMapper.MAX_JOINS && own > before) {
            throw capFailure("joins", WizPhysicalViewMapper.MAX_JOINS, own);
         }
      }
   }

   private static DmFailure capFailure(String field, int max, long got) {
      return WizPhysicalViewMapper.invalid(List.of(new Err(
         field, "a physical view takes at most " + max + " own " + field + ", the definition " +
            "has " + got + "; split the view (a view already above the cap may keep what it " +
            "has but not grow)", "LIMIT_EXCEEDED")));
   }

   private static void planJoins(View stored, View want, boolean creating, Plan plan) {
      Map<String, JoinDef> storedJoins = new LinkedHashMap<>();
      stored.joins().forEach(j -> storedJoins.put(j.key(), j));
      Map<String, JoinDef> wantJoins = new LinkedHashMap<>();
      want.joins().forEach(j -> wantJoins.put(j.key(), j));
      Set<String> removedTables = new HashSet<>();
      plan.removeTables.forEach(t -> removedTables.add(t.name()));

      for(JoinDef old : stored.joins()) {
         JoinDef now = wantJoins.get(old.key());

         if(now == null) {
            if(old.baseElement()) {
               if(creating) {
                  continue;
               }

               throw baseReadOnly("joins[" + old.label() + "]");
            }

            plan.removeJoins.add(old);
            continue;
         }

         boolean sameOrientation = old.fromTable().equals(now.fromTable()) &&
            old.fromColumn().equals(now.fromColumn());

         if(old.baseElement() && (!sameOrientation || !sameJoinAttributes(old, now))) {
            throw baseReadOnly("joins[" + old.label() + "]");
         }

         if(!sameOrientation) {
            plan.removeJoins.add(old);
            plan.addJoins.add(now);
         }
         else if(!sameJoinAttributes(old, now)) {
            plan.changeJoins.add(new JoinChange(old, now));
         }
      }

      for(JoinDef now : want.joins()) {
         if(!storedJoins.containsKey(now.key())) {
            plan.addJoins.add(now);
         }
      }

      // a join that touches a table removed in this plan is gone with the table; its removal
      // is harmless to repeat but a join on a re-added table must be added again
      for(JoinDef old : stored.joins()) {
         if((removedTables.contains(old.fromTable()) || removedTables.contains(old.toTable())) &&
            wantJoins.containsKey(old.key()) && !plan.addJoins.contains(wantJoins.get(old.key())))
         {
            plan.changeJoins.removeIf(c -> c.stored().key().equals(old.key()));
            plan.addJoins.add(wantJoins.get(old.key()));
         }
      }
   }

   /**
    * Plans the auto aliases of every table and checks the final view's auto aliases against its
    * final joins. The Portal checks none of this on the server (its dialog does), and the server
    * silently drops an auto alias whose join is gone, so each rule is enforced here and named:
    * only a plain table of the view's own can carry one (setting one on an alias table would
    * delete that alias, an inherited table ignores it), it is derived for a table joined to it,
    * its name collides with no table and no other auto alias, a prefix goes with keepOutgoing.
    * An update that leaves a table's auto aliases unmentioned keeps them, so one that removes the
    * last join (or table) they are derived from is refused instead of dropping them quietly.
    */
   private static void planAutoAliases(View stored, Desired desired, boolean creating,
                                       Map<String, TableDef> storedTables,
                                       Map<String, TableDef> wantTables, Plan plan,
                                       List<Err> errors)
   {
      Set<String> removed = new HashSet<>();
      plan.removeTables.forEach(t -> removed.add(t.name()));
      Map<String, String> oldToNew = new HashMap<>();
      plan.renamedFrom().forEach((now, was) -> oldToNew.put(was, now));
      Set<String> pairs = joinedPairs(stored, desired, creating, wantTables, oldToNew);
      Set<String> allNames = new HashSet<>(wantTables.keySet());
      Map<String, String> aliasOwner = new HashMap<>();

      for(TableDef now : wantTables.values()) {
         TableDef old = storedTables.get(now.name());
         boolean fresh = old == null || removed.contains(now.name());
         List<AutoAliasDef> storedList = fresh ? List.of() :
            canonical(old.autoAliases(), oldToNew);
         boolean explicit = desired.hasTables() && now.autoAliases() != null;
         List<AutoAliasDef> wanted = explicit ? canonical(now.autoAliases(), Map.of()) :
            storedList;
         // validated in the order the caller sent, so a path names the entry it sent
         List<AutoAliasDef> listed = explicit ? now.autoAliases() : storedList;
         String path = "tables[" + now.name() + "].autoAliases";

         if(explicit && !sameAutoAliases(storedList, wanted)) {
            if(old != null && old.baseElement() && !fresh) {
               throw baseReadOnly(path);
            }

            if(now.kind() != Kind.PLAIN) {
               errors.add(new Err(path, "auto aliases can be set only on a plain table of the " +
                  "view; \"" + now.name() + "\" is " + (now.kind() == Kind.ALIAS ?
                  "an alias table (setting one would delete the alias itself)" :
                  "an inline view"), "AUTO_ALIAS_INVALID"));
               continue;
            }

            plan.autoAliasChanges.add(new AutoAliasChange(now.name(), storedList, wanted));
         }

         Set<String> froms = new HashSet<>();

         for(int i = 0; i < listed.size(); i++) {
            AutoAliasDef entry = listed.get(i);
            String entryPath = path + "[" + i + "]";

            if(!froms.add(entry.from())) {
               errors.add(new Err(entryPath + ".from", "table \"" + entry.from() +
                  "\" is listed twice; a table has one auto alias per joined table",
                  "AUTO_ALIAS_INVALID"));
            }

            if(entry.from().equals(now.name())) {
               errors.add(new Err(entryPath + ".from", "an auto alias is derived for another " +
                  "table joined to \"" + now.name() + "\", not for itself", "AUTO_ALIAS_INVALID"));
            }
            else if(!wantTables.containsKey(entry.from())) {
               errors.add(new Err(entryPath + ".from", explicit ?
                  "no table named \"" + entry.from() + "\" in this view" :
                  "the update removes table \"" + entry.from() + "\", which the auto alias \"" +
                     entry.alias() + "\" of \"" + now.name() + "\" is derived for; send " +
                     path + " without it (or [] to remove them all) to confirm",
                  explicit ? "AUTO_ALIAS_INVALID" : "AUTO_ALIAS_DROPPED"));
            }
            else if(!pairs.contains(pairKey(now.name(), entry.from()))) {
               errors.add(new Err(entryPath + ".from", explicit ?
                  "no join between \"" + now.name() + "\" and \"" + entry.from() + "\" in the " +
                     "view; an auto alias is derived from a join" :
                  "the update removes the last join between \"" + now.name() + "\" and \"" +
                     entry.from() + "\", which drops the auto alias \"" + entry.alias() +
                     "\"; send " + path + " without it (or [] to remove them all) to confirm, " +
                     "or keep the join", explicit ? "AUTO_ALIAS_INVALID" : "AUTO_ALIAS_DROPPED"));
            }

            if(allNames.contains(entry.alias())) {
               errors.add(new Err(entryPath + ".alias", "the alias \"" + entry.alias() +
                  "\" is already the name of a table of the view", "AUTO_ALIAS_INVALID"));
            }
            else {
               String other = aliasOwner.put(entry.alias(), entryPath);

               if(other != null) {
                  errors.add(new Err(entryPath + ".alias", "the alias \"" + entry.alias() +
                     "\" is used by another auto alias (" + other +
                     "); alias names are unique in a view", "AUTO_ALIAS_INVALID"));
               }
            }
         }
      }
   }

   /** The unordered table pairs the final view joins. */
   private static Set<String> joinedPairs(View stored, Desired desired, boolean creating,
                                          Map<String, TableDef> wantTables,
                                          Map<String, String> oldToNew)
   {
      Set<String> pairs = new HashSet<>();

      if(desired.hasJoins()) {
         desired.view().joins().forEach(j -> pairs.add(pairKey(j.fromTable(), j.toTable())));

         if(creating) {
            stored.joins().stream().filter(JoinDef::baseElement)
               .forEach(j -> pairs.add(pairKey(j.fromTable(), j.toTable())));
         }
      }
      else {
         for(JoinDef join : stored.joins()) {
            String from = oldToNew.getOrDefault(join.fromTable(), join.fromTable());
            String to = oldToNew.getOrDefault(join.toTable(), join.toTable());

            if(wantTables.containsKey(from) && wantTables.containsKey(to)) {
               pairs.add(pairKey(from, to));
            }
         }
      }

      return pairs;
   }

   private static String pairKey(String a, String b) {
      return a.compareTo(b) <= 0 ? a + "\u0001" + b : b + "\u0001" + a;
   }

   /**
    * The comparable form of a list of auto aliases: unused prefixes dropped, sorted by the table
    * they are derived for, {@code from} mapped through the renames of the same plan.
    */
   static List<AutoAliasDef> canonical(List<AutoAliasDef> list, Map<String, String> renamed) {
      List<AutoAliasDef> result = new ArrayList<>();

      if(list != null) {
         for(AutoAliasDef entry : list) {
            result.add(new AutoAliasDef(renamed.getOrDefault(entry.from(), entry.from()),
                                        entry.alias(), entry.keepOutgoing(),
                                        entry.prefix()).canonical());
         }
      }

      result.sort(Comparator.comparing(AutoAliasDef::from));
      return result;
   }

   static boolean sameAutoAliases(List<AutoAliasDef> a, List<AutoAliasDef> b) {
      return canonical(a, Map.of()).equals(canonical(b, Map.of()));
   }

   /** Whether every specified attribute of the desired join equals the stored one. */
   static boolean sameJoinAttributes(JoinDef stored, JoinDef desired) {
      return (desired.type() == null || desired.type().equals(stored.type())) &&
         (desired.mergingRule() == null || desired.mergingRule().equals(stored.mergingRule())) &&
         (desired.weak() == null || desired.weak().equals(stored.weak())) &&
         (desired.priority() == null || desired.priority().equals(stored.priority())) &&
         (desired.cardinality() == null || desired.cardinality().equals(stored.cardinality()));
   }

   private static boolean sameKind(TableDef a, TableDef b) {
      return a.kind() == b.kind();
   }

   /** Whether two tables are the same table; schema and catalog count only when specified. */
   private static boolean sameTable(TableDef stored, TableDef desired, boolean strict) {
      if(stored.kind() != desired.kind()) {
         return false;
      }

      switch(stored.kind()) {
      case ALIAS:
         return Tool.equals(stored.aliasOf(), desired.aliasOf());
      case VIEW:
         return !strict || Tool.equals(stored.sql(), desired.sql());
      default:
         return (desired.schema() == null || desired.schema().equals(stored.schema())) &&
            (desired.catalog() == null || desired.catalog().equals(stored.catalog()));
      }
   }

   private static Map<String, TableDef> byName(List<TableDef> tables) {
      Map<String, TableDef> map = new LinkedHashMap<>();
      tables.forEach(t -> map.put(t.name(), t));
      return map;
   }

   private static void validateTables(View want, List<Err> errors, Map<String, TableDef> out) {
      for(TableDef table : want.tables()) {
         if(out.containsKey(table.name())) {
            errors.add(new Err("tables[" + table.name() + "].name", "table name \"" +
               table.name() + "\" is listed twice", "DUPLICATE_NAME"));
            continue;
         }

         out.put(table.name(), table);
      }
   }

   private static void validateAliases(View want, List<Err> errors, Map<String, TableDef> out) {
      for(TableDef table : want.tables()) {
         if(table.kind() != Kind.ALIAS) {
            continue;
         }

         String path = "tables[" + table.name() + "].aliasOf";

         if(table.aliasOf().equals(table.name())) {
            errors.add(new Err(path, "an alias cannot alias itself", "ALIAS_SOURCE_UNKNOWN"));
         }
         else if(!out.containsKey(table.aliasOf())) {
            errors.add(new Err(path, "no table named \"" + table.aliasOf() +
               "\" in this view to alias (list it in tables)", "ALIAS_SOURCE_UNKNOWN"));
         }
         else if(aliasCycle(table, out)) {
            errors.add(new Err(path, "the aliases of \"" + table.name() + "\" form a cycle",
                               "ALIAS_SOURCE_UNKNOWN"));
         }
      }
   }

   private static boolean aliasCycle(TableDef start, Map<String, TableDef> tables) {
      Set<String> seen = new HashSet<>();
      TableDef current = start;

      while(current != null && current.kind() == Kind.ALIAS) {
         if(!seen.add(current.name())) {
            return true;
         }

         current = tables.get(current.aliasOf());
      }

      return false;
   }

   private static void validateJoins(View want, Map<String, TableDef> tables, List<Err> errors) {
      Set<String> keys = new HashSet<>();

      for(JoinDef join : want.joins()) {
         String path = "joins[" + join.label() + "]";

         if(!tables.containsKey(join.fromTable())) {
            errors.add(new Err(path + ".from.table", "no table named \"" + join.fromTable() +
               "\" in this view", "JOIN_TABLE_UNKNOWN"));
         }

         if(!tables.containsKey(join.toTable())) {
            errors.add(new Err(path + ".to.table", "no table named \"" + join.toTable() +
               "\" in this view", "JOIN_TABLE_UNKNOWN"));
         }

         if(join.fromTable().equals(join.toTable())) {
            errors.add(new Err(path, "a join needs two different tables (use an alias for a " +
               "self join)", "JOIN_INVALID"));
         }

         if(!keys.add(join.key())) {
            errors.add(new Err(path, "the join is listed twice", "JOIN_INVALID"));
         }
      }
   }

   /** An alias after the table or alias it aliases. */
   private static void orderAliases(List<TableDef> aliases, Map<String, TableDef> tables) {
      List<TableDef> ordered = new ArrayList<>();
      Set<String> pending = new HashSet<>();
      aliases.forEach(a -> pending.add(a.name()));
      List<TableDef> rest = new ArrayList<>(aliases);

      while(!rest.isEmpty()) {
         boolean progress = false;

         for(Iterator<TableDef> i = rest.iterator(); i.hasNext();) {
            TableDef alias = i.next();

            if(!pending.contains(alias.aliasOf())) {
               ordered.add(alias);
               pending.remove(alias.name());
               i.remove();
               progress = true;
            }
         }

         if(!progress) {
            ordered.addAll(rest);
            break;
         }
      }

      aliases.clear();
      aliases.addAll(ordered);
   }

   private static DmFailure baseReadOnly(String path) {
      return new DmFailure(
         WizDataModelWire.BASE_ELEMENT_READ_ONLY, path,
         "\"" + path + "\" is inherited from the base view and cannot be changed here");
   }

   // -- read-back ------------------------------------------------------------------------------

   /**
    * Compares what was asked for (names already translated to the stored names) with the stored
    * view read back. Returns the first difference, or null when the stored view is as written.
    */
   static String mismatch(View desired, Desired submitted, View stored) {
      if(submitted.hasDescription() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(desired.description()),
         WizPhysicalViewMapper.blankToNull(stored.description())))
      {
         return "definition.description";
      }

      if(submitted.hasFolder() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(desired.folder()),
         WizPhysicalViewMapper.blankToNull(stored.folder())))
      {
         return "definition.folder";
      }

      if(submitted.hasConnection() && !Tool.equals(
         WizPhysicalViewMapper.blankToNull(desired.connection()),
         WizPhysicalViewMapper.blankToNull(stored.connection())))
      {
         return "definition.connection";
      }

      Map<String, TableDef> storedTables = byName(stored.tables());

      if(submitted.hasTables()) {
         Set<String> wanted = new HashSet<>();

         for(TableDef table : desired.tables()) {
            wanted.add(table.name());
            TableDef now = storedTables.get(table.name());

            if(now == null) {
               return "tables[" + table.name() + "] is missing";
            }

            if(!sameTable(now, table, true)) {
               return "tables[" + table.name() + "]";
            }

            if(table.autoAliases() != null &&
               !sameAutoAliases(now.autoAliases(), table.autoAliases()))
            {
               return "tables[" + table.name() + "].autoAliases";
            }
         }

         for(TableDef table : stored.tables()) {
            if(!wanted.contains(table.name()) && !table.baseElement()) {
               return "tables[" + table.name() + "] is still there";
            }
         }
      }

      if(submitted.hasJoins()) {
         Map<String, JoinDef> storedJoins = new LinkedHashMap<>();
         stored.joins().forEach(j -> storedJoins.put(j.key(), j));
         Set<String> wanted = new HashSet<>();

         for(JoinDef join : desired.joins()) {
            wanted.add(join.key());
            JoinDef now = storedJoins.get(join.key());

            if(now == null) {
               return "joins[" + join.label() + "] is missing";
            }

            if(!sameJoinAttributes(now, join) ||
               !now.fromTable().equals(join.fromTable()) ||
               !now.fromColumn().equals(join.fromColumn()))
            {
               return "joins[" + join.label() + "]";
            }
         }

         for(JoinDef join : stored.joins()) {
            if(!wanted.contains(join.key()) && !join.baseElement()) {
               return "joins[" + join.label() + "] is still there";
            }
         }
      }

      return null;
   }
}
