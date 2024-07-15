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
package inetsoft.report.filter;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.io.Serializable;
import java.util.Comparator;

/**
 * This comparator forces 'Others' to be the last on a list.
 *
 * @version 13.3, 7/16/2020
 * @author InetSoft Technology Corp
 */
public class OthersComparator implements Comparator, Serializable {
   @Override
   public int compare(Object o1, Object o2) {
      o1 = Tool.equals(o1, "Others") ? Catalog.getCatalog().getString("Others") : o1;
      o2 = Tool.equals(o2, "Others") ? Catalog.getCatalog().getString("Others") : o2;

      boolean other1 = others.equals(o1);
      boolean other2 = others.equals(o2);

      if(other1 != other2) {
         return other1 ? 1 : -1;
      }

      return 0;
   }

   private final String others = Catalog.getCatalog().getString("Others");
}
