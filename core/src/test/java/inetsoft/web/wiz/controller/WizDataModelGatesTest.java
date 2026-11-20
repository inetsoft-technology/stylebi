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
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The gates deny with the Portal's SecurityException text and ask the same services it asks. */
@Tag("core")
class WizDataModelGatesTest {
   private final Principal bob = () -> "bob";
   private DataSourceService dss;
   private LogicalModelService lms;
   private WizDataModelGates gates;

   @BeforeEach
   void setUp() {
      dss = mock(DataSourceService.class);
      lms = mock(LogicalModelService.class);
      gates = new WizDataModelGates(dss, lms);
   }

   @Test
   void dataSourceDenialNamesTheSourceAndUser() throws Exception {
      when(dss.checkPermission("D", ResourceAction.WRITE, bob)).thenReturn(false);

      SecurityException e = assertThrows(SecurityException.class,
         () -> gates.requireDataSourcePermission("D", ResourceAction.WRITE, bob));
      assertEquals("Unauthorized access to resource \"D\" by user " + bob, e.getMessage());

      when(dss.checkPermission("D", ResourceAction.READ, bob)).thenReturn(true);
      assertDoesNotThrow(() -> gates.requireDataSourcePermission("D", ResourceAction.READ, bob));
   }

   @Test
   void folderGateNamesTheFolderPathOrTheSourceForTheRoot() throws Exception {
      when(dss.checkPermission(anyString(), any(), any(ResourceAction.class), any(Principal.class)))
         .thenReturn(false);

      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireFolderPermission("D", "F", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D/F\""));
      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireFolderPermission("D", "", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D\""));
   }

   @Test
   void modelGateUsesTheStoredFolderOfTheModel() {
      when(lms.checkPermission("D", "F", "M", null, ResourceAction.DELETE, bob)).thenReturn(false);
      when(lms.checkPermission("D", "F", "M", null, ResourceAction.READ, bob)).thenReturn(true);

      assertTrue(assertThrows(SecurityException.class,
         () -> gates.requireModelPermission("D", "F", "M", ResourceAction.DELETE, bob))
         .getMessage().contains("\"D/F/M\""));
      assertDoesNotThrow(
         () -> gates.requireModelPermission("D", "F", "M", ResourceAction.READ, bob) );
   }

   @Test
   void editAccessIsTheServicesOwnCheck() throws Exception {
      doThrow(new SecurityException("x")).when(dss).checkDataModelEditPermission("D", "c", bob);

      assertThrows(SecurityException.class, () -> gates.requireEditAccess("D", "c", bob));
      assertDoesNotThrow(() -> gates.requireEditAccess("D", null, bob));
   }
}
