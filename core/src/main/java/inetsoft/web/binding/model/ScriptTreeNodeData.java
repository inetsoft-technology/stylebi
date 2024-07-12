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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Script tree node model for formula editor column tree.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableScriptTreeNodeData.class)
@JsonDeserialize(as = ImmutableScriptTreeNodeData.class)
public interface ScriptTreeNodeData {
   @Nullable String expression();

   @Nullable String parentName();

   @Nullable String parentLabel();

   @Nullable Object parentData();

   @Nullable String name();

   @Nullable String suffix();

   @Nullable String useragg();

   @Nullable Object data();

   @Nullable Boolean component();

   @Nullable List<String> fields();

   @Value.Default
   default boolean dot() {
      return false;
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableScriptTreeNodeData.Builder {
   }
}
