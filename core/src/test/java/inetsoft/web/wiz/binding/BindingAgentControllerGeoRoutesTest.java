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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.wiz.model.GeoDetectResponse;
import inetsoft.web.wiz.model.MapViewResponse;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.service.WizGeoService;
import inetsoft.web.wiz.viewsheet.ViewsheetFormatService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #80334: Clear Geographic and the saved map view are token-routed on the binding agent
 * controller (like chart/geo-shelf), so the paired runtime is resolved from the session token.
 */
@Tag("core")
class BindingAgentControllerGeoRoutesTest {
   private ViewsheetSessionService sessions;
   private WizGeoService geoService;
   private SheetAgentFeature feature;
   private BindingAgentController controller;
   private final Principal user = () -> "admin";

   @BeforeEach
   void setUp() {
      sessions = mock(ViewsheetSessionService.class);
      geoService = mock(WizGeoService.class);
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class), sessions,
         mock(BindableFieldsService.class), mock(BindingReadService.class),
         mock(ChartBindingService.class), mock(ChartAestheticAgentService.class),
         mock(TableBindingService.class), mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(ViewsheetFormatService.class), mock(SheetAgentBroadcastService.class));
   }

   private MockedStatic<ConfigurationContext> springContext() {
      MockedStatic<ConfigurationContext> statics = mockStatic(ConfigurationContext.class);
      ConfigurationContext ctx = mock(ConfigurationContext.class);
      statics.when(ConfigurationContext::getContext).thenReturn(ctx);
      when(ctx.getSpringBean(WizGeoService.class)).thenReturn(geoService);
      return statics;
   }

   private void runMutationsAgainst(RuntimeViewsheet rvs) throws Exception {
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return java.util.List.of();
      }).when(sessions).mutate(eq("tok"), any(Principal.class), any());
   }

   @Test
   void clearGeographicRunsInsideOneMutationOnTheTokenRuntime() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      GeoDetectResponse response = new GeoDetectResponse();
      when(geoService.clear(rvs, "Map1", "State")).thenReturn(response);
      runMutationsAgainst(rvs);

      try(MockedStatic<ConfigurationContext> ignored = springContext()) {
         assertSame(response, controller.clearGeographic(
            "tok", new BindingAgentController.GeoClearRequest("Map1", "State"), user));
      }

      verify(geoService).clear(rvs, "Map1", "State");
   }

   @Test
   void getMapViewReadsWithoutACheckpoint() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      MapViewResponse response = new MapViewResponse();
      when(sessions.resolve("tok", user)).thenReturn(rvs);
      when(geoService.getMapView(rvs, "Map1")).thenReturn(response);

      try(MockedStatic<ConfigurationContext> ignored = springContext()) {
         assertSame(response, controller.getChartMapView(
            "tok", new BindingAgentController.MapViewGetRequest("Map1"), user));
      }

      verify(sessions, never()).mutate(anyString(), any(), any());
   }

   @Test
   void setMapViewPassesEveryFieldThroughInsideOneMutation() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      MapViewResponse response = new MapViewResponse();
      when(geoService.setMapView(rvs, "Map1", 2, 0.25, null, null)).thenReturn(response);
      runMutationsAgainst(rvs);

      try(MockedStatic<ConfigurationContext> ignored = springContext()) {
         assertSame(response, controller.setChartMapView(
            "tok", new BindingAgentController.MapViewSetRequest("Map1", 2, 0.25, null, null),
            user));
      }
   }

   @Test
   void unknownTokenIsRefusedByAllThreeRoutes() throws Exception {
      PairingException expired = new PairingException(
         PairingException.Kind.SESSION_EXPIRED, "Invalid or expired viewsheet session: bad");
      when(sessions.resolve(eq("bad"), any())).thenThrow(expired);
      when(sessions.mutate(eq("bad"), any(), any())).thenThrow(expired);

      try(MockedStatic<ConfigurationContext> ignored = springContext()) {
         assertThrows(PairingException.class, () -> controller.clearGeographic(
            "bad", new BindingAgentController.GeoClearRequest("Map1", "State"), user));
         assertThrows(PairingException.class, () -> controller.getChartMapView(
            "bad", new BindingAgentController.MapViewGetRequest("Map1"), user));
         assertThrows(PairingException.class, () -> controller.setChartMapView(
            "bad", new BindingAgentController.MapViewSetRequest("Map1", 1, null, null, null),
            user));
      }

      verifyNoInteractions(geoService);
   }

   @Test
   void routesAreRefusedWhenTheFeatureIsDisabled() {
      when(feature.isEnabled()).thenReturn(false);

      assertThrows(ResponseStatusException.class, () -> controller.clearGeographic(
         "tok", new BindingAgentController.GeoClearRequest("Map1", "State"), user));
      assertThrows(ResponseStatusException.class, () -> controller.getChartMapView(
         "tok", new BindingAgentController.MapViewGetRequest("Map1"), user));
      assertThrows(ResponseStatusException.class, () -> controller.setChartMapView(
         "tok", new BindingAgentController.MapViewSetRequest("Map1", 1, null, null, null), user));
      verifyNoInteractions(sessions);
   }
}
