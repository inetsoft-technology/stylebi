/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.logviewer;

/*
 * Test strategy
 *
 * LogSettingController is a thin delegation layer over LogSettingService.
 * All permission enforcement is handled by the @Secured framework annotation
 * and is not exercised at the unit level.
 *
 * Behavioral guarantees covered:
 *
 * [G1] getLogSettings() returns the LogSettingsModel from the service.
 * [G2] setLogSettings(model, principal) forwards both arguments to the service.
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class LogSettingControllerTest {

   @Mock private LogSettingService logSettingService;
   @Mock private Principal principal;

   private LogSettingController controller;

   @BeforeEach
   void setUp() {
      controller = new LogSettingController(logSettingService);
   }

   // [G1] getLogSettings returns the model from the service
   @Test
   void getLogSettings_delegatesToService() {
      LogSettingsModel model = mock(LogSettingsModel.class);
      when(logSettingService.getConfiguration()).thenReturn(model);

      assertSame(model, controller.getLogSettings());
   }

   // [G2] setLogSettings forwards model and principal to the service
   @Test
   void setLogSettings_delegatesModelAndPrincipalToService() {
      LogSettingsModel model = mock(LogSettingsModel.class);

      controller.setLogSettings(model, principal);

      verify(logSettingService).setConfiguration(model, principal);
   }
}
