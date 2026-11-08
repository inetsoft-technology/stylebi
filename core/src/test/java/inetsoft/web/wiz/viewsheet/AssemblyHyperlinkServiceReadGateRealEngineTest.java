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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80639: {@code AssemblyHyperlinkService.set} accepted a link to a viewsheet the caller
 * cannot READ, because {@code containsEntry} does no permission check. Runs against the real
 * asset repository (an {@code AbstractAssetEngine}) with real principals, because a mocked
 * {@code checkAssetPermission} proves only the wiring, not who is actually refused.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssemblyHyperlinkServiceReadGateRealEngineTest {
   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String PATH = "vs80639";

   @BeforeEach
   void setUp() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      // the permission checks resolve a principal's roles through its user in the provider
      addUser(authc, "admin", new IdentityID("Administrator", null));
      addUser(authc, "bob");
      addUser(authc, "alice");
      admin = principal("admin", "Administrator");
      bob = principal("bob", null);
      alice = principal("alice", null);

      repository = AssetUtil.getAssetRepository(false);
      aliceEntry = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, PATH,
                                  new IdentityID("alice", ORG), ORG);

      // permission checks are not under test while the fixture is built
      AssetRepository.IGNORE_PERM.set(true);

      try {
         repository.setSheet(aliceEntry, new Viewsheet(), admin, true);
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
      }

      assertTrue(repository.containsEntry(aliceEntry));
   }

   @AfterEach
   void tearDown() throws Exception {
      AssetRepository.IGNORE_PERM.set(true);

      try {
         if(repository != null && repository.containsEntry(aliceEntry)) {
            repository.removeSheet(aliceEntry, null, true);
         }
      }
      catch(Exception ignore) {
         // best-effort cleanup
      }
      finally {
         AssetRepository.IGNORE_PERM.remove();
         SreeEnv.remove("security.enabled");
      }
   }

   @Test
   void aNonAdminCannotLinkToAnotherUsersPrivateDashboardById() throws Exception {
      String id = aliceEntry.toIdentifier();

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> set(bob, PATH, id));

      assertTrue(e.getMessage().startsWith("No viewsheet with assetLinkId '" + id + "'"),
                 e.getMessage());
      assertEquals(missingIdMessage(bob, id), e.getMessage());
   }

   @Test
   void theOwnerCanLinkToTheirOwnPrivateDashboard() throws Exception {
      HyperlinkDialogService links = set(alice, PATH, aliceEntry.toIdentifier());

      assertEquals(aliceEntry.toIdentifier(), captured(links).getAssetLinkId());
   }

   /** A system administrator holds READ on every user's private entry by product design. */
   @Test
   void aSystemAdministratorPassesTheReadGate() throws Exception {
      HyperlinkDialogService links = set(admin, PATH, aliceEntry.toIdentifier());

      assertEquals(aliceEntry.toIdentifier(), captured(links).getAssetLinkId());
   }

   private HyperlinkDialogService set(Principal user, String path, String id) throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");
      when(rvs.getAssetRepository()).thenReturn(repository);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                         anyBoolean(), anyBoolean(), anyBoolean(),
                                         anyBoolean(), any(Principal.class)))
         .thenReturn(new HyperlinkDialogModel());

      Map<String, Object> link = new LinkedHashMap<>();
      link.put("linkType", "viewsheet");
      link.put("assetLinkPath", path);
      link.put("assetLinkId", id);
      new AssemblyHyperlinkService(sessions, links).set("tok", user, "Chart1", null, link, "");
      return links;
   }

   /** The text the same call gets for an id that does not exist at all. */
   private String missingIdMessage(Principal user, String existingId) {
      String ghostId = existingId.replace(PATH, PATH + "ghost");
      return assertThrows(IllegalArgumentException.class,
                          () -> set(user, PATH + "ghost", ghostId))
         .getMessage().replace(PATH + "ghost", PATH);
   }

   private static HyperlinkDialogModel captured(HyperlinkDialogService links) throws Exception {
      org.mockito.ArgumentCaptor<HyperlinkDialogModel> captor =
         org.mockito.ArgumentCaptor.forClass(HyperlinkDialogModel.class);
      verify(links).setHyperlinkDialogModel(eq("rt1"), anyString(), captor.capture(),
                                            anyString(), any(Principal.class), any());
      return captor.getValue();
   }

   private static void addUser(FileAuthenticationProvider authc, String name, IdentityID... roles) {
      IdentityID id = new IdentityID(name, ORG);

      if(authc.getUser(id) == null) {
         FSUser user = new FSUser(id);
         List<IdentityID> all = new ArrayList<>(Arrays.asList(roles));
         all.add(new IdentityID("Everyone", ORG));
         user.setRoles(all.toArray(new IdentityID[0]));
         authc.addUser(user);
      }
   }

   private static SRPrincipal principal(String name, String role) {
      IdentityID[] roles = role == null ? new IdentityID[0]
         : new IdentityID[] { new IdentityID(role, null) };
      SRPrincipal principal =
         new SRPrincipal(new IdentityID(name, ORG), roles, new String[0], ORG, 1L);
      // stands in for a login: the permission checks refuse a principal that never logged in
      principal.setIgnoreLogin(true);
      return principal;
   }

   private AssetRepository repository;
   private AssetEntry aliceEntry;
   private SRPrincipal admin;
   private SRPrincipal bob;
   private SRPrincipal alice;
}
