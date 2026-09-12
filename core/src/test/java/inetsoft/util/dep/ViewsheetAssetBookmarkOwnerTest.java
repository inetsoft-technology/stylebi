/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.util.dep;

/*
 * Regression tests for the orphan-bookmark import bug.
 *
 * Bookmark owners ride inside the viewsheet's JAR entry as an <AllBookmarks> block and are
 * carried verbatim; only the org ID is remapped on import. Importing a package into an org
 * where the named user no longer exists (e.g. it was renamed) used to write a bookmark under
 * that dead identity, producing a row in the bookmark list that no one can ever reach.
 *
 * ViewsheetAsset.bookmarkOwnerExists() is the guard both the import path
 * (ViewsheetAsset.parseContent0) and the conflict scan
 * (DeployManagerService.getBookmarkConflicts) consult, so it is tested directly here.
 *
 * Coverage scope:
 *   [security on, user missing]     owner rejected -> caller skips the block
 *   [security on, user present]     owner accepted -> unchanged behavior
 *   [security on, anonymous/_NULL_] owner accepted (matches DeployService.importAsset)
 *   [virtual provider]              always accepted; getUser() is never consulted
 *   [null / blank owner]            accepted; malformed input is not this guard's job
 *   [no provider]                   accepted; cannot prove absence without a provider
 *   [engine / provider throws]      accepted; must never fail the viewsheet import
 */

import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ViewsheetAssetBookmarkOwnerTest {
   private static final String ORG = "org0";

   private MockedStatic<SecurityEngine> securityEngineMock;
   private SecurityEngine mockEngine;
   private SecurityProvider mockProvider;

   @BeforeEach
   void setUp() {
      securityEngineMock =
         mockStatic(SecurityEngine.class, withSettings().strictness(Strictness.LENIENT));
      mockEngine = mock(SecurityEngine.class, withSettings().lenient());
      mockProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      // stubbed explicitly rather than relying on Mockito's false for the isVirtual() default
      // method: the whole guard hinges on this value
      when(mockProvider.isVirtual()).thenReturn(false);
      securityEngineMock.when(SecurityEngine::getSecurity).thenReturn(mockEngine);
   }

   @AfterEach
   void tearDown() {
      securityEngineMock.close();
   }

   private static IdentityID id(String name) {
      return new IdentityID(name, ORG);
   }

   // ── the reported bug: admin was renamed to test, so admin no longer exists ──

   // [security on, user missing] the whole point of the fix
   @Test
   void bookmarkOwnerExists_userRenamedAway_rejected() {
      when(mockProvider.getUser(id("admin"))).thenReturn(null);

      assertFalse(ViewsheetAsset.bookmarkOwnerExists(id("admin")));
   }

   // [security on, user present] the surviving owner in the same <AllBookmarks> block still
   // imports — the guard must reject per-owner, not per-viewsheet
   @Test
   void bookmarkOwnerExists_existingUser_accepted() {
      when(mockProvider.getUser(id("test"))).thenReturn(new FSUser(id("test")));

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("test")));
   }

   // ── exemptions mirroring DeployService.validateUsers / importAsset ──

   // [security on, anonymous] no-security exports name anonymous as the owner
   @Test
   void bookmarkOwnerExists_anonymous_accepted() {
      when(mockProvider.getUser(any(IdentityID.class))).thenReturn(null);

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id(XPrincipal.ANONYMOUS)));
      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("_NULL_")));
   }

   // ── a virtual provider only knows admin/system/anonymous, so its getUser() cannot prove
   //    any other name is absent ──

   // [virtual provider] accepted without ever consulting getUser(). SecurityEngine returns the
   // virtual provider both when security is off AND when security is on but no real provider
   // was initialized, so gating on isSecurityEnabled() instead would drop every non-admin
   // owner's bookmarks in that second case.
   @Test
   void bookmarkOwnerExists_virtualProvider_acceptedWithoutUserLookup() {
      when(mockProvider.isVirtual()).thenReturn(true);

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("anyone")));
      verify(mockProvider, never()).getUser(any(IdentityID.class));
   }

   // ── defensive cases: never block an import on input this guard cannot judge ──

   // [null / blank owner] malformed blocks are already skipped by parseContent0
   @Test
   void bookmarkOwnerExists_nullOrBlankOwner_accepted() {
      assertTrue(ViewsheetAsset.bookmarkOwnerExists(null));
      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("")));
      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id(null)));
   }

   // [no provider] absence cannot be proven, so do not drop data
   @Test
   void bookmarkOwnerExists_noSecurityProvider_accepted() {
      when(mockEngine.getSecurityProvider()).thenReturn(null);

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("admin")));
   }

   // ── fail open: parseContent0 declares throws Exception, so an escape here would fail the
   //    entire viewsheet import over auxiliary data ──

   // [engine throws] Spring bean lookup fails (minimal context: shell, scheduler, initializer)
   @Test
   void bookmarkOwnerExists_securityEngineThrows_accepted() {
      securityEngineMock.when(SecurityEngine::getSecurity)
         .thenThrow(new RuntimeException("no application context"));

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("admin")));
   }

   // [provider throws] external provider (LDAP/SSO) connection failure
   @Test
   void bookmarkOwnerExists_providerLookupThrows_accepted() {
      when(mockProvider.getUser(id("admin"))).thenThrow(new RuntimeException("LDAP down"));

      assertTrue(ViewsheetAsset.bookmarkOwnerExists(id("admin")));
   }
}
