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
package inetsoft.web.binding.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import org.immutables.value.Value;

/**
 * Class that encapsulates the parameters for convert table ref event.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableConvertTableRefEvent.class)
@JsonDeserialize(as = ImmutableConvertTableRefEvent.class)
public interface ConvertTableRefEvent extends ViewsheetEvent {
   /**
    * Get the name of the ref wanted to convert.
    * @return the name of the ref wanted to convert.
    */
   String[] refNames();

   /**
    * Get the conversion type.
    * @return the conversion type.
    */
   int convertType();

   /**
    * Get the source info.
    * @return the source info.
    */
   SourceInfo source();

   /**
    * Check if is source change.
    * @return <tt>true</tt> if source change, <tt>false</tt> otherwise.
    */
   boolean sourceChange();

   /**
    * Get the table name.
    * @return table name.
    */
   String table();
}
