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
package inetsoft.web.composer.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import inetsoft.uql.asset.AssetEntry;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonDeserialize(builder = LoadAssetTreeNodesEvent.Builder.class)
public abstract class LoadAssetTreeNodesEvent {
   @Nullable
   public abstract AssetEntry targetEntry();

   @Value.Default
   public List<LoadAssetTreeNodesEvent> expandedDescendants() {
      return new ArrayList<>();
   }

   @Nullable
   public abstract String[] path();

   @Value.Default
   public int index() { return 0; }

   @Value.Default
   public int scope() { return 0; }

   @Value.Default
   public boolean loadAll() { return false; }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableLoadAssetTreeNodesEvent.Builder {
   }
}
