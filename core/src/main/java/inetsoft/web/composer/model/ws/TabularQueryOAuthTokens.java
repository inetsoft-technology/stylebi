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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.tabular.TabularView;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableTabularQueryOAuthTokens.class)
@JsonDeserialize(as = ImmutableTabularQueryOAuthTokens.class)
public interface TabularQueryOAuthTokens {
   String accessToken();
   String refreshToken();
   String issued();
   @Nullable String expiration();
   String scope();
   Map<String, String> properties();
   TabularView view();
   String method();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableTabularQueryOAuthTokens.Builder {
   }
}
