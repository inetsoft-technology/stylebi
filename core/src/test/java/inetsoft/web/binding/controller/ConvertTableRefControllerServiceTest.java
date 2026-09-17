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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.event.ConvertTableRefEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.ConvertTableRefService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76674, the same shape #4971 fixed in {@code ModifyCalculateFieldService}.
 *
 * <p>{@code convertTableRef} finishes by repopulating the binding tree. It used to do that
 * through {@code VSBindingTreeController}, a {@code @MessageMapping} controller that re-derives
 * the runtime id from {@code RuntimeViewsheetRef} -- a STOMP-message-scoped bean populated only
 * from a native header on a live browser WebSocket session. Reached without one, that yields
 * null, and the null lands in Ignite's affinity routing. The fix drops the controller hop and
 * calls {@code VSBindingTreeControllerServiceProxy} with the {@code @ClusterProxyKey} id this
 * method was already given.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConvertTableRefControllerServiceTest {
   /**
    * The assertion that actually distinguishes fixed from unfixed code is the second verify.
    * Asserting only that the call did not throw would pass either way, because nothing here
    * rethrows -- and asserting only that getBinding ran would pass against a null id too. What
    * has to hold is that the id reaching the routed call is this call's own id.
    */
   @Test
   void refreshesBindingTreeWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String id = "rt-converttable-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ConvertTableRefEvent event = mock(ConvertTableRefEvent.class);
      when(event.name()).thenReturn("Table1");
      when(event.table()).thenReturn("Orders");
      when(event.refNames()).thenReturn(new String[]{ "QUANTITY" });
      when(event.convertType()).thenReturn(1);
      when(event.sourceChange()).thenReturn(false);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Table1")).thenReturn(mock(TableVSAssembly.class));

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSAssemblyInfoHandler assemblyInfoHandler = mock(VSAssemblyInfoHandler.class);
      // source unchanged, so the method runs through to the binding-tree refresh
      when(assemblyInfoHandler.handleSourceChanged(any(), anyString(), anyString(), any(), any(),
                                                   any())).thenReturn(false);

      VSBindingTreeControllerServiceProxy vsBindingTreeService =
         mock(VSBindingTreeControllerServiceProxy.class);

      ConvertTableRefControllerService service = new ConvertTableRefControllerService(
         vsBindingTreeService, mock(ConvertTableRefService.class), assemblyInfoHandler,
         viewsheetService);

      service.convertTableRef(id, event, principal, dispatcher);

      verify(vsBindingTreeService).getBinding(
         eq(id), any(RefreshBindingTreeEvent.class), eq(principal), eq(dispatcher));
      verify(vsBindingTreeService, never()).getBinding(
         isNull(), any(RefreshBindingTreeEvent.class), any(), any());
   }
}
