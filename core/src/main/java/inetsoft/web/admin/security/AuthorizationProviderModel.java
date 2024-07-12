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
package inetsoft.web.admin.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.AuthorizationProvider;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableAuthorizationProviderModel.class)
@JsonDeserialize(as = ImmutableAuthorizationProviderModel.class)
public abstract class AuthorizationProviderModel {
   public abstract String providerName();

   @JsonProperty("providerType")
   public abstract SecurityProviderType providerType();

   @Nullable
   public abstract CustomProviderModel customProviderModel();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableAuthorizationProviderModel.Builder {
      public Builder customProviderModel(AuthorizationProvider provider, ObjectMapper mapper) {
         String jsonConfiguration;

         try {
            JsonNode configNode = provider.writeConfiguration(mapper);
            jsonConfiguration =
               mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configNode);
         }
         catch(JsonProcessingException e) {
            throw new RuntimeException("Failed to write configuration", e);
         }

         return customProviderModel(
            CustomProviderModel.builder()
               .className(provider.getClass().getName())
               .jsonConfiguration(jsonConfiguration)
               .build()
         );
      }
   }
}
