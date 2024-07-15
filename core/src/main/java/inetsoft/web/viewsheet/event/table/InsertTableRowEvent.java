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
package inetsoft.web.viewsheet.event.table;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Class that encapsulates the parameters for appending/inserting table rows.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableInsertTableRowEvent.class)
@JsonDeserialize(builder = InsertTableRowEvent.Builder.class)
public abstract class InsertTableRowEvent implements BaseTableEvent {
   public abstract boolean insert();

   public abstract int row();

   public abstract int start();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableInsertTableRowEvent.Builder {
   }
}
