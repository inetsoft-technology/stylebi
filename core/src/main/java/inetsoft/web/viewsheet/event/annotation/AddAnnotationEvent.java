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
package inetsoft.web.viewsheet.event.annotation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;

@Value.Immutable
@Serial.Structural
@JsonDeserialize(builder = AddAnnotationEvent.Builder.class)
public interface AddAnnotationEvent extends Serializable {
   // HTML content
   String getContent();

   // annotation x position as offset from parent
   int getX();

   // annotation y position as offset from parent
   int getY();

   // row in data table
   int getRow();

   // col in data table
   int getCol();

   // name of the measure annotation is tied to (if data annotation)
   @Nullable
   String getMeasureName();

   // name of parent object
   @Nullable
   String getParent();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableAddAnnotationEvent.Builder {}
}
