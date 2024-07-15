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
import inetsoft.report.internal.Util;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.QueryInfo;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Data transfer object that represents the {@link QueryModel} for the
 * Viewsheet Monitoring page
 */

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableQueryModel.class)
@JsonDeserialize(as = ImmutableQueryModel.class)
public interface QueryModel extends Serializable {
   String id();
   String thread();
   String name();
   @Nullable String asset();
   @Nullable
   IdentityID user();
   String age();
   @Nullable String rows();
   @Nullable String task();

   static QueryModel.Builder builder() {
      return new QueryModel.Builder();
   }

   class Builder extends ImmutableQueryModel.Builder {
      public Builder from(QueryInfo info, boolean includeRowCount) {
         id(info.getId());
         thread(info.getThreadId());
         name(info.getName());
         asset(info.getAsset());
         user(info.getUser());
         age(Util.formatAge(info.getDateCreated(), false));
         rows(includeRowCount ? Integer.toString(info.getRowCount()) : null);
         task(info.getTask());
         return this;
      }
   }
}
