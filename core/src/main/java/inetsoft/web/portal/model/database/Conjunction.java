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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonTypeName;

@SuppressWarnings({ "unused", "WeakerAccess" })
@JsonTypeName("conjunction")
public class Conjunction extends DataConditionItem {
   public Conjunction() {
      super("conjunction");
   }

   public boolean isIsNot() {
      return isNot;
   }

   public void setIsNot(boolean isNot) {
      this.isNot = isNot;
   }

   public String getConjunction() {
      return conjunction;
   }

   public void setConjunction(String conjunction) {
      this.conjunction = conjunction;
   }

   private boolean isNot;
   private String conjunction;
   private boolean junc = true;
}
