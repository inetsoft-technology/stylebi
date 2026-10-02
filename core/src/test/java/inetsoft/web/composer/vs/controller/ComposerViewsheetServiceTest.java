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

package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.viewsheet.event.OpenPreviewViewsheetEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposerViewsheetServiceTest {
   @AfterEach
   void clearOrgContext() {
      OrganizationContextHolder.clear();
   }

   // Bug #77070: previewViewsheet must restore, not wipe, an org context set by its caller
   // (e.g. the host-org switch done by MessageScopeInterceptor for a global shared dashboard).
   @Test
   void previewViewsheet_restoresCallerOrgContext() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      AssetEntry entry = mock(AssetEntry.class);
      XPrincipal principal = mock(XPrincipal.class);

      when(viewsheetService.getViewsheet("rid", principal)).thenReturn(rvs);
      when(rvs.getEntry()).thenReturn(entry);
      // abort the preview at the first statement inside the org-scoped block
      when(entry.getDescription()).thenThrow(new NullPointerException("abort preview"));

      ComposerViewsheetService service = new ComposerViewsheetService(
         null, null, viewsheetService, null, null, null, null, null, null,
         mock(inetsoft.uql.util.XSessionService.class));

      OrganizationContextHolder.setCurrentOrgId("callerOrg");

      try(MockedStatic<VSUtil> vsUtil = mockStatic(VSUtil.class)) {
         vsUtil.when(() -> VSUtil.isDefaultVSGloballyViewsheet(any(), any())).thenReturn(false);
         service.previewViewsheet("rid", new OpenPreviewViewsheetEvent(), principal,
                                  mock(CommandDispatcher.class), "/");
      }

      assertEquals("callerOrg", OrganizationContextHolder.getCurrentOrgId(),
                   "caller's org context must be restored after preview");
   }
}
