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
package inetsoft.web.admin.presentation.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.model.FileData;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Objects;

@Value.Immutable
@JsonSerialize(as = ImmutableCustomThemeModel.class)
@JsonDeserialize(as = ImmutableCustomThemeModel.class)
public interface CustomThemeModel {
   String id();
   String name();
   @Nullable Boolean global();
   @Nullable Boolean defaultTheme();
//   @Nullable List<String> users();
//   @Nullable List<String> groups();
//   @Nullable List<String> roles();
   @Nullable FileData jar();
   @Nullable ThemeCssModel portalCss();
   @Nullable ThemeCssModel emCss();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableCustomThemeModel.Builder {
      public Builder from(CustomTheme theme) {
         id(theme.getId());
         name(theme.getName());
         global(theme.getOrgID() == null);
//         users(theme.getUsers());
//         groups(theme.getGroups());
//         roles(theme.getRoles());

         String selected = CustomThemesManager.getManager().getSelectedTheme();

         boolean isSiteAdmin = true;

         XPrincipal principal = (XPrincipal) ThreadContext.getContextPrincipal();

         if(principal != null) {
            isSiteAdmin = OrganizationManager.getInstance().isSiteAdmin(principal);
         }

         if(isSiteAdmin) {
            defaultTheme(Objects.equals(selected, theme.getId()));
         }
         else {
            defaultTheme(theme.getOrganizations().contains(OrganizationManager.getCurrentOrgName()));
         }

         return this;
      }
   }
}
