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
package inetsoft.web.admin.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Catalog;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableNameLabelTuple.class)
@JsonDeserialize(as = ImmutableNameLabelTuple.class)
public interface NameLabelTuple {
   String name();
   String label();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableNameLabelTuple.Builder {
      public Builder from(String value, Catalog catalog) {
         name(value);
         label(catalog.getString(value));
         return this;
      }
   }
}
