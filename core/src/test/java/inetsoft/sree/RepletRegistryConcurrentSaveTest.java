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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76977: two repository folders created at the same moment on two cluster nodes must both
 * survive.
 *
 * <p>Each cluster node holds its own {@link RepletRegistry} for the org, over one shared
 * {@code {orgId}/repository.xml}. Here two registry instances for the same org over the one real
 * {@link DataSpace} play the two nodes. A save on either one reaches both (and itself) through the
 * DataSpace change listener, on the single BlobStorageEvent thread, as a remote commit would. The
 * tests order that delivery explicitly: {@link #blockEvents()} parks the event thread so that no
 * reload can happen, and {@link #awaitEventsDelivered()} waits until every event queued so far has
 * been dispatched.</p>
 *
 * <p>{@link #addFolder} and {@link #save} are the two steps a node takes for
 * {@code POST /api/portal/tree/add-folder} (RepletEngine.addFolder: registry.addFolder, alias,
 * description, then registry.save). What each test asserts is only the scenario: both folders are
 * stored, whichever node saves last.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RepletRegistryConcurrentSaveTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      orgId = "test76977_" + info.getTestMethod().orElseThrow().getName().toLowerCase();
      space = DataSpace.getDataSpace();
      space.addChangeListener(null, orgId + "/repository.xml", repositoryWatch);
      seedParentFolder();
      nodeA = new RepletRegistry(orgId);
      nodeB = new RepletRegistry(orgId);
      nodeA.addPropertyChangeListener(reloadsOnA);
      nodeB.addPropertyChangeListener(reloadsOnB);
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
    * Last-writer-wins: neither node sees the other's commit before its own save (row O9 in
    * 01-hypothesis-reload.md, lww F9). The last save must not drop the first node's folder.
    */
   @Test
   void concurrentSavesFromStaleCopiesKeepBothFolders() throws Exception {
      blockEvents();

      addFolder(nodeA, X);
      addFolder(nodeB, Y);
      save(nodeA);
      save(nodeB);
      String beforeRelease = reloads();

      releaseEvents();
      awaitRepositoryEvent();
      awaitRepositoryEvent();

      assertStoredFolders("reloads before the saves were delivered: " + beforeRelease, X, Y);
   }

   /**
    * Reload between mutation and save: node A's commit reaches node B after B added its folder and
    * before B saved it (row O8 in 01-hypothesis-reload.md). B's save, the last commit, must still
    * contain B's own folder.
    */
   @Test
   void remoteCommitBetweenAddAndSaveKeepsBothFolders() throws Exception {
      addFolder(nodeA, X);
      addFolder(nodeB, Y);
      save(nodeA);
      awaitRepositoryEvent();
      // node B has now processed A's change event
      String beforeSaveB = reloads();
      save(nodeB);
      awaitRepositoryEvent();

      assertStoredFolders("reloads before B's save: " + beforeSaveB, X, Y);
   }

   private void addFolder(RepletRegistry node, String folder) {
      node.addFolder(folder);
      node.setFolderAlias(folder, null);
      node.setFolderDescription(folder, null);
   }

   private void save(RepletRegistry node) throws Exception {
      node.save();
   }

   /**
    * Stores {@link #PARENT} the way the reporter's step 1 does, and waits until its change event has
    * been dispatched, so that no late event from the setup reaches the two node registries.
    */
   private void seedParentFolder() throws Exception {
      RepletRegistry seed = new RepletRegistry(orgId);
      seed.addFolder(PARENT);
      seed.save();
      seed.shutdown();
      awaitRepositoryEvent();
   }

   private void assertStoredFolders(String context, String... expected) throws Exception {
      RepletRegistry reader = new RepletRegistry(orgId);

      try {
         Set<String> stored = new TreeSet<>(Arrays.asList(reader.getAllFolders()));
         String xml = readRepositoryXml();

         for(String folder : expected) {
            assertTrue(stored.contains(folder),
                       "stored repository.xml must contain " + folder + "; stored folders: " +
                       stored + "; A=" + folders(nodeA) + "; B=" + folders(nodeB) + "; " +
                       context + "; repository.xml: " + xml.replaceAll("\\s*\\n\\s*", " "));
         }
      }
      finally {
         reader.shutdown();
      }
   }

   private String reloads() {
      return "A=" + reloadsOnA.count + ", B=" + reloadsOnB.count;
   }

   private Set<String> folders(RepletRegistry registry) {
      return new TreeSet<>(Arrays.asList(registry.getAllFolders()));
   }

   private String readRepositoryXml() throws Exception {
      try(InputStream in = space.getInputStream(orgId, "repository.xml")) {
         return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
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
    * listeners. Events are dispatched in order on one BlobStorageEvent thread, so a marker event
    * written now is dispatched after them. An event that is still on its way to that thread is not
    * covered, see {@link #awaitRepositoryEvent()}.
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

   private static final String PARENT = "cluster-p113";
   private static final String X = PARENT + "/x";
   private static final String Y = PARENT + "/y";
   private static final String BARRIER_DIR = "test76977-barrier";
   private static final long TIMEOUT = 30;

   private String orgId;
   private DataSpace space;
   private RepletRegistry nodeA;
   private RepletRegistry nodeB;
   private Runnable releaseBlocker;
   private final Semaphore repositoryEvents = new Semaphore(0);
   private final DataChangeListener repositoryWatch = e -> repositoryEvents.release();
   // registries hold their property change listeners weakly, so these are fields
   private final ReloadCounter reloadsOnA = new ReloadCounter();
   private final ReloadCounter reloadsOnB = new ReloadCounter();

   private static final class ReloadCounter implements PropertyChangeListener {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
         if(RepletRegistry.RELOAD_EVENT.equals(evt.getPropertyName())) {
            count++;
         }
      }

      private volatile int count;
   }
}
