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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomTheme;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class IdentityThemeService {
   public IdentityThemeService(CustomThemesManager customThemesManager) {
      this.customThemesManager = customThemesManager;
   }

   public IdentityThemeList getThemes() {
      return getThemes(null, null);
   }

   /**
    * Gets the themes that can be selected for an organization, i.e. the global themes and the
    * themes of the organization.
    *
    * @param orgID     the ID of the edited organization, or <tt>null</tt> for the current
    *                  organization. Another organization is only listed for a site
    *                  administrator, otherwise the current organization is listed.
    * @param principal the user that requests the themes.
    */
   public IdentityThemeList getThemes(String orgID, Principal principal) {
      OrganizationManager orgManager = OrganizationManager.getInstance();
      String currentOrgID = orgManager.getCurrentOrgID();
      String listOrgID = Tool.isEmptyString(orgID) ||
         !orgID.equals(currentOrgID) && !orgManager.isSiteAdmin(principal) ? currentOrgID : orgID;
      Set<CustomTheme> themes = customThemesManager
         .getCustomThemes()
         .stream()
         // same rule as IdentityService.getEligibleOrgTheme(), which checks the saved theme
         .filter(theme -> Tool.isEmptyString(theme.getOrgID()) ||
            Tool.equals(theme.getOrgID(), listOrgID))
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
    * @param ntheme  the ID of the selected theme, an empty string or
    *                {@link CustomTheme#DEFAULT_THEME_ID} for the default theme, or
    *                <tt>null</tt> to only rename the user. A theme that cannot be assigned to
    *                the user's organization is ignored.
    */
   public void updateUserTheme(String oldName, String name, String orgID, String ntheme) {
      updateIdentityTheme(oldName, name, orgID, ntheme, CustomTheme::getUsers,
                          theme -> theme.isIdentityOrganization(orgID));
   }

   /**
    * Renames a user, group or role in the themes that can refer to it and assigns the identity
    * to the selected theme. Only the themes of the identity's organization are changed, see
    * {@link CustomTheme#isIdentityOrganization(String)}.
    * <p>
    * A global role (<tt>null</tt> organization) is renamed in the themes of every organization,
    * like {@link #updateTheme(String, String, String, Function)}, but is only assigned to, or
    * removed from, the themes of the current organization and, for a site administrator or in
    * single-tenant mode, the global themes. Otherwise editing a global role in one organization
    * would change the theme of the role's users in every other organization.
    *
    * @param oldName   the old identity name.
    * @param name      the new identity name.
    * @param orgID     the organization of the identity, or <tt>null</tt> for a global role.
    * @param ntheme    the ID of the selected theme, an empty string or
    *                  {@link CustomTheme#DEFAULT_THEME_ID} for the default theme, or
    *                  <tt>null</tt> to only rename the identity. A theme that cannot be assigned
    *                  to the identity is ignored.
    * @param fn        the function that gets the identity list of a theme.
    * @param principal the user that edits the identity.
    */
   public void updateIdentityTheme(String oldName, String name, String orgID, String ntheme,
                                   Function<CustomTheme, List<String>> fn, Principal principal)
   {
      Predicate<CustomTheme> assignable;

      if(orgID == null) {
         OrganizationManager orgManager = OrganizationManager.getInstance();
         String currentOrgID = orgManager.getCurrentOrgID();
         boolean globalThemes = !SUtil.isMultiTenant() || orgManager.isSiteAdmin(principal);
         assignable = theme -> Tool.isEmptyString(theme.getOrgID()) ?
            globalThemes : theme.getOrgID().equals(currentOrgID);
      }
      else {
         assignable = theme -> theme.isIdentityOrganization(orgID);
      }

      updateIdentityTheme(oldName, name, orgID, ntheme, fn, assignable);
   }

   /**
    * @param assignable the themes that the identity may be assigned to or removed from, a
    *                   subset of the themes that can refer to the identity.
    */
   private void updateIdentityTheme(String oldName, String name, String orgID, String ntheme,
                                    Function<CustomTheme, List<String>> fn,
                                    Predicate<CustomTheme> assignable)
   {
      // nothing to rename or select, so skip the themes lock and read
      if(oldName == null || name == null || ntheme == null && oldName.equals(name)) {
         return;
      }

      customThemesManager.updateCustomThemes(themes -> {
         // the default theme id selects the default theme like an empty string
         String selected = CustomTheme.isReservedId(ntheme) ? "" : ntheme;

         if(!Tool.isEmptyString(selected) && themes.stream().noneMatch(
            theme -> ntheme.equals(theme.getId()) && theme.isIdentityOrganization(orgID) &&
               assignable.test(theme)))
         {
            LOG.warn("Ignoring theme {} for {} because it cannot be assigned to organization {}",
                     ntheme, name, orgID);
            selected = null;
         }

         boolean changed = false;

         for(CustomTheme theme : themes) {
            if(!theme.isIdentityOrganization(orgID)) {
               continue;
            }

            List<String> identities = fn.apply(theme);
            boolean renamed = !oldName.equals(name) && identities.contains(oldName);
            boolean assigned = selected == null || !assignable.test(theme) ?
               identities.contains(oldName) || identities.contains(name) :
               selected.equals(theme.getId());

            // an identity is assigned to at most one theme, so selecting a theme (or the default
            // theme) removes the identity from the theme that was previously selected
            if(renamed || assigned != identities.contains(name)) {
               identities.removeIf(identity -> identity.equals(oldName) || identity.equals(name));

               if(assigned) {
                  identities.add(name);
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
