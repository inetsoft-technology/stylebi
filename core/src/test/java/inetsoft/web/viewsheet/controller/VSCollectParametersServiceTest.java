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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class VSCollectParametersServiceTest {
   @BeforeEach
   void setUp() {
      viewsheetService = mock(ViewsheetService.class);
      xRepository = mock(XRepository.class);
      service = new VSCollectParametersService(
         mock(CoreLifecycleService.class), viewsheetService, mock(AssetRepository.class),
         mock(XSessionService.class), xRepository);
   }

   // Bug #77329, a viewer must not be able to set the identity variables that VPM filters on
   @Test
   void fillVariableTableDropsContextVariables() throws Exception {
      List<VariableAssemblyModelInfo> variables = List.of(
         variable("_USER_", "Hardware"),
         variable("_ROLES_", "Hardware"),
         variable("_GROUPS_", "Hardware"),
         variable("__principal__", "Hardware"),
         variable("region", "West"));
      VariableTable vtable = new VariableTable();
      Principal user = mock(Principal.class);

      service.fillVariableTable(variables, vtable, user, "plain", Set.of());

      assertFalse(vtable.contains("_USER_"));
      assertFalse(vtable.contains("_ROLES_"));
      assertFalse(vtable.contains("_GROUPS_"));
      assertFalse(vtable.contains("__principal__"));
      assertEquals("West", vtable.get("region"));

      // the forged value must not be cached for the next open either
      verify(viewsheetService, never())
         .setCachedProperty(any(), contains("_USER_"), any());
      verify(viewsheetService).setCachedProperty(user, "plain variable : region", "West");
   }

   // Bug #77429, a db login for a data source the viewsheet did not prompt for is dropped, so
   // the server neither tests the credentials nor caches them
   @Test
   void fillVariableTableDropsUnpromptedDbLogin() throws Exception {
      List<VariableAssemblyModelInfo> variables = List.of(
         variable("_Db_User_Secret", "sa"),
         variable("_Db_Password_Secret", "guess"),
         variable("region", "West"));
      VariableTable vtable = new VariableTable();
      XPrincipal user = mock(XPrincipal.class);

      service.fillVariableTable(variables, vtable, user, "plain", Set.of());

      assertFalse(vtable.contains("_Db_User_Secret"));
      assertFalse(vtable.contains("_Db_Password_Secret"));
      assertEquals("West", vtable.get("region"));
      verify(xRepository, never()).getDataSource(anyString());
      verify(xRepository, never()).testDataSource(any(), any(), any());
      verify(xRepository, never()).connect(any(), anyString(), any());
      verify(user, never()).setProperty(anyString(), anyString());
      verify(viewsheetService, never())
         .setCachedProperty(any(), contains("_Db_"), any());
   }

   // Bug #77429, a prompted name that does not resolve is skipped instead of being tested as null
   @Test
   void fillVariableTableSkipsUnresolvedDataSource() throws Exception {
      List<VariableAssemblyModelInfo> variables = List.of(
         variable("_Db_User_Gone", "sa"),
         variable("_Db_Password_Gone", "pw"));

      service.fillVariableTable(variables, new VariableTable(), mock(XPrincipal.class), "plain",
                                Set.of("_Db_User_Gone", "_Db_Password_Gone"));

      verify(xRepository, never()).testDataSource(any(), any(), any());
      verify(xRepository, never()).connect(any(), anyString(), any());
   }

   private static VariableAssemblyModelInfo variable(String name, String value) {
      VariableAssemblyModelInfo info = new VariableAssemblyModelInfo();
      info.setName(name);
      info.setType("string");
      info.setValue(new Object[] { value });
      return info;
   }

   private ViewsheetService viewsheetService;
   private XRepository xRepository;
   private VSCollectParametersService service;
}
