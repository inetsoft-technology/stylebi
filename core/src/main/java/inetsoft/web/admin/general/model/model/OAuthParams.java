/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.admin.general.model.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableOAuthParams.class)
@JsonDeserialize(as = ImmutableOAuthParams.class)
public interface OAuthParams {
   String license();
   @Nullable
   String user();
   @Nullable String password();
   @Nullable String clientId();
   @Nullable String clientSecret();
   @Nullable
   List<String> scope();
   @Nullable String authorizationUri();
   @Nullable String tokenUri();
   @Nullable
   Set<String> flags();
   @Nullable
   String method();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableOAuthParams.Builder {
   }
}
