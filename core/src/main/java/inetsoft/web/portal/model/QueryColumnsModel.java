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
package inetsoft.web.portal.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object that represents the {@link QueryColumnsModel} for the
 * email address dialog.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableQueryColumnsModel.class)
@JsonDeserialize(as = ImmutableQueryColumnsModel.class)
public abstract class QueryColumnsModel {
   @Value.Default
   public List<String> columns() {
      return new ArrayList<>();
   }

   @Value.Default
   public List<String> columnLabels() {
      return new ArrayList<>();
   }

   public static QueryColumnsModel.Builder builder() {
      return new QueryColumnsModel.Builder();
   }

   public static class Builder extends ImmutableQueryColumnsModel.Builder {
   }
}
