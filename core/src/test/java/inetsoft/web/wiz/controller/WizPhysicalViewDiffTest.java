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

import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizPhysicalViewDiff.Plan;
import inetsoft.web.wiz.controller.WizPhysicalViewMapper.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The plan of an update, with no server: what is removed, renamed, added and changed. */
@Tag("core")
class WizPhysicalViewDiffTest {
   private static TableDef plain(String name, boolean base) {
      return new TableDef(name, Kind.PLAIN, null, null, null, null, null, base);
   }

   private static TableDef alias(String name, String of, String oldName) {
      return new TableDef(name, Kind.ALIAS, null, null, of, null, oldName, false);
   }

   private static TableDef inline(String name, String sql, String oldName) {
      return new TableDef(name, Kind.VIEW, null, null, null, sql, oldName, false);
   }

   private static JoinDef join(String from, String fromColumn, String to, String toColumn,
                               String type, boolean base)
   {
      return new JoinDef(from, fromColumn, to, toColumn, type, "and", false, 0,
                         "MANY_TO_ONE", base);
   }

   private static View view(List<TableDef> tables, List<JoinDef> joins) {
      return new View("v", null, null, null, null, tables, joins);
   }

   private static Desired desired(List<TableDef> tables, List<JoinDef> joins) {
      return new Desired(view(tables, joins), false, false, false, true, true);
   }

   @Test
   void identicalViewsPlanNothing() {
      View stored = view(List.of(plain("T1", false), alias("A", "T1", null)),
                         List.of(join("A", "X", "T1", "Y", "EQUAL", false)));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(stored.tables(), stored.joins()),
                                           false);

