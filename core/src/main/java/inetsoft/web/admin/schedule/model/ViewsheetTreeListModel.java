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
package inetsoft.web.admin.schedule.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.RepositoryEntry;
import inetsoft.util.Catalog;
import org.immutables.value.Value;

import java.util.Arrays;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableViewsheetTreeListModel.class)
@JsonDeserialize(as = ImmutableViewsheetTreeListModel.class)
public interface ViewsheetTreeListModel {
   List<ViewsheetTreeModel> nodes();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableViewsheetTreeListModel.Builder {
      public Builder from(RepositoryEntry[] entries, Catalog catalog, String user) {
         Arrays.stream(entries)
            .map(e -> ViewsheetTreeModel.builder().from(e, null, catalog, user).build())
            .forEach(this::addNodes);
         return this;
      }
   }
}
