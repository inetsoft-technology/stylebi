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

/*
 * reloadThemes(path) — invoked by the storage browser after it deletes a file or directory.
 * The method must remove every theme whose jarPath begins with the deleted path and preserve
 * all others.
 *
 *  [A] exact file path match     → matching theme removed, others kept
 *  [B] directory prefix match    → all themes under that directory removed
 *  [C] no match                  → set unchanged (setCustomThemes still called)
 *  [D] theme with null jarPath   → always preserved (null-safe guard)
 *  [E] empty theme set           → setCustomThemes never called (early-exit guard)
 */

import inetsoft.storage.KeyValueStorageManager;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
      doNothing().when(manager).setCustomThemes(any());
      return manager;
   }

   @SuppressWarnings("unchecked")
   private static Set<CustomTheme> captureSetCustomThemes(CustomThemesManager manager) {
      ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
      verify(manager).setCustomThemes(captor.capture());
      return captor.getValue();
   }
}
