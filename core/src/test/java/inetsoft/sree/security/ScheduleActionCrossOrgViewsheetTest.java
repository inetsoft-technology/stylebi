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
package inetsoft.sree.security;

/*
 * Bug #77058: the schedule action metadata endpoints (portal and EM hasPrintLayout,
 * viewsheet/highlights, viewsheet/parameters, viewsheet/tableDataAssemblies, EM batch-action
 * parameters) build an AssetEntry from a client-supplied id, whose 5th component is taken as
 * the entry's orgID. Those endpoints now open the viewsheet as the calling principal instead of
 * null; this test pins the guard that change relies on: AbstractAssetEngine.checkAssetPermission
 * rejects a non-site-admin principal reading a viewsheet of another organization.
 *
 * SUtil.isMultiTenant() is structurally false on community/core's test classpath, so it is
 * mocked via Mockito.mockStatic(SUtil.class, CALLS_REAL_METHODS), following
 * PermissionMatrixSpecialTest. StubAssetEngine is the same minimal AbstractAssetEngine used there.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleActionCrossOrgViewsheetTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser("orgAUser", ORG_A, "password")
         .setup();
      orgAUser = builder.principalOf("orgAUser", ORG_A);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void checkAssetPermission_otherOrgViewsheet_rejectsNonSiteAdminCaller() {
      ThreadContext.setContextPrincipal(null);
      AbstractAssetEngine engine = new StubAssetEngine();
      // the id shape a client can send to /api/portal/schedule/task/action/viewsheet/*
      AssetEntry orgBViewsheet = AssetEntry.createAssetEntry("1^128^__NULL__^Secret^" + ORG_B);
      assertEquals(ORG_B, orgBViewsheet.getOrgID());

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertThrows(MessageException.class,
            () -> engine.checkAssetPermission(orgAUser, orgBViewsheet, ResourceAction.READ, true));
      }
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return false;
      }
   }
}
