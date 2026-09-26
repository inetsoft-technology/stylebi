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
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76977: changes other than an added folder, made on two registry copies of one org (two
 * cluster nodes) and saved one after the other, must all be kept.
 *
 * <p>The remove and rename cases do not order the change events: their outcome must hold whether
 * the second copy reloads the first one's commit before its own save (init merge) or not (the
 * fresh read under the lock in save). The other cases order the events with
 * {@link #blockEvents()} and {@link #awaitRepositoryEvent()}, so that each one drives a single
 * path.</p>
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
      space = DataSpace.getDataSpace();
      space.addChangeListener(null, orgId + "/repository.xml", repositoryWatch);
      RepletRegistry seed = new RepletRegistry(orgId);
      seed.addFolder(A);
      seed.setFolderAlias(A, "seed");
      seed.save();
      seed.shutdown();
      // no late setup event may reach the two node registries
      awaitRepositoryEvent();
      nodeA = new RepletRegistry(orgId);
      nodeB = new HookedRegistry(orgId);
   }

   @AfterEach
   void tearDown() {
      releaseEvents();
      space.removeChangeListener(null, orgId + "/repository.xml", repositoryWatch);

      for(RepletRegistry registry : new RepletRegistry[] { nodeA, nodeB }) {
         if(registry != null) {
            registry.shutdown();
         }
      }
   }

   /**
    * A's alias change must survive B's later save from a copy that has not seen it. Events are
    * parked, so B can only learn about it through the fresh read under the lock.
    */
   @Test
   void aliasChangeSurvivesAnotherNodesLaterSave() throws Exception {
      blockEvents();
      nodeA.setFolderAlias(A, "changed");
      nodeB.addFolder(B);
      nodeA.save();
      nodeB.save();

      RepletRegistry stored = read();
      assertEquals("changed", stored.getFolderAlias(A));
      assertTrue(stored.isFolder(B));
      stored.shutdown();
   }

   /**
    * B's unsaved alias change must survive B's reload of A's commit, which is dispatched before B
    * saves (the RepositoryTreeController edit branch: setFolderAlias, then save).
    */
   @Test
   void localAliasSurvivesRemoteCommitBeforeSave() throws Exception {
      nodeA.addFolder(X);
      nodeB.setFolderAlias(A, "mine");
      nodeA.save();
      awaitRepositoryEvent();
      // node B has now processed A's change event
      assertTrue(nodeB.isFolder(X), "B did not reload A's commit");
      assertEquals("mine", nodeB.getFolderAlias(A));
      nodeB.save();
      awaitRepositoryEvent();

      RepletRegistry stored = read();
      assertEquals("mine", stored.getFolderAlias(A));
      assertTrue(stored.isFolder(X));
      stored.shutdown();
   }

   /**
    * B's unsaved favorites change must survive B's reload of A's commit, which is dispatched before
    * B saves (FavoritesRepositoryEntryController: addFolderFavoritesUser, then save).
    */
   @Test
   void localFavoritesSurviveRemoteCommitBeforeSave() throws Exception {
      nodeA.addFolder(X);
      nodeB.addFolderFavoritesUser(A, FAVORITES_USER);
      nodeA.save();
      awaitRepositoryEvent();
      // node B has now processed A's change event
      assertTrue(nodeB.isFolder(X), "B did not reload A's commit");
      assertTrue(favorites(nodeB).contains(FAVORITES_USER), "B's favorites: " + favorites(nodeB));
      nodeB.save();
      awaitRepositoryEvent();

      RepletRegistry stored = read();
      assertTrue(favorites(stored).contains(FAVORITES_USER), "stored favorites: " + favorites(stored));
      assertEquals("seed", stored.getFolderAlias(A));
      assertTrue(stored.isFolder(X));
      stored.shutdown();
   }

   /**
    * A commits while B's event-driven reload (which does not hold the lock) is reading storage.
    * B's copy then lacks A's folder, so B's next save must read storage again instead of writing
    * over A's commit.
    */
   @Test
   void commitDuringUnlockedReloadIsNotOverwritten() throws Exception {
      blockEvents();
      nodeA.addFolder(X);
      nodeB.afterLoad = () -> {
         try {
            nodeA.save();
         }
         catch(Exception ex) {
            throw new RuntimeException(ex);
         }
      };
      // as the change listener's init() does on the event thread
      nodeB.reload();
      assertNull(nodeB.afterLoad, "B's reload did not read storage");
      nodeB.addFolder(B);
      nodeB.save();

      RepletRegistry stored = read();
      Set<String> folders = new TreeSet<>(Arrays.asList(stored.getAllFolders()));
      assertTrue(folders.contains(X), "stored folders: " + folders);
      assertTrue(folders.contains(B), "stored folders: " + folders);
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

   private static List<String> favorites(RepletRegistry registry) {
      return Arrays.asList(registry.getFolderFavoritesUser(A).split("\\^_\\^"));
   }

   /**
    * Waits until the next change event of repository.xml has been dispatched to every listener,
    * the node registries included.
    */
   private void awaitRepositoryEvent() throws Exception {
      assertTrue(repositoryEvents.tryAcquire(TIMEOUT, TimeUnit.SECONDS),
                 "repository.xml change event was not delivered");
      awaitEventsDelivered();
   }

   /**
    * Parks the DataSpace event thread until {@link #releaseEvents()}, so that change events queue
    * up and no registry reloads in the meantime.
    */
   private void blockEvents() throws Exception {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      String file = "block-" + UUID.randomUUID();
      DataChangeListener blocker = e -> {
         entered.countDown();

         try {
            release.await(TIMEOUT, TimeUnit.SECONDS);
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      };

      space.addChangeListener(BARRIER_DIR, file, blocker);
      releaseBlocker = () -> {
         release.countDown();
         space.removeChangeListener(BARRIER_DIR, file, blocker);
      };
      touch(file);
      assertTrue(entered.await(TIMEOUT, TimeUnit.SECONDS), "event thread was not blocked");
   }

   private void releaseEvents() {
      if(releaseBlocker != null) {
         releaseBlocker.run();
         releaseBlocker = null;
      }
   }

   /**
    * Waits until every change event already queued on the event thread has been dispatched to all
    * listeners, by writing a marker file that is dispatched after them.
    */
   private void awaitEventsDelivered() throws Exception {
      CountDownLatch delivered = new CountDownLatch(1);
      String file = "barrier-" + UUID.randomUUID();
      DataChangeListener marker = e -> delivered.countDown();
      space.addChangeListener(BARRIER_DIR, file, marker);

      try {
         touch(file);
         assertTrue(delivered.await(TIMEOUT, TimeUnit.SECONDS), "change events were not delivered");
      }
      finally {
         space.removeChangeListener(BARRIER_DIR, file, marker);
      }
   }

   private void touch(String file) throws Exception {
      space.withOutputStream(BARRIER_DIR, file,
                             out -> out.write(file.getBytes(StandardCharsets.UTF_8)));
   }

   /**
    * A registry that runs {@link #afterLoad} once, right after it has parsed a copy of storage and
    * before the reload that read it has finished.
    */
   private static final class HookedRegistry extends RepletRegistry {
      HookedRegistry(String orgId) throws Exception {
         super(orgId);
      }

      @Override
      protected void load(InputStream repository) throws Exception {
         super.load(repository);
         Runnable hook = afterLoad;

         if(hook != null) {
            afterLoad = null;
            hook.run();
         }
      }

      private volatile Runnable afterLoad;
   }

   private static final String PARENT = "cluster-p113";
   private static final String A = PARENT + "/a";
   private static final String B = PARENT + "/b";
   private static final String X = PARENT + "/x";
   private static final String RENAMED = PARENT + "/renamed";
   private static final String FAVORITES_USER = "user76977~;~host-org";
   private static final String BARRIER_DIR = "test76977-local-barrier";
   private static final long TIMEOUT = 30;

   private String orgId;
   private DataSpace space;
   private RepletRegistry nodeA;
   private HookedRegistry nodeB;
   private Runnable releaseBlocker;
   private final Semaphore repositoryEvents = new Semaphore(0);
   private final DataChangeListener repositoryWatch = e -> repositoryEvents.release();
}
