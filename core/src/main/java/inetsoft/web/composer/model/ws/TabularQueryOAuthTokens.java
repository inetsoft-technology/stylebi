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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.tabular.TabularView;
import inetsoft.web.viewsheet.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableTabularQueryOAuthTokens.class)
@JsonDeserialize(as = ImmutableTabularQueryOAuthTokens.class)
// The browser OAuth flow posts the whole authorization result, which carries extra fields such as
// "complete", and the password-grant flow omits issued/properties. Mirror DataSourceOAuthTokens.
@JsonIgnoreProperties(ignoreUnknown = true)
public interface TabularQueryOAuthTokens {
   @Nullable String accessToken();
   @Nullable String refreshToken();
   @Nullable String issued();
   @Nullable String expiration();
   @Nullable String scope();
   @AllowNulls
   Map<String, Object> properties();
   TabularView view();
   String method();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableTabularQueryOAuthTokens.Builder {
   }
}
