/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.content.plugins;

/*
 * Test strategy
 *
 * PluginsController has real in-controller logic in three areas:
 *
 *   installPluginFiles(UploadDriverFileModel, Principal) — calls pluginsService.installPlugins()
 *     with the request's file ID, then self-delegates to getPluginsModel() to return the
 *     refreshed model.
 *
 *   deletePluginFiles(PluginsModel, Principal) — calls pluginsService.uninstallPlugins() with the
 *     supplied model, then self-delegates to getPluginsModel() to return the refreshed model.
 *
 *   scanDrivers(String, Principal) — calls pluginsService.scanDrivers() and wraps the returned
 *     List<String> in a DriverList.
 *
 * getPluginsModel() and createDriverPlugin() are pure delegation and are covered by E2E tests.
 *
 * Coverage scope:
 *   [getPluginsModel delegation]          service.getModel() called; result returned unchanged
 *   [installPluginFiles sequence]         installPlugins() then getModel(); refreshed model returned
 *   [deletePluginFiles sequence]          uninstallPlugins() then getModel(); refreshed model returned
 *   [scanDrivers wrapping]               scanDrivers() result wrapped in DriverList
 */

import inetsoft.web.admin.content.plugins.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class PluginsControllerTest {

   @Mock private PluginsService pluginsService;
   @Mock private PluginsModel pluginsModel;
   @Mock private PluginsModel requestModel;
   @Mock private UploadDriverFileModel uploadRequest;
   @Mock private Principal principal;

   private PluginsController controller;

   @BeforeEach
   void setUp() {
      controller = new PluginsController(pluginsService);
   }

   // -------------------------------------------------------------------------
   // getPluginsModel()
   // -------------------------------------------------------------------------

   // [delegation] service.getModel() called; result returned unchanged
   @Test
   void getPluginsModel_delegatesToService() throws Exception {
      when(pluginsService.getModel(principal)).thenReturn(pluginsModel);

      assertSame(pluginsModel, controller.getPluginsModel(principal));
      verify(pluginsService).getModel(principal);
   }

   // -------------------------------------------------------------------------
   // installPluginFiles()
   // -------------------------------------------------------------------------

   // [sequence] installPlugins() called; then getModel() called; refreshed model returned
   @Test
   void installPluginFiles_installsThenFetchesRefreshedModel() throws Exception {
      when(uploadRequest.files()).thenReturn("upload-id-123");
      when(pluginsService.getModel(principal)).thenReturn(pluginsModel);

      PluginsModel result = controller.installPluginFiles(uploadRequest, principal);

      assertSame(pluginsModel, result);
      verify(pluginsService).installPlugins("upload-id-123", principal);
      verify(pluginsService).getModel(principal);
   }

   // -------------------------------------------------------------------------
   // deletePluginFiles()
   // -------------------------------------------------------------------------

   // [sequence] uninstallPlugins() called with request model; then getModel(); refreshed model returned
   @Test
   void deletePluginFiles_uninstallsThenFetchesRefreshedModel() throws Exception {
      when(pluginsService.getModel(principal)).thenReturn(pluginsModel);

      PluginsModel result = controller.deletePluginFiles(requestModel, principal);

      assertSame(pluginsModel, result);
      verify(pluginsService).uninstallPlugins(requestModel, principal);
      verify(pluginsService).getModel(principal);
   }

   // -------------------------------------------------------------------------
   // scanDrivers()
   // -------------------------------------------------------------------------

   // [wrapping] service result wrapped in DriverList
   @Test
   void scanDrivers_wrapsServiceResultInDriverList() throws Exception {
      List<String> drivers = List.of("com.mysql.Driver", "org.postgresql.Driver");
      when(pluginsService.scanDrivers("upload-id-123", principal)).thenReturn(drivers);

      DriverList result = controller.scanDrivers("upload-id-123", principal);

      assertEquals(drivers, result.drivers());
   }
}
