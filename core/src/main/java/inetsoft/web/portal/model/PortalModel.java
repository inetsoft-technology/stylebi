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
package inetsoft.web.portal.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutablePortalModel.class)
@JsonDeserialize(as = ImmutablePortalModel.class)
public abstract class PortalModel {
   public abstract CurrentUserModel currentUser();
   public abstract boolean helpVisible();
   public abstract boolean preferencesVisible();
   public abstract boolean logoutVisible();
   public abstract boolean homeVisible();
   public abstract String homeLink();
   public abstract boolean reportEnabled();
   public abstract boolean composerEnabled();
   public abstract boolean dashboardEnabled();
   public abstract boolean customLogo();
   public abstract String helpURL();
   public abstract String logoutUrl();
   public abstract boolean accessible();
   public abstract boolean hasDashboards();
   public abstract String title();
   public abstract boolean newWorksheetEnabled();
   public abstract boolean newViewsheetEnabled();
   public abstract boolean newDatasourceEnabled();
   public abstract boolean profile();
   public abstract boolean profiling();

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutablePortalModel.Builder {
   }
}
