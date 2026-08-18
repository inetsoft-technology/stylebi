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

import inetsoft.util.InvalidUserException;
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
 *
 * <p><b>Scope.</b> These stub {@link WizVsService}, so they pin what the controller does with a result
 * or an exception — request mapping, and the status each failure becomes. What the service actually
 * produces (an expired runtime, a missing assembly, the permission gate) belongs to
 * {@code WizVsServiceFetchAssemblyDataTest}, and must be tested there: a stubbed service can only
 * confirm the stub.
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
   void passesAnEmptyResultThroughAsA200() throws Exception {
      // Named for what it can actually establish. It stubs the service, so it says nothing about WHEN
      // an empty result is produced — that belongs to WizVsServiceFetchAssemblyDataTest. The version
      // of this test that claimed it ("returnsAnEmptyResultWhenTheAssemblyIsGone") asserted Mockito
      // rather than StyleBI, which is how an expired runtime went on throwing while the docs said the
      // opposite: a test named after the claim could never disprove the claim.
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any())).thenReturn(new CreateViewsheetResult());

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-dead", "Chart1"), mock(Principal.class));

      // 200 with no rows, not an error status: "nothing to answer from" is itself an answer.
      assertEquals(HttpStatus.OK, resp.getStatusCode());
      assertNull(((CreateViewsheetResult) resp.getBody()).getRows());
   }

   @Test
   void mapsAPermissionDenialTo403() throws Exception {
      // WizControllerErrorHandler maps SecurityException to 403 for this whole package — but an
      // @ControllerAdvice only sees what LEAVES the controller method, and run()'s catch-all took it
      // first. So every permission denial on this controller was answered as a content-free 500 by
      // the very controller the advice exists to cover.
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any()))
         .thenThrow(new inetsoft.sree.security.SecurityException("denied"));

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-1", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
   }

   @Test
   void mapsAnotherUsersRuntimeTo403WithoutNamingItsOwner() throws Exception {
      // getSheet throws this when the runtime id belongs to someone else. Its message is the
      // "common.invalidUser" catalog string, which names the OWNING user — echoing that back would
      // answer, for anyone probing runtime ids, a question they should not get to ask.
      WizVsService svc = mock(WizVsService.class);
      when(svc.fetchAssemblyData(any(), any(), any()))
         .thenThrow(new InvalidUserException("user 'alice' is not 'bob'", mock(Principal.class)));

      ResponseEntity<?> resp =
         controller(svc).assemblyData(request("rt-someone-else", "Chart1"), mock(Principal.class));

      assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
      assertEquals(Map.of("error", "Forbidden"), resp.getBody());
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
