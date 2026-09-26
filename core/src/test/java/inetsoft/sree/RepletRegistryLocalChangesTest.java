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

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76977: changes other than an added folder, made on two registry copies of one org (two
 * cluster nodes) and saved one after the other, must all be kept. The outcome must not depend on
 * whether the second copy reloads the first one's commit before its own save, so the change
 * events are not ordered here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryLocalChangesTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      orgId = "test76977_" + info.getTestMethod().orElseThrow().getName().toLowerCase();
      RepletRegistry seed = new RepletRegistry(orgId);
      seed.addFolder(A);
      seed.setFolderAlias(A, "seed");
      seed.save();
      seed.shutdown();
      nodeA = new RepletRegistry(orgId);
      nodeB = new RepletRegistry(orgId);
   }

   @AfterEach
   void tearDown() {
      for(RepletRegistry registry : new RepletRegistry[] { nodeA, nodeB }) {
         if(registry != null) {
            registry.shutdown();
         }
      }
   }

   @Test
   void aliasChangeSurvivesAnotherNodesLaterSave() throws Exception {
      nodeA.setFolderAlias(A, "changed");
      nodeB.addFolder(B);
      nodeA.save();
      nodeB.save();

      RepletRegistry stored = read();
      assertEquals("changed", stored.getFolderAlias(A));
      assertTrue(stored.isFolder(B));
      stored.shutdown();
   }

   @Test
   void removedFolderStaysRemovedAfterAnotherNodesLaterSave() throws Exception {
      nodeA.removeFolder(A);
      nodeB.addFolder(B);
      nodeA.save();
      nodeB.save();

      RepletRegistry stored = read();
      Set<String> folders = new TreeSet<>(Arrays.asList(stored.getAllFolders()));
      assertFalse(folders.contains(A), "stored folders: " + folders);
      assertTrue(folders.contains(B), "stored folders: " + folders);
      stored.shutdown();
   }

   @Test
   void renamedFolderKeepsItsNewNameAfterAnotherNodesLaterSave() throws Exception {
      nodeA.changeFolder(A, RENAMED);
      nodeB.addFolder(B);
      nodeA.save();
      nodeB.save();

      RepletRegistry stored = read();
      Set<String> folders = new TreeSet<>(Arrays.asList(stored.getAllFolders()));
      assertFalse(folders.contains(A), "stored folders: " + folders);
      assertTrue(folders.contains(RENAMED), "stored folders: " + folders);
      assertTrue(folders.contains(B), "stored folders: " + folders);
      assertEquals("seed", stored.getFolderAlias(RENAMED));
      stored.shutdown();
   }

   private RepletRegistry read() throws Exception {
      return new RepletRegistry(orgId);
   }

   private static final String PARENT = "cluster-p113";
   private static final String A = PARENT + "/a";
   private static final String B = PARENT + "/b";
   private static final String RENAMED = PARENT + "/renamed";

   private String orgId;
   private RepletRegistry nodeA;
   private RepletRegistry nodeB;
}
