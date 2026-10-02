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
package inetsoft.web.composer.vs.dialog;

/*
 * Bug #77263: HyperlinkDialogService.getHyperlink (reached from setHyperlinkDialogModel and
 * checkVSTableTrap) resolved the bookmark of the client-supplied assetLinkId with
 * VSUtil.getBookmarks and no READ check, so an org A caller could store another org's all-share
 * bookmark name and owner key on the hyperlink. The org B viewsheet and bookmark are stored
 * through the real asset repository; only SecurityEngine.getSecurity is stubbed to a non-virtual
 * provider so VSUtil.getBookmarks enumerates bookmark users, and whose per-resource ACL grants
 * everything, so the cross-org check in AbstractAssetEngine.checkAssetPermission0 is what denies.
 */

import inetsoft.report.Hyperlink;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HyperlinkBookmarkCrossOrgTest {
   private static final String ORG_A = "gapOrgA";
   private static final String ORG_B = "gapOrgB";
   private static final IdentityID ATTACKER = new IdentityID("attacker", ORG_A);
   private static final IdentityID VICTIM = new IdentityID("victim", ORG_B);
   private static final String SECRET_BOOKMARK = "SecretBookmarkOfOrgB";

   private AssetRepository repository;
   private AssetEntry victimEntry;
   private SRPrincipal attacker;
   private SRPrincipal victim;

   @BeforeEach
   void setUp() throws Exception {
      repository = AssetUtil.getAssetRepository(false);
      victimEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                   "GapSecret/Payroll", null, ORG_B);
      Viewsheet vs = new Viewsheet();
      OrganizationContextHolder.setCurrentOrgId(ORG_B);

      try {
         AssetEntry folder = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, "GapSecret", null, ORG_B);

         if(!repository.containsEntry(folder)) {
            repository.addFolder(folder, null);
         }

         repository.setSheet(victimEntry, vs, null, true);

         VSBookmark bookmark = new VSBookmark();
         bookmark.setUser(VICTIM);
         bookmark.addBookmark(SECRET_BOOKMARK, vs, VSBookmarkInfo.ALLSHARE, false, false);
         repository.setVSBookmark(victimEntry, bookmark, new XPrincipal(VICTIM));
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }

      attacker = new SRPrincipal(ATTACKER, new IdentityID[0], new String[0], ORG_A, 1L);
      victim = new SRPrincipal(VICTIM, new IdentityID[0], new String[0], ORG_B, 2L);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
   }

   @Test
   void otherOrgViewsheet_bookmarkOwnerNotResolved() throws Exception {
      Hyperlink link = getHyperlink(attacker, ORG_A);

      assertNull(link.getBookmarkUser(),
         "an org B bookmark (and its owner) must not be resolved for an org A caller, got " +
            link.getBookmarkName() + " / " + link.getBookmarkUser());
      assertNull(link.getBookmarkName());
      // the link itself is still stored; only the bookmark is dropped
      assertEquals(victimEntry.toIdentifier(), link.getLink());
   }

   @Test
   void sameOrgViewsheet_bookmarkStillResolved() throws Exception {
      Hyperlink link = getHyperlink(victim, ORG_B);

      assertEquals(SECRET_BOOKMARK, link.getBookmarkName());
      assertEquals(VICTIM.convertToKey(), link.getBookmarkUser());
   }

   private Hyperlink getHyperlink(SRPrincipal caller, String orgId) throws Exception {
      HyperlinkDialogService service = new HyperlinkDialogService(
         null, null, null, null, null, null, null, repository);
      Method getHyperlink = HyperlinkDialogService.class.getDeclaredMethod(
         "getHyperlink", HyperlinkDialogModel.class, Principal.class);
      getHyperlink.setAccessible(true);

      HyperlinkDialogModel model = new HyperlinkDialogModel();
      model.setLinkType(Hyperlink.VIEWSHEET_LINK);
      model.setAssetLinkId(victimEntry.toIdentifier());
      model.setBookmark(SECRET_BOOKMARK + "(whoever)");

      // VSUtil.getBookmarks() only enumerates bookmark users for a non-virtual provider; the test
      // context's provider is virtual, so stand in a real-looking one (the repository is real)
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.isVirtual()).thenReturn(false);
      when(provider.getOrganization(anyString())).thenReturn(mock(Organization.class));
      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      // the per-resource ACL grants, so only the org check in checkAssetPermission can deny
      when(engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                  any(ResourceAction.class))).thenReturn(true);

      ThreadContext.setContextPrincipal(caller);
      OrganizationContextHolder.setCurrentOrgId(orgId);

      try(MockedStatic<SecurityEngine> st = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS)) {
         st.when(SecurityEngine::getSecurity).thenReturn(engine);
         return (Hyperlink) getHyperlink.invoke(service, model, caller);
      }
   }
}
