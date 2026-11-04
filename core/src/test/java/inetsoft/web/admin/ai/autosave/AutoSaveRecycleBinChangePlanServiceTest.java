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

import inetsoft.util.Tool;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79938: a restore plan used to accept any non-blank assetName, including names EM's restore
 * dialog cannot express (banned characters, a leading non-alphanumeric file name) and a
 * destination under a folder that does not exist.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AutoSaveRecycleBinChangePlanServiceTest {
   @Mock private AutoSaveRecycleBinService autoSaveRecycleBinService;
   @Mock private Principal user;

   private AutoSaveRecycleBinChangePlanService planService;
   private MockedStatic<Tool> tool;
   private AutoSaveRecycleBinEntryProjection entry;

   @BeforeEach
   void setUp() throws Exception {
      planService = new AutoSaveRecycleBinChangePlanService(autoSaveRecycleBinService);
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      entry = new AutoSaveRecycleBinEntryProjection("id1",
         AutoSaveRecycleBinEntryProjection.TYPE_WORKSHEET, "Untitled-9",
         AutoSaveRecycleBinEntryProjection.SCOPE_GLOBAL, "admin~;~host-org", null);
      lenient().when(autoSaveRecycleBinService.requireEntry("id1", user)).thenReturn(entry);
      // A mock returns null for an unstubbed Occupant; nothing occupies the destination by default
      // (the #79939 occupant check runs on every restore plan).
      lenient().when(autoSaveRecycleBinService.occupantOf(any(), anyString(), any()))
         .thenReturn(AutoSaveRecycleBinService.Occupant.NONE);
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   @Test
   void validNameAtRootPlans() throws Exception {
      assertDoesNotThrow(() -> plan("QA-name-probe"));
   }

   @Test
   void blankAssetNameDefaultsToDraftPathAndSkipsNameChecks() throws Exception {
      assertDoesNotThrow(() -> plan(null));
      verify(autoSaveRecycleBinService, never()).parentFolderExists(any(), anyString(), any());
   }

   @Test
   void bannedCharacterIsRefusedNamingAssetName() {
      for(String name : new String[] {"QA<bad%name-probe", "a\\b", "a\"b", "a^b", "a~b"}) {
         IllegalArgumentException e =
            assertThrows(IllegalArgumentException.class, () -> plan(name), name);
         assertTrue(e.getMessage().contains("changes[0].assetName"), e.getMessage());
      }
   }

   @Test
   void reportedValueWithBannedCharacterIsRefused() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> plan("QA-NoSuchFolder-Probe/QA*bad?name-%"));
      assertTrue(e.getMessage().contains("assetName"), e.getMessage());
   }

   @Test
   void fileNameMustStartWithLetterOrDigit() {
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> plan("_leading-underscore"));
      assertTrue(e.getMessage().contains("must start with a letter or digit"), e.getMessage());
   }

   @Test
   void emptyPathSegmentIsRefused() {
      assertThrows(IllegalArgumentException.class, () -> plan("/name"));
      assertThrows(IllegalArgumentException.class, () -> plan("folder//name"));
      assertThrows(IllegalArgumentException.class, () -> plan("folder/"));
   }

   @Test
   void missingParentFolderIsRefused() throws Exception {
      when(autoSaveRecycleBinService.parentFolderExists(entry, "QA-NoSuchFolder-Probe/QA-name-probe",
                                                        user)).thenReturn(false);
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> plan("QA-NoSuchFolder-Probe/QA-name-probe"));
      assertTrue(e.getMessage().contains("changes[0].assetName"), e.getMessage());
      assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
   }

   @Test
   void existingParentFolderPlans() throws Exception {
      when(autoSaveRecycleBinService.parentFolderExists(entry, "Reports/QA-name", user))
         .thenReturn(true);
      assertDoesNotThrow(() -> plan("Reports/QA-name"));
   }

   /** Bug #80195: overwrite:true only replaces a same-type sheet, so the text must not claim it
    * destroys whatever occupies the path. */
   @Test
   void collisionWithoutOverwriteDoesNotClaimAnyOccupantIsDeleted() throws Exception {
      occupy(AutoSaveRecycleBinService.Occupant.SAME_TYPE_SHEET);
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> plan("Existing", false));
      assertTrue(e.getMessage().contains("changes[0].overwrite"), e.getMessage());
      assertFalse(e.getMessage().contains("whatever currently occupies"), e.getMessage());
   }

   @Test
   void overwriteDescriptionSaysOnlySameTypeSheetIsReplaced() throws Exception {
      occupy(AutoSaveRecycleBinService.Occupant.SAME_TYPE_SHEET);
      String description = plan("Existing", true).changes().get(0).description();
      assertTrue(description.contains("PERMANENTLY DESTROYS the existing worksheet"), description);
      assertTrue(description.contains("only a same-type sheet is replaced"), description);
   }

   @Test
   void folderOccupantIsRefusedEvenWithOverwriteAndTextSaysSo() throws Exception {
      occupy(AutoSaveRecycleBinService.Occupant.FOLDER);
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> plan("Existing", true));
      assertTrue(e.getMessage().contains("only replaces a worksheet"), e.getMessage());
      assertTrue(e.getMessage().contains("folder or viewsheet snapshot is never replaced"),
                 e.getMessage());
      assertTrue(e.getMessage().contains("choose a different assetName"), e.getMessage());
   }

   @Test
   void snapshotOccupantIsRefusedEvenWithOverwrite() throws Exception {
      occupy(AutoSaveRecycleBinService.Occupant.SNAPSHOT);
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> plan("Existing", true));
      assertTrue(e.getMessage().contains("viewsheet snapshot"), e.getMessage());
      assertTrue(e.getMessage().contains("left in place"), e.getMessage());
   }

   private void occupy(AutoSaveRecycleBinService.Occupant occupant) throws Exception {
      when(autoSaveRecycleBinService.occupantOf(any(), eq("Existing"), any())).thenReturn(occupant);
   }

   private ResolvedPlan plan(String assetName, boolean overwrite) throws Exception {
      AutoSaveRecycleBinChangeRequest r = new AutoSaveRecycleBinChangeRequest();
      r.setVerb(AutoSaveRecycleBinChangeRequest.VERB_RESTORE);
      r.setId("id1");
      r.setAssetName(assetName);
      r.setOverwrite(overwrite);
      AutoSaveRecycleBinChangePlanRequest req = new AutoSaveRecycleBinChangePlanRequest();
      req.setTask("restore draft");
      req.setChanges(List.of(r));
      return planService.resolve(req, user);
   }

   private void plan(String assetName) throws Exception {
      AutoSaveRecycleBinChangeRequest r = new AutoSaveRecycleBinChangeRequest();
      r.setVerb(AutoSaveRecycleBinChangeRequest.VERB_RESTORE);
      r.setId("id1");
      r.setAssetName(assetName);
      AutoSaveRecycleBinChangePlanRequest req = new AutoSaveRecycleBinChangePlanRequest();
      req.setTask("restore draft");
      req.setChanges(List.of(r));
      planService.resolve(req, user);
   }
}
