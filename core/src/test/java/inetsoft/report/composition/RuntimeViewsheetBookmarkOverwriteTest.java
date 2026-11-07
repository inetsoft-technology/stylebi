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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.ImmutableVSEditBookmarkEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.event.VSEditBookmarkEvent;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.viewsheet.service.VSObjectService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79737: overwriting another user's shared bookmark took its type, read-only flag and write
 * permission from the session cache, which can predate the owner's change. The decision is now
 * taken from the freshly loaded storage inside RuntimeViewsheet.addBookmark.
 *
 * Axis: where the type / readOnly / visibility come from (stale session cache vs storage) x what
 * the owner changed (type, readOnly, visibility, deleted) x the save path (Override, confirmed
 * without bookmarkConfirmed, Readd).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class RuntimeViewsheetBookmarkOverwriteTest {
   private static final String ORG = "host-org";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final AtomicInteger COUNTER = new AtomicInteger();

   private RuntimeViewsheet rvs;
   private AssetRepository rep;
   private AssetEntry entry;
   private VSBookmarkService service;
   private CommandDispatcher dispatcher;
   private String name;

   @BeforeEach
   void setUp() throws Exception {
      rvs = vsResource.getRuntimeViewsheet();
      rep = rvs.getAssetRepository();
      entry = rvs.getEntry();
      name = "bm" + COUNTER.incrementAndGet();

      VSObjectService objectService = mock(VSObjectService.class);
      when(objectService.getRuntimeViewsheet(anyString(), any())).thenReturn(rvs);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      service = new VSBookmarkService(objectService, null, security, null, null, null);
      dispatcher = mock(CommandDispatcher.class);
   }

   // ---- helpers

   private void store(IdentityID owner, int type, boolean readOnly) throws Exception {
      VSBookmark bookmark = rep.getVSBookmark(entry, new XPrincipal(owner), true);
      bookmark.addBookmark(name, rvs.getViewsheet(), type, readOnly, true);
      rep.setVSBookmark(entry, bookmark, new XPrincipal(owner));
      Thread.sleep(20);
   }

   private void remove(IdentityID owner) throws Exception {
      VSBookmark bookmark = rep.getVSBookmark(entry, new XPrincipal(owner), true);
      bookmark.removeBookmark(name);
      rep.setVSBookmark(entry, bookmark, new XPrincipal(owner));
   }

   private VSBookmarkInfo stored(IdentityID owner) throws Exception {
      return rep.getVSBookmark(entry, new XPrincipal(owner), true).getBookmarkInfo(name);
   }

   private boolean errorSent() {
      try {
         verify(dispatcher).sendCommand(argThat(
            c -> c instanceof MessageCommand &&
               ((MessageCommand) c).getType() == MessageCommand.Type.ERROR));
         return true;
      }
      catch(AssertionError e) {
         return false;
      }
   }

   private void save(IdentityID owner, IdentityID caller, int type, boolean readOnly,
                     boolean confirmed, String bookmarkConfirmed) throws Exception
   {
      VSBookmarkInfoModel model = VSBookmarkInfoModel.builder()
         .name(name).owner(owner).type(type).readOnly(readOnly).build();
      VSEditBookmarkEvent event = ImmutableVSEditBookmarkEvent.builder()
         .vsBookmarkInfoModel(model).instruction("add").confirmed(confirmed)
         .bookmarkConfirmed(bookmarkConfirmed).build();
      service.saveBookmark("rid", event, new SRPrincipal(caller), dispatcher, null);
   }

   // ---- owner changed the bookmark after the caller's session loaded it

   @Test
   void overrideIsRejectedWhenOwnerMadeItPrivate() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE); // the caller's session caches it as shared
      store(ALICE, VSBookmarkInfo.PRIVATE, false);

      save(ALICE, BOB, VSBookmarkInfo.ALLSHARE, false, true, "Override");

      assertEquals(VSBookmarkInfo.PRIVATE, stored(ALICE).getType());
      assertTrue(errorSent());
   }

   @Test
   void overrideIsRejectedWhenOwnerMovedItToAGroupTheCallerIsNotIn() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);
      store(ALICE, VSBookmarkInfo.GROUPSHARE, false);

      save(ALICE, BOB, VSBookmarkInfo.ALLSHARE, false, true, "Override");

      assertEquals(VSBookmarkInfo.GROUPSHARE, stored(ALICE).getType());
      assertTrue(errorSent());
   }

   @Test
   void confirmedWithoutFlagCannotClearReadOnlySetByTheOwner() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);
      store(ALICE, VSBookmarkInfo.ALLSHARE, true);

      save(ALICE, BOB, VSBookmarkInfo.ALLSHARE, false, true, null);

      assertTrue(stored(ALICE).isReadOnly());
      assertTrue(errorSent());
   }

   @Test
   void storedTypeAndReadOnlyWinOverTheValuesPassedToAddBookmark() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);

      rvs.addBookmark(name, VSBookmarkInfo.PRIVATE, ALICE, true);

      VSBookmarkInfo info = stored(ALICE);
      assertEquals(VSBookmarkInfo.ALLSHARE, info.getType());
      assertFalse(info.isReadOnly());
   }

   // ---- cases that must keep working

   @Test
   void overrideOfAnUnchangedSharedBookmarkSucceeds() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);
      long before = stored(ALICE).getLastModified();
      Thread.sleep(20);

      save(ALICE, BOB, VSBookmarkInfo.PRIVATE, true, true, "Override");

      VSBookmarkInfo info = stored(ALICE);
      assertEquals(VSBookmarkInfo.ALLSHARE, info.getType());
      assertFalse(info.isReadOnly());
      assertTrue(info.getLastModified() > before);
      assertFalse(errorSent());
   }

   @Test
   void readdAfterOwnerDeletedItStillWorks() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);
      remove(ALICE);
      assertNull(stored(ALICE));

      save(ALICE, BOB, VSBookmarkInfo.ALLSHARE, false, true, "Readd");

      VSBookmarkInfo info = stored(ALICE);
      assertNotNull(info);
      assertEquals(VSBookmarkInfo.ALLSHARE, info.getType());
      assertFalse(errorSent());
   }

   @Test
   void ownBookmarkKeepsTheRequestedTypeAndReadOnly() throws Exception {
      IdentityID self = IdentityID.getIdentityIDFromKey(rvs.getUser().getName());
      store(self, VSBookmarkInfo.ALLSHARE, false);

      save(self, self, VSBookmarkInfo.PRIVATE, true, true, "Override");

      VSBookmarkInfo info = stored(self);
      assertEquals(VSBookmarkInfo.PRIVATE, info.getType());
      assertTrue(info.isReadOnly());
      assertFalse(errorSent());
   }

   @Test
   void changedByOthersDetectionStillSeesTheOwnersChange() throws Exception {
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      rvs.reloadVSBookmark(ALICE);
      assertFalse(rvs.bookmarkUpdated(name, ALICE));

      // the session baseline must stay the copy the caller loaded, so the owner's change shows
      store(ALICE, VSBookmarkInfo.ALLSHARE, false);
      assertTrue(rvs.bookmarkUpdated(name, ALICE));
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
