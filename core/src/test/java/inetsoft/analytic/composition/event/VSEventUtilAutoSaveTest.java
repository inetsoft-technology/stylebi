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
package inetsoft.analytic.composition.event;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.AutoSaveUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class VSEventUtilAutoSaveTest {
   // Bug #75777: an entry created for a recycle bin file already refers to a discarded auto save
   // file. The file name embeds the user and ip address of the session that created it, so the
   // recycled name can not be recreated from the user performing the restore. Moving it again
   // would rename the recycle bin entry to a name attributed to that user, orphaning it so that
   // the explicit cleanup after the restore no longer finds it.
   @Test
   void deleteAutoSavedFile_recycledEntry_doesNotMoveFileAgain() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "dashboard1", null);
      entry.setProperty("autoFileName", AssetRepository.GLOBAL_SCOPE +
         "^VIEWSHEET^alice^dashboard1^0_0_0_0_0_0_0_1~");
      entry.setProperty("isRecycle", "true");

      try(MockedStatic<AutoSaveUtils> utils = mockStatic(AutoSaveUtils.class)) {
         VSEventUtil.deleteAutoSavedFile(entry, null);

         utils.verifyNoInteractions();
      }
   }

   // Bug #75777: an entry with no file name still refers to the active auto save file of the
   // sheet, which is moved to the recycle bin so that it can be recovered from the enterprise
   // manager.
   @Test
   void deleteAutoSavedFile_activeEntry_movesFileToRecycleBin() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "dashboard1", null);
      String savefile = AssetRepository.GLOBAL_SCOPE + "^VIEWSHEET^alice^dashboard1^ip~";
      String recyclefile = AutoSaveUtils.RECYCLE_PREFIX + savefile;

      try(MockedStatic<AutoSaveUtils> utils = mockStatic(AutoSaveUtils.class)) {
         utils.when(() -> AutoSaveUtils.getAutoSavedFile(entry, null)).thenReturn(savefile);
         utils.when(() -> AutoSaveUtils.getAutoSavedFile(entry, null, true)).thenReturn(recyclefile);
         utils.when(() -> AutoSaveUtils.exists(savefile, null)).thenReturn(true);

         VSEventUtil.deleteAutoSavedFile(entry, null);

         utils.verify(() -> AutoSaveUtils.renameAutoSaveFile(savefile, recyclefile, null));
      }
   }

   // The auto save file may have already been removed, e.g. by the weekly cleanup, in which case
   // there is nothing to move.
   @Test
   void deleteAutoSavedFile_missingFile_doesNotRename() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "dashboard1", null);
      String savefile = AssetRepository.GLOBAL_SCOPE + "^VIEWSHEET^alice^dashboard1^ip~";

      try(MockedStatic<AutoSaveUtils> utils = mockStatic(AutoSaveUtils.class)) {
         utils.when(() -> AutoSaveUtils.getAutoSavedFile(entry, null)).thenReturn(savefile);
         utils.when(() -> AutoSaveUtils.exists(savefile, null)).thenReturn(false);

         VSEventUtil.deleteAutoSavedFile(entry, null);

         utils.verify(() -> AutoSaveUtils.renameAutoSaveFile(anyString(), anyString(), any()),
                      never());
      }
   }
}
