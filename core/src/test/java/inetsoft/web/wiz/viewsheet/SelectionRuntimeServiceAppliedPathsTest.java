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
package inetsoft.web.wiz.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * bug-78808: the snapshot used to report another filter's selection being removed by Association
 * counts a selected value only while it is not excluded (multi-select "applied" rule).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceAppliedPathsTest {
   private static SelectionValue value(String name, boolean selected, boolean excluded) {
      SelectionValue value = new SelectionValue(name, name);
      value.setSelected(selected);
      value.setExcluded(excluded);
      return value;
   }

   @Test
   void skipExcludedDropsSelectedButExcludedValues() {
      SelectionValue[] values = {
         value("East", true, true), value("West", true, false), value("North", false, false)
      };

      assertEquals(List.of(List.of("East"), List.of("West")),
                   SelectionRuntimeService.selectedPaths(values, false));
      assertEquals(List.of(List.of("West")),
                   SelectionRuntimeService.selectedPaths(values, true));
   }

   @Test
   void skipExcludedDropsChildrenOfExcludedParent() {
      CompositeSelectionValue parent = new CompositeSelectionValue("East", "East");
      parent.setSelected(true);
      parent.setExcluded(true);
      SelectionList children = new SelectionList();
      SelectionValue child = value("NY", true, false);
      child.setLevel(1);
      children.addSelectionValue(child);
      parent.setSelectionList(children);

      assertTrue(SelectionRuntimeService.selectedPaths(
         new SelectionValue[] { parent }, true).isEmpty());
      assertEquals(List.of(java.util.Arrays.asList("East", "NY")),
                   SelectionRuntimeService.selectedPaths(new SelectionValue[] { parent }, false));
   }

   private static Map<String, SelectionRuntimeService.OtherSelection> snap(
      Object... nameTypePaths)
   {
      Map<String, SelectionRuntimeService.OtherSelection> map = new LinkedHashMap<>();

      for(int i = 0; i < nameTypePaths.length; i += 2) {
         @SuppressWarnings("unchecked")
         List<List<String>> paths = (List<List<String>>) nameTypePaths[i + 1];
         map.put((String) nameTypePaths[i], new SelectionRuntimeService.OtherSelection("a list", paths));
      }

      return map;
   }

   private static final List<List<String>> NONE = List.of();
   private static final List<List<String>> CA_NY = List.of(List.of("CA"), List.of("NY"));
   private static final List<List<String>> NY = List.of(List.of("NY"));

   @Test
   void diffReportsClearedSelection() {
      List<Map<String, Object>> out =
         SelectionRuntimeService.diffOthers(snap("State", CA_NY), snap("State", NONE));

      assertEquals(1, out.size());
      assertEquals("State", out.get(0).get("assembly"));
      assertEquals("a list", out.get(0).get("type"));
      assertEquals(CA_NY, out.get(0).get("removed"));
      assertEquals(NONE, out.get(0).get("added"));
   }

   @Test
   void diffReportsPartialRemoval() {
      Map<String, Object> entry =
         SelectionRuntimeService.diffOthers(snap("State", CA_NY), snap("State", NY)).get(0);

      assertEquals(List.of(List.of("CA")), entry.get("removed"));
      assertEquals(NONE, entry.get("added"));
      assertEquals(NY, entry.get("after"));
   }

   @Test
   void diffReportsRePickAsRemovedAndAdded() {
      Map<String, Object> entry = SelectionRuntimeService.diffOthers(
         snap("State", NY), snap("State", List.of(List.of("CA")))).get(0);

      assertEquals(NY, entry.get("removed"));
      assertEquals(List.of(List.of("CA")), entry.get("added"));
   }

   @Test
   void diffIgnoresUnchangedAndReorderedSelection() {
      assertTrue(SelectionRuntimeService.diffOthers(
         snap("State", CA_NY), snap("State", List.of(List.of("NY"), List.of("CA")))).isEmpty());
   }

   @Test
   void diffReportsNothingWhenBeforeSnapshotIsUnknown() {
      assertTrue(SelectionRuntimeService.diffOthers(snap(), snap("State", CA_NY)).isEmpty());
   }

   @Test
   void diffSkipsAssemblyMissingAfterAndKeepsAbsoluteNamesApart() {
      assertTrue(SelectionRuntimeService.diffOthers(snap("State", CA_NY), snap()).isEmpty());

      List<Map<String, Object>> out = SelectionRuntimeService.diffOthers(
         snap("List1", NY, "Emb1.List1", NY), snap("List1", NY, "Emb1.List1", NONE));

      assertEquals(1, out.size());
      assertEquals("Emb1.List1", out.get(0).get("assembly"));
   }

   @Test
   void diffOfEmptySnapshotsIsEmpty() {
      assertTrue(SelectionRuntimeService.diffOthers(snap(), snap()).isEmpty());
   }
}
