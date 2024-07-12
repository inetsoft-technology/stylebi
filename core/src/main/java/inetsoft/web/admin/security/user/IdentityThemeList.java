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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.portal.CustomTheme;
import org.immutables.value.Value;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Value.Immutable
@JsonSerialize(as = ImmutableIdentityThemeList.class)
@JsonDeserialize(as = ImmutableIdentityThemeList.class)
public interface IdentityThemeList {
   List<IdentityTheme> themes();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableIdentityThemeList.Builder {
      Builder from(Collection<CustomTheme> themes) {
         return this.themes(themes.stream()
                               .map(t -> IdentityTheme.builder().from(t).build())
                               .collect(Collectors.toList()));
      }
   }
}
