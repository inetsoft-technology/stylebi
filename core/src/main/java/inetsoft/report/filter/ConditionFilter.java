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

import inetsoft.report.TableLens;

/**
 * ConditionFilter is a TableFilter which will apply conditions on underlying
 * table.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class ConditionFilter extends AbstractConditionFilter implements Cloneable {
   /**
    * Create a ConditionFilter. The conditions are used for filter.
    * @param table table.
    * @param conditions the condition group used for filter.
    */
   public ConditionFilter(TableLens table, ConditionGroup conditions) {
      setTable(table);
      this.conditions = conditions;
   }

   /**
    * Evaluate a row to check of all conditions are met.
    */
   @Override
   protected boolean checkCondition(int r) {
      return conditions.evaluate(getTable(), r);
   }

   @Override
   public ConditionFilter clone() {
      ConditionFilter table = (ConditionFilter) super.clone();
      table.conditions = conditions.clone();
      return table;
   }

   protected ConditionGroup conditions;
}
