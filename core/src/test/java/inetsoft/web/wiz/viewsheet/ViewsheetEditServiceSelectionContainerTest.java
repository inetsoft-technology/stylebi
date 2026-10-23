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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79239: edit(op:'add_to_selection_container') and edit(op:'reorder_container_children')
 * on an EXISTING Selection Container. Real ViewsheetEditService and a real Viewsheet with real
 * CurrentSelectionVSAssembly children; GroupingService is a stand-in that appends the way
 * {@code groupComponents(selection=true)} does, so the position logic around it is what is
 * under test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetEditServiceSelectionContainerTest {
   private Viewsheet vs;
   private ViewsheetEditService service;
   private GroupingService grouping;
   private CoreLifecycleService lifecycle;

   private void setup() throws Exception {
      vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      add(new TableVSAssembly(vs, "OrderTable"), 300, 20);
      add(new SelectionListVSAssembly(vs, "ListA"), 20, 20);
      add(new SelectionListVSAssembly(vs, "ListB"), 20, 220);
      add(new SelectionListVSAssembly(vs, "ListC"), 20, 420);
      add(new SelectionListVSAssembly(vs, "ListD"), 20, 620);
      add(new SelectionListVSAssembly(vs, "ListE"), 20, 820);
      CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, "Box");
      add(cs, 20, 20);
      cs.setAssemblies(new String[] { "ListA", "ListB" });
      CurrentSelectionVSAssembly other = new CurrentSelectionVSAssembly(vs, "Other");
      add(other, 600, 20);
      other.setAssemblies(new String[] { "ListE" });
      CurrentSelectionVSAssembly grouped = new CurrentSelectionVSAssembly(vs, "GroupedBox");
      add(grouped, 900, 20);
      GroupContainerVSAssembly group = new GroupContainerVSAssembly(vs, "Group1");
      add(group, 900, 20);
      group.setAssemblies(new String[] { "GroupedBox" });
      add(new TextVSAssembly(vs, "Text1"), 300, 300);
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      add(tab, 1200, 20);
      tab.setAssemblies(new String[] { "ListD", "Text1" });

      List<AssemblyNode> nodes = new ArrayList<>();

      for(Assembly a : vs.getAssemblies()) {
         nodes.add(new AssemblyNode(a.getName(), "Text", 0, 0, 10, 10, 0, null, true));
      }

      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", nodes));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, "rt1", mock(CapturingCommandDispatcher.class));
         return List.of();
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      grouping = mock(GroupingService.class);
      doAnswer(inv -> {
         CurrentSelectionVSAssembly c = inv.getArgument(1);
         VSAssembly filter = inv.getArgument(2);
         // Read the old parent before the append: getContainer() scans for the first holder.
         VSAssembly oldParent = filter.getContainer();
         List<String> list = new ArrayList<>(Arrays.asList(c.getAssemblies()));
         list.add(filter.getName());
         c.setAssemblies(list.toArray(new String[0]));

         if(oldParent instanceof CurrentSelectionVSAssembly old && old != c) {
            old.removeAssembly(filter.getAbsoluteName());
         }

         return null;
      }).when(grouping).groupComponents(any(), any(), any(), eq(true), any(), any());

      lifecycle = mock(CoreLifecycleService.class);
      ViewsheetService vss = mock(ViewsheetService.class);
      when(vss.getViewsheet(anyString(), any())).thenReturn(rvs);
      VSObjectPropertyService props = mock(VSObjectPropertyService.class);
      ComposerGroupService groups = new ComposerGroupService(
         mock(CoreLifecycleService.class), vss, mock(VSObjectTreeService.class), props,
         mock(VSCompositionService.class), mock(VSLayoutService.class));
      service = new ViewsheetEditService(sessions, mock(ComposerObjectService.class),
                                         mock(ClipboardControllerService.class),
                                         mock(VSObjectPropertyService.class), reader, groups,
                                         grouping, mock(VSRefreshService.class), lifecycle);
   }

   private void add(VSAssembly a, int x, int y) {
      a.setPixelOffset(new Point(x, y));
      a.setPixelSize(new Dimension(200, 100));
      vs.addAssembly(a);
   }

   private void addTo(String container, Integer index, String... filters) throws Exception {
      service.apply("tok", () -> "admin", request("add_to_selection_container", container, index,
                                                  filters), "");
   }

   private void reorder(String container, String... order) throws Exception {
      service.apply("tok", () -> "admin", request("reorder_container_children", container, null,
                                                  order), "");
   }

   private static EditRequest request(String op, String assembly, Integer index,
                                      String... assemblies)
   {
      return new EditRequest(op, assembly, null, null, null, null, null, null, null, null,
                             List.of(assemblies), null, null, null, null, null, null, index);
   }

   private List<String> children(String container) {
      return Arrays.asList(((CurrentSelectionVSAssembly) vs.getAssembly(container))
                              .getAssemblies());
   }

   private IllegalArgumentException refused(ThrowingRunnable r) {
      return assertThrows(IllegalArgumentException.class, r::run);
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   @Test
   void appendKeepsTheContainerAndNeverCreatesANewOne() throws Exception {
      setup();
      int count = vs.getAssemblies().length;
      addTo("Box", null, "ListC");
      assertEquals(List.of("ListA", "ListB", "ListC"), children("Box"));
      assertEquals(count, vs.getAssemblies().length);
      verify(grouping).groupComponents(any(), eq(vs.getAssembly("Box")),
                                       eq(vs.getAssembly("ListC")), eq(true), any(), any());
      verify(lifecycle).refreshVSAssembly(any(), eq((VSAssembly) vs.getAssembly("Box")), any());
   }

   @Test
   void indexZeroPutsTheFilterFirst() throws Exception {
      setup();
      addTo("Box", 0, "ListC");
      assertEquals(List.of("ListC", "ListA", "ListB"), children("Box"));
   }

   @Test
   void middleIndexAndSeveralFiltersLandContiguouslyFromIndex() throws Exception {
      setup();
      addTo("Box", 1, "ListC", "ListE");
      assertEquals(List.of("ListA", "ListC", "ListE", "ListB"), children("Box"));
      assertEquals(List.of(), children("Other"));
   }

   @Test
   void indexEqualToChildCountAppends() throws Exception {
      setup();
      addTo("Box", 2, "ListC");
      assertEquals(List.of("ListA", "ListB", "ListC"), children("Box"));
   }

   @Test
   void indexOutOfRangeIsRefusedNamingTheRange() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("Box", 3, "ListC"));
      assertTrue(e.getMessage().contains("0..2"), e.getMessage());
      refused(() -> addTo("Box", -1, "ListC"));
      assertEquals(List.of("ListA", "ListB"), children("Box"));
   }

   @Test
   void filterAlreadyInTheContainerIsRefusedPointingAtReorder() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("Box", 0, "ListB"));
      assertTrue(e.getMessage().contains("reorder_container_children"), e.getMessage());
      verifyNoInteractions(grouping);
      assertEquals(List.of("ListA", "ListB"), children("Box"));
   }

   @Test
   void aBadLaterNameLeavesEarlierFiltersUnmoved() throws Exception {
      setup();
      refused(() -> addTo("Box", null, "ListC", "OrderTable"));
      verifyNoInteractions(grouping);
      assertEquals(List.of("ListA", "ListB"), children("Box"));
   }

   @Test
   void duplicateAndSelfReferenceAreRefused() throws Exception {
      setup();
      refused(() -> addTo("Box", null, "ListC", "ListC"));
      refused(() -> addTo("Box", null, "Box"));
      verifyNoInteractions(grouping);
   }

   @Test
   void nonFilterIsRefused() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("Box", null, "Text1"));
      assertTrue(e.getMessage().contains("not a selection filter"), e.getMessage());
      refused(() -> addTo("Box", null, "Other"));
      verifyNoInteractions(grouping);
   }

   @Test
   void filterInATabIsRefusedPointingAtMoveFromContainer() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("Box", null, "ListD"));
      assertTrue(e.getMessage().contains("move_from_container"), e.getMessage());
      verifyNoInteractions(grouping);
   }

   @Test
   void filterInAnotherSelectionContainerIsMovedAcross() throws Exception {
      setup();
      addTo("Box", null, "ListE");
      assertEquals(List.of("ListA", "ListB", "ListE"), children("Box"));
      assertEquals(List.of(), children("Other"));
   }

   @Test
   void containerInsideAGroupIsRefused() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("GroupedBox", null, "ListC"));
      assertTrue(e.getMessage().contains("Group1"), e.getMessage());
      verifyNoInteractions(grouping);
   }

   @Test
   void aNonContainerTargetIsRefusedNamingItsContainer() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> addTo("ListA", null, "ListC"));
      assertTrue(e.getMessage().contains("child of Selection Container 'Box'"), e.getMessage());
      refused(() -> addTo("OrderTable", null, "ListC"));
      verifyNoInteractions(grouping);
   }

   @Test
   void containerAliasIsAcceptedAndAConflictIsRefused() throws Exception {
      setup();
      service.apply("tok", () -> "admin",
                    new EditRequest("add_to_selection_container", null, null, null, null, null,
                                    null, null, null, "Box", List.of("ListC"), null, null, null,
                                    null, null, null, null), "");
      assertEquals(List.of("ListA", "ListB", "ListC"), children("Box"));
      refused(() -> service.apply(
         "tok", () -> "admin",
         new EditRequest("add_to_selection_container", "Box", null, null, null, null, null, null,
                         null, "Other", List.of("ListD"), null, null, null, null, null, null,
                         null), ""));
   }

   @Test
   void reorderAppliesTheFullOrder() throws Exception {
      setup();
      reorder("Box", "ListB", "ListA");
      assertEquals(List.of("ListB", "ListA"), children("Box"));
      verify(lifecycle).refreshVSAssembly(any(), eq((VSAssembly) vs.getAssembly("Box")), any());
   }

   @Test
   void reorderOfThreeChildrenIsAPermutation() throws Exception {
      setup();
      addTo("Box", null, "ListC");
      reorder("Box", "ListC", "ListA", "ListB");
      assertEquals(List.of("ListC", "ListA", "ListB"), children("Box"));
      reorder("Box", "ListB", "ListC", "ListA");
      assertEquals(List.of("ListB", "ListC", "ListA"), children("Box"));
   }

   @Test
   void reorderAlreadyInOrderIsANoOpWithoutARefresh() throws Exception {
      setup();
      reorder("Box", "ListA", "ListB");
      assertEquals(List.of("ListA", "ListB"), children("Box"));
      verify(lifecycle, never()).refreshVSAssembly(any(), any(VSAssembly.class), any());
   }

   @Test
   void reorderWithMissingExtraOrDuplicateNamesListsTheCurrentChildren() throws Exception {
      setup();
      IllegalArgumentException e = refused(() -> reorder("Box", "ListA"));
      assertTrue(e.getMessage().contains("[ListA, ListB]"), e.getMessage());
      refused(() -> reorder("Box", "ListA", "ListC"));
      refused(() -> reorder("Box", "ListA", "ListA"));
      refused(() -> reorder("Box", "ListA", "ListB", "ListC"));
      assertEquals(List.of("ListA", "ListB"), children("Box"));
   }

   @Test
   void reorderOnANonContainerIsRefused() throws Exception {
      setup();
      refused(() -> reorder("OrderTable", "ListA"));
      refused(() -> reorder("GroupedBox", "ListA"));
   }
}
