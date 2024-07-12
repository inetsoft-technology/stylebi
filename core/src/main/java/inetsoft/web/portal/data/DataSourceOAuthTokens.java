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
package inetsoft.web.portal.data;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableDataSourceOAuthTokens.Builder.class)
@JsonDeserialize(builder = DataSourceOAuthTokens.Builder.class)
public interface DataSourceOAuthTokens {
   @Nullable String accessToken();
   @Nullable String refreshToken();
   @Nullable String issued();
   @Nullable String expiration();
   @Nullable String scope();
   @JsonAnyGetter
   @AllowNulls
   Map<String, Object> properties();
   DataSourceDefinition dataSource();
   List<String> restricted_to();
   String method();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableDataSourceOAuthTokens.Builder {
   }
}
