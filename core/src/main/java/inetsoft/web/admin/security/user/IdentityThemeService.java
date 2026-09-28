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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IdentityThemeService {
   public IdentityThemeService(CustomThemesManager customThemesManager) {
      this.customThemesManager = customThemesManager;
   }

   public IdentityThemeList getThemes() {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      Set<CustomTheme> themes = customThemesManager
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

      // the organizations list holds globally unique organization IDs, so only user, group and
      // role names are limited to the identities a theme can refer to. Sorted by ID to show the
      // theme that CustomThemesImpl.getUserTheme() applies when several themes match.
      return customThemesManager.getCustomThemes().stream()
         .filter(t -> fn.apply(t).contains(name.name))
         .filter(theme -> theme.getOrgID() == null || theme.getOrgID().equals(orgID))
         .filter(theme -> fn.apply(theme) == theme.getOrganizations() ||
            theme.isIdentityOrganization(name.orgID))
         .map(CustomTheme::getId)
         .filter(Objects::nonNull)
         .sorted()
         .findFirst()
         .orElse(null);

   }

   public void removeTheme(String orgID) {
      customThemesManager.updateCustomThemes(themes -> {
         Iterator<CustomTheme> iterator = themes.iterator();

         while (iterator.hasNext()) {
            CustomTheme theme = iterator.next();

            if(Tool.equals(orgID, theme.getOrgID())) {
               iterator.remove();
            }
            else {
               // strip the deleted org's membership from remaining (e.g. globally-shared)
               // themes so a new org reusing this ID doesn't inherit a stale theme
               theme.getOrganizations().remove(orgID);
            }
         }

         return themes;
      });

      customThemesManager.setOrgSelectedTheme("default", orgID);
   }

   /**
    * Renames a user, group or role in the themes that can refer to it. Only the themes of the
    * identity's organization are changed, see {@link CustomTheme#isIdentityOrganization(String)}.
    *
    * @param oldName the old identity name.
    * @param name    the new identity name.
    * @param orgID   the organization of the identity, or <tt>null</tt> for a global identity
    *                (e.g. a global role), which is renamed in the themes of every organization.
    * @param fn      the function that gets the identity list of a theme.
    */
   public void updateTheme(String oldName, String name, String orgID,
                           Function<CustomTheme, List<String>> fn)
   {
      if(oldName == null || name == null || oldName.equals(name)) {
         return;
      }

      customThemesManager.updateCustomThemes(themes -> {
         boolean changed = false;

         for(CustomTheme theme : themes) {
            List<String> identities = fn.apply(theme);

            if(theme.isIdentityOrganization(orgID) && identities.contains(oldName)) {
               identities.removeIf(identity -> identity.equals(oldName) || identity.equals(name));
               identities.add(name);
               changed = true;
            }
         }

         return changed ? themes : null;
      });
   }

   /**
    * Removes a deleted user, group or role from the themes that can refer to it, so that a new
    * identity with the same name does not inherit the theme. Only the themes of the identity's
    * organization are changed, see {@link CustomTheme#isIdentityOrganization(String)}.
    *
    * @param name  the name of the deleted identity.
    * @param orgID the organization of the identity, or <tt>null</tt> for a global identity
    *              (e.g. a global role), which is removed from the themes of every organization.
    * @param fn    the function that gets the identity list of a theme.
    */
   public void removeIdentity(String name, String orgID, Function<CustomTheme, List<String>> fn) {
      if(name == null) {
         return;
      }

      customThemesManager.updateCustomThemes(themes -> {
         boolean changed = false;

         for(CustomTheme theme : themes) {
            if(theme.isIdentityOrganization(orgID)) {
               changed |= fn.apply(theme).removeIf(name::equals);
            }
         }

         return changed ? themes : null;
      });
   }

   /**
    * @deprecated use {@link #updateTheme(String, String, String, Function)}. This overload
    * treats the identity as belonging to the current organization.
    */
   @Deprecated
   public void updateTheme(String oldName, String name, Function<CustomTheme, List<String>> fn) {
      updateTheme(oldName, name, OrganizationManager.getInstance().getCurrentOrgID(), fn);
   }

   /**
    * Renames a user in the themes that can refer to it and assigns the user to the selected
    * theme. Only the themes of the user's organization are changed, see
    * {@link CustomTheme#isIdentityOrganization(String)}.
    *
    * @param oldName the old user name.
    * @param name    the new user name.
    * @param orgID   the organization of the user.
    * @param ntheme  the ID of the selected theme, an empty string for the default theme, or
    *                <tt>null</tt> to only rename the user. A theme that cannot be assigned to
    *                the user's organization is ignored.
    */
   public void updateUserTheme(String oldName, String name, String orgID, String ntheme) {
      if(oldName == null || name == null) {
         return;
      }

      customThemesManager.updateCustomThemes(themes -> {
         String selected = ntheme;

         if(!Tool.isEmptyString(ntheme) && themes.stream().noneMatch(
            theme -> ntheme.equals(theme.getId()) && theme.isIdentityOrganization(orgID)))
         {
            LOG.warn("Ignoring theme {} for user {} because it cannot be assigned to organization {}",
                     ntheme, name, orgID);
            selected = null;
         }

         boolean changed = false;

         for(CustomTheme theme : themes) {
            if(!theme.isIdentityOrganization(orgID)) {
               continue;
            }

            List<String> users = theme.getUsers();
            boolean renamed = !oldName.equals(name) && users.contains(oldName);
            boolean assigned = selected == null ?
               users.contains(oldName) || users.contains(name) : selected.equals(theme.getId());

            // a user is assigned to at most one theme, so selecting a theme (or the default
            // theme) removes the user from the theme that was previously selected
            if(renamed || assigned != users.contains(name)) {
               users.removeIf(user -> user.equals(oldName) || user.equals(name));

               if(assigned) {
                  users.add(name);
               }

               changed = true;
            }
         }

         return changed ? themes : null;
      });
   }

   /**
    * @deprecated use {@link #updateUserTheme(String, String, String, String)}. This overload
    * treats the user as belonging to the current organization.
    */
   @Deprecated
   public void updateUserTheme(String oldName, String name, String ntheme) {
      updateUserTheme(oldName, name, OrganizationManager.getInstance().getCurrentOrgID(), ntheme);
   }

   private final CustomThemesManager customThemesManager;
   private static final Logger LOG = LoggerFactory.getLogger(IdentityThemeService.class);
}
