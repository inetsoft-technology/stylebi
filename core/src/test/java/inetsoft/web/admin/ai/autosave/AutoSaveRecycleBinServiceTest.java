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
package inetsoft.web.admin.ai.autosave;

import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.BlobStorage;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.AutoSaveUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Finding 3 (round-1 review): {@code parse}'s owner extraction only recognized {@code "_NULL_"}/
 * empty as the no-owner sentinel, missing {@code "anonymous"} -- which {@code AutoSaveUtils} itself
 * treats as an equivalent no-owner sentinel in {@code deleteUserAutoSaveFiles} and its own
 * organization-scoped listing helper (both normalize {@code "anonymous"} away before any owner
 * comparison). Left unrecognized, an anonymous/guest-session draft got a real-looking but bogus
 * owner identity of {@code "anonymous"}, which then made {@code isVisible}'s {@code ADMIN}-on-
 * {@code SECURITY_USER("anonymous")} check fail for every admin (there is no such identity),
 * silently hiding the entry from everyone.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AutoSaveRecycleBinServiceTest {
   @Mock private AssetRepository assetRepository;
   @Mock private SecurityProvider securityProvider;
   @Mock private Principal user;

   private AutoSaveRecycleBinService service;
   private MockedStatic<AutoSaveUtils> autoSaveUtils;

   @BeforeEach
   void setUp() throws Exception {
      service = new AutoSaveRecycleBinService(assetRepository, securityProvider);
      autoSaveUtils = mockStatic(AutoSaveUtils.class, withSettings().lenient());
      // getAutoSavedByName is otherwise unstubbed (defaults to null), and a null argument would
      // not match the anyString() matcher below, silently defeating the getLastModified() stub.
      autoSaveUtils.when(() -> AutoSaveUtils.getAutoSavedByName(anyString(), anyBoolean()))
         .thenAnswer(inv -> "recycle/" + inv.getArgument(0));

      // No real storage backing these synthetic ids in this unit test -- parse's own timestampOf
      // helper already treats a FileNotFoundException as "no timestamp available" and returns null
      // cleanly, so route getStorage()'s own getLastModified through a mock that throws it (
      // getStorage itself declares no checked exception, so it cannot throw one directly).
      @SuppressWarnings("unchecked")
      BlobStorage<AutoSaveUtils.Metadata> storage = mock(BlobStorage.class);
      lenient().when(storage.getLastModified(anyString()))
         .thenThrow(new FileNotFoundException("no storage in test"));
      autoSaveUtils.when(() -> AutoSaveUtils.getStorage(any())).thenReturn(storage);
   }

   @AfterEach
   void tearDown() {
      autoSaveUtils.close();
   }

   @Test
   void parseTreatsAnonymousOwnerAsNoOwner() {
      AutoSaveRecycleBinEntryProjection entry = service.parse("0^WORKSHEET^anonymous^ws1", user);

      assertNotNull(entry);
      assertNull(entry.owner());
      // And, per its own javadoc, an owner-less entry is always visible once the caller has
      // cleared the controller-level site-administrator gate -- no SECURITY_USER("anonymous")
      // check against a nonexistent identity.
      assertTrue(service.isVisible(entry, user));
      verifyNoInteractions(securityProvider);
   }

   @Test
   void parseStillTreatsNullSentinelAsNoOwner() {
      AutoSaveRecycleBinEntryProjection entry = service.parse("0^WORKSHEET^_NULL_^ws1", user);

      assertNotNull(entry);
      assertNull(entry.owner());
   }

   @Test
   void parseKeepsARealOwnerIdentity() {
      AutoSaveRecycleBinEntryProjection entry =
         service.parse("0^WORKSHEET^admin~;~host-org^ws1", user);

      assertNotNull(entry);
      assertEquals("admin~;~host-org", entry.owner());
   }

   private AutoSaveRecycleBinService.Occupant occupantWith(String entryType, String path,
                                                           AssetEntry.Type... present)
      throws Exception
   {
      lenient().when(user.getName()).thenReturn("admin~;~host-org");
      java.util.Set<AssetEntry.Type> types = new java.util.HashSet<>(Arrays.asList(present));
      lenient().when(assetRepository.containsEntry(any(AssetEntry.class)))
         .thenAnswer(inv -> types.contains(((AssetEntry) inv.getArgument(0)).getType()));
      AutoSaveRecycleBinEntryProjection entry = new AutoSaveRecycleBinEntryProjection("id", entryType,
         path, AutoSaveRecycleBinEntryProjection.SCOPE_GLOBAL, null, null);
      return service.occupantOf(entry, path, user);
   }

   @Test
   void occupantOfReportsFolderForWorksheetDraft() throws Exception {
      assertEquals(AutoSaveRecycleBinService.Occupant.FOLDER, occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET, "Examples", AssetEntry.Type.FOLDER));
   }

   @Test
   void occupantOfReportsFolderAndSnapshotForViewsheetDraft() throws Exception {
      assertEquals(AutoSaveRecycleBinService.Occupant.FOLDER, occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_DASHBOARD, "Examples",
         AssetEntry.Type.REPOSITORY_FOLDER));
      assertEquals(AutoSaveRecycleBinService.Occupant.SNAPSHOT, occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_DASHBOARD, "vs", AssetEntry.Type.VIEWSHEET_SNAPSHOT));
   }

   @Test
   void occupantOfReportsSameTypeSheetAsOverwritable() throws Exception {
      AutoSaveRecycleBinService.Occupant ws = occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET, "ws", AssetEntry.Type.WORKSHEET);
      AutoSaveRecycleBinService.Occupant vs = occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_DASHBOARD, "vs", AssetEntry.Type.VIEWSHEET);

      assertEquals(AutoSaveRecycleBinService.Occupant.SAME_TYPE_SHEET, ws);
      assertEquals(AutoSaveRecycleBinService.Occupant.SAME_TYPE_SHEET, vs);
      assertTrue(ws.overwritable());
   }

   @Test
   void occupantOfPrefersFolderOverSameTypeSheetAndReportsNone() throws Exception {
      assertEquals(AutoSaveRecycleBinService.Occupant.FOLDER, occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET, "x", AssetEntry.Type.WORKSHEET,
         AssetEntry.Type.FOLDER));
      assertEquals(AutoSaveRecycleBinService.Occupant.NONE, occupantWith(
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET, "x"));
   }
}
