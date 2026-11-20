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
 * Bug #79990, claims 2 and 3: with a real RepletEngine listening to the registry, a folder created
 * through the EM path (RepletRegistryService.addRepositoryFolder = addFolder + save) is mirrored to
 * an asset folder (composer tree). After a legacy-root overwrite on a new manager the registry no
 * longer has it, so the portal listing (RepletEngine.getRepositoryEntries) omits it while the asset
 * folder stays.
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryLegacyRootPortalListingTest {
   @org.springframework.beans.factory.annotation.Autowired
   AnalyticRepository repository;

   @org.springframework.beans.factory.annotation.Autowired
   RepletRegistryManager MANAGER;

   @AfterEach
   void removeRootFile() {
      DataSpace.getDataSpace().delete(null, "repository.xml");
   }

   private static String read(String path) throws Exception {
      try(InputStream in = DataSpace.getDataSpace().getInputStream(null, path)) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         in.transferTo(out);
         return out.toString(StandardCharsets.UTF_8);
      }
   }

   @Test
   void portalListingOmitsFolderWhileAssetFolderRemains() throws Exception {
      RepletEngine engine = repository.unwrap(RepletEngine.class);
      String org = inetsoft.sree.security.Organization.getDefaultOrganizationID();
      RepletRegistry reg = MANAGER.getRegistry(org);
      reg.addFolder("Old");
      reg.save();
      String xml = read(org + "/repository.xml");
      DataSpace.getDataSpace().withOutputStream(
         null, "repository.xml", out -> out.write(xml.getBytes(StandardCharsets.UTF_8)));

      // EM path: RepletRegistryService.addRepositoryFolder = addFolder + (alias/description) + save
      assertTrue(reg.addFolder("F"));
      reg.save();
      AssetEntry assetF = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                         AssetEntry.Type.REPOSITORY_FOLDER, "F", null);
      assertTrue(engine.containsEntry(assetF), "setup: ADD_FOLDER mirrored F to an asset folder");
      XPrincipal admin = new XPrincipal(new IdentityID("admin", org));
      List<String> before = names(engine.getRepositoryEntries(
         "/", admin, (EnumSet<ResourceAction>) null, RepositoryEntry.FOLDER, false));
      assertTrue(before.contains("F"), "setup: portal listing shows F before restart: " + before);

      // restart
      RepletRegistryManager restarted = new RepletRegistryManager(DataSpace.getDataSpace());
      RepletRegistry loaded = restarted.getRegistry(org);
      assertTrue(loaded.isFolder("F"), "registry must keep F after the legacy root conversion (claim 1)");

      assertTrue(engine.containsEntry(assetF), "asset folder (composer tree) must remain");
      // the engine reads its own manager: clear its cache so it reloads from the file, as a new
      // JVM would
      MANAGER.clearOrgCache(org);
      List<String> after = names(engine.getRepositoryEntries(
         "/", admin, (EnumSet<ResourceAction>) null, RepositoryEntry.FOLDER, false));
      assertTrue(after.contains("F"), "portal listing omits F after restart while the asset " +
                 "folder remains; listing=" + after);
   }

   private static List<String> names(RepositoryEntry[] entries) {
      List<String> names = new ArrayList<>();

      for(RepositoryEntry e : entries) {
         names.add(e.getName());
      }

      return names;
   }
}
