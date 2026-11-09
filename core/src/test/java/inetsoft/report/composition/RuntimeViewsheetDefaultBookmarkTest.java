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
package inetsoft.report.composition;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.BookmarkLockManager;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmark.DefaultBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #80642: a default bookmark only stores the name and owner of the bookmark. When the
 * default is another user's bookmark that the owner later made private (or moved to a group the
 * user is not in), opening the viewsheet still applied it and locked it. The default is now
 * applied and locked only while the user can still see the bookmark; it is kept (not cleared)
 * while it is hidden, and cleared as before when the bookmark no longer exists.
 *
 * Axis: owner (self / other) x type (PRIVATE / ALLSHARE / GROUPSHARE same group / other group)
 * x existence (stored / deleted / other owner's (Home)).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class RuntimeViewsheetDefaultBookmarkTest {
   private static final String ORG = "host-org";
   private static final IdentityID ALICE = new IdentityID("alice80642", ORG);
   private static final AtomicInteger COUNTER = new AtomicInteger();

   private RuntimeViewsheet rvs;
   private AssetRepository rep;
   private AssetEntry entry;
   private IdentityID self;
   private String name;

   @BeforeEach
   void setUp() {
      rvs = vsResource.getRuntimeViewsheet();
      rep = rvs.getAssetRepository();
      entry = rvs.getEntry();
      self = IdentityID.getIdentityIDFromKey(rvs.getUser().getName());
      name = "def" + COUNTER.incrementAndGet();
   }

   // ---- helpers

   private void store(IdentityID owner, String bookmarkName, int type) throws Exception {
      VSBookmark bookmark = rep.getVSBookmark(entry, new XPrincipal(owner), true);
      bookmark.addBookmark(bookmarkName, rvs.getViewsheet(), type, false, true);
      rep.setVSBookmark(entry, bookmark, new XPrincipal(owner));
      rvs.reloadVSBookmark(owner);
   }

   private void setDefault(String bookmarkName, IdentityID owner) throws Exception {
      rvs.setDefaultBookmark(new DefaultBookmark(bookmarkName, owner));
   }

   private DefaultBookmark storedDefault() throws Exception {
      return rep.getVSBookmark(entry, rvs.getUser(), true).getDefaultBookmark();
   }

   /**
    * Re-applies the default bookmark the way opening the viewsheet does, with security enabled
    * and the given groups for alice and the viewer.
    */
   private void refresh(String[] aliceGroups, String[] selfGroups) {
      // the test context's security engine is itself a mock, so delegate to it instead of spying
      SecurityEngine security =
         mock(SecurityEngine.class, delegatesTo(SecurityEngine.getSecurity()));
      doReturn(true).when(security).isSecurityEnabled();

      try(MockedStatic<SecurityEngine> engine = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         engine.when(SecurityEngine::getSecurity).thenReturn(security);
         sutil.when(() -> SUtil.getGroups(any(IdentityID.class))).thenAnswer(
            inv -> ALICE.equals(inv.getArgument(0)) ? aliceGroups : selfGroups);
         rvs.refresh();
      }
   }

   private void refresh() {
      refresh(new String[0], new String[0]);
   }

   private boolean applied() {
      VSBookmarkInfo opened = rvs.getOpenedBookmark();
      return opened != null && name.equals(opened.getName());
   }

   /** Locks the default bookmark the way setting the runtime id does, and tells if it locked. */
   private boolean locksDefault(String bookmarkName, IdentityID owner) throws Exception {
      Method lockPath = RuntimeViewsheet.class.getDeclaredMethod(
         "getLockPath", String.class, IdentityID.class);
      lockPath.setAccessible(true);
      String path = (String) lockPath.invoke(rvs, bookmarkName, owner);
      rvs.setID(rvs.getID());

      try {
         return BookmarkLockManager.getManager().getLockedBookmarkUser(path, "nobody") != null;
      }
      finally {
         rvs.unLockBookmark(bookmarkName, owner);
      }
   }

   // ---- another user's bookmark that is no longer visible

   @Test
   void otherUsersPrivateDefaultIsNotAppliedNotClearedAndNotLocked() throws Exception {
      store(ALICE, name, VSBookmarkInfo.PRIVATE);
      setDefault(name, ALICE);

      refresh();

      assertFalse(applied());
      DefaultBookmark def = storedDefault();
      assertNotNull(def);
      assertEquals(name, def.getName());
      assertEquals(ALICE, def.getOwner());
      assertFalse(locksDefault(name, ALICE));
   }

   @Test
   void otherUsersGroupShareDefaultIsNotAppliedOutsideTheGroup() throws Exception {
      store(ALICE, name, VSBookmarkInfo.GROUPSHARE);
      setDefault(name, ALICE);

      refresh(new String[] { "g1" }, new String[] { "g2" });

      assertFalse(applied());
      assertNotNull(storedDefault());
   }

   @Test
   void otherUsersHomeIsNotApplied() throws Exception {
      name = VSBookmark.HOME_BOOKMARK;
      store(ALICE, VSBookmark.HOME_BOOKMARK, VSBookmarkInfo.ALLSHARE);
      setDefault(VSBookmark.HOME_BOOKMARK, ALICE);

      refresh();

      VSBookmarkInfo opened = rvs.getOpenedBookmark();
      assertTrue(opened == null || !ALICE.equals(opened.getOwner()));
      assertFalse(locksDefault(VSBookmark.HOME_BOOKMARK, ALICE));
   }

   // ---- cases that must keep working

   @Test
   void otherUsersAllShareDefaultIsAppliedAndLocked() throws Exception {
      store(ALICE, name, VSBookmarkInfo.ALLSHARE);
      setDefault(name, ALICE);

      refresh();

      assertTrue(applied());
      assertTrue(locksDefault(name, ALICE));
   }

   @Test
   void otherUsersGroupShareDefaultIsAppliedInTheSameGroup() throws Exception {
      store(ALICE, name, VSBookmarkInfo.GROUPSHARE);
      setDefault(name, ALICE);

      refresh(new String[] { "g1" }, new String[] { "g1" });

      assertTrue(applied());
   }

   @Test
   void ownPrivateDefaultIsApplied() throws Exception {
      store(self, name, VSBookmarkInfo.PRIVATE);
      setDefault(name, self);

      refresh();

      assertTrue(applied());
      // the lock is taken by the viewer itself, so it shows up only for another user
      assertTrue(locksDefault(name, self));
   }

   @Test
   void deletedDefaultIsClearedAsBefore() throws Exception {
      setDefault(name, ALICE);

      refresh();

      assertFalse(applied());
      assertNull(storedDefault());
   }

   private static OpenViewsheetEvent openEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openEvent());
}
