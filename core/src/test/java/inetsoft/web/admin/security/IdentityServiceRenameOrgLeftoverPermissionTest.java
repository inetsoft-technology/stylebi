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
package inetsoft.web.admin.security;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77940: an organization ID change moves the old ID's permission entries to the new ID and
 * removes the old ones. A removal that fails is only logged, so an entry can stay under the old
 * ID, where an organization created later with that ID would receive it. The change must report
 * it as a user message, and must not report anything when every entry was moved.
 * <p>
 * syncIdentity runs on a partial mock of IdentityService. The provider's copyOrganization makes
 * the two permission migrations AbstractEditableAuthenticationProvider.copyOrganizationInternal
 * makes on a rename. The permissions are kept in a map-backed authorization provider whose
 * removal of one key can be made to fail.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceRenameOrgLeftoverPermissionTest {
   private static final String OLD_ORG = "rnlftold";
   private static final String NEW_ORG = "rnlftnew";
   private static final String VS_KEY = "VIEWSHEET:" + OLD_ORG + ":r/vs";
   private static final String USER_KEY =
      "SECURITY_USER:" + OLD_ORG + ":" + new IdentityID("alice", OLD_ORG).convertToKey();

   private final Map<String, Permission> store = new LinkedHashMap<>();
   private final Set<String> failingRemoves = new HashSet<>();
   private final Set<String> failOnceRemoves = new HashSet<>();
   private IdentityService service;

   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      store.clear();
      failingRemoves.clear();
      failOnceRemoves.clear();
      store.put(VS_KEY, grant());
      store.put(USER_KEY, grant());
      service = newService(mapProvider());
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void renameOrgId_removeFailsOnEveryAttempt_reportsLeftover() throws Exception {
      failingRemoves.add(VS_KEY);

      renameOrg(OLD_ORG, NEW_ORG, "Org1", "Org1");

      assertTrue(store.containsKey(VS_KEY), "the old key was not left");
      assertTrue(store.containsKey("VIEWSHEET:" + NEW_ORG + ":r/vs"), "the key was not moved");
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "the leftover must be reported");
      assertTrue(message.getMessage().contains(OLD_ORG), message.getMessage());
      assertTrue(message.getMessage().contains(NEW_ORG), message.getMessage());
   }

   @Test
   void renameOrgId_identityKeyRemoveFails_reportsLeftover() throws Exception {
      failingRemoves.add(USER_KEY);

      renameOrg(OLD_ORG, NEW_ORG, "Org1", "Org1");

      assertTrue(store.containsKey(USER_KEY), "the old key was not left");
      assertNotNull(Tool.getUserMessage(), "the leftover must be reported");
   }

   // the first pass fails to remove the key and the second pass removes it, so nothing is left
   @Test
   void renameOrgId_removeFailsOnFirstPassOnly_noUserMessage() throws Exception {
      failOnceRemoves.add(USER_KEY);

      renameOrg(OLD_ORG, NEW_ORG, "Org1", "Org1");

      assertTrue(failOnceRemoves.isEmpty(), "the removal did not fail once");
      assertFalse(store.containsKey(USER_KEY), store.toString());
      assertNull(Tool.getUserMessage(), "a key removed by the second pass is not left");
   }

   @Test
   void renameOrgId_noFailure_noUserMessage() throws Exception {
      renameOrg(OLD_ORG, NEW_ORG, "Org1", "Org1");

      assertTrue(store.keySet().stream().noneMatch(k -> k.contains(":" + OLD_ORG + ":")), store.toString());
      assertNull(Tool.getUserMessage());
   }

   @Test
   void renameOrgIdCaseOnly_noFailure_noUserMessage() throws Exception {
      renameOrg(OLD_ORG, OLD_ORG.toUpperCase(), "Org1", "Org1");

      assertTrue(store.keySet().stream().noneMatch(k -> k.contains(":" + OLD_ORG + ":")), store.toString());
      assertNull(Tool.getUserMessage());
   }

   @Test
   void renameOrgNameOnly_keysKept_noUserMessage() throws Exception {
      renameOrg(OLD_ORG, OLD_ORG, "Org1", "Org2");

      assertTrue(store.containsKey(VS_KEY), store.toString());
      assertNull(Tool.getUserMessage(), "the keys of an organization whose ID is kept are not left");
   }

   private void renameOrg(String oldOrgId, String newOrgId, String oldName, String newName)
      throws Exception
   {
      FSOrganization oldOrg = new FSOrganization(new IdentityID(oldName, oldOrgId));
      FSOrganization newOrg = new FSOrganization(new IdentityID(newName, newOrgId));
      EditableAuthenticationProvider eprovider = mock(EditableAuthenticationProvider.class);
      when(eprovider.getOrganization(oldOrgId)).thenReturn(oldOrg);
      // the permission migrations of copyOrganizationInternal with replace == true
      doAnswer(inv -> {
         service.addCopiedIdentityPermission(oldOrg.getIdentityID(), newOrg.getIdentityID(),
                                             newOrgId, Identity.ORGANIZATION, true);
         service.updateIdentityPermissions(Identity.ORGANIZATION, oldOrg.getIdentityID(),
                                           newOrg.getIdentityID(), oldOrgId, newOrgId, true);
         return null;
      }).when(eprovider).copyOrganization(any(), any(), anyString(), anyString(), any(), any(),
                                          any(), any(), any(), eq(true));

      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);

      try {
         method.invoke(service, eprovider, newOrg, oldOrg.getIdentityID());
      }
      catch(InvocationTargetException e) {
         throw (Exception) e.getCause();
      }
   }

   private AuthorizationProvider mapProvider() {
      AuthorizationProvider authz = mock(AuthorizationProvider.class);
      when(authz.getPermissions()).thenAnswer(inv -> {
         List<Tuple4<ResourceType, String, String, Permission>> list = new ArrayList<>();

         for(Map.Entry<String, Permission> e : store.entrySet()) {
            String[] parts = e.getKey().split(":", 3);
            list.add(new Tuple4<>(ResourceType.valueOf(parts[0]), parts[1], parts[2], e.getValue()));
         }

         return list;
      });
      doAnswer(inv -> {
         store.put(inv.getArgument(0) + ":" + inv.getArgument(3) + ":" + inv.getArgument(1),
                   inv.getArgument(2));
         return null;
      }).when(authz).setPermission(any(ResourceType.class), anyString(), any(), any());
      doAnswer(inv -> {
         String key = inv.getArgument(0) + ":" + inv.getArgument(2) + ":" + inv.getArgument(1);

         if(failingRemoves.contains(key) || failOnceRemoves.remove(key)) {
            throw new MessageException("simulated, may not have been saved");
         }

         store.remove(key);
         return null;
      }).when(authz).removePermission(any(ResourceType.class), anyString(), any());
      return authz;
   }

   private static Permission grant() {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), OLD_ORG);
      return p;
   }

   private static IdentityService newService(AuthorizationProvider authz) {
      AuthorizationChain chain = mock(AuthorizationChain.class);
      when(chain.getProviders()).thenReturn(List.of(authz));
      SecurityProvider sp = mock(SecurityProvider.class);
      when(sp.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(sp);
      when(engine.getAuthorizationChain()).thenReturn(Optional.of(chain));

      IdentityService service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "securityProvider", sp);
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "dashboardRegistryManager",
                                   mock(DashboardRegistryManager.class));
      ReflectionTestUtils.setField(service, "logManager", mock(LogManager.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      return service;
   }
}
