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
package inetsoft.sree.security;

/*
 * Scenarios 9a/9b/9c (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.6 Replet Registry". RepletRegistry is the backing store for the Portal
 * repository tree's folder structure ({orgId}/repository.xml, matrix appendix row A1) plus each
 * user's "My Dashboard" personal folder tree (portal/{orgId}/{user}/..., row A2) -- i.e. this is
 * literally "the folders under Repository", both the org-shared tree and each user's private one.
 *
 * IdentityService.copyRepletRegistry()/updateRepletRegistry() are both public, so they are called
 * directly (no reflection needed), same style as OrgLifecycleDataSpaceIntegrationTest for 8b-8d --
 * only RepletRegistryManager (real, backed by the real DataSpace bean) and a mocked SecurityEngine
 * (only getOrgUsers() is touched by copyRepletRegistry()) are wired into IdentityService; everything
 * else stays null/Optional.empty() because these two methods don't reach any other dependency.
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                   OrgLifecycleRepletRegistryIntegrationTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleRepletRegistryIntegrationTest {

   // RepletRegistryManager's cache miss path (RepletRegistryManager.java:473) unconditionally
   // resolves AnalyticRepository.getInstance() -> ConfigurationContext.getSpringBean() the first
   // time any org's registry is loaded; BaseTestConfiguration does not register one, so a mock
   // stand-in is supplied purely to satisfy that lookup (its return value is only used via an
   // `instanceof PropertyChangeListener` check, which a plain mock fails harmlessly).
   @Configuration
   static class TestConfig {
      @Bean
      public AnalyticRepository analyticRepository() {
         return mock(AnalyticRepository.class);
      }
   }

   @Autowired
   private DataSpace dataSpace;

   private RepletRegistryManager repletRegistryManager;

   // ── scenario 9a: copyRepletRegistry() -- folders + folderContextMap + per-user registry copied, source untouched ──

   @Test
   void copy_copyRepletRegistry_foldersContextAndUserRegistryCopied_sourceUntouched() throws Exception {
      repletRegistryManager = new RepletRegistryManager(dataSpace);
      String fromOrgId = "replet9a_from";
      String toOrgId = "replet9a_to";
      IdentityID alice = new IdentityID("alice", fromOrgId);

      RepletRegistry fromRegistry = repletRegistryManager.getRegistry(fromOrgId);
      fromRegistry.addFolder("Reports", false);
      fromRegistry.addFolder("Reports/Q1", false);
      fromRegistry.setFolderAlias("Reports", "季度报表");
      fromRegistry.save();

      // seed alice's personal "My Dashboard" tree so copyUser() has something to copy.
      dataSpace.withOutputStream("portal/" + fromOrgId + "/alice", "marker.txt",
                                 out -> out.write(bytes("alice-personal-data")));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getOrgUsers(fromOrgId)).thenReturn(new IdentityID[] { alice });

      IdentityService identityService = newIdentityService(securityEngine);
      identityService.copyRepletRegistry(fromOrgId, toOrgId);

      RepletRegistry toRegistry = repletRegistryManager.getRegistry(toOrgId);
      assertTrue(containsFolder(toRegistry, "Reports"),
                 "new org must receive the copied folder");
      assertTrue(containsFolder(toRegistry, "Reports/Q1"),
                 "new org must receive the copied sub-folder");
      assertEquals("季度报表", toRegistry.getFolderAlias("Reports"),
                   "folder context map (alias/description) must be copied alongside the folder itself");

      assertTrue(dataSpace.exists("portal/" + toOrgId, "alice"),
                 "new org must receive a copy of the source user's personal registry tree");
      assertTrue(dataSpace.exists("portal/" + toOrgId + "/alice", "marker.txt"),
                 "copied personal tree must retain its file content location");

      // copy must not touch the source.
      assertTrue(containsFolder(fromRegistry, "Reports"),
                 "copy must not remove the folder from the source org");
      assertTrue(dataSpace.exists("portal/" + fromOrgId, "alice"),
                 "copy must not remove the source user's personal registry tree");
   }

   // ── scenario 9b: rename -- copyRepletRegistry() (9a's copy logic) + updateRepletRegistry(fromOrgId, null) to clear source ──

   @Test
   void rename_copyThenUpdateRepletRegistry_newOrgGetsCopy_sourceRegistryCleared() throws Exception {
      repletRegistryManager = new RepletRegistryManager(dataSpace);
      String fromOrgId = "replet9b_from";
      String toOrgId = "replet9b_to";

      RepletRegistry fromRegistry = repletRegistryManager.getRegistry(fromOrgId);
      fromRegistry.addFolder("Sales", false);
      fromRegistry.save();

      IdentityService identityService = newIdentityService(mock(SecurityEngine.class));

      // mirrors the real rename order in AbstractEditableAuthenticationProvider.copyOrganizationInternal():
      // copyRepletRegistry() runs first (unconditionally), then updateRepletRegistry(fromOrgId, null)
      // clears the source once replace==true.
      identityService.copyRepletRegistry(fromOrgId, toOrgId);
      identityService.updateRepletRegistry(fromOrgId, null);

      RepletRegistry toRegistry = repletRegistryManager.getRegistry(toOrgId);
      assertTrue(containsFolder(toRegistry, "Sales"),
                 "new org must have received the folder via copyRepletRegistry()");

      assertFalse(containsFolder(fromRegistry, "Sales"),
                  "source org's in-memory registry must have the folder removed by updateRepletRegistry()");
   }

   // Side discovery while building the 9b/9c fixtures: updateRepletRegistry()'s folder removal
   // (RepletRegistry.removeFolder(folder, true, false, false)) is called with saveBeforeEvent=false,
   // and neither removeFolder() nor updateRepletRegistry() itself ever calls RepletRegistry.save()
   // afterward. So the removal only mutates the in-memory folder map -- it is never flushed to the
   // physical {orgId}/repository.xml blob. In the real production delete/rename flows this is masked
   // because the *file itself* is independently relocated/deleted a step earlier by the DataSpace-level
   // mechanism (removeOrgScopedDataSpaceElements()/copyDataSpace(replace=true) -- matrix rows A1/A2,
   // both matched by getOrgScopedPaths() rule 2/5), so there is nothing left on disk to leave behind
   // as an orphan in practice. But updateRepletRegistry() is not self-sufficient on its own: if the
   // registry happens to still be disk-backed when it runs (e.g. cache not yet cleared, or the
   // DataSpace-level path relocation is ever skipped/reordered), the folder removal silently does not
   // survive a cache eviction + reload. Pinned here as current behavior, not asserted to be correct.
   @Test
   void delete_updateRepletRegistry_removalIsInMemoryOnly_notPersistedToDisk() throws Exception {
      repletRegistryManager = new RepletRegistryManager(dataSpace);
      String orgId = "replet9c_delete";

      RepletRegistry registry = repletRegistryManager.getRegistry(orgId);
      registry.addFolder("Marketing", false);
      registry.save();
      assertTrue(dataSpace.exists(orgId, "repository.xml"),
                 "precondition: folder must actually be persisted to the backing repository.xml file");

      IdentityService identityService = newIdentityService(mock(SecurityEngine.class));
      identityService.updateRepletRegistry(orgId, null);

      assertFalse(containsFolder(registry, "Marketing"),
                  "in-memory folder map must reflect the removal immediately");

      // Force a reload from disk by evicting the cache -- the real delete path does exactly this via
      // repletRegistryManager.clearOrgCache(orgID) (IdentityService.java:634), right after
      // updateRepletRegistry() (:625).
      repletRegistryManager.clearOrgCache(orgId);
      RepletRegistry reloaded = repletRegistryManager.getRegistry(orgId);

      assertTrue(containsFolder(reloaded, "Marketing"),
                 "documents a real gap: updateRepletRegistry()'s folder removal is never saved to "
                 + "disk, so once the in-memory instance is evicted from cache, reloading from the "
                 + "still-unmodified repository.xml brings the folder right back -- updateRepletRegistry() "
                 + "is not self-sufficient for cleanup and only appears correct in production because "
                 + "the DataSpace-level path deletion/rename (matrix row A1) already removed/relocated "
                 + "the physical file a step earlier in the real call chain");
   }

   // ── fixture helpers ──

   private static byte[] bytes(String s) {
      return s.getBytes(StandardCharsets.UTF_8);
   }

   private static boolean containsFolder(RepletRegistry registry, String folder) {
      for(String f : registry.getAllFolders()) {
         if(f.equals(folder)) {
            return true;
         }
      }

      return false;
   }

   private IdentityService newIdentityService(SecurityEngine securityEngine) {
      // Positional constructor -- see IdentityService.java:76-103 for the full 29-parameter list.
      // Position 1 (securityEngine) is mocked (only getOrgUsers() is touched, by copyRepletRegistry()),
      // position 24 (dataSpace) and position 28 (repletRegistryManager) are real; everything else is
      // null/Optional.empty() because neither method under test here touches any other dependency.
      return new IdentityService(
         securityEngine,                                                                   // 1
         null, null, null, null, null, null, null, null, null, null, null, null,            // 2-13
         null,                                                                              // 14
         Optional.empty(),                                                                  // 15
         null, null, null, null, null, null, null, null,                                    // 16-23
         dataSpace,                                                                          // 24
         null, null, null,                                                                  // 25-27
         repletRegistryManager,                                                              // 28
         Optional.empty());                                                                 // 29
   }
}
