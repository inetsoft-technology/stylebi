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
package inetsoft.uql.tabular.oauth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableTokens.class)
public interface Tokens {
   @Nullable String accessToken();
   @Nullable String refreshToken();
   long issued();
   long expiration();
   @Nullable String scope();
   @Nullable
   @AllowNulls
   Map<String, Object> properties();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableTokens.Builder {
      public Builder issued(String value) {
         if(value == null) {
            return issued(0L);
         }

         Instant instant = Instant.parse(value);
         return issued(instant.toEpochMilli());
      }

      public Builder expiration(String value) {
         if(value == null) {
            return expiration(0L);
         }

         Instant instant = Instant.parse(value);
         return expiration(instant.toEpochMilli());
      }
   }
}
