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
package inetsoft.sree.portal;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Test support for code that changes the custom themes through
 * {@link CustomThemesManager#updateCustomThemes(CustomThemesManager.ThemesUpdate)}.
 */
public final class CustomThemesManagerMocks {
   private CustomThemesManagerMocks() {
   }

   /**
    * Makes {@code updateCustomThemes(...)} on a mock manager behave like the real method,
    * without the cluster lock: the update is applied to a copy of {@code getCustomThemes()} and
    * the result, if any, is passed to {@code setCustomThemes(...)}. Tests can then stub
    * {@code getCustomThemes()} and verify {@code setCustomThemes(...)} as before.
    *
    * @param manager the mock manager.
    */
   public static void applyUpdates(CustomThemesManager manager) {
      lenient().doAnswer(invocation -> {
         CustomThemesManager.ThemesUpdate<?> update = invocation.getArgument(0);
         Set<CustomTheme> themes = update.apply(new HashSet<>(manager.getCustomThemes()));

         if(themes != null) {
            manager.setCustomThemes(themes);
         }

         return null;
      }).when(manager).updateCustomThemes(any());
   }
}
