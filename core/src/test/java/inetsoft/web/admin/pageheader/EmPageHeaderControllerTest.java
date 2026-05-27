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
package inetsoft.web.admin.pageheader;

/*
 * Test strategy
 *
 * EmPageHeaderController has two endpoints with testable in-controller logic:
 *
 * --- getPageHeaderModel ---
 *   The method conditionally builds an org list for site administrators.
 *   XUtil.getSecurityProvider() — called on the site-admin path — cannot be mocked
 *   statically in this environment, so only the non-site-admin gate is unit-tested here:
 *
 *     [security disabled]  isSecurityEnabled() == false → orgs/orgIDs/currOrgID all null
 *     [non-site admin]     security on, isSiteAdmin() == false → same nulls; XUtil not reached
 *
 * --- setCurrOrg ---
 *     [non-site admin]            security on, isSiteAdmin() == false → returns early;
 *                                 cluster message not sent
 *     [site admin]                principal properties updated; cluster.sendMessage() called
 *     [storage not initialized]   dataSourceRegistry.init() and mvManager.initMVDefMap() called;
 *                                 indexedStorage.setInitialized() called
 */

import inetsoft.mv.MVManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ConnectionProcessor;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EmPageHeaderControllerTest {

   @Mock private SecurityEngine securityEngine;
   @Mock private Cluster cluster;
   @Mock private DataSourceRegistry dataSourceRegistry;
   @Mock private MVManager mvManager;
   @Mock private IndexedStorage indexedStorage;
   @Mock private SecurityProvider securityProvider;
   @Mock private OrganizationManager orgManager;
   @Mock private XPrincipal xPrincipal;

   private EmPageHeaderController controller;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ConnectionProcessor> connectionProcessorStatic;

   @BeforeEach
   void setUp() {
      controller = new EmPageHeaderController(
         securityEngine, cluster, dataSourceRegistry, mvManager, indexedStorage);

      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      connectionProcessorStatic = mockStatic(ConnectionProcessor.class, withSettings().lenient());

      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
      orgManagerStatic.close();
      connectionProcessorStatic.close();
   }

   // -------------------------------------------------------------------------
   // getPageHeaderModel
   // -------------------------------------------------------------------------

   // [security disabled] isSecurityEnabled() == false → all org fields are null
   @Test
   void getPageHeaderModel_securityDisabled_returnsNullOrgs() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      EmPageHeaderModel model = controller.getPageHeaderModel("Primary", false, mock(Principal.class));

      assertNull(model.orgs());
      assertNull(model.orgIDs());
      assertNull(model.currOrgID());
   }

   // [non-site admin] security enabled, isSiteAdmin == false → all org fields are null
   @Test
   void getPageHeaderModel_nonSiteAdmin_returnsNullOrgs() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(orgManager.isSiteAdmin(xPrincipal)).thenReturn(false);

      EmPageHeaderModel model = controller.getPageHeaderModel("Primary", false, xPrincipal);

      assertNull(model.orgs());
      assertNull(model.orgIDs());
      assertNull(model.currOrgID());
   }

   // -------------------------------------------------------------------------
   // setCurrOrg
   // -------------------------------------------------------------------------

   // [non-site admin] returns early without sending cluster message
   @Test
   void setCurrOrg_nonSiteAdmin_doesNotSendClusterMessage() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(orgManager.isSiteAdmin(xPrincipal)).thenReturn(false);

      controller.setCurrOrg(new EmPageHeaderModel(null, null, "org1", "Primary", false), xPrincipal);

      verify(cluster, never()).sendMessage(any());
   }

   // [site admin] principal properties updated; cluster message sent
   @Test
   void setCurrOrg_siteAdmin_updatesPropertiesAndSendsMessage() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(orgManager.isSiteAdmin(xPrincipal)).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.getOrgNameFromID("org1")).thenReturn("Org One");
      when(xPrincipal.getSessionID()).thenReturn("session-123");
      when(indexedStorage.isInitialized("org1")).thenReturn(true);
      ConnectionProcessor connProcessor = mock(ConnectionProcessor.class);
      connectionProcessorStatic.when(ConnectionProcessor::getInstance).thenReturn(connProcessor);

      controller.setCurrOrg(new EmPageHeaderModel(null, null, "org1", "Primary", false), xPrincipal);

      verify(xPrincipal).setProperty("curr_org_id", "org1");
      verify(xPrincipal).setProperty("curr_provider_name", "Primary");
      verify(cluster).sendMessage(any());
   }

   // [storage not initialized] dataSourceRegistry and mvManager init delegated; storage marked initialized
   @Test
   void setCurrOrg_storageNotInitialized_initializesStorage() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(orgManager.isSiteAdmin(xPrincipal)).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.getOrgNameFromID("org1")).thenReturn("Org One");
      when(xPrincipal.getSessionID()).thenReturn("session-123");
      when(indexedStorage.isInitialized("org1")).thenReturn(false);
      ConnectionProcessor connProcessor = mock(ConnectionProcessor.class);
      connectionProcessorStatic.when(ConnectionProcessor::getInstance).thenReturn(connProcessor);

      controller.setCurrOrg(new EmPageHeaderModel(null, null, "org1", "Primary", false), xPrincipal);

      verify(dataSourceRegistry).init();
      verify(mvManager).initMVDefMap();
      verify(indexedStorage).setInitialized("org1");
   }
}
