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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.util.List;

/**
 * DateComparisonPeriods, periods for date comparison.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public interface DateComparisonPeriods extends Cloneable, XMLSerializable {
   static DateComparisonPeriods createDateComparisonPeriods(Element element)
      throws Exception
   {
      String cls = Tool.getAttribute(element, "class");

      if(Tool.isEmptyString(cls)) {
         return null;
      }

      DateComparisonPeriods dateComparisonPeriods = (DateComparisonPeriods) Class.forName(cls).newInstance();
      dateComparisonPeriods.parseXML(element);

      return dateComparisonPeriods;
   }

   DateComparisonPeriods clone();

   List<DynamicValue> getDynamicValues();

   /**
    * Get a text description of the comparison periods for displaying.
    */
   String getDescription();
}
