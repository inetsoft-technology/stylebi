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
package inetsoft.web.admin.viewsheet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.composition.WorksheetEngine;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import java.io.Serializable;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableViewsheetThreadModel.class)
@JsonDeserialize(as = ImmutableViewsheetThreadModel.class)
public interface ViewsheetThreadModel extends Serializable {
   String id();
   long startTime();

   @Value.Derived
   default long age() {
      return System.currentTimeMillis() - startTime();
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableViewsheetThreadModel.Builder {
      public Builder from(WorksheetEngine.ThreadDef thread) {
         id(Long.toString(thread.getThread().getId()));
         startTime(thread.getStartTime());
         return this;
      }
   }
}
