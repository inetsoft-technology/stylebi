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
package inetsoft.sree.web;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81813: both {@code NamedSessionService.newSession} overloads must log out the other
 * sessions of the same named user that come from a different IP address (Feature #36409).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class NamedSessionServiceReplaceTest {
   @Test
   void oneArgLogsOutOtherIpSession() throws Exception {
      run((service, loggedOut) -> {
         SRPrincipal old = principal("alice", "10.0.0.1", "s1");
         service.newSession(old);
         service.newSession(principal("alice", "10.0.0.2", "s2"));
         assertEquals(List.of(old), loggedOut);
      });
   }

   @Test
   void twoArgLogsOutOtherIpSessionOfSameUser() throws Exception {
      run((service, loggedOut) -> {
         SRPrincipal old = principal("alice", "10.0.0.1", "s1");
         service.newSession(old);
         SRPrincipal fresh = principal("alice", "10.0.0.2", "s2");
         service.newSession(fresh, null);
         assertEquals(List.of(old), loggedOut);
         assertEquals(java.util.Set.of(fresh), service.getActiveSessions());
      });
   }

   @Test
   void twoArgWithReplacedSessionAlsoOtherIp() throws Exception {
      run((service, loggedOut) -> {
         SRPrincipal old = principal("alice", "10.0.0.1", "s1");
         SRPrincipal other = principal("alice", "10.0.0.3", "s3");
         service.newSession(old);
         service.newSession(other);
         SRPrincipal fresh = principal("alice", "10.0.0.2", "s2");
         service.newSession(fresh, "s1");
         assertTrue(loggedOut.contains(old));
         assertTrue(loggedOut.contains(other));
         assertEquals(java.util.Set.of(fresh), service.getActiveSessions());
      });
   }

   @Test
   void twoArgKeepsSameIpSession() throws Exception {
      run((service, loggedOut) -> {
         SRPrincipal old = principal("alice", "10.0.0.1", "s1");
         service.newSession(old);
         SRPrincipal fresh = principal("alice", "10.0.0.1", "s2");
         service.newSession(fresh, null);
         assertTrue(loggedOut.isEmpty());
         assertEquals(2, service.getActiveSessions().size());
      });
   }

   @Test
   void twoArgKeepsOtherUserSession() throws Exception {
      run((service, loggedOut) -> {
         SRPrincipal bob = principal("bob", "10.0.0.1", "s1");
         service.newSession(bob);
         service.newSession(principal("alice", "10.0.0.2", "s2"), null);
         assertTrue(loggedOut.isEmpty());
         assertEquals(2, service.getActiveSessions().size());
      });
   }

   // the license service drops sessions that the security engine does not list as active
   private final List<SRPrincipal> activePrincipals = new ArrayList<>();

   private void run(Scenario scenario) throws Exception {
      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getNamedUsers()).thenReturn(null);
      Profile profile = mock(Profile.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getActivePrincipalList()).thenReturn(activePrincipals);
      List<Principal> loggedOut = new ArrayList<>();
      Supplier<Integer> max = () -> Integer.MAX_VALUE;
      SessionLicenseService.NamedSessionService[] holder =
         new SessionLicenseService.NamedSessionService[1];

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<LicenseManager> license = mockStatic(LicenseManager.class);
          MockedStatic<Profile> profileStatic = mockStatic(Profile.class);
          MockedStatic<SecurityEngine> securityStatic = mockStatic(SecurityEngine.class))
      {
         sutil.when(SUtil::isCluster).thenReturn(false);
         // mimic the logout listener, which releases the session from the license service
         sutil.when(() -> SUtil.logout(any(Principal.class), eq(true))).thenAnswer(inv -> {
            Principal p = inv.getArgument(0);
            loggedOut.add(p);
            holder[0].releaseSession((SRPrincipal) p);
            return null;
         });
         license.when(LicenseManager::getInstance).thenReturn(licenseManager);
         profileStatic.when(Profile::getInstance).thenReturn(profile);
         securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
         holder[0] = new SessionLicenseService.NamedSessionService(max, true);
         scenario.run(holder[0], loggedOut);
      }
   }

   private SRPrincipal principal(String user, String ip, String sid) {
      ClientInfo info = new ClientInfo(new IdentityID(user, "host-org"), ip, sid);
      SRPrincipal principal = new SRPrincipal(info, new IdentityID[0], 1L);
      activePrincipals.add(principal);
      return principal;
   }

   @FunctionalInterface
   private interface Scenario {
      void run(SessionLicenseService.NamedSessionService service, List<Principal> loggedOut)
         throws Exception;
   }
}
