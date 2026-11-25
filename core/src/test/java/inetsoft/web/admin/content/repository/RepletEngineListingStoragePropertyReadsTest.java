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
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.*;
import inetsoft.sree.internal.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80317: a folder listing read security.users.multiTenant from storage for every child
 * (about 2N + 2 reads). Inside a PermissionRequestScope it is read once.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepletEngineListingStoragePropertyReadsTest {
   private static final String KEY = "security.users.multiTenant";

   @Test
   @Timeout(value = 120)
   void listingReadsMultiTenantOnceInsideScope() throws Exception {
      String orgId = Organization.getDefaultOrganizationID();
      AssetRepository repo = AssetUtil.getAssetRepository(false);
      String folder = "b80317listing";
      RepletRegistry reg = RepletRegistryManager.getInstance().getRegistry(orgId);
      reg.addFolder(folder);
      reg.save();
      SRPrincipal admin = new SRPrincipal(
         new IdentityID("admin", orgId), new IdentityID[] { new IdentityID("Administrator", null) },
         new String[0], orgId, 1L);

      AtomicInteger checks = new AtomicInteger();
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityEngine security = mock(SecurityEngine.class, withSettings().spiedInstance(real)
         .defaultAnswer(inv -> {
            String n = inv.getMethod().getName();

            if("checkPermission".equals(n)) {
               checks.incrementAndGet();
            }

            return "checkPermission".equals(n) || "isSecurityEnabled".equals(n) ? Boolean.TRUE :
               inv.callRealMethod();
         }));
      AtomicInteger reads = new AtomicInteger();

      try(MockedStatic<SecurityEngine> sec = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
          MockedStatic<LicenseManager> lic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
          MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class, CALLS_REAL_METHODS))
      {
         sec.when(SecurityEngine::getSecurity).thenReturn(security);
         for(int i = 0; i < 40; i++) {
            repo.setSheet(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                         folder + "/zz_sheet" + i, null, orgId),
                          new Viewsheet(), admin, true);
         }


         lic.when(LicenseManager::isEnterprise).thenReturn(true);
         env.when(() -> SreeEnv.getPropertyFromStorage(anyString())).thenAnswer(inv -> {
            if(KEY.equals(inv.getArgument(0, String.class))) {
               reads.incrementAndGet();
            }

            return inv.callRealMethod();
         });
         RepletEngine engine = (RepletEngine) SUtil.getRepletRepository();
         engine.getRepositoryEntries(folder, admin, ResourceAction.READ, RepositoryEntry.ALL, false);

         reads.set(0);
         String without = describe(engine.getRepositoryEntries(
            folder, admin, ResourceAction.READ, RepositoryEntry.ALL, false));
         int readsWithout = reads.get();

         reads.set(0);
         checks.set(0);
         String with;

         try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
            with = describe(engine.getRepositoryEntries(
               folder, admin, ResourceAction.READ, RepositoryEntry.ALL, false));
         }

         int readsWith = reads.get();
         System.out.println("BUG80317 SecurityEngine.checkPermission calls per scoped listing=" +
                            checks.get() + " (each can issue provider KV gets; not asserted)");
         System.out.println("BUG80317 multiTenant storage reads: without scope=" + readsWithout +
                            ", inside scope=" + readsWith);
         assertEquals(without, with);
         assertTrue(readsWithout > 40, "without a scope the reads are per child: " + readsWithout);
         assertTrue(readsWith <= 2, "inside a scope: " + readsWith);
      }
   }

   private static String describe(RepositoryEntry[] entries) {
      List<String> l = new ArrayList<>();

      for(RepositoryEntry e : entries) {
         l.add(e.getType() + ":" + e.getName());
      }

      Collections.sort(l);
      return l.toString();
   }
}
