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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.wiz.model.GeoApplyRequest;
import inetsoft.web.wiz.model.GeoDetectRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the VIEWSHEET/ACCESS action gates added to {@link WizGeoService#detect} and
 * {@link WizGeoService#apply}: both mutate (and apply persists) a viewsheet, so the composer
 * action right must be checked before any runtime lookup. Denial short-circuits before the
 * viewsheet service is touched.
 */
@Tag("core")
class WizGeoServiceSecurityTest {

   @Test
   void detect_throwsSecurityExceptionAndSkipsRuntimeLookupWhenDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      VSChartHandler chartHandler = mock(VSChartHandler.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizGeoService service = new WizGeoService(vsService, chartHandler, securityEngine);

      assertThrows(SecurityException.class,
         () -> service.detect(new GeoDetectRequest(), principal));

      verifyNoInteractions(vsService);
   }

   @Test
   void apply_throwsSecurityExceptionAndSkipsRuntimeLookupWhenDenied() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      VSChartHandler chartHandler = mock(VSChartHandler.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      Principal principal = mock(Principal.class);

      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.VIEWSHEET), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizGeoService service = new WizGeoService(vsService, chartHandler, securityEngine);

      assertThrows(SecurityException.class,
         () -> service.apply(new GeoApplyRequest(), principal));

      verifyNoInteractions(vsService);
   }
}
