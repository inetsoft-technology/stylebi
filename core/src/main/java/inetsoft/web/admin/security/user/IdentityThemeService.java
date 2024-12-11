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
package inetsoft.web.admin.security.user;

import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdentityThemeService {
   public IdentityThemeList getThemes() {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      Set<CustomTheme> themes = CustomThemesManager.getManager()
         .getCustomThemes()
         .stream()
         .filter(theme -> theme.getOrgID() == null || theme.getOrgID().equals(orgID))
         .collect(Collectors.toSet());
      return IdentityThemeList.builder()
         .from(themes)
         .build();
   }

   public String getTheme(IdentityID name, Function<CustomTheme, List<String>> fn) {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      return CustomThemesManager.getManager().getCustomThemes().stream()
         .filter(t -> fn.apply(t).contains(name.name))
         .filter(theme -> theme.getOrgID() == null || theme.getOrgID().equals(orgID))
         .map(CustomTheme::getId)
         .findFirst()
         .orElse(null);

   }

   public void removeTheme(String orgID) {
      CustomThemesManager themeManager = CustomThemesManager.getManager();
      Set<CustomTheme> themes = new HashSet<>(themeManager.getCustomThemes());
      Iterator<CustomTheme> iterator = themes.iterator();

      while (iterator.hasNext()) {
         CustomTheme theme = iterator.next();

         if(Tool.equals(orgID, theme.getOrgID())) {
            iterator.remove();
         }
      }

      themeManager.setCustomThemes(themes);
      themeManager.save();
   }

   public void updateTheme(String oldId, String id, Function<CustomTheme, List<String>> fn) {
      CustomThemesManager themeManager = CustomThemesManager.getManager();
      Set<CustomTheme> themes = new HashSet<>(themeManager.getCustomThemes());
      String oldThemeIdentity = oldId == null ? id : oldId;
      themes.stream()
      .map(theme -> {
         if(fn.apply(theme).contains(oldThemeIdentity)) {
            fn.apply(theme).remove(oldThemeIdentity);
            fn.apply(theme).add(id);
         }

         if(Tool.equals(theme.getOrgID(), oldId)) {
            theme.setOrgID(id);
            theme.setJarPath(theme.getJarPath().replace(oldId, id));
         }

         return theme;
      })
      .collect(Collectors.toList());

      themeManager.setCustomThemes(themes);
      themeManager.save();
   }

   public void updateUserTheme(String oldId, String id, String ntheme) {
      CustomThemesManager themeManager = CustomThemesManager.getManager();
      Set<CustomTheme> themes = new HashSet<>(themeManager.getCustomThemes());

      themes.stream().map(theme -> {
         if(theme.getUsers().contains(oldId)) {
            theme.getUsers().remove(oldId);
            theme.getUsers().add(id);
         }

         if(Tool.equals(theme.getId(), ntheme) && !theme.getUsers().contains(oldId)) {
            theme.getUsers().add(id);
         }

         return theme;
      })
      .collect(Collectors.toList());

      themeManager.setCustomThemes(themes);
      themeManager.save();
   }
}
