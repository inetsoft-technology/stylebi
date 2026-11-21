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
}
