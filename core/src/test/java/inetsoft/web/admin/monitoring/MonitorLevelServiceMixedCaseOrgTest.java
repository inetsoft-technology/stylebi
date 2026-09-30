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
package inetsoft.web.admin.monitoring;

import inetsoft.sree.security.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77355: MonitorLevelService.getOrgUsers() passes the no-arg
 * OrganizationManager.getCurrentOrgID(), which lower-cases the id, to getOrgUsers0(), which
 * compares it case-sensitively against Organization.getOrganizationID(). For an org whose id
 * has upper-case letters ("OrgMixed") no organization matches and the user list is empty, so
 * UserService.getServerFailedModel() (EM > Monitoring > Users > failed logins) shows nothing.
 */
@Tag("core")
class MonitorLevelServiceMixedCaseOrgTest {
   private MockedStatic<SecurityEngine> engineStatic;
   private SecurityProvider provider;

   @BeforeEach
   void setUp() {
      SecurityEngine engine = mock(SecurityEngine.class);
      provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      engineStatic = mockStatic(SecurityEngine.class);
      engineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);
   }

   @AfterEach
   void tearDown() {
      engineStatic.close();
      ThreadContext.setContextPrincipal(null);
   }

   private List<IdentityID> orgUsersFor(String orgId) {
      Organization org = mock(Organization.class);
      when(org.getOrganizationID()).thenReturn(orgId);
      when(org.getId()).thenReturn(orgId);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { orgId });
      when(provider.getOrganization(orgId)).thenReturn(org);

      IdentityID userId = new IdentityID("u1", orgId);
      User user = mock(User.class);
      when(user.getOrganizationID()).thenReturn(orgId);
      when(user.getIdentityID()).thenReturn(userId);
      when(provider.getUsers()).thenReturn(new IdentityID[] { userId });
      when(provider.getUser(userId)).thenReturn(user);

      // SRPrincipal's constructor needs a Spring context; the service only reads these
      SRPrincipal admin = mock(SRPrincipal.class);
      when(admin.getCurrentOrgId()).thenReturn(orgId);
      when(admin.getIdentityID()).thenReturn(new IdentityID("admin", orgId));
      when(admin.getProperty("__internal__")).thenReturn("true"); // no SSO entry appended
      ThreadContext.setContextPrincipal(admin);

      MonitorLevelService service =
         new MonitorLevelService(new String[0], new String[0], new String[0]) { };
      return service.getOrgUsers();
   }

   @Test
   void getOrgUsers_mixedCaseOrgId_returnsOrgUsers() {
      List<IdentityID> users = orgUsersFor("OrgMixed");

      assertEquals(List.of(new IdentityID("u1", "OrgMixed")), users,
                   "users of the current org 'OrgMixed' must be listed");
   }

   // control: the same fixture with an all-lower-case org id works
   @Test
   void getOrgUsers_lowerCaseOrgId_returnsOrgUsers() {
      List<IdentityID> users = orgUsersFor("orgmixed");

      assertEquals(List.of(new IdentityID("u1", "orgmixed")), users);
   }
}
