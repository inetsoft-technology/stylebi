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
package inetsoft.web.admin.general.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.graph.geo.service.MapboxStyle;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableWebMapSettingsModel.class)
@JsonDeserialize(as = ImmutableWebMapSettingsModel.class)
public interface WebMapSettingsModel {
   @Nullable
   String service();

   @Nullable
   String mapboxUser();

   @Nullable
   String mapboxToken();

   @Nullable
   String mapboxStyle();

   @Nullable
   String googleKey();

   boolean defaultOn();

   @Nullable
   List<MapboxStyle> mapboxStyles();

   static WebMapSettingsModel.Builder builder() {
      return new WebMapSettingsModel.Builder();
   }

   final class Builder extends ImmutableWebMapSettingsModel.Builder {
   }
}
