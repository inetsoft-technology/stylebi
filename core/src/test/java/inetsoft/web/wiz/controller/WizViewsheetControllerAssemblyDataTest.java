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

package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.model.AssemblyDataRequest;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.service.WizAutoBindingService;
import inetsoft.web.wiz.service.WizGeoService;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link WizViewsheetController#assemblyData} — the read-only endpoint that returns an
 * existing assembly's rendered data.
 *
 * <p>It exists so a follow-up question about a chart built EARLIER in a conversation can be answered
 * from that chart's own rows. The caller holds only what the front end renders with — the runtime id
 * and the assembly name — so those are the only inputs, and deliberately the only ones: accepting a
 * row cap or a filter here would let a caller quietly fetch data the user's chart does not show.
 */
@Tag("core")
class WizViewsheetControllerAssemblyDataTest {
   private WizViewsheetController controller(WizVsService svc) {
      return new WizViewsheetController(
         svc, mock(WizAutoBindingService.class), mock(WizGeoService.class));
   }

   private AssemblyDataRequest request(String runtimeId, String assemblyName) {
      AssemblyDataRequest req = new AssemblyDataRequest();
      req.setRuntimeId(runtimeId);
      req.setAssemblyName(assemblyName);
      return req;
   }

   @Test
   void returnsTheAssemblysRenderedData() throws Exception {
      WizVsService svc = mock(WizVsService.class);
      CreateViewsheetResult result = new CreateViewsheetResult();
      result.setHeaders(List.of("STATE", "SUM(SALES)"));
      result.setRows(List.of(Map.of("STATE", "NJ", "SUM(SALES)", 420)));
      when(svc.fetchAssemblyData(eq("rt-1"), eq("Chart1"), any())).thenReturn(result);

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-1", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.OK, resp.getStatusCode());
      assertSame(result, resp.getBody());
   }

   @Test
   void addressesTheAssemblyByRuntimeIdAndName() throws Exception {
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any())).thenReturn(new CreateViewsheetResult());
      Principal user = mock(Principal.class);

      controller(svc).assemblyData(request("rt-7", "Chart3"), user);

      // The same pair the browser embed renders with, and nothing else — no row cap, no filter.
      verify(svc).fetchAssemblyData("rt-7", "Chart3", user);
   }

   @Test
   void returnsAnEmptyResultWhenTheAssemblyIsGone() throws Exception {
      // A reaped runtime or a deleted assembly: fetchAssemblyData already answers with an empty
      // result rather than throwing, and the caller reads "no rows" as "cannot answer from this
      // chart" — which is the honest outcome, not an error to retry.
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any())).thenReturn(new CreateViewsheetResult());

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-dead", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.OK, resp.getStatusCode());
      assertNull(((CreateViewsheetResult) resp.getBody()).getRows());
   }

   @Test
   void mapsIllegalArgumentTo400() throws Exception {
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any()))
         .thenThrow(new IllegalArgumentException("bad runtime id"));

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("bogus", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
   }

   @Test
   void mapsUnexpectedFailureTo500() throws Exception {
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any())).thenThrow(new IllegalStateException("sandbox gone"));

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-1", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
   }
}
