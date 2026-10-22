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
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
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
import static org.mockito.Mockito.when;

/**
 * bug-79196: {@code selectionState} carries the whole-domain {@code selectedTotal} and
 * {@code selectableTotal}, because the plugin hands the caller only a bounded page of
 * {@code values}. A real {@code SelectionList} needs the Spring recipe of
 * {@link SelectionRuntimeServiceSubtreeDomainTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceSelectionStateTest {
   private static SelectionValue value(String v, boolean selected, boolean included) {
      SelectionValue sv = new SelectionValue(v, v);
      sv.setSelected(selected);

      if(included) {
         sv.setState(sv.getState() | SelectionValue.STATE_INCLUDED);
      }

      return sv;
   }

   /** East{NY selected, CT}, Beta selected; East is marked selected like a fixed-hierarchy select. */
   private static SelectionList tree() {
      SelectionList domain = new SelectionList();
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      east.setSelected(true);
      east.setState(east.getState() | SelectionValue.STATE_INCLUDED);
      SelectionList children = new SelectionList();
      SelectionValue ny = value("NY", true, true);
      ny.setLevel(1);
      SelectionValue ct = value("CT", false, true);
      ct.setLevel(1);
      children.addSelectionValue(ny);
      children.addSelectionValue(ct);
      east.setSelectionList(children);
      domain.addSelectionValue(east);
      CompositeSelectionValue beta = new CompositeSelectionValue("Beta", "Beta");
      beta.setSelected(true);
      beta.setState(beta.getState() | SelectionValue.STATE_INCLUDED);
      domain.addSelectionValue(beta);
      return domain;
   }

   @Test
   void aListReportsSelectedAndSelectableTotals() throws Exception {
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(value("A", true, true));
      domain.addSelectionValue(value("B", false, true));
      domain.addSelectionValue(value("C", true, false));
      SelectionListVSAssembly assembly = SelectionRuntimeServiceTest.list(0, false, null);
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Map<String, Object> result =
         h.service().selectionState("tok", SelectionRuntimeServiceTest.principal(), "Filter1");

      assertEquals(3, result.get("totalValues"));
      assertEquals(2, result.get("selectedTotal"));
      assertEquals(2, result.get("selectableTotal"));
   }

   @Test
   void aNonIdTreeCountsLeavesLikeSetSelection() throws Exception {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(false);
      when(assembly.getSelectionList()).thenReturn(tree());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Map<String, Object> result =
         h.service().selectionState("tok", SelectionRuntimeServiceTest.principal(), "Tree1");

      assertEquals(4, result.get("totalValues"));
      assertEquals(2, result.get("selectedTotal"), "NY and Beta, not East's chain");
      assertEquals(3, result.get("selectableTotal"), "leaves NY, CT, Beta; East is a container");
   }

   @Test
   void anIdTreeCountsNodes() throws Exception {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      when(assembly.getSelectionList()).thenReturn(tree());
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Map<String, Object> result =
         h.service().selectionState("tok", SelectionRuntimeServiceTest.principal(), "Tree1");

      assertEquals(3, result.get("selectedTotal"), "East, NY and Beta");
      assertEquals(4, result.get("selectableTotal"));
   }

   @Test
   void anUncomputedListReportsZeroTotals() throws Exception {
      SelectionListVSAssembly assembly = SelectionRuntimeServiceTest.list(0, false, null);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Map<String, Object> result =
         h.service().selectionState("tok", SelectionRuntimeServiceTest.principal(), "Filter1");

      assertEquals(false, result.get("computed"));
      assertEquals(0, result.get("selectedTotal"));
      assertEquals(0, result.get("selectableTotal"));
   }

   @Test
   void countSelectableHandlesAnEmptyChildrenListAsALeaf() {
      Map<String, Object> parent = new LinkedHashMap<>();
      parent.put("selectableInSelectAll", true);
      parent.put("children", List.of());

      assertEquals(1, SelectionRuntimeService.countSelectable(List.of(parent), true));
      assertEquals(0, SelectionRuntimeService.countSelectable(List.of(), true));
   }
}
