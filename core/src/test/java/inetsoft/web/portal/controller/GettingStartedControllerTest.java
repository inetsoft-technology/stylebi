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

import inetsoft.report.LibManagerProvider;
import inetsoft.report.internal.DesignSession;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.web.portal.service.GettingStartedService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Flaky on the build server")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GettingStartedControllerTest {
   @Autowired
   SecurityEngine securityEngine;
   @Autowired
   Cluster cluster;
   GettingStartedController gettingStartedController;
   SRPrincipal admin;
   SRPrincipal user1;

   @BeforeEach
   void before() throws Exception {
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      AssetRepository assetRepository = Mockito.mock(AssetRepository.class);
      DeployManagerService deployManagerService = Mockito.mock(DeployManagerService.class);
      IndexedStorage indexedStorage = Mockito.mock(IndexedStorage.class);
      DesignSession designSession = Mockito.mock(DesignSession.class);
      LibManagerProvider libManagerProvider = Mockito.mock(LibManagerProvider.class);
      DataCycleManager dataCycleManager = Mockito.mock(DataCycleManager.class);
      AnalyticEngine engine = new AnalyticEngine(deployManagerService, designSession, libManagerProvider, dataCycleManager, cluster);
      PortalThemesManager portalThemesManager = Mockito.mock(PortalThemesManager.class);
      GettingStartedService gettingStartedService = new GettingStartedService(assetRepository, engine, securityEngine, indexedStorage, portalThemesManager);
      gettingStartedController = new GettingStartedController(gettingStartedService, securityEngine);

      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()), new IdentityID[] { new IdentityID("Everyone",Organization.getDefaultOrganizationID())}, new String[0], Organization.getDefaultOrganizationID(),
                              Tool.getSecureRandom().nextLong());
      admin.setProperty("showGettingStated", "true");

      user1 = new SRPrincipal(new IdentityID("user1", Organization.getDefaultOrganizationID()), new IdentityID[] {new IdentityID("Everyone", Organization.getDefaultOrganizationID())}, new String[0], Organization.getDefaultOrganizationID(),
                              Tool.getSecureRandom().nextLong());
      user1.setProperty("showGettingStated", "true");
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