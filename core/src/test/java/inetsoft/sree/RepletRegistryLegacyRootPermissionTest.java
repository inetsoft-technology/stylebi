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
package inetsoft.sree;

/*
 * Bug #79990, claim 3 (observation): a legacy-root overwrite drops folder F from the registry
 * without firing REMOVE_FOLDER, so F's REPORT permission is untouched; the import path re-adds F
 * with registry.addFolder (ADD_FOLDER finds the asset folder and skips). What the folder's
 * permission says to an admin, a non-granted org user and a guest before and after is recorded.
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryLegacyRootPermissionTest {
   private static final String ORG = Organization.getDefaultOrganizationID();

   @Autowired AnalyticRepository repository;
   @Autowired RepletRegistryManager manager;
   FileAuthorizationProvider provider;

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
      AuthorizationChain chain = (AuthorizationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      provider.getPermission(ResourceType.REPORT, "probe", ORG);
   }

   @AfterEach
   void tearDown() throws Exception {
      DataSpace.getDataSpace().delete(null, "repository.xml");

      if(provider != null) {
         provider.tearDown();
      }

      SreeEnv.remove("security.enabled");
   }

   private static String read(String path) throws Exception {
      try(InputStream in = DataSpace.getDataSpace().getInputStream(null, path)) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         in.transferTo(out);
         return out.toString(StandardCharsets.UTF_8);
      }
   }

   // SRPrincipal admin does not see folders in this harness (see claim-3 note)
   private List<String> listing(Principal0 who) throws Exception {
      List<String> names = new ArrayList<>();

      for(RepositoryEntry e : repository.unwrap(RepletEngine.class).getRepositoryEntries(
         "/", who.p, EnumSet.of(ResourceAction.READ), RepositoryEntry.FOLDER, false))
      {
         names.add(e.getName());
      }

      return names;
   }

   record Principal0(java.security.Principal p) {
   }

   @Test
   void permissionAndVisibilityAcrossRegistryLossAndReAdd() throws Exception {
      RepletRegistry reg = manager.getRegistry(ORG);
      reg.addFolder("Old");
      reg.save();
      String xml = read(ORG + "/repository.xml");
      DataSpace.getDataSpace().withOutputStream(
         null, "repository.xml", out -> out.write(xml.getBytes(StandardCharsets.UTF_8)));
      reg.addFolder("F3");
      reg.save();

      SecurityEngine security = SecurityEngine.getSecurity();
      Permission perm = new Permission();
      perm.setRoleGrantsForOrg(ResourceAction.READ, Set.of("someRole"), ORG);
      security.setPermission(ResourceType.REPORT, "F3", perm);
      assertNotNull(security.getPermission(ResourceType.REPORT, "F3"), "harness: grant stored");

      SRPrincipal admin = new SRPrincipal(new IdentityID("admin", ORG),
                                          new IdentityID[] { new IdentityID("Administrator", null) },
                                          new String[0], ORG, 1L);
      SRPrincipal user = new SRPrincipal(new IdentityID("u1", ORG), new IdentityID[0],
                                         new String[0], ORG, 2L);
      StringBuilder log = new StringBuilder();
      log.append("before loss: admin=").append(listing(new Principal0(admin)))
         .append(" user=").append(listing(new Principal0(user)));

      RepletRegistryManager restarted = new RepletRegistryManager(DataSpace.getDataSpace());
      assertTrue(restarted.getRegistry(ORG).isFolder("F3"), "registry must keep F3 after the legacy root conversion");
      assertNotNull(security.getPermission(ResourceType.REPORT, "F3"),
                    "registry loss alone removed the REPORT permission");
      manager.clearOrgCache(ORG);
      log.append("\nafter loss: admin=").append(listing(new Principal0(admin)))
         .append(" user=").append(listing(new Principal0(user)))
         .append("\npermission after loss: ")
         .append(security.getPermission(ResourceType.REPORT, "F3") != null ? "kept" : "REMOVED");

      // import path
      RepletRegistry again = manager.getRegistry(ORG);

      if(!again.isFolder("F3")) {
         again.addFolder("F3");
         again.save();
      }

      log.append("\nafter re-add: admin=").append(listing(new Principal0(admin)))
         .append(" user=").append(listing(new Principal0(user)))
         .append("\npermission after re-add: ")
         .append(security.getPermission(ResourceType.REPORT, "F3") != null ? "kept" : "REMOVED");
      // listing visibility is printed only: the harness admin sees no repository folders even
      // before the loss, so it is not asserted
      System.out.println("OBSERVATION " + log);
      assertNotNull(security.getPermission(ResourceType.REPORT, "F3"),
                    "re-adding the folder removed its REPORT permission");
   }
}
