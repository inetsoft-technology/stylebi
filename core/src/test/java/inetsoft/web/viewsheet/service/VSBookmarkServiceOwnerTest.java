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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import inetsoft.web.viewsheet.service.VSBookmarkService.BookmarkAccess;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79436: the STOMP bookmark handlers took the bookmark owner from the request payload, so
 * any viewer could delete, overwrite, or load a bookmark that belongs to another user. The
 * owner must be validated against the caller before it is used.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSBookmarkServiceOwnerTest {
   private static final String ORG = "host";
   private static final IdentityID BOB = new IdentityID("bob", ORG);
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID ALICE_OTHER_ORG = new IdentityID("alice", "other");

   private static VSBookmarkInfo info(int type, boolean readOnly) {
      return new VSBookmarkInfo("bm", type, ALICE, readOnly, 0L);
   }

   // ---- the decision rule

   @Test
   void ownIsAlwaysAllowed() {
      for(BookmarkAccess access : BookmarkAccess.values()) {
         assertTrue(
            VSBookmarkService.isBookmarkAccessAllowed(BOB, BOB, null, () -> false, access));
      }
   }

   @Test
   void ownerOnlyOperationsRejectEveryOtherOwner() {
      for(int type : new int[] { VSBookmarkInfo.PRIVATE, VSBookmarkInfo.ALLSHARE,
                                 VSBookmarkInfo.GROUPSHARE })
      {
         assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
            BOB, ALICE, info(type, false), () -> true, BookmarkAccess.OWNER));
      }
   }

   @Test
   void privateOfAnotherUserIsNeverAccessible() {
      for(BookmarkAccess access : BookmarkAccess.values()) {
         assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
            BOB, ALICE, info(VSBookmarkInfo.PRIVATE, false), () -> true, access));
      }
   }

   @Test
   void unknownBookmarkOfAnotherUserIsRejected() {
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, null, () -> true, BookmarkAccess.READ));
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, null, () -> true, BookmarkAccess.WRITE));
   }

   @Test
   void allShareCanBeReadAndWrittenWhenNotReadOnly() {
      assertTrue(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.ALLSHARE, true), () -> false, BookmarkAccess.READ));
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.ALLSHARE, true), () -> false, BookmarkAccess.WRITE));
      assertTrue(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.ALLSHARE, false), () -> false, BookmarkAccess.WRITE));
   }

   @Test
   void groupShareNeedsTheSameGroup() {
      assertTrue(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.GROUPSHARE, false), () -> true, BookmarkAccess.READ));
      assertTrue(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.GROUPSHARE, false), () -> true, BookmarkAccess.WRITE));
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.GROUPSHARE, false), () -> false, BookmarkAccess.READ));
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         BOB, ALICE, info(VSBookmarkInfo.GROUPSHARE, true), () -> true, BookmarkAccess.WRITE));
   }

   @Test
   void ownerIsComparedByNameAndOrganization() {
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         ALICE, ALICE_OTHER_ORG, null, () -> true, BookmarkAccess.OWNER));
      assertFalse(VSBookmarkService.isBookmarkAccessAllowed(
         ALICE, ALICE_OTHER_ORG, info(VSBookmarkInfo.PRIVATE, false), () -> true,
         BookmarkAccess.READ));
   }

   @Test
   void emptyOwnerMapsToCaller() {
      assertEquals(BOB, VSBookmarkService.resolveBookmarkOwner(null, BOB));
      assertEquals(BOB, VSBookmarkService.resolveBookmarkOwner(new IdentityID("", ""), BOB));
      assertEquals(ALICE, VSBookmarkService.resolveBookmarkOwner(ALICE, BOB));
   }

   // ---- the handlers

   private final ScheduleManager scheduleManager = mock(ScheduleManager.class);
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);
   private VSBookmarkService service;
   private SRPrincipal bob;

   @BeforeEach
   void setUp() throws Exception {
      VSObjectService objectService = mock(VSObjectService.class);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.isSecurityEnabled()).thenReturn(true);
      when(objectService.getRuntimeViewsheet(anyString(), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      // the bookmark list bob can see does not contain alice's private bookmark
      service = new VSBookmarkService(
         objectService, null, security, scheduleManager, null, null);
      bob = new SRPrincipal(BOB);
   }

   private VSEditBookmarkEvent forged(String oldName) {
      VSBookmarkInfoModel model = VSBookmarkInfoModel.builder()
         .name("Secret").owner(ALICE).type(VSBookmarkInfo.PRIVATE).readOnly(false).build();
      return ImmutableVSEditBookmarkEvent.builder()
         .vsBookmarkInfoModel(model).oldName(oldName).confirmed(false).build();
   }

   @Test
   void forgedDeleteIsRejectedBeforeAnySinkOrScheduleCheck() throws Exception {
      service.deleteBookmark("rid", forged(null), bob, dispatcher, null);

      verify(dispatcher).sendCommand(argThat(c -> c instanceof MessageCommand &&
         ((MessageCommand) c).getType() == MessageCommand.Type.ERROR));
      verify(rvs, never()).removeBookmark(anyString(), any(IdentityID.class));
      verify(rvs, never()).removeBookmark(anyString(), any(IdentityID.class), anyBoolean());
      verifyNoInteractions(scheduleManager);
   }

   @Test
   void forgedSaveIsRejectedBeforeAnyLockOrWrite() throws Exception {
      service.saveBookmark("rid", forged(null), bob, dispatcher, null);

      verify(dispatcher).sendCommand(argThat(c -> c instanceof MessageCommand));
      verify(rvs, never()).addBookmark(anyString(), anyInt(), any(), anyBoolean());
      verify(rvs, never()).bookmarkUpdated(anyString(), any());
   }

   @Test
   void forgedGotoSetDefaultAndEditAreRejected() throws Exception {
      service.gotoBookmark("rid", forged(null), bob, dispatcher, null);
      service.setDefaultBookmark("rid", forged(null), bob, dispatcher, null);
      service.editBookmark("rid", forged("Secret"), bob, dispatcher, null);

      verify(dispatcher, times(3)).sendCommand(argThat(c -> c instanceof MessageCommand));
      verify(rvs, never()).gotoBookmark(anyString(), any(), any());
      verify(rvs, never()).setDefaultBookmark(any());
      verify(rvs, never()).editBookmark(anyString(), anyString(), anyInt(), anyBoolean());
   }
}
