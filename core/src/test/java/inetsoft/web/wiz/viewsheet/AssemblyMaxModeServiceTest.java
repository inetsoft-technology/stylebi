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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.controller.chart.VSChartMaxModeService;
import inetsoft.web.viewsheet.controller.table.VSTableMaxModeService;
import inetsoft.web.viewsheet.service.MaxModeAssemblyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code width}/{@code height} reach this service directly, not only through the plugin's own
 * tool -- a non-positive value must be refused loud here too, rather than silently building a
 * degenerate {@link java.awt.Dimension} that "maximizes" an assembly to zero (or negative) size.
 *
 * <p>A Selection Container's children have no Show Enlarged of their own in the Viewer (only the
 * container does), so enlarging one is refused instead of blanking the Viewer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssemblyMaxModeServiceTest {
   @Test
   void refusesAZeroWidth() {
      AssemblyMaxModeService service = harness();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "TableView1", true, 0, 600, ""));
      assertTrue(ex.getMessage().contains("'width' must be a positive number, got 0"));
      verifyNoInteractions(sessions);
   }

   @Test
   void refusesANegativeHeight() {
      AssemblyMaxModeService service = harness();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "TableView1", true, 800, -1, ""));
      assertTrue(ex.getMessage().contains("'height' must be a positive number, got -1"));
      verifyNoInteractions(sessions);
   }

   /**
    * A null override (the "use the default" case) is not a validation failure -- a real,
    * non-blank assembly name, so the only guard left to pass is the width/height one, and
    * verifying {@code sessions.mutate(...)} is actually reached (not just "no exception") is
    * what proves both null checks were passed rather than short-circuited by an unrelated guard.
    */
   @Test
   void allowsANullWidthAndHeight() throws Exception {
      AssemblyMaxModeService service = harness();

      service.setMaxMode("tok", mock(Principal.class), "TableView1", true, null, null, "");

      verify(sessions).mutate(eq("tok"), any(Principal.class),
                              any(ViewsheetSessionService.Mutation.class));
   }

   @Test
   void refusesToMaximizeASelectionListInsideASelectionContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      assertChildRefused(vs, addChild(vs, new SelectionListVSAssembly(vs, "List1")));
   }

   @Test
   void refusesToMaximizeARangeSliderInsideASelectionContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      assertChildRefused(vs, addChild(vs, new TimeSliderVSAssembly(vs, "Slider1")));
   }

   @Test
   void refusesToMaximizeASelectionTreeInsideASelectionContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      assertChildRefused(vs, addChild(vs, new SelectionTreeVSAssembly(vs, "Tree1")));
   }

   @Test
   void maximizesTheSelectionContainerItself() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new CurrentSelectionVSAssembly(vs, "Container1"));
      AssemblyMaxModeService service = harness(vs);

      service.setMaxMode("tok", mock(Principal.class), "Container1", true, null, null, "");

      verify(maxModeAssemblyService).toggleMaxMode(
         any(), eq("Container1"), eq(new Dimension(1580, 825)), any(), any());
   }

   @Test
   void stillMaximizesAChartInsideATabContainer() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      TabVSAssembly tab = new TabVSAssembly(vs, "Tab1");
      vs.addAssembly(tab);
      tab.setAssemblies(new String[] { "Chart1" });
      AssemblyMaxModeService service = harness(vs);

      service.setMaxMode("tok", mock(Principal.class), "Chart1", true, null, null, "");

      verify(chartMaxModeService).eventHandler(any(), any(), any(), any(), any());
   }

   /** A sheet already left in the invalid state can be repaired by restoring the child. */
   @Test
   void restoresAChildThatAlreadyCarriesAMaxSize() throws Exception {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = addChild(vs, new SelectionListVSAssembly(vs, "List1"));
      list.getMaxModeInfo().setMaxSize(new Dimension(800, 600));
      AssemblyMaxModeService service = harness(vs);

      service.setMaxMode("tok", mock(Principal.class), "List1", false, null, null, "");

      verify(maxModeAssemblyService).toggleMaxMode(any(), eq("List1"), isNull(), any(), any());
   }

   /** Restoring would clear the sheet-wide max flag that a maximized container relies on. */
   @Test
   void restoringAChildThatIsNotEnlargedLeavesTheMaximizedContainerAlone() throws Exception {
      Viewsheet vs = new Viewsheet();
      addChild(vs, new SelectionListVSAssembly(vs, "List1"));
      AssemblyMaxModeService service = harness(vs);

      var result = service.setMaxMode("tok", mock(Principal.class), "List1", false, null, null, "");

      assertEquals(false, result.get("maximized"));
      verifyNoInteractions(maxModeAssemblyService);
   }

   @Test
   void namesTheUserFacingTypeAndListsSelectionContainersForAnUnsupportedType() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new GaugeVSAssembly(vs, "Gauge1"));
      AssemblyMaxModeService service = harness(vs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "Gauge1", true, null, null, ""));
      assertTrue(ex.getMessage().contains("'Gauge1' is a Gauge, which has no"));
      assertTrue(ex.getMessage().contains("selection containers"));
      assertFalse(ex.getMessage().contains("VSAssembly"));
   }

   private void assertChildRefused(Viewsheet vs, VSAssembly child) throws Exception {
      String name = child.getName();
      AssemblyMaxModeService service = harness(vs);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), name, true, null, null, ""));
      assertEquals("'" + name + "' is inside Selection Container 'Container1'; " +
                   "maximize the container instead.", ex.getMessage());
      assertNull(AssemblyMaxModeService.maxSizeOf(child));
      verifyNoInteractions(maxModeAssemblyService);
   }

   /** Adds the child to the sheet and to a (created on first use) Selection Container. */
   private <T extends VSAssembly> T addChild(Viewsheet vs, T child) {
      CurrentSelectionVSAssembly container =
         (CurrentSelectionVSAssembly) vs.getAssembly("Container1");

      if(container == null) {
         container = new CurrentSelectionVSAssembly(vs, "Container1");
         vs.addAssembly(container);
      }

      vs.addAssembly(child);
      String[] old = container.getAssemblies();
      String[] arr = Arrays.copyOf(old == null ? new String[0] : old,
                                   (old == null ? 0 : old.length) + 1);
      arr[arr.length - 1] = child.getName();
      container.setAssemblies(arr);
      return child;
   }

   private AssemblyMaxModeService harness(Viewsheet vs) throws Exception {
      AssemblyMaxModeService service = harness();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(sessions.mutate(any(), any(), any(ViewsheetSessionService.Mutation.class)))
         .thenAnswer(inv -> {
            inv.<ViewsheetSessionService.Mutation>getArgument(2).run(rvs, "rt", null);
            return List.of();
         });
      return service;
   }

   private AssemblyMaxModeService harness() {
      sessions = mock(ViewsheetSessionService.class);
      chartMaxModeService = mock(VSChartMaxModeService.class);
      maxModeAssemblyService = mock(MaxModeAssemblyService.class);
      return new AssemblyMaxModeService(sessions, mock(VSTableMaxModeService.class),
                                        chartMaxModeService, maxModeAssemblyService);
   }

   private ViewsheetSessionService sessions;
   private VSChartMaxModeService chartMaxModeService;
   private MaxModeAssemblyService maxModeAssemblyService;
}
