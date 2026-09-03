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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ModifyCalculateFieldEvent;
import inetsoft.web.binding.event.RefreshBindingTreeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.XRepository;
import inetsoft.web.viewsheet.controller.VSRefreshController;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the NullPointerException a session-less caller (the wiz agent surface's
 * {@code CalcFieldAgentService}) hit live: {@code modifyCalculateField}'s non-wizard branch used
 * to ask {@code VSBindingTreeController} to refresh the binding tree, which re-derives the
 * runtime id from the WebSocket-session-scoped {@code RuntimeViewsheetRef} instead of using the
 * id already passed into this very method -- null for any caller with no live WebSocket session,
 * which then blew up in {@code VSBindingTreeControllerServiceProxy}'s cluster-affinity routing
 * before the real binding-tree read ever ran.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ModifyCalculateFieldServiceTest {
   /**
    * The exact shape CalcFieldAgentService drives live: create a new, non-wizard, non-SQL,
    * detail-level calc field with no target assembly named. Before the fix this threw
    * NullPointerException("Ouch! Argument cannot be null: key") from deep inside
    * VSBindingTreeControllerServiceProxy's cluster routing, because VSBindingTreeController
    * re-derived a null runtime id from RuntimeViewsheetRef rather than using the id already known
    * here. The fix calls vsBindingTreeService (VSBindingTreeControllerServiceProxy) directly with
    * this method's own id -- this test's core assertion is exactly that: the CORRECT, non-null id
    * reaches the binding-tree refresh, not a null one silently swallowed by mocking.
    */
   @Test
   void refreshesBindingTreeWithTheCallsOwnRuntimeIdNotANullSessionScopedOne() throws Exception {
      String id = "rt-calcfield-1";
      Principal principal = mock(Principal.class);
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      ExpressionRef exprRef = new ExpressionRef();
      exprRef.setName("NetTotal");
      exprRef.setExpression("1");
      CalculateRef cref = new CalculateRef(true);
      cref.setDataRef(exprRef);
      cref.setSQL(false);

      CalculateRefModel calcModel = mock(CalculateRefModel.class);
      when(calcModel.createDataRef()).thenReturn(cref);

      ModifyCalculateFieldEvent event = mock(ModifyCalculateFieldEvent.class);
      when(event.calculateRef()).thenReturn(calcModel);
      when(event.tableName()).thenReturn("Orders");
      when(event.refName()).thenReturn("NetTotal");
      when(event.create()).thenReturn(true);
      when(event.remove()).thenReturn(false);
      when(event.name()).thenReturn(null);
      when(event.wizard()).thenReturn(false);
      when(event.wizardOriginalMode()).thenReturn(null);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet(eq(id), eq(principal))).thenReturn(rvs);

      VSBindingTreeControllerServiceProxy vsBindingTreeService =
         mock(VSBindingTreeControllerServiceProxy.class);

      ModifyCalculateFieldService service = new ModifyCalculateFieldService(
         mock(VSBindingService.class), vsBindingTreeService, mock(VSChartHandler.class),
         mock(XRepository.class), mock(VSWizardBindingHandler.class),
         mock(VSRefreshController.class), viewsheetService, mock(VSAssemblyInfoHandler.class),
         mock(DataSourceRegistry.class));

      service.modifyCalculateField(id, event, principal, dispatcher, "");

      verify(vsBindingTreeService).getBinding(
         eq(id), any(RefreshBindingTreeEvent.class), eq(principal), eq(dispatcher));
      verify(vs).addCalcField(eq("Orders"), eq(cref));
   }
}
