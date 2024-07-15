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
package inetsoft.web.admin.content.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.*;

@Value.Immutable
@JsonSerialize(as = ImmutableContentRepositoryTreeNode.class)
@JsonDeserialize(as = ImmutableContentRepositoryTreeNode.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface ContentRepositoryTreeNode {
   String label();

   // The path to get the correct permission for the corresponding resource.
   // If used in a different way/context, no guarantees can be made about what it represents
   // such as location or path within the repository tree
   String path();

   @Nullable
   String fullPath();

   @Nullable
   IdentityID owner();

   int type();

   @Nullable
   String icon();

   @Value.Default
   default boolean visible() {
      return true;
   }

   @Nullable
   @Value.Default
   default Boolean readOnly() {
      return null;
   }

   @Nullable
   @Value.Default
   default Boolean builtIn() {
      return null;
   }

   @Nullable
   String description();

   @Nullable
   String lastModifiedTime();

   @Nullable
   @Value.Default
   default Map<String, String> properties() {
      return new HashMap<>();
   }

   /**
    * If we pass the children here it will short circuit the lazy loading mechanism for the tree
    */
   @Nullable
   List<ContentRepositoryTreeNode> children();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableContentRepositoryTreeNode.Builder {
   }
}
