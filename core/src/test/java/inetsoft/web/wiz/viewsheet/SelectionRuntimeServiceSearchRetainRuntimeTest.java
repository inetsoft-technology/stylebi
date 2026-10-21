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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.controller.VSCalendarService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.VSSelectionService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78983: set_selection replace under an active search keeps a search-hidden selected value
 * that shares a top-level ancestor with a visible match. Runs the real SelectionRuntimeService over
 * the real VSSelectionService (EmbeddedVS1, tree year > name) because the merge-back that loses
 * the value is invisible to a mocked VSSelectionService.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
class SelectionRuntimeServiceSearchRetainRuntimeTest {
   @BeforeEach
   void setUp() throws Exception {
      oldPrincipal = ThreadContext.getContextPrincipal();
      AssetDataCache.getCache().clear();

      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      vs = box.getViewsheet();
      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "ST1");
      tree.setTableName(table);
      tree.setDataRefs(new DataRef[] { column("year"), column("name") });
      vs.addAssembly(tree);
      box.lockWrite();

      try {
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.unlockWrite();
      }

      principal = ThreadContext.getPrincipal();
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      CapturingCommandDispatcher dispatcher =
         mock(CapturingCommandDispatcher.class, RETURNS_DEEP_STUBS);
      String rid = vsResource.getRuntimeId();
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, rid, dispatcher);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      service = new SelectionRuntimeService(sessions, selections, mock(VSCalendarService.class));
   }

   @AfterEach
   void restore() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   private SelectionTreeVSAssembly tree() {
      return (SelectionTreeVSAssembly) vs.getAssembly("ST1");
   }

   private List<String> names(String year) {
      CompositeSelectionValue node =
         (CompositeSelectionValue) tree().getSelectionList().findValue(year, false);
      List<String> out = new ArrayList<>();

      for(SelectionValue v : node.getSelectionList().getSelectionValues()) {
         out.add(v.getValue());
      }

      return out;
   }

   private List<String> years() {
      List<String> out = new ArrayList<>();

      for(SelectionValue v : tree().getSelectionList().getSelectionValues()) {
         out.add(v.getValue());
      }

      return out;
   }

   private Set<List<String>> selected() {
      return new HashSet<>(SelectionRuntimeService.selectedPaths(
         tree().getSelectionList().getSelectionValues()));
   }

   private Map<String, Object> replace(String search, List<List<String>> values) throws Exception {
      return service.setSelection("tok", principal, "ST1", values, null, null, null, null, search,
                                  "");
   }

   /** A year, a name n visible under search n, and a name h of the same year that n hides. */
   private String[] pickVisibleAndHidden() {
      for(String year : years()) {
         List<String> names = names(year);

         for(String n : names) {
            for(String h : names) {
               if(!h.toLowerCase().contains(n.toLowerCase())) {
                  return new String[] { year, n, h };
               }
            }
         }
      }

      throw new AssertionError("no suitable data");
   }

   @Test
   void replaceUnderSearchKeepsHiddenSelectionSharingTheTopLevelAncestor() throws Exception {
      String[] p = pickVisibleAndHidden();
      replace(null, List.of(List.of(p[0], p[1]), List.of(p[0], p[2])));
      assertEquals(Set.of(List.of(p[0], p[1]), List.of(p[0], p[2])), selected());

      Map<String, Object> result = replace(p[1], List.of(List.of(p[0], p[1])));

      assertEquals(Set.of(List.of(p[0], p[1]), List.of(p[0], p[2])), selected());
      assertEquals(List.of(List.of(p[0], p[2])), result.get("scopedBySearchRetained"));
   }

   @Test
   void replaceUnderSearchWithAVisibleValueUnderAnotherYearDoesNotCollapseToTheSharedAncestor()
      throws Exception
   {
      // a search letter that shows a name of year1 and a name of year2 but hides another of year1
      String year = null;
      String search = null;
      String visible = null;
      String hidden = null;
      String other = null;
      String otherName = null;
      List<String> years = years();

      for(char c = 'a'; c <= 'z' && search == null; c++) {
         String letter = String.valueOf(c);
         String v = null;
         String h = null;

         for(String n : names(years.get(0))) {
            if(n.toLowerCase().contains(letter)) {
               v = n;
            }
            else {
               h = n;
            }
         }

         String on = null;

         for(String n : names(years.get(1))) {
            if(n.toLowerCase().contains(letter)) {
               on = n;
            }
         }

         if(v != null && h != null && on != null) {
            year = years.get(0);
            search = letter;
            visible = v;
            hidden = h;
            other = years.get(1);
            otherName = on;
         }
      }

      assertNotNull(search, "no suitable data");
      replace(null, List.of(List.of(year, visible), List.of(year, hidden)));

      Map<String, Object> result = replace(search, List.of(List.of(other, otherName)));

      assertTrue(selected().contains(List.of(year, hidden)), "hidden value was cleared");
      assertTrue(selected().contains(List.of(other, otherName)));
      assertFalse(selected().contains(List.of(year, visible)));
      assertEquals(List.of(List.of(year, hidden)), result.get("scopedBySearchRetained"));
   }

   @Test
   void replaceUnderSearchWhereEverySelectedValueIsVisibleReplacesNormally() throws Exception {
      String[] p = pickVisibleAndHidden();
      replace(null, List.of(List.of(p[0], p[1])));

      Map<String, Object> result = replace(p[1].substring(0, 1), List.of(List.of(p[0], p[1])));

      assertEquals(Set.of(List.of(p[0], p[1])), selected());
      assertFalse(result.containsKey("scopedBySearchRetained"));

      // swap to another visible value of the same year: the old one is cleared, nothing retained
      String swap = null;

      for(String n : names(p[0])) {
         if(!n.equals(p[1]) && n.toLowerCase().contains(p[1].substring(0, 1).toLowerCase())) {
            swap = n;
         }
      }

      Assumptions.assumeTrue(swap != null, "no second visible name");
      result = replace(p[1].substring(0, 1), List.of(List.of(p[0], swap)));

      assertEquals(Set.of(List.of(p[0], swap)), selected());
      assertFalse(result.containsKey("scopedBySearchRetained"));
   }

   @Test
   void replaceWithoutASearchStillClearsEverythingElse() throws Exception {
      String[] p = pickVisibleAndHidden();
      replace(null, List.of(List.of(p[0], p[1]), List.of(p[0], p[2])));

      replace(null, List.of(List.of(p[0], p[1])));

      assertEquals(Set.of(List.of(p[0], p[1])), selected());
   }

   private static ColumnRef column(String name) {
      return new ColumnRef(new AttributeRef(null, name));
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @Autowired
   private VSSelectionService selections;
   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
   private SelectionRuntimeService service;
   private Principal oldPrincipal;
   private Principal principal;
   private Viewsheet vs;
}
