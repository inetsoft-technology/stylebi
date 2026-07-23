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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.model.WizDashboardEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the pre-open validation/guard/permission branches that
 * {@link WizDashboardService#composeDashboard} owns directly (name/identifiers validation,
 * the managed-folder guard applied to every identifier, and the permission check performed
 * before any runtime viewsheet is opened).
 *
 * <p>The happy-path compose+save and the all-skipped-visualizations-&gt;400 path require a live
 * {@code ViewsheetService}/{@code AssetRepository} engine (opening a real temporary viewsheet,
 * merging worksheets, finalizing/persisting) and are not unit-testable here — they are verified
 * later in the A4 integration test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizDashboardServiceTest {
   private WizDashboardService createService(ViewsheetService vs, AddVisualizationServiceProxy add,
                                              SecurityEngine sec)
      throws Exception
   {
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      return new WizDashboardService(vs, add, sec, mock(WizDashboardFilterBuilder.class));
   }

   @Test
   void rejectsEmptyIdentifiers() throws Exception {
      WizDashboardService svc = createService(mock(ViewsheetService.class),
         mock(AddVisualizationServiceProxy.class), mock(SecurityEngine.class));
      WizDashboardEvent ev = new WizDashboardEvent();
      ev.setName("D");
      ev.setIdentifiers(List.of());
      assertThrows(IllegalArgumentException.class, () -> svc.composeDashboard(ev, mock(Principal.class)));
   }

   @Test
   void rejectsIdentifierOutsideComponentsFolder() throws Exception {
      WizDashboardService svc = createService(mock(ViewsheetService.class),
         mock(AddVisualizationServiceProxy.class), mock(SecurityEngine.class));
      AssetEntry outside = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "some/unmanaged/vs1", null);
      WizDashboardEvent ev = new WizDashboardEvent();
      ev.setName("D");
      ev.setIdentifiers(List.of(outside.toIdentifier()));
      assertThrows(IllegalArgumentException.class, () -> svc.composeDashboard(ev, mock(Principal.class)));
   }

   @Test
   void throwsSecurityExceptionWhenPermissionDenied() throws Exception {
      ViewsheetService vs = mock(ViewsheetService.class);
      SecurityEngine sec = mock(SecurityEngine.class);
      when(sec.checkPermission(any(), any(), anyString(), any())).thenReturn(false);
      WizDashboardService svc = new WizDashboardService(vs, mock(AddVisualizationServiceProxy.class), sec,
         mock(WizDashboardFilterBuilder.class));
      // identifier UNDER the components folder so the folder guard passes and the permission
      // check is reached:
      String id = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null).toIdentifier();
      WizDashboardEvent ev = new WizDashboardEvent();
      ev.setName("D");
      ev.setIdentifiers(List.of(id));
      assertThrows(inetsoft.sree.security.SecurityException.class,
         () -> svc.composeDashboard(ev, mock(Principal.class)));
   }
}
