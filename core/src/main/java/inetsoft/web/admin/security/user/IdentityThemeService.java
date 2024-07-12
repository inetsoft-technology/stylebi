/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security.user;

import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
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

   public void updateTheme(String oldName, String name, String themeId,
                           Function<CustomTheme, List<String>> fn)
   {
      String sanitizedId = sanitizeThemeId(themeId);
      CustomThemesManager themeManager = CustomThemesManager.getManager();
      Set<CustomTheme> themes = new HashSet<>(themeManager.getCustomThemes());
      String oldThemeIdentity = oldName == null ? name : oldName;
      CustomTheme currentTheme = themes.stream()
         .filter(t -> fn.apply(t).contains(oldThemeIdentity))
         .findFirst()
         .orElse(null);

      if(sanitizedId == null && currentTheme != null) {
         fn.apply(currentTheme).remove(oldThemeIdentity);
      }
      else if(sanitizedId != null && currentTheme != null) {
         if(currentTheme.getId().equals(sanitizedId) && !oldThemeIdentity.equals(name)) {
            fn.apply(currentTheme).remove(oldThemeIdentity);
            fn.apply(currentTheme).add(name);
         }
         else if(!currentTheme.getId().equals(sanitizedId)) {
            fn.apply(currentTheme).remove(oldName);
            themes.stream()
               .filter(t -> t.getId().equals(sanitizedId))
               .findFirst()
               .ifPresent(t -> fn.apply(t).add(name));
         }
      }
      else if(sanitizedId != null) {
         themes.stream()
            .filter(t -> t.getId().equals(sanitizedId))
            .findFirst()
            .ifPresent(t -> fn.apply(t).add(name));
      }

      themeManager.setCustomThemes(themes);
      themeManager.save();
   }

   private String sanitizeThemeId(String themeId) {
      String sanitized = themeId;

      if(sanitized != null) {
         sanitized = sanitized.trim();

         if(sanitized.isEmpty()) {
            sanitized = null;
         }
      }

      return sanitized;
   }
}
