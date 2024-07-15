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
package inetsoft.web.binding.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Class that encapsulates the parameters for convert chart ref event.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableChangeGeographicEvent.class)
@JsonDeserialize(builder = ImmutableChangeGeographicEvent.Builder.class)
public interface ChangeGeographicEvent extends ViewsheetEvent {
   /**
    * @return the name of the ref wanted to convert.
    */
   @Nullable
   String refName();

   /**
    * @return if the ref is a dimension
    */
   boolean isDim();

   /**
    * @return the conversion type.
    */
   String type();

   /**
    * @return the chart binding model
    */
   ChartBindingModel binding();

   /**
    * @return the table name.
    */
   String table();
}