      assertTrue(plan.isEmpty());
   }

   @Test
   void absentListsKeepTheStoredOnes() {
      View stored = view(List.of(plain("T1", false)),
                         List.of(join("T1", "X", "T1b", "Y", "EQUAL", false)));
      Desired onlyDescription = new Desired(
         new View("v", "new text", null, null, null, List.of(), List.of()),
         true, false, false, false, false);
      Plan plan = WizPhysicalViewDiff.plan(stored, onlyDescription, false);

      assertTrue(plan.structureEmpty());
      assertTrue(plan.descriptionChanged);
   }

   @Test
   void aRemovedTableAndItsJoinAreRemoved() {
      View stored = view(List.of(plain("T1", false), plain("T2", false)),
                         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(List.of(plain("T1", false)),
                                                           List.of()), false);

      assertEquals(List.of("T2"), plan.removeTables.stream().map(TableDef::name).toList());
      assertEquals(1, plan.removeJoins.size());
   }

   @Test
   void anAliasRenameWithOldNameIsARenameNotARemoveAndAdd() {
      View stored = view(List.of(plain("T1", false), alias("A1", "T1", null)), List.of());
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), alias("A2", "T1", "A1")), List.of()), false);

      assertEquals(1, plan.renames.size());
      assertEquals("A1", plan.renames.get(0).from().name());
      assertEquals("A2", plan.renames.get(0).to().name());
      assertTrue(plan.removeTables.isEmpty());
      assertTrue(plan.addAliases.isEmpty());
      assertEquals(Map.of("A2", "A1"), plan.renamedFrom());
   }

   @Test
   void anAliasWithoutOldNameThatReplacesAnotherIsARemoveAndAdd() {
      // no marker, no migration: the stored alias is gone and a new one is added
      View stored = view(List.of(plain("T1", false), alias("A1", "T1", null)), List.of());
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), alias("A2", "T1", null)), List.of()), false);

      assertTrue(plan.renames.isEmpty());
      assertEquals(List.of("A1"), plan.removeTables.stream().map(TableDef::name).toList());
      assertEquals(List.of("A2"), plan.addAliases.stream().map(TableDef::name).toList());
      assertTrue(plan.renamedFrom().isEmpty());
   }

   @Test
   void aRenameOfAnUnknownOrPlainTableIsRefused() {
      View stored = view(List.of(plain("T1", false), alias("A1", "T1", null)), List.of());

      DmFailure unknown = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), alias("A2", "T1", "NOPE")), List.of()),
         false));
      assertEquals("tables[A2].oldName", unknown.toBody().errors().get(0).field());

      DmFailure kindChange = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), inline("V9", "select 1", "A1")), List.of()),
         false));
      assertEquals("TABLE_INVALID", kindChange.toBody().errors().get(0).code());
   }

   @Test
   void anAliasThatChangesItsSourceIsReAdded() {
      View stored = view(List.of(plain("T1", false), plain("T2", false),
                                 alias("A", "T1", null)), List.of());
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), plain("T2", false), alias("A", "T2", null)),
                         List.of()), false);

      assertEquals(List.of("A"), plan.removeTables.stream().map(TableDef::name).toList());
      assertEquals(List.of("A"), plan.addAliases.stream().map(TableDef::name).toList());
   }

   @Test
   void anInlineViewWhoseSqlChangesIsUpdatedInPlace() {
      View stored = view(List.of(inline("V", "select 1", null)), List.of());
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(inline("V", "select 2", null)), List.of()), false);

      assertEquals(1, plan.updateViews.size());
      assertTrue(plan.removeTables.isEmpty());
   }

   @Test
   void aliasesAreAddedAfterTheAliasesTheyAlias() {
      View stored = view(List.of(plain("T1", false)), List.of());
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), alias("A3", "A2", null),
                                 alias("A2", "A1", null), alias("A1", "T1", null)), List.of()),
         false);

      assertEquals(List.of("A1", "A2", "A3"),
                   plan.addAliases.stream().map(TableDef::name).toList());
   }

   @Test
   void aJoinChangedInPlaceIsAnUpdateAndAReversedOneIsARemoveAndAdd() {
      JoinDef stored = join("T2", "X", "T1", "Y", "EQUAL", false);
      View view = view(List.of(plain("T1", false), plain("T2", false)), List.of(stored));

      Plan changed = WizPhysicalViewDiff.plan(view, desired(
         view.tables(), List.of(join("T2", "X", "T1", "Y", "LEFT_OUTER", false))), false);
      assertEquals(1, changed.changeJoins.size());
      assertTrue(changed.removeJoins.isEmpty() && changed.addJoins.isEmpty());

      Plan reversed = WizPhysicalViewDiff.plan(view, desired(
         view.tables(), List.of(join("T1", "Y", "T2", "X", "EQUAL", false))), false);
      assertEquals(1, reversed.removeJoins.size());
      assertEquals(1, reversed.addJoins.size());
   }

   @Test
   void anUnspecifiedJoinAttributeKeepsTheStoredValue() {
      View stored = view(List.of(plain("T1", false), plain("T2", false)),
                         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      JoinDef bare = new JoinDef("T2", "X", "T1", "Y", null, null, null, null, null, false);
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(stored.tables(), List.of(bare)),
                                           false);

      assertTrue(plan.isEmpty());
   }

   @Test
   void theSubmittedListIsCheckedBeforeAnything() {
      View stored = view(List.of(plain("T1", false)), List.of());
      DmFailure failure = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), plain("T1", false),
                                 alias("A", "A", null), alias("B", "C", null),
                                 alias("C", "B", null)),
                         List.of(join("T1", "X", "T1", "Y", "EQUAL", false),
                                 join("T1", "X", "ZZ", "Y", "EQUAL", false))), false));
      List<String> codes = failure.toBody().errors().stream()
         .map(WizDataModelWire.Err::code).toList();

      assertTrue(codes.contains("DUPLICATE_NAME"), codes.toString());
      assertTrue(codes.contains("ALIAS_SOURCE_UNKNOWN"), codes.toString());
      assertTrue(codes.contains("JOIN_TABLE_UNKNOWN"), codes.toString());
      assertTrue(codes.contains("JOIN_INVALID"), codes.toString());
   }

   // -- extended views -------------------------------------------------------------------------

   private static View extended() {
      return view(List.of(plain("T1", true), plain("T2", true), plain("T3", false)),
                  List.of(join("T2", "X", "T1", "Y", "EQUAL", true),
                          join("T3", "X", "T2", "Y", "EQUAL", false)));
   }

   @Test
   void aBaseTableCannotBeRemovedRenamedOrChanged() {
      View stored = extended();
      DmFailure removed = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T2", true), plain("T3", false)), stored.joins()), false));
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, removed.toBody().reason());
      assertEquals("tables[T1]", removed.toBody().field());

      DmFailure changed = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(inline("T1", "select 1", null), plain("T2", true),
                                 plain("T3", false)), stored.joins()), false));
      assertEquals("tables[T1]", changed.toBody().field());
   }

   @Test
   void aBaseJoinCannotBeRemovedOrChanged() {
      View stored = extended();
      DmFailure removed = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(stored.tables(), List.of(stored.joins().get(1))), false));
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, removed.toBody().reason());

      DmFailure changed = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(stored.tables(), List.of(join("T2", "X", "T1", "Y", "LEFT_OUTER", false),
                                                  stored.joins().get(1))), false));
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, changed.toBody().reason());
   }

   @Test
   void anOwnElementOfAnExtendedViewChangesFreely() {
      View stored = extended();
      Plan plan = WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", true), plain("T2", true)),
                         List.of(stored.joins().get(0))), false);

      assertEquals(List.of("T3"), plan.removeTables.stream().map(TableDef::name).toList());
      assertEquals(1, plan.removeJoins.size());
   }

   @Test
   void aNewExtendedViewDeclaresOnlyItsOwnElements() {
      View start = view(List.of(plain("T1", true), plain("T2", true)),
                        List.of(join("T2", "X", "T1", "Y", "EQUAL", true)));
      Plan plan = WizPhysicalViewDiff.plan(
         start, desired(List.of(plain("T3", false)),
                        List.of(join("T3", "X", "T2", "Y", "EQUAL", false))), true);

      assertEquals(List.of("T3"), plan.addPlain.stream().map(TableDef::name).toList());
      assertEquals(1, plan.addJoins.size());
      assertTrue(plan.removeTables.isEmpty() && plan.removeJoins.isEmpty());
   }

   // -- the cap counts own elements and never refuses a view that does not grow -----------------

   private static List<TableDef> own(int count, boolean base) {
      List<TableDef> tables = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         tables.add(plain((base ? "B" : "T") + i, base));
      }

      return tables;
   }

   private static List<JoinDef> joins(int count, boolean base) {
      List<JoinDef> joins = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         joins.add(join("A", "C" + i, "B", "D" + i, "EQUAL", base));
      }

      return joins;
   }

   private static List<TableDef> plus(List<TableDef> a, List<TableDef> b) {
      List<TableDef> all = new ArrayList<>(a);
      all.addAll(b);
      return all;
   }

   @Test
   void aStoredViewAboveTheCapFedBackUnchangedIsNoChange() {
      View stored = view(plus(own(250, false), List.of(plain("A", false), plain("B", false))),
                         joins(1100, false));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(stored.tables(), stored.joins()),
                                           false);

      assertTrue(plan.isEmpty());
   }

   @Test
   void aViewAboveTheCapMayNotGrowButMayShrink() {
      View stored = view(plus(own(250, false), List.of(plain("A", false), plain("B", false))),
                         joins(1100, false));
      DmFailure tables = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(plus(stored.tables(), List.of(plain("EXTRA", false))), stored.joins()),
         false));
      assertEquals("tables", tables.toBody().field());
      assertEquals("LIMIT_EXCEEDED", tables.toBody().errors().get(0).code());
      assertTrue(tables.getMessage().contains("200"));

      List<JoinDef> more = new ArrayList<>(stored.joins());
      more.add(join("A", "X", "B", "Y", "EQUAL", false));
      DmFailure joins = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(stored.tables(), more), false));
      assertEquals("joins", joins.toBody().field());
      assertTrue(joins.getMessage().contains("1000"));

      Plan shrink = WizPhysicalViewDiff.plan(
         stored, desired(stored.tables().subList(0, 240), List.of()), false);
      assertFalse(shrink.removeTables.isEmpty());
   }

   @Test
   void createIsCappedAtTwoHundredTablesAndAThousandJoins() {
      View empty = view(List.of(), List.of());

      assertDoesNotThrow(() -> WizPhysicalViewDiff.plan(
         empty, desired(own(200, false), List.of()), true));
      DmFailure tables = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         empty, desired(own(201, false), List.of()), true));
      assertEquals("tables", tables.toBody().field());

      List<TableDef> two = List.of(plain("A", false), plain("B", false));
      assertDoesNotThrow(() -> WizPhysicalViewDiff.plan(empty, desired(two, joins(1000, false)),
                                                        true));
      DmFailure joins = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         empty, desired(two, joins(1001, false)), true));
      assertEquals("joins", joins.toBody().field());
   }

   @Test
   void inheritedTablesDoNotCountAgainstTheCap() {
      // 150 inherited and 60 own: 210 in the get result, 60 own
      View stored = view(plus(own(150, true), own(60, false)), List.of());
      Plan same = WizPhysicalViewDiff.plan(stored, desired(stored.tables(), List.of()), false);
      assertTrue(same.isEmpty());

      // a new extended view over a base of 250 tables declares 200 of its own
      View start = view(own(250, true), List.of());
      assertDoesNotThrow(() -> WizPhysicalViewDiff.plan(
         start, desired(own(200, false), List.of()), true));
      assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         start, desired(own(201, false), List.of()), true));
   }

   @Test
   void aCallerCannotFlagItsOwnTablesAsInheritedToEscapeTheCap() {
      View stored = view(List.of(plain("T0", false)), List.of());
      List<TableDef> flagged = new ArrayList<>();

      for(int i = 0; i < 201; i++) {
         flagged.add(plain("F" + i, true));
      }

      assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(flagged, List.of()), false));
   }

   // -- read-back ------------------------------------------------------------------------------

   @Test
   void theReadBackNamesTheFirstDifference() {
      View stored = view(List.of(plain("T1", false), plain("T2", false)),
                         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Desired same = desired(stored.tables(), stored.joins());

      assertNull(WizPhysicalViewDiff.mismatch(stored, same, stored));

      View missingJoin = view(stored.tables(), List.of());
      assertTrue(WizPhysicalViewDiff.mismatch(stored, same, missingJoin).startsWith("joins["));

      View extraTable = view(List.of(plain("T1", false), plain("T2", false),
                                     plain("T3", false)), stored.joins());
      assertTrue(WizPhysicalViewDiff.mismatch(stored, same, extraTable).contains("T3"));

      View otherType = view(stored.tables(),
                            List.of(join("T2", "X", "T1", "Y", "LEFT_OUTER", false)));
      assertTrue(WizPhysicalViewDiff.mismatch(stored, same, otherType).startsWith("joins["));
   }

   // -- auto aliases ---------------------------------------------------------------------------

   private static AutoAliasDef auto(String from, String alias, boolean keep, String prefix) {
      return new AutoAliasDef(from, alias, keep, prefix);
   }

   private static TableDef withAuto(TableDef table, AutoAliasDef... autoAliases) {
      return new TableDef(table.name(), table.kind(), table.schema(), table.catalog(),
                          table.aliasOf(), table.sql(), table.oldName(), table.baseElement(),
                          List.of(autoAliases));
   }

   @Test
   void anAutoAliasOnAJoinedTableIsPlanned() {
      View stored = view(List.of(withAuto(plain("T1", false)), plain("T2", false)),
                         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", true, "p_")), plain("T2", false)),
         stored.joins()), false);

      assertEquals(1, plan.autoAliasChanges.size());
      assertEquals("T1", plan.autoAliasChanges.get(0).table());
      assertTrue(plan.autoAliasChanges.get(0).stored().isEmpty());
      assertEquals(List.of(auto("T2", "t2a", true, "p_")), plan.autoAliasChanges.get(0).desired());
      assertFalse(plan.isEmpty());
   }

   @Test
   void theSameAutoAliasesPlanNothingAndAnUnusedPrefixDoesNotCount() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, null)),
                 plain("T2", false)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, "stale")),
                 plain("T2", false)), stored.joins()), false);

      assertTrue(plan.isEmpty());
   }

   @Test
   void anEmptyListClearsAndAnAbsentKeyKeeps() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, null)),
                 plain("T2", false)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Plan cleared = WizPhysicalViewDiff.plan(stored, desired(
         List.of(withAuto(plain("T1", false)), plain("T2", false)), stored.joins()), false);
      Plan kept = WizPhysicalViewDiff.plan(stored, desired(
         List.of(plain("T1", false), plain("T2", false)), stored.joins()), false);

      assertEquals(1, cleared.autoAliasChanges.size());
      assertTrue(cleared.autoAliasChanges.get(0).desired().isEmpty());
      assertTrue(kept.isEmpty());
   }

   @Test
   void removingTheLastJoinOfAKeptAutoAliasIsRefusedByName() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, null)),
                 plain("T2", false)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      DmFailure failure = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false), plain("T2", false)), List.of()), false));

      assertEquals("tables[T1].autoAliases[0].from", failure.toBody().errors().get(0).field());
      assertEquals("AUTO_ALIAS_DROPPED", failure.toBody().errors().get(0).code());
   }

   @Test
   void removingTheTableAnAutoAliasIsDerivedForIsRefused() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, null)),
                 plain("T2", false)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      DmFailure failure = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(List.of(plain("T1", false)), List.of()), false));

      assertEquals("AUTO_ALIAS_DROPPED", failure.toBody().errors().get(0).code());
   }

   @Test
   void droppingTheAutoAliasInTheSameRequestAllowsTheJoinRemoval() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", false, null)),
                 plain("T2", false)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));
      Plan plan = WizPhysicalViewDiff.plan(stored, desired(
         List.of(withAuto(plain("T1", false)), plain("T2", false)), List.of()), false);

      assertEquals(1, plan.removeJoins.size());
      assertEquals(1, plan.autoAliasChanges.size());
   }

   @Test
   void anAutoAliasNeedsAJoinAPlainTableAndAFreeName() {
      View stored = view(List.of(plain("T1", false), plain("T2", false), plain("T3", false),
                                 alias("A", "T1", null)),
                         List.of(join("T2", "X", "T1", "Y", "EQUAL", false)));

      // not joined
      DmFailure notJoined = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(
            List.of(withAuto(plain("T1", false), auto("T3", "t3a", false, null)),
                    plain("T2", false), plain("T3", false), alias("A", "T1", null)),
            stored.joins()), false));
      assertEquals("tables[T1].autoAliases[0].from", notJoined.toBody().errors().get(0).field());

      // on an alias table (it would delete the alias)
      DmFailure onAlias = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(
            List.of(plain("T1", false), plain("T2", false), plain("T3", false),
                    withAuto(alias("A", "T1", null), auto("T2", "x", false, null))),
            stored.joins()), false));
      assertEquals("tables[A].autoAliases", onAlias.toBody().errors().get(0).field());

      // a name that is a table
      DmFailure taken = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(
            List.of(withAuto(plain("T1", false), auto("T2", "T3", false, null)),
                    plain("T2", false), plain("T3", false), alias("A", "T1", null)),
            stored.joins()), false));
      assertEquals("tables[T1].autoAliases[0].alias", taken.toBody().errors().get(0).field());
   }

   @Test
   void anInheritedTableKeepsItsAutoAliases() {
      View stored = view(
         List.of(withAuto(plain("T1", true), auto("T2", "t2a", false, null)),
                 plain("T2", true)),
         List.of(join("T2", "X", "T1", "Y", "EQUAL", true)));
      Plan same = WizPhysicalViewDiff.plan(stored, desired(
         List.of(withAuto(plain("T1", true), auto("T2", "t2a", false, null)), plain("T2", true)),
         stored.joins()), false);

      assertTrue(same.isEmpty());
      DmFailure failure = assertThrows(DmFailure.class, () -> WizPhysicalViewDiff.plan(
         stored, desired(
            List.of(withAuto(plain("T1", true), auto("T2", "other", false, null)),
                    plain("T2", true)), stored.joins()), false));
      assertEquals(WizDataModelWire.BASE_ELEMENT_READ_ONLY, failure.reason());
   }

   @Test
   void theReadBackComparesTheStatedAutoAliases() {
      View stored = view(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", true, "p_")), plain("T2", false)),
         List.of());
      Desired same = desired(
         List.of(withAuto(plain("T1", false), auto("T2", "t2a", true, "p_")), plain("T2", false)),
         List.of());
      Desired other = desired(
         List.of(withAuto(plain("T1", false), auto("T2", "t2b", true, "p_")), plain("T2", false)),
         List.of());
      Desired silent = desired(List.of(plain("T1", false), plain("T2", false)), List.of());

      assertNull(WizPhysicalViewDiff.mismatch(same.view(), same, stored));
      assertEquals("tables[T1].autoAliases",
                   WizPhysicalViewDiff.mismatch(other.view(), other, stored));
      assertNull(WizPhysicalViewDiff.mismatch(silent.view(), silent, stored));
   }
}
