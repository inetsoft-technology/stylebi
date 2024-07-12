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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableEditUserPaneModel.class)
@JsonDeserialize(as = ImmutableEditUserPaneModel.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface EditUserPaneModel extends EntityModel {
   @Value.Default
   default boolean status() {
      return true;
   }

   @Value.Default
   @Nullable
   default String alias() {
      return "";
   }

   @Value.Default
   @Nullable
   default String email() {
      return "";
   }

   @Value.Default
   @Nullable
   default String locale() {
      return "";
   }

   @Value.Default
   default String organization() {return Organization.getDefaultOrganizationName();}

   @Value.Derived
   @JsonIgnore
   default String oldIdentityKey() {
      return new IdentityID(oldName(), organization()).convertToKey();
   }

   @Value.Derived
   @JsonIgnore
   default String identityKey() {
      return new IdentityID(name(), organization()).convertToKey();
   }
   // Password is null when it is unchanged. The password should only be sent from the client to the server
   @Nullable
   String password();

   @Value.Default
   default boolean currentUser() {
      return false;
   }

   @Value.Default
   default List<String> localesList(){ return new ArrayList<>(); }

   static EditUserPaneModel.Builder builder() {
      return new EditUserPaneModel.Builder();
   }

   final class Builder extends ImmutableEditUserPaneModel.Builder {

   }
}
