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
 * Bug #79990: a legacy root repository.xml (replet.repository.file, no org prefix) is never
 * deleted after RepletRegistryManager.isolateOrgRegistryFiles() splits it into
 * {org}/repository.xml, so every new manager (JVM / pod start) splits it again and overwrites the
 * org file with the stale root snapshot. A repository folder created after the snapshot is lost
 * from the registry (portal tree) while its asset folder (composer tree) remains.
 */

import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  RepletRegistryLegacyRootConversionTest.RepoConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryLegacyRootConversionTest {
   private static final String ROOT = "repository.xml";

   /** RepletRegistryManager looks up an AnalyticRepository bean when it creates a registry. */
   @org.springframework.context.annotation.Configuration
   static class RepoConfig {
      @org.springframework.context.annotation.Bean
      AnalyticRepository analyticRepository() {
         return org.mockito.Mockito.mock(AnalyticRepository.class);
      }
   }

   @AfterEach
   void removeRootFile() {
      DataSpace.getDataSpace().delete(null, ROOT);
   }

   private static String read(String path) throws Exception {
      try(InputStream in = DataSpace.getDataSpace().getInputStream(null, path)) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         in.transferTo(out);
         return out.toString(StandardCharsets.UTF_8);
      }
   }

   /** Save registry of org with the given folders; returns the open registry. */
   private static RepletRegistry save(String org, String... folders) throws Exception {
      RepletRegistry reg = new RepletRegistry(org);

      for(String f : folders) {
         reg.addFolder(f);
      }

      reg.save();
      return reg;
   }

   /** Make the current content of {org}/repository.xml the legacy root file. */
   private static void snapshotToRoot(String org) throws Exception {
      String xml = read(org + "/" + ROOT);
      DataSpace.getDataSpace().withOutputStream(
         null, ROOT, out -> out.write(xml.getBytes(StandardCharsets.UTF_8)));
   }

   /**
    * Claim 1: a folder added after the root snapshot survives a restart, and a folder deleted
    * after the snapshot does not come back.
    */
   @Test
   void folderCreatedAfterLegacySnapshotSurvivesNewManager() throws Exception {
      String org = "bug79990a";
      RepletRegistry reg = save(org, "Old");
      snapshotToRoot(org);

      // after the "upgrade": new folder F, and Old deleted
      reg.addFolder("F");
      reg.removeFolder("Old");
      reg.save();
      reg.shutdown();
      assertTrue(read(org + "/" + ROOT).contains("F"), "setup: F saved in the org file");

      // restart: a fresh manager, any getRegistry
      RepletRegistryManager manager = new RepletRegistryManager(DataSpace.getDataSpace());
      RepletRegistry loaded = manager.getRegistry(org);

      try {
         assertTrue(loaded.isFolder("F"),
                    "folder F created after the legacy root snapshot was dropped from " +
                    org + "/repository.xml by isolateOrgRegistryFiles; org file now:\n" +
                    read(org + "/" + ROOT));
         assertFalse(loaded.isFolder("Old"),
                     "folder Old, deleted after the snapshot, reappeared from the root file");
      }
      finally {
         loaded.shutdown();
      }
   }

   /** Control: an org that has no entry in the root file is left alone. */
   @Test
   void orgAbsentFromRootFileIsUntouched() throws Exception {
      String in = "bug79990b";
      String other = "bug79990c";
      RepletRegistry r1 = save(in, "Old");
      snapshotToRoot(in);
      RepletRegistry r2 = save(other, "OtherFolder");
      String before = read(other + "/" + ROOT);
      r1.shutdown();
      r2.shutdown();

      RepletRegistryManager manager = new RepletRegistryManager(DataSpace.getDataSpace());
      RepletRegistry loaded = manager.getRegistry(other);

      try {
         assertTrue(loaded.isFolder("OtherFolder"));
         assertEquals(before, read(other + "/" + ROOT), "org absent from root file rewritten");
      }
      finally {
         loaded.shutdown();
      }
   }

   /** Control: a second getRegistry on the same manager does not rewrite the org file. */
   @Test
   void secondGetRegistryOnSameManagerDoesNotRewrite() throws Exception {
      String org = "bug79990d";
      RepletRegistry reg = save(org, "Old");
      snapshotToRoot(org);
      reg.shutdown();

      RepletRegistryManager manager = new RepletRegistryManager(DataSpace.getDataSpace());
      RepletRegistry first = manager.getRegistry(org);
      String afterFirst = read(org + "/" + ROOT);
      // a folder added through the live registry, saved
      first.addFolder("Live");
      first.save();
      String afterSave = read(org + "/" + ROOT);

      RepletRegistry second = manager.getRegistry(org);

      try {
         assertSame(first, second);
         assertEquals(afterSave, read(org + "/" + ROOT), "second getRegistry rewrote the file");
         assertTrue(second.isFolder("Live"));
         assertNotEquals(afterFirst, afterSave);
      }
      finally {
         first.shutdown();
      }
   }

   /**
    * The upgrade path (#70861) must keep working: an org that has no org file yet is converted
    * from the root file, while an org that already has one is left alone in the same pass.
    */
   @Test
   void orgWithoutOrgFileIsConvertedFromRootFile() throws Exception {
      String fresh = "bug79990e";
      String existing = "bug79990f";
      RepletRegistry r1 = save(fresh, "Legacy");
      RepletRegistry r2 = save(existing, "Legacy");
      String freshXml = read(fresh + "/" + ROOT);
      String existingXml = read(existing + "/" + ROOT);
      // root file holds the entries of both orgs, as a pre-split install did
      String root = freshXml.replace("</Registry>",
                                     existingXml.substring(existingXml.indexOf("<Replet")));
      DataSpace.getDataSpace().withOutputStream(
         null, ROOT, out -> out.write(root.getBytes(StandardCharsets.UTF_8)));
      r1.shutdown();
      r2.shutdown();
      // org "fresh" has not been converted yet; org "existing" was and then got a newer folder
      DataSpace.getDataSpace().delete(null, fresh + "/" + ROOT);
      assertFalse(DataSpace.getDataSpace().exists(null, fresh + "/" + ROOT), "setup");
      RepletRegistry e = new RepletRegistry(existing);
      e.addFolder("Newer");
      e.save();
      e.shutdown();

      RepletRegistryManager manager = new RepletRegistryManager(DataSpace.getDataSpace());
      RepletRegistry converted = manager.getRegistry(fresh);
      RepletRegistry kept = manager.getRegistry(existing);

      try {
         assertTrue(DataSpace.getDataSpace().exists(null, fresh + "/" + ROOT),
                    "org file was not created from the root file");
         assertTrue(converted.isFolder("Legacy"), "folder from the root file not converted");
         assertTrue(kept.isFolder("Newer"), "existing org file was overwritten");
      }
      finally {
         converted.shutdown();
         kept.shutdown();
      }
   }
}
