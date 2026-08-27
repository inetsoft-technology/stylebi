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

import inetsoft.sree.security.SecurityException;
import inetsoft.web.wiz.model.WizVisualizationRenameResult;
import inetsoft.web.wiz.request.WizVisualizationRenameRequest;
import inetsoft.web.wiz.service.WizDashboardService;
import inetsoft.web.wiz.service.WizVisualizationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link WizVisualizationController#renameVisualization} maps
 * {@link WizVisualizationService#renameVisualization} outcomes to the correct HTTP status,
 * mirroring {@link WizVisualizationControllerRenderTest}'s shape — in particular that a
 * {@link inetsoft.sree.security.SecurityException} maps to 403, not the generic 500
 * {@code /save} would give it.
 */
@Tag("core")
class WizVisualizationControllerRenameTest {
   @Test
   void returnsResultOnSuccess() throws Exception {
      WizVisualizationService svc = mock(WizVisualizationService.class);
      WizVisualizationRenameResult r = new WizVisualizationRenameResult("1^128^__NULL__^vs1", "New Name");
      when(svc.renameVisualization(any(), any())).thenReturn(r);

      ResponseEntity<?> resp = new WizVisualizationController(svc, mock(WizDashboardService.class))
         .renameVisualization(new WizVisualizationRenameRequest("1^128^__NULL__^vs1", "New Name"), mock(Principal.class));

      assertEquals(HttpStatus.OK, resp.getStatusCode());
      assertSame(r, resp.getBody());
   }

   @Test
   void mapsSecurityExceptionTo403() throws Exception {
      WizVisualizationService svc = mock(WizVisualizationService.class);
      when(svc.renameVisualization(any(), any())).thenThrow(new SecurityException("permission denied"));

      ResponseEntity<?> resp = new WizVisualizationController(svc, mock(WizDashboardService.class))
         .renameVisualization(new WizVisualizationRenameRequest("id", "New Name"), mock(Principal.class));

      assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
   }

   @Test
   void mapsIllegalArgumentTo400() throws Exception {
      WizVisualizationService svc = mock(WizVisualizationService.class);
      when(svc.renameVisualization(any(), any())).thenThrow(new IllegalArgumentException("bad id"));

      ResponseEntity<?> resp = new WizVisualizationController(svc, mock(WizDashboardService.class))
         .renameVisualization(new WizVisualizationRenameRequest("id", "New Name"), mock(Principal.class));

      assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
   }

   @Test
   void mapsGenericExceptionTo500() throws Exception {
      WizVisualizationService svc = mock(WizVisualizationService.class);
      when(svc.renameVisualization(any(), any())).thenThrow(new RuntimeException("boom"));

      ResponseEntity<?> resp = new WizVisualizationController(svc, mock(WizDashboardService.class))
         .renameVisualization(new WizVisualizationRenameRequest("id", "New Name"), mock(Principal.class));

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
   }
}
