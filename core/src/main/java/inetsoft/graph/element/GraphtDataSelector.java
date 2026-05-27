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
package inetsoft.graph.element;

import inetsoft.graph.data.DataSet;

import java.io.Serializable;

/**
 * This interface is the API for selecting data point to be plotted on the chart.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public interface GraphtDataSelector extends Serializable {
   /**
    * Return true if the row should be plotted.
    * @param fields if not null, the fields that are used in the caller's calculation.
    */
   boolean accept(DataSet data, int row, String[] fields);
}
