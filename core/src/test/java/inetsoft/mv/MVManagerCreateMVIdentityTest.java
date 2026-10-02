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
package inetsoft.mv;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins the identity that {@link MVManager} passes to {@link VSMVAnalyzer} when it builds an
 * on-demand MV (Bug #77342). Principal names are in key form ({@code name~;~org}), so guest
 * and virtual-admin sessions are scoped to their own identity (and VPM) like any other user;
 * only a missing principal produces a {@code null} (unscoped) identity.
 */
@Tag("core")
class MVManagerCreateMVIdentityTest {
   // XPrincipal's constructor asks the (Spring-managed) session service for a session ID
   private MockedStatic<XSessionService> sessionServiceStatic;

   @BeforeEach
   void mockSessionService() {
      sessionServiceStatic = mockStatic(XSessionService.class);
      sessionServiceStatic.when(XSessionService::getService)
         .thenReturn(mock(XSessionService.class));
   }

   @AfterEach
   void closeSessionService() {
      sessionServiceStatic.close();
   }

   @Test
   void nullUserAnalyzesWithoutIdentity() throws Exception {
      assertNull(identityPassedToAnalyzer(null));
   }

   @Test
   void guestIsScopedToItsOwnIdentity() throws Exception {
      IdentityID guest = new IdentityID(XPrincipal.ANONYMOUS, "host-org");
      assertUserIdentity(guest, identityPassedToAnalyzer(new SRPrincipal(guest)));
   }

   @Test
   void guestInAnotherOrgIsScopedToItsOwnIdentity() throws Exception {
      IdentityID guest = new IdentityID(XPrincipal.ANONYMOUS, "orgA");
      XPrincipal user = new DestinationUserNameProviderPrincipal(
         guest, new IdentityID[0], new String[0], "orgA", 0L);
      assertUserIdentity(guest, identityPassedToAnalyzer(user));
   }

   @Test
   void virtualAdminIsScopedToItsOwnIdentity() throws Exception {
      IdentityID admin = new IdentityID("admin", "host-org");
      assertUserIdentity(admin, identityPassedToAnalyzer(new SRPrincipal(admin)));
   }

   @Test
   void namedUserIsScopedToItsOwnIdentity() throws Exception {
      IdentityID alice = new IdentityID("alice", "orgA");
      assertUserIdentity(alice, identityPassedToAnalyzer(new XPrincipal(alice)));
   }

   @Test
   void globalOrgUserIsScopedToItsOwnIdentity() throws Exception {
      XPrincipal user = new XPrincipal(new IdentityID("bob", null));
      Identity id = identityPassedToAnalyzer(user);
      assertUserIdentity(IdentityID.getIdentityIDFromKey(user.getName()), id);
   }

   private static void assertUserIdentity(IdentityID expected, Identity actual) {
      assertNotNull(actual);
      assertEquals(Identity.USER, actual.getType());
      assertEquals(expected, actual.getIdentityID());
   }

   /**
    * Runs {@code createMV} until the analyzer is constructed and returns the identity it was
    * given. The security provider reports itself as virtual so that any name-based admin
    * special case would be observable.
    */
   private static Identity identityPassedToAnalyzer(XPrincipal user) throws Exception {
      // skip the constructor and field initializers (storage, cluster); createMV only needs
      // the analyzer before the point where this test stops it
      MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.toIdentifier()).thenReturn("1^128^__NULL__^vs1^host-org");
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getViewsheetInfo()).thenReturn(mock(ViewsheetInfo.class));

      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.isVirtual()).thenReturn(true);

      RuntimeException stop = new RuntimeException("stop after analyzer construction");
      Identity[] captured = new Identity[1];
      boolean[] constructed = new boolean[1];

      try(MockedStatic<SecurityEngine> engineStatic = mockStatic(SecurityEngine.class);
          MockedConstruction<VSMVAnalyzer> ignored = mockConstruction(
             VSMVAnalyzer.class, (analyzer, context) -> {
                constructed[0] = true;
                captured[0] = (Identity) context.arguments().get(2);
                when(analyzer.analyze()).thenThrow(stop);
             }))
      {
         engineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);
         Method createMV = MVManager.class.getDeclaredMethod(
            "createMV", AssetEntry.class, Viewsheet.class, String.class, XPrincipal.class,
            ViewsheetSandbox.class);
         createMV.setAccessible(true);

         InvocationTargetException ex = assertThrows(
            InvocationTargetException.class,
            () -> createMV.invoke(manager, entry, vs, "t1", user, mock(ViewsheetSandbox.class)));
         assertSame(stop, ex.getCause());
      }

      assertTrue(constructed[0], "VSMVAnalyzer was not constructed");
      return captured[0];
   }
}
