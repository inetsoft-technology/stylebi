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
package inetsoft.web.vswizard.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import org.immutables.value.Value;

/**
 * convert column to dimension or measure
 */
@Value.Immutable
@JsonSerialize(as = ImmutableConvertColumnEvent.class)
@JsonDeserialize(as = ImmutableConvertColumnEvent.class)
public interface ConvertColumnEvent extends ViewsheetEvent {
   /**
    * get the current entry
    * @return
    */
   AssetEntry currentEntry();

   /**
    * Get the name of the column wanted to convert.
    * @return the name of the column wanted to convert.
    */
   String[] columnNames();

   /**
    * Get the conversion type.
    * @return the conversion type.
    */
   int changeType();

   /**
    *  get the table name
    * @return
    */
   String tableName();
}
