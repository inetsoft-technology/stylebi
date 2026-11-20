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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.MySQLDatabaseType;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.request.WizDatabaseTestRequest;
import inetsoft.web.wiz.request.WizFolderCreateRequest;
import inetsoft.web.wiz.request.WizFolderRenameRequest;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code WizDatasourceGates} duplicates the private gates of {@code WizDatabaseController} on
 * purpose, so a divergence is the one way the new endpoints could be looser or stricter than the
 * existing ones. These cases drive the same permission table through both and compare the outcome
 * and the 403 text.
 */
@Tag("core")
class WizDatasourceGatesTest {
   @Test
   void theDataSourceGateAgreesWithTheControllersOnEveryCase() throws Exception {
      for(boolean granted : new boolean[]{ true, false }) {
         Fixture f = new Fixture();
         when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE, "db/orders",
                                         ResourceAction.WRITE)).thenReturn(granted);

         String viaGates = outcome(
            () -> f.gates.requireDataSourcePermission("db/orders", ResourceAction.WRITE, f.principal));
         String viaController = outcome(() -> f.controller.testDatabaseConnection(
            new WizDatabaseTestRequest("db/orders", definition()), f.principal));

         assertEquals(viaController, viaGates, "granted=" + granted);
         assertEquals(granted, viaGates.equals("allowed"));
      }
   }

   @Test
   void theFolderGateAgreesWithTheControllersOnEveryCase() throws Exception {
      for(boolean granted : new boolean[]{ true, false }) {
         Fixture f = new Fixture();
         when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE_FOLDER, "sales",
                                         ResourceAction.WRITE)).thenReturn(granted);

         String viaGates = outcome(
            () -> f.gates.requireFolderPermission("sales", ResourceAction.WRITE, f.principal));
         String viaController = outcome(() -> f.controller.renameFolder(
            new WizFolderRenameRequest("sales", "retail"), f.principal));

         assertEquals(viaController, viaGates, "granted=" + granted);
         assertEquals(granted, viaGates.equals("allowed"));
      }
   }

   @Test
   void theCreateGateAgreesWithTheControllersOnTheWholeTable() throws Exception {
      for(String parent : new String[]{ null, "sales" }) {
         for(boolean writeOnParent : new boolean[]{ true, false }) {
            for(boolean createDataSource : new boolean[]{ true, false }) {
               Fixture f = new Fixture();
               when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE_FOLDER,
                                               parent == null ? "/" : parent, ResourceAction.WRITE))
                  .thenReturn(writeOnParent);
               when(f.security.checkPermission(f.principal, ResourceType.CREATE_DATA_SOURCE, "*",
                                               ResourceAction.ACCESS)).thenReturn(createDataSource);

               String viaGates = outcome(() -> f.gates.requireCreatePermission(parent, f.principal));
               String viaController = outcome(() -> f.controller.createFolder(
                  new WizFolderCreateRequest(parent, "x"), f.principal));
               String label = "parent=" + parent + " write=" + writeOnParent +
                  " createDataSource=" + createDataSource;

               assertEquals(viaController, viaGates, label);
            }
         }
      }
   }

   @Test
   void theStandaloneGrantOnlyCountsAtTheRootAndIsReportedAsUserScope() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(f.principal, ResourceType.CREATE_DATA_SOURCE, "*",
                                      ResourceAction.ACCESS)).thenReturn(true);

      assertTrue(f.gates.requireCreatePermission(null, f.principal),
                 "root with only the standalone grant is userScope");
      assertThrows(SecurityException.class, () -> f.gates.requireCreatePermission("sales", f.principal),
                   "the standalone grant does not open a subfolder");

      when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE_FOLDER, "/",
                                      ResourceAction.WRITE)).thenReturn(true);

      assertFalse(f.gates.requireCreatePermission(null, f.principal),
                  "WRITE on the folder wins and is not userScope");
   }

   @FunctionalInterface
   private interface Call {
      void run() throws Exception;
   }

   /** "allowed" when the gate let the call through (anything but a SecurityException). */
   private static String outcome(Call call) {
      try {
         call.run();
      }
      catch(SecurityException ex) {
         return "denied: " + ex.getMessage();
      }
      catch(Exception ex) {
         // Past the gate: the mocked services behind it have no more to say.
      }

      return "allowed";
   }

   private static WizDatabaseDefinition definition() {
      return new WizDatabaseDefinition(
         "orders", null, MySQLDatabaseType.TYPE, new WizNetworkLocation("h", 3306),
         new WizAuthentication(true, "u", null), null, false, -1, 3, null, false, false);
   }

   private static final class Fixture {
      Fixture() throws Exception {
         doReturn(new MySQLDatabaseType()).when(types).getDatabaseType(MySQLDatabaseType.TYPE);
         controller = new WizDatabaseController(
            mock(DataSourceBrowserService.class), mock(DataSourceStatusService.class),
            mock(DatabaseDatasourcesService.class), types, security, mock(Config.class),
            mock(XRepository.class), mock(EndpointCatalogReader.class),
            mock(DatasourcesService.class));
      }

      final DatabaseTypeService types = mock(DatabaseTypeService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final Principal principal = mock(Principal.class);
      final WizDatasourceGates gates = new WizDatasourceGates(security);
      final WizDatabaseController controller;
   }
}
