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
package inetsoft.web.admin.query;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableQueryMonitoringTableModel.class)
@JsonDeserialize(as = ImmutableQueryMonitoringTableModel.class)
public interface QueryMonitoringTableModel {
   String id();
   String thread();
   String name();
   @Nullable String asset();
   String user();
   String age();
   @Nullable String rows();
   @Nullable String task();

   static QueryMonitoringTableModel.Builder builder() {
      return new QueryMonitoringTableModel.Builder();
   }

   class Builder extends ImmutableQueryMonitoringTableModel.Builder {
      public Builder from(QueryModel model) {
         id(model.id());
         thread(model.thread());
         name(model.name());
         asset(model.asset());
         user(model.user().getName());
         age(model.age());
         rows(model.rows());
         task(model.task());
         return this;
      }
   }
}
