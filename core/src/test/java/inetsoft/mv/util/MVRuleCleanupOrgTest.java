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
package inetsoft.mv.util;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79387: {@link MVRule#cleanup()} must remove the expired MVs of every organization, not
 * only those of the organization of the thread that triggered it. The MV listing, removal and
 * file deletion resolve the organization from the current context, so each organization is
 * cleaned in its own scope.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVRuleCleanupOrgTest {
   private static final String OTHER_ORG = "orgB";

   private MockedStatic<MVManager> managerStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<ClusterUtil> clusterStatic;
   private MVManager manager;
   private String currentOrg;
   // "name@org-seen-by-the-delete-path"
   private final List<String> clusterDeletes = new ArrayList<>();
   private final List<String> removes = new ArrayList<>();

   @BeforeEach
   void setUp() {
      SreeEnv.setProperty("mv.maxAge", "1000");
      currentOrg = OrganizationManager.getInstance().getCurrentOrgID();

      manager = mock(MVManager.class);
      managerStatic = mockStatic(MVManager.class);
      managerStatic.when(MVManager::getManager).thenReturn(manager);

      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getOrganizations()).thenReturn(new String[] { currentOrg, OTHER_ORG });
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

      clusterStatic = mockStatic(ClusterUtil.class);
      clusterStatic.when(() -> ClusterUtil.deleteClusterMV(anyString())).thenAnswer(inv -> {
         clusterDeletes.add(inv.getArgument(0) + "@" +
            OrganizationManager.getInstance().getCurrentOrgID());
         return null;
      });

      when(manager.remove(any(MVDef.class))).thenAnswer(inv -> {
         removes.add(((MVDef) inv.getArgument(0)).getName() + "@" +
            OrganizationManager.getInstance().getCurrentOrgID());
         return true;
      });
   }

   @AfterEach
   void tearDown() {
      clusterStatic.close();
      securityStatic.close();
      managerStatic.close();
      SreeEnv.remove("mv.maxAge");
      OrganizationContextHolder.clear();
   }

   @Test
   void expiredMVsOfEveryOrgAreRemoved() {
      MVDef expiredA = mv("mvA", true);
      MVDef freshA = mv("freshA", false);
      MVDef expiredB = mv("mvB", true);
      MVDef freshB = mv("freshB", false);
      when(manager.list(false)).thenReturn(new MVDef[] { expiredA, freshA });
      when(manager.list(new String[] { OTHER_ORG })).thenReturn(List.of(expiredB, freshB));

      MVRule.cleanup();

      assertEquals(List.of("mvA@" + currentOrg, "mvB@" + OTHER_ORG.toLowerCase()), clusterDeletes);
      assertEquals(List.of("mvA@" + currentOrg, "mvB@" + OTHER_ORG.toLowerCase()), removes);
      assertNull(OrganizationContextHolder.getCurrentOrgId(), "org scope must be restored");
   }

   @Test
   void failureInOneOrgDoesNotStopTheOthers() {
      MVDef expiredA = mv("mvA", true);
      when(manager.list(false)).thenReturn(new MVDef[] { expiredA });
      when(manager.list(new String[] { OTHER_ORG })).thenThrow(new RuntimeException("boom"));

      assertDoesNotThrow(MVRule::cleanup);
      assertEquals(List.of("mvA@" + currentOrg), removes);
   }

   private static MVDef mv(String name, boolean expired) {
      MVDef mv = mock(MVDef.class);
      when(mv.getName()).thenReturn(name);
      when(mv.getLastUpdateTime()).thenReturn(
         expired ? System.currentTimeMillis() - 60_000L : System.currentTimeMillis() + 3_600_000L);
      return mv;
   }
}
