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
package inetsoft.web.portal.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.web.portal.service.GettingStartedService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("Flaky on the build server")
@SreeHome
class GettingStartedControllerTest {
   static SecurityEngine securityEngine;
   static GettingStartedController gettingStartedController;
   static SRPrincipal admin, user1;

   @BeforeAll
   static void before() throws Exception {
      securityEngine = SecurityEngine.getSecurity();
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      AssetRepository assetRepository = Mockito.mock(AssetRepository.class);
      GettingStartedService gettingStartedService = new GettingStartedService(assetRepository, new AnalyticEngine(), securityEngine);
      gettingStartedController = new GettingStartedController(gettingStartedService);

      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationName()), new IdentityID[] { new IdentityID("Everyone",Organization.getDefaultOrganizationName())}, new String[0], Organization.getDefaultOrganizationID(),
                              Tool.getSecureRandom().nextLong());
      admin.setProperty("showGettingStated", "true");

      user1 = new SRPrincipal(new IdentityID("user1", Organization.getSelfOrganizationName()), new IdentityID[] {new IdentityID("Everyone", Organization.getSelfOrganizationName())}, new String[0], Organization.getSelfOrganizationID(),
                              Tool.getSecureRandom().nextLong());
      user1.setProperty("showGettingStated", "true");
   }

   @AfterAll
   static void cleanup() throws Exception {
      SecurityEngine.clear();
      securityEngine.disableSecurity();
   }

   @Test
   void checkShowGettingStarted() throws IOException {
      SreeEnv.setProperty("getting.started", "false", false);
      SreeEnv.save();
      assertEquals("false", gettingStartedController.showGettingStarted(admin));

      SreeEnv.remove("getting.started", false);
      SreeEnv.save();
      assertEquals("true", gettingStartedController.showGettingStarted(user1));
   }

   @Test
   void checkSingUpUserCreateQueryWSVSPermission() throws Exception {
      assertEquals("4^1^user1" + IdentityID.KEY_DELIMITER + "Self Organization^/^SELF",
                   gettingStartedController.checkCreateQueryPermission(user1).folderId());
      assertEquals("4^1^user1" + IdentityID.KEY_DELIMITER + "Self Organization^/^SELF",
                   gettingStartedController.checkCreateWSPermission(user1).folderId());
      assertEquals("4^4097^user1" + IdentityID.KEY_DELIMITER + "Self Organization^/^SELF",
                   gettingStartedController.getVSDefaultSaveFolder(user1).folderId());
   }
}