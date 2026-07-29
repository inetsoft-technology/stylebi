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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.viewsheet.event.RefreshVSAssemblyEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.ParameterService;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for two related bugs in how {@link VSRefreshService#refreshVsAssembly}
 * tracks the embed container size of assemblies embedded via wiz's standalone
 * {@code stylebi-embed-elements} package.
 *
 * <p>Bug 1 (type switch): switching a wiz-embedded visualization's type (e.g. table -&gt;
 * crosstab, or back) left the newly-swapped-in assembly without the embed container's pixel
 * size, reproducing the "blank space on the right" bug for the *second* and later type switches
 * even though the very first render of either type was fine. Root cause:
 * {@code WizAutoBindingService#changeType} replaces the viewsheet's primary assembly with a
 * differently-named one on every type switch (same {@code wizRuntimeId}, new assembly name)
 * rather than mutating one assembly in place.
 *
 * <p>Bug 2 (concurrent multi-chart embed): a single wiz conversation can have several chart/table
 * cards that all embed different assemblies on the SAME shared runtime (each card its own
 * {@code assemblyName}, same {@code wizRuntimeId}). Restoring the conversation after a service
 * restart mounts every embed element in the same instant, so their independent WebSocket
 * connections race to refresh the same runtime nearly simultaneously. Both bugs trace back to the
 * same root cause: {@code RuntimeViewsheet} used to track embed info as a single runtime-wide
 * slot ({@code EmbedAssemblyInfo}), so whichever assembly's refresh happened to run last
 * overwrote the *only* record - either erasing a still-relevant sibling assembly's tracking
 * (bug 2) or leaving the tracker permanently pointed at an assembly that no longer exists (bug
 * 1), so {@code CoreLifecycleService#applyEmbedChartSize} silently stopped sizing the affected
 * assembly. The fix tracks embed info per assembly name (see
 * {@code RuntimeViewsheet#getEmbedAssemblyInfo(String)}), so sibling/successor assemblies can
 * never clobber each other.
 */
@Tag("core")
class VSRefreshServiceEmbedAssemblyTest {
   /**
    * Stubs {@code rvs.getEmbedAssemblyInfo(name)}/{@code putEmbedAssemblyInfo(name, info)} with a
    * simple in-memory map keyed by assembly name, mirroring the real
    * {@code ConcurrentHashMap}-backed implementation on {@link RuntimeViewsheet}.
    */
   private static Map<String, EmbedAssemblyInfo> stubEmbedAssemblyInfoStorage(RuntimeViewsheet rvs) {
      Map<String, EmbedAssemblyInfo> stored = new HashMap<>();
      when(rvs.getEmbedAssemblyInfo(any(String.class))).thenAnswer(inv -> stored.get(inv.getArgument(0)));
      org.mockito.Mockito.doAnswer(inv -> {
         stored.put(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(rvs).putEmbedAssemblyInfo(any(), any());
      return stored;
   }

   private RuntimeViewsheet mockRuntimeViewsheet(String runtimeId, ViewsheetService viewsheetService,
                                                  Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      // Neither assembly needs to actually exist for these tests: refreshVsAssembly only needs
      // the embed-info bookkeeping to run, which happens before the (skipped, assembly == null)
      // call to refreshAssemblyAndDependencies.
      when(vs.getAssembly(any(String.class))).thenReturn(null);
      when(viewsheetService.getViewsheet(runtimeId, principal)).thenReturn(rvs);
      return rvs;
   }

   private VSRefreshService createService(ViewsheetService viewsheetService) {
      return new VSRefreshService(
         mock(CoreLifecycleService.class), viewsheetService,
         mock(VSObjectTreeService.class), mock(VSBookmarkService.class),
         mock(ParameterService.class), mock(XSessionService.class));
   }

   @Test
   void reEmbedTracksTheCurrentlyEmbeddedAssemblyAcrossATypeSwitch() throws Exception {
      String runtimeId = "wiz-runtime-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mockRuntimeViewsheet(runtimeId, viewsheetService, principal);
      stubEmbedAssemblyInfoStorage(rvs);

      VSRefreshService service = createService(viewsheetService);
      Dimension containerSize = new Dimension(908, 600);

      // First render: a table assembly is embedded and sized.
      RefreshVSAssemblyEvent tableEvent = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId)
         .assemblyName("vs_table_1")
         .embed(true)
         .assemblySize(containerSize)
         .build();

      service.refreshVsAssembly(runtimeId, tableEvent, dispatcher, "", principal);

      assertEquals("vs_table_1", rvs.getEmbedAssemblyInfo("vs_table_1").getAssemblyName());
      assertEquals(containerSize, rvs.getEmbedAssemblyInfo("vs_table_1").getAssemblySize());

      // User switches type: changeType() replaced the table assembly with a differently-named
      // crosstab assembly on the SAME runtime. The embed component remounts and resends its
      // (embed=true) refresh with the new assembly name and the same container size.
      RefreshVSAssemblyEvent crosstabEvent = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId)
         .assemblyName("vs_crosstab_2")
         .embed(true)
         .assemblySize(containerSize)
         .build();

      service.refreshVsAssembly(runtimeId, crosstabEvent, dispatcher, "", principal);

      assertEquals("vs_crosstab_2", rvs.getEmbedAssemblyInfo("vs_crosstab_2").getAssemblyName(),
         "the newly-swapped-in assembly must get its own tracked embed info, or " +
         "applyEmbedChartSize() never finds a size to apply to it");
      assertEquals(containerSize, rvs.getEmbedAssemblyInfo("vs_crosstab_2").getAssemblySize());
   }

   @Test
   void concurrentlyEmbeddedSiblingAssembliesOnTheSameRuntimeDoNotClobberEachOther() throws Exception {
      String runtimeId = "wiz-runtime-2";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mockRuntimeViewsheet(runtimeId, viewsheetService, principal);
      stubEmbedAssemblyInfoStorage(rvs);

      VSRefreshService service = createService(viewsheetService);

      Dimension sizeA = new Dimension(540, 300);
      Dimension sizeB = new Dimension(540, 260);

      // Two different chart/table cards from the same wiz conversation share this ONE runtime
      // (same wizRuntimeId, different assemblyName). Restoring the conversation after a service
      // restart mounts both embed elements in the same instant, so their independent WebSocket
      // connections race to refresh the SAME shared runtime nearly simultaneously - B's refresh
      // lands in between A's own bookkeeping steps.
      RefreshVSAssemblyEvent eventA = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId).assemblyName("vs_chart_A").embed(true).assemblySize(sizeA).build();
      RefreshVSAssemblyEvent eventB = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId).assemblyName("vs_table_B").embed(true).assemblySize(sizeB).build();

      service.refreshVsAssembly(runtimeId, eventA, dispatcher, "", principal);
      service.refreshVsAssembly(runtimeId, eventB, dispatcher, "", principal);

      assertEquals("vs_chart_A", rvs.getEmbedAssemblyInfo("vs_chart_A").getAssemblyName(),
         "assembly A's own tracked entry must survive assembly B's later, unrelated refresh - " +
         "a single runtime-wide slot would have overwritten A's name/size with B's");
      assertEquals(sizeA, rvs.getEmbedAssemblyInfo("vs_chart_A").getAssemblySize(),
         "assembly A must keep its own container size, not fall back to B's or to null");
      assertEquals("vs_table_B", rvs.getEmbedAssemblyInfo("vs_table_B").getAssemblyName());
      assertEquals(sizeB, rvs.getEmbedAssemblyInfo("vs_table_B").getAssemblySize());
   }
}
