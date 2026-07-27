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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the bug where switching a wiz-embedded visualization's type (e.g.
 * table -&gt; crosstab, or back) left the newly-swapped-in assembly without the embed
 * container's pixel size, reproducing the "blank space on the right" bug for the *second* and
 * later type switches even though the very first render of either type was fine.
 *
 * <p>Root cause: {@code WizAutoBindingService#changeType} replaces the viewsheet's primary
 * assembly with a differently-named one on every type switch (same {@code wizRuntimeId}, new
 * assembly name) rather than mutating one assembly in place. The embed component always resends
 * an {@code embed=true} {@link RefreshVSAssemblyEvent} with its (new) assembly name after
 * remounting, but {@link VSRefreshService#refreshVsAssembly} only ever wrote
 * {@link EmbedAssemblyInfo#setAssemblyName} the *first* time an {@code EmbedAssemblyInfo} was
 * created for a runtime — every later switch kept comparing against the original, now-removed
 * assembly's name in {@code CoreLifecycleService#applyEmbedChartSize}, so the size was silently
 * never applied to the new assembly.
 */
@Tag("core")
class VSRefreshServiceEmbedAssemblyTest {
   private VSRefreshService createService() {
      return new VSRefreshService(
         mock(CoreLifecycleService.class), mock(ViewsheetService.class),
         mock(VSObjectTreeService.class), mock(VSBookmarkService.class),
         mock(ParameterService.class), mock(XSessionService.class));
   }

   @Test
   void reEmbedTracksTheCurrentlyEmbeddedAssemblyAcrossATypeSwitch() throws Exception {
      String runtimeId = "wiz-runtime-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      // Neither assembly needs to actually exist for this test: refreshVsAssembly only needs the
      // embed-info bookkeeping to run, which happens before the (skipped, assembly == null) call
      // to refreshAssemblyAndDependencies.
      when(vs.getAssembly(any(String.class))).thenReturn(null);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(runtimeId, principal)).thenReturn(rvs);

      VSRefreshService service = new VSRefreshService(
         mock(CoreLifecycleService.class), viewsheetService,
         mock(VSObjectTreeService.class), mock(VSBookmarkService.class),
         mock(ParameterService.class), mock(XSessionService.class));

      Dimension containerSize = new Dimension(908, 600);

      // First render: a table assembly is embedded and sized.
      RefreshVSAssemblyEvent tableEvent = RefreshVSAssemblyEvent.builder()
         .vsRuntimeId(runtimeId)
         .assemblyName("vs_table_1")
         .embed(true)
         .assemblySize(containerSize)
         .build();

      // The real rvs.getEmbedAssemblyInfo()/setEmbedAssemblyInfo() round-trip through a plain
      // field on the (here mocked) RuntimeViewsheet - stub it with a simple in-memory holder so
      // the second call sees what the first call stored, exactly as the real class behaves.
      final EmbedAssemblyInfo[] stored = new EmbedAssemblyInfo[1];
      when(rvs.getEmbedAssemblyInfo()).thenAnswer(inv -> stored[0]);
      org.mockito.Mockito.doAnswer(inv -> {
         stored[0] = inv.getArgument(0);
         return null;
      }).when(rvs).setEmbedAssemblyInfo(any());

      service.refreshVsAssembly(runtimeId, tableEvent, dispatcher, "", principal);

      assertEquals("vs_table_1", rvs.getEmbedAssemblyInfo().getAssemblyName());
      assertEquals(containerSize, rvs.getEmbedAssemblyInfo().getAssemblySize());

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

      assertEquals("vs_crosstab_2", rvs.getEmbedAssemblyInfo().getAssemblyName(),
         "the embed target must follow the currently-embedded assembly across a type switch, " +
         "or applyEmbedChartSize() keeps comparing against the original (now-gone) assembly " +
         "name forever and silently stops sizing the new one");
      assertEquals(containerSize, rvs.getEmbedAssemblyInfo().getAssemblySize());
   }
}
