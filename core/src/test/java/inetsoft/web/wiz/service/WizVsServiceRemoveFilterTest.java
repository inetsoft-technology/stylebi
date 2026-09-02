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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.model.RemoveFilterRequest;
import inetsoft.web.wiz.model.RemoveFilterResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * WizVsService.removeFilter mirrors {@link WizVsServiceRemoveVisualizationTest}'s
 * removeVisualization coverage almost exactly (see that class's doc comment) — idempotent on a
 * missing/expired runtime or an already-absent assembly, persists only when a
 * viewsheetIdentifier is supplied.
 */
@Tag("core")
class WizVsServiceRemoveFilterTest {
   private static SecurityEngine grantedSecurity() throws Exception {
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return sec;
   }

   private static RemoveFilterRequest request(String runtimeId, String assemblyName, String viewsheetIdentifier) {
      RemoveFilterRequest req = new RemoveFilterRequest();
      req.setRuntimeId(runtimeId);
      req.setAssemblyName(assemblyName);
      req.setViewsheetIdentifier(viewsheetIdentifier);
      return req;
   }

   @Test
   void removesTheNamedControlAndResetsTheSandbox() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      VSAssembly control = mock(VSAssembly.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("SelectionList1")).thenReturn(control);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      WizVsService service = new WizVsService(vsService, engine, grantedSecurity(), null, null, null);
      RemoveFilterResponse resp = service.removeFilter(request("rt-1", "SelectionList1", null), null);

      assertTrue(resp.isRemoved());
      verify(vs).removeAssembly("SelectionList1");
      verify(box).resetDataMap("SelectionList1");
   }

   @Test
   void persistsWhenAViewsheetIdentifierIsSupplied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      VSAssembly control = mock(VSAssembly.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("SelectionList1")).thenReturn(control);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      WizVsService real = new WizVsService(vsService, engine, grantedSecurity(), null, null, null);
      WizVsService service = spy(real);
      doReturn("vi-1").when(service).persistViewsheet(any(), any(), any());

      RemoveFilterResponse resp = service.removeFilter(request("rt-1", "SelectionList1", "vi-1"), null);

      assertTrue(resp.isRemoved());
      verify(service).persistViewsheet(vs, "vi-1", null);
   }

   @Test
   void doesNotPersistWhenNoViewsheetIdentifierIsSupplied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      VSAssembly control = mock(VSAssembly.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("SelectionList1")).thenReturn(control);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      WizVsService real = new WizVsService(vsService, engine, grantedSecurity(), null, null, null);
      WizVsService service = spy(real);

      service.removeFilter(request("rt-1", "SelectionList1", null), null);

      verify(service, never()).persistViewsheet(any(), any(), any());
   }

   @Test
   void idempotentWhenTheControlIsAlreadyAbsent() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("SelectionList1")).thenReturn(null);

      WizVsService service = new WizVsService(vsService, engine, grantedSecurity(), null, null, null);
      RemoveFilterResponse resp = service.removeFilter(request("rt-1", "SelectionList1", null), null);

      assertFalse(resp.isRemoved());
      verify(vs, never()).removeAssembly(anyString());
   }

   @Test
   void idempotentWhenTheRuntimeIsUnavailable() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);

      when(vsService.getViewsheet("rt-1", null)).thenThrow(new RuntimeException("expired"));

      WizVsService service = new WizVsService(vsService, engine, grantedSecurity(), null, null, null);
      RemoveFilterResponse resp = service.removeFilter(request("rt-1", "SelectionList1", null), null);

      assertFalse(resp.isRemoved());
   }

   @Test
   void throwsSecurityExceptionWhenViewsheetAccessDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(false);

      WizVsService service = new WizVsService(vsService, engine, sec, null, null, null);

      assertThrows(SecurityException.class,
                   () -> service.removeFilter(request("rt-1", "SelectionList1", null), null));

      verifyNoInteractions(vsService);
   }
}
