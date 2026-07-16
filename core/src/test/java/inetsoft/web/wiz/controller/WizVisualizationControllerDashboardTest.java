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

import inetsoft.web.wiz.model.WizDashboardEvent;
import inetsoft.web.wiz.model.WizDashboardResult;
import inetsoft.web.wiz.service.WizDashboardService;
import inetsoft.web.wiz.service.WizVisualizationService;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link WizVisualizationController#dashboard} maps
 * {@link WizDashboardService#composeDashboard} outcomes to the correct HTTP status.
 */
@Tag("core")
class WizVisualizationControllerDashboardTest {
   private WizVisualizationController controller(WizDashboardService dash) {
      return new WizVisualizationController(mock(WizVisualizationService.class), dash);
   }

   @Test
   void returnsIdentifierOnSuccess() throws Exception {
      WizDashboardService dash = mock(WizDashboardService.class);
      WizDashboardResult r = new WizDashboardResult();
      r.setSavedViewsheetIdentifier("dash1"); r.setSkipped(List.of());
      when(dash.composeDashboard(any(), any())).thenReturn(r);
      ResponseEntity<?> resp = controller(dash).dashboard(new WizDashboardEvent(), mock(Principal.class));
      assertEquals(HttpStatus.OK, resp.getStatusCode());
      assertSame(r, resp.getBody());
   }

   @Test
   void mapsSecurityTo403() throws Exception {
      WizDashboardService dash = mock(WizDashboardService.class);
      when(dash.composeDashboard(any(), any())).thenThrow(new inetsoft.sree.security.SecurityException("denied"));
      ResponseEntity<?> resp = controller(dash).dashboard(new WizDashboardEvent(), mock(Principal.class));
      assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
   }

   @Test
   void mapsIllegalArgumentTo400() throws Exception {
      WizDashboardService dash = mock(WizDashboardService.class);
      when(dash.composeDashboard(any(), any())).thenThrow(new IllegalArgumentException("empty"));
      ResponseEntity<?> resp = controller(dash).dashboard(new WizDashboardEvent(), mock(Principal.class));
      assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
   }

   @Test
   void mapsGenericExceptionTo500() throws Exception {
      WizDashboardService dash = mock(WizDashboardService.class);
      when(dash.composeDashboard(any(), any())).thenThrow(new RuntimeException("boom"));
      ResponseEntity<?> resp = controller(dash).dashboard(new WizDashboardEvent(), mock(Principal.class));
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
   }
}
