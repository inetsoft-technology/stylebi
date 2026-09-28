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
package inetsoft.web.viewsheet.controller;

/*
 * Bug #77063: /api/vs/route-data loads the client-identified viewsheet with the READ check
 * (which rejects another org). A viewsheet the caller cannot read gets the all-false defaults,
 * the same as a missing one, so the route resolver still navigates and the open reports the
 * actual error. Only MessageException is swallowed.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.viewsheet.model.ViewsheetRouteDataModel;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OpenViewsheetControllerRouteDataTest {
   private static final String VS_ID = "1^128^__NULL__^Target^orgb_id";

   @Test
   void getRouteData_checksReadPermission() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal principal = mock(Principal.class);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);
      when(repository.getSheet(any(AssetEntry.class), any(), anyBoolean(), any())).thenReturn(vs);

      ViewsheetRouteDataModel model = controller(repository).getRouteData(VS_ID, principal);

      assertTrue(model.scaleToScreen());
      verify(repository).getSheet(argThat(e -> VS_ID.equals(e.toIdentifier())), same(principal),
                                  eq(true), eq(AssetContent.CONTEXT));
   }

   @Test
   void getRouteData_unreadableViewsheet_returnsDefaults() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.getSheet(any(AssetEntry.class), any(), eq(true), any()))
         .thenThrow(new MessageException("Read access denied"));

      ViewsheetRouteDataModel model =
         controller(repository).getRouteData(VS_ID, mock(Principal.class));

      assertFalse(model.scaleToScreen());
      assertFalse(model.fitToWidth());
      assertFalse(model.hasBaseEntry());
   }

   @Test
   void getRouteData_otherFailure_propagates() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.getSheet(any(AssetEntry.class), any(), anyBoolean(), any()))
         .thenThrow(new IllegalStateException("boom"));

      assertThrows(IllegalStateException.class,
         () -> controller(repository).getRouteData(VS_ID, mock(Principal.class)));
   }

   private static OpenViewsheetController controller(AssetRepository repository) {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getAssetRepository()).thenReturn(repository);
      return new OpenViewsheetController(null, null, null, null, null, viewsheetService, null, null);
   }
}
