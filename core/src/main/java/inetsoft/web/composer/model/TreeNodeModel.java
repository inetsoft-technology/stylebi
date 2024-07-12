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
package inetsoft.web.composer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.*;

@Value.Immutable
@JsonSerialize(as = ImmutableTreeNodeModel.class)
@JsonDeserialize(as = ImmutableTreeNodeModel.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface TreeNodeModel extends Comparable<TreeNodeModel> {
   @Nullable String label();

   @Nullable
   String baseLabel();

   @Nullable
   String alias();

   @Nullable
   String organization();

   @Nullable
   Object data();

   @Nullable String dataLabel();

   @Nullable String icon();

   @Nullable String expandedIcon();

   @Nullable String collapsedIcon();

   @Nullable String toggleExpandedIcon();

   @Nullable String toggleCollapsedIcon();

   @Nullable String tooltip();

   @Value.Default
   default boolean leaf() {
      return false;
   }

   @Value.Default
   default List<TreeNodeModel> children() {
      return new ArrayList<>();
   }

   @Value.Default
   default boolean expanded() {
      return false;
   }

   @Nullable String dragName();

   @Nullable String dragData();

   @Nullable String type();

   @Nullable String cssClass();

   @Value.Default
   default boolean disabled() {
      return false;
   }

   @Value.Default
   default boolean materialized() {
      return false;
   }

   @Override
   default int compareTo(TreeNodeModel node) {
      return Comparator.comparing(
         TreeNodeModel::label, Comparator.nullsFirst(Comparator.naturalOrder()))
         .compare(this, node);
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableTreeNodeModel.Builder {
   }
}
