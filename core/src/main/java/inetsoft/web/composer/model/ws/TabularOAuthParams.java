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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableTabularOAuthParams.class)
@JsonDeserialize(as = ImmutableTabularOAuthParams.class)
public interface TabularOAuthParams {
   String license();
   @Nullable String user();
   @Nullable String password();
   @Nullable String clientId();
   @Nullable String clientSecret();
   @Nullable List<String> scope();
   @Nullable String authorizationUri();
   @Nullable String tokenUri();
   @Nullable Set<String> flags();
   @Nullable String error();

   /**
    * The name of the data source that a password grant is requested for.
    */
   @Nullable String dataSourceName();

   /**
    * The name that the data source of a password grant is saved under, if it is renamed.
    */
   @Nullable String dataSourceOldName();

   /**
    * The folder of the data source that a password grant is requested for.
    */
   @Nullable String dataSourceParentPath();

   /**
    * The name of the parent data source, if a password grant is requested for an additional
    * connection.
    */
   @Nullable String parentDataSource();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableTabularOAuthParams.Builder {
   }
}
