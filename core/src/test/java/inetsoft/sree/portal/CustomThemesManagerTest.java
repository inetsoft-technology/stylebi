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
package inetsoft.sree.portal;

import inetsoft.storage.KeyValueStorageManager;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class CustomThemesManagerTest {
   @Mock private KeyValueStorageManager keyValueStorageManager;
   @Mock private DataSpace dataSpace;

   // [A] exact file path — only the theme whose jarPath matches the deleted file is removed
   @Test
   void reloadThemes_exactFilePath_removesMatchingThemeOnly() {
      CustomTheme target = theme("target", "portal/theme/target.jar");
      CustomTheme other  = theme("other",  "portal/theme/other.jar");

      CustomThemesManager manager = managerWithThemes(target, other);

      manager.reloadThemes("portal/theme/target.jar");

      Set<CustomTheme> saved = captureSetCustomThemes(manager);
      assertEquals(1, saved.size());
      assertEquals("other", saved.iterator().next().getId());
   }

   // [B] directory prefix — all themes stored under the deleted directory are removed
   @Test
   void reloadThemes_directoryPath_removesAllThemesUnderThatDirectory() {
      CustomTheme inDir1  = theme("t1", "portal/theme/t1.jar");
      CustomTheme inDir2  = theme("t2", "portal/theme/t2.jar");
      CustomTheme outside = theme("t3", "portal/other/t3.jar");

      CustomThemesManager manager = managerWithThemes(inDir1, inDir2, outside);

      manager.reloadThemes("portal/theme");

      Set<CustomTheme> saved = captureSetCustomThemes(manager);
      assertEquals(1, saved.size());
      assertEquals("t3", saved.iterator().next().getId());
   }

   // [C] no match — all themes are preserved; setCustomThemes is still called
   @Test
   void reloadThemes_noMatchingPath_allThemesPreserved() {
      CustomTheme t1 = theme("t1", "portal/theme/t1.jar");
      CustomTheme t2 = theme("t2", "portal/theme/t2.jar");

      CustomThemesManager manager = managerWithThemes(t1, t2);

      manager.reloadThemes("portal/theme/gone.jar");

      Set<CustomTheme> saved = captureSetCustomThemes(manager);
      assertEquals(2, saved.size());
   }

   // [D] null jarPath — themes with no JAR are never affected by reloadThemes
   @Test
   void reloadThemes_themeWithNullJarPath_alwaysPreserved() {
      CustomTheme withJar    = theme("with-jar", "portal/theme/target.jar");
      CustomTheme withoutJar = theme("no-jar", null);

      CustomThemesManager manager = managerWithThemes(withJar, withoutJar);

      manager.reloadThemes("portal/theme/target.jar");

      Set<CustomTheme> saved = captureSetCustomThemes(manager);
      assertEquals(1, saved.size());
      assertEquals("no-jar", saved.iterator().next().getId());
   }

   // [F] path-separator boundary — a theme under "portal/themes/" must not be removed when
   //     the deleted path is "portal/theme" (shares a string prefix but a different directory)
   @Test
   void reloadThemes_pathSharesPrefixButDifferentDirectory_doesNotRemoveUnrelatedTheme() {
      CustomTheme inSimilarDir = theme("t1", "portal/themes/t1.jar");
      CustomTheme inExactDir   = theme("t2", "portal/theme/t2.jar");

      CustomThemesManager manager = managerWithThemes(inSimilarDir, inExactDir);

      manager.reloadThemes("portal/theme");

      Set<CustomTheme> saved = captureSetCustomThemes(manager);
      assertEquals(1, saved.size());
      assertEquals("t1", saved.iterator().next().getId());
   }

   // removeSelectedTheme is called for each deleted theme but not for preserved ones
   @Test
   void reloadThemes_deletedThemes_removeSelectedThemeCalledForDeletedOnly() {
      CustomTheme deleted = theme("deleted", "portal/theme/deleted.jar");
      CustomTheme kept    = theme("kept",    "portal/theme/kept.jar");

      CustomThemesManager manager = managerWithThemes(deleted, kept);

      manager.reloadThemes("portal/theme/deleted.jar");

      verify(manager).removeSelectedTheme("deleted");
      verify(manager, never()).removeSelectedTheme("kept");
   }

   // [E] empty theme set — early exit; setCustomThemes is never invoked
   @Test
   void reloadThemes_emptyThemeSet_doesNotCallSetCustomThemes() {
      CustomThemesManager manager = managerWithThemes();

      manager.reloadThemes("portal/theme/any.jar");

      verify(manager, never()).setCustomThemes(any());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static CustomTheme theme(String id, String jarPath) {
      CustomTheme t = new CustomTheme();
      t.setId(id);
      t.setName(id);
      t.setJarPath(jarPath);
      return t;
   }

   private CustomThemesManager managerWithThemes(CustomTheme... themes) {
      CustomThemesManager manager = spy(new CustomThemesManager(keyValueStorageManager, dataSpace));
      Set<CustomTheme> themeSet = new HashSet<>(Arrays.asList(themes));
      doReturn(themeSet).when(manager).getCustomThemes();
      lenient().doNothing().when(manager).setCustomThemes(any());
      lenient().doNothing().when(manager).removeSelectedTheme(any());
      return manager;
   }

   private static Set<CustomTheme> captureSetCustomThemes(CustomThemesManager manager) {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Set<CustomTheme>> captor = ArgumentCaptor.forClass((Class) Set.class);
      verify(manager).setCustomThemes(captor.capture());
      return captor.getValue();
   }
}
