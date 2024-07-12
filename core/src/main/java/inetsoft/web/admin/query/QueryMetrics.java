/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.query;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.List;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableQueryMetrics.class)
@JsonDeserialize(as = ImmutableQueryMetrics.class)
public interface QueryMetrics extends Serializable {
   int count();
   List<QueryModel> queries();

   /**
    * This is a rotating list of the number executed rows, sampled every 5 seconds.
    */
   List<Integer> executedRows();

   @Value.Derived
   default int throughput() {
      if(executedRows().isEmpty()) {
         return 0;
      }

      int total = executedRows().stream().mapToInt(Integer::intValue).sum();
      return Math.round(total / (executedRows().size() * 5F));
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableQueryMetrics.Builder {
   }
}
