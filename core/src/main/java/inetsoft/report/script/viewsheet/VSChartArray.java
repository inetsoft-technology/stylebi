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
package inetsoft.report.script.viewsheet;

import inetsoft.report.script.AbstractChartArray;

/**
 * This represents an array of viewsheet chart styles, axises in a chart info.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */

public abstract class VSChartArray extends AbstractChartArray {
   /**
    * Constructor.
    * @param property property name, e.g. Object.
    * @param property type, e.g. Object.class.
    */
   public VSChartArray(String property, Class pType) {
      super(property, pType);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "VSChartArray";
   }
}
