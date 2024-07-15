/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableSAMLAttributesModel.class)
@JsonDeserialize(as = ImmutableSAMLAttributesModel.class)
public interface SAMLAttributesModel {
   @Nullable String spEntityId();
   @Nullable String assertionUrl();
   @Nullable String idpEntityId();
   @Nullable String idpSignOnUrl();
   @Nullable String idpLogoutUrl();
   @Nullable String idpPublicKey();
   @Nullable String roleClaim();
   @Nullable String groupClaim();
   @Nullable String orgIDClaim();

   class Builder extends ImmutableSAMLAttributesModel.Builder {
   }
}
