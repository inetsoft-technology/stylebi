/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.composition;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MultiMap;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.BookmarkLockManager.BookmarkLock;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82336: a bookmark lock carries the id of the node that holds the runtime viewsheet, and
 * a lock of a node that left the cluster no longer refuses other users. When the viewer's
 * sheet is reloaded from the cluster cache on a live node (the client resumes its runtime id
 * after a failover), the lock must be re-asserted with the live node, and disposing that
 * reloaded copy (the HTTP-session-end close) must still release it.
 *
 * A second {@link RuntimeSheetCache} on the same cache plays the surviving node: its local map
 * is empty, so {@code get} rebuilds the sheet from the saved state, as after a failover.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "EmbeddedVS1.zip")
@Tag("slow")
@Tag("integration")
class RuntimeViewsheetBookmarkLockFailoverTest {
   private static final String CACHE_NAME = "bug82336.sheets";
   private static final String DEAD_NODE = "dead-node-82336";
   private static final String OTHER = "other82336~;~host-org";

   private RuntimeViewsheet rvs;
   private String user;
   private String rid;
   private MultiMap<String, BookmarkLock> lockMap;
   private BookmarkLockManager manager;

   @BeforeEach
   void setUp() {
      rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser().getName();
      rid = rvs.getID();
      lockMap = Cluster.getInstance().getMultiMap("BOOKMARK_LOCK_MAP");
      manager = BookmarkLockManager.getManager();
   }

   @AfterEach
   void tearDown() {
      manager.unlockAll(user, rid);
   }

   /** Saves the sheet to the cluster cache and loads it on a node that does not hold it. */
   private RuntimeSheet reloadOnAnotherNode() throws Exception {
      new RuntimeSheetCache(Cluster.getInstance(), CACHE_NAME)
         .putSheet(rid, rvs).get(10, TimeUnit.SECONDS);
      RuntimeSheet restored = new RuntimeSheetCache(Cluster.getInstance(), CACHE_NAME).get(rid);
      assertNotNull(restored, "premise: the sheet is rebuilt from the cluster cache");
      assertNotSame(rvs, restored, "premise: the sheet is a reloaded copy");
      return restored;
   }

   @Test
   void reloadReassertsTheLockOfADeadNodeAndDisposeReleasesIt() throws Exception {
      String key = "bug82336/a";
      // the lock the viewer's sheet took on the node that has since died
      lockMap.put(key, new BookmarkLock(user, rid, DEAD_NODE));
      assertNull(manager.getLockedBookmarkUser(key, OTHER),
                 "a lock of a node that left the cluster must not refuse other users");

      RuntimeSheet restored = reloadOnAnotherNode();

      // (a) the viewer is back on a live node, so the bookmark is protected again
      assertEquals(user, manager.getLockedBookmarkUser(key, OTHER));
      Collection<BookmarkLock> locks = lockMap.get(key);
      assertEquals(1, locks.size());
      assertEquals(Cluster.getInstance().getLocalNodeId(), locks.iterator().next().getNodeId());

      // (c) the session-end close disposes the reloaded copy, which releases the lock
      restored.dispose();
      assertNull(manager.getLockedBookmarkUser(key, OTHER));
      assertNull(lockMap.get(key));
   }

   @Test
   void reloadDoesNotTakeAReleasedLockAgain() throws Exception {
      String key = "bug82336/b";
      manager.lock(key, user, rid);
      manager.unlock(key, user, rid);

      RuntimeSheet restored = reloadOnAnotherNode();

      try {
         assertNull(manager.getLockedBookmarkUser(key, OTHER));
      }
      finally {
         restored.dispose();
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   @Order(1)
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}
