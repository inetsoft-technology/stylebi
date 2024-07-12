/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.datasource.surveymonkey;

import inetsoft.uql.rest.json.AbstractEndpoint;

import java.util.*;

public class SurveyMonkeyEndpoint extends AbstractEndpoint {
   /**
    * @return per-endpoint page limit
    */
   public int getPageLimit() {
      return pageLimit;
   }

   public void setPageLimit(int pageLimit) {
      this.pageLimit = pageLimit;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      if(!super.equals(o)) return false;
      SurveyMonkeyEndpoint that = (SurveyMonkeyEndpoint) o;
      return pageLimit == that.pageLimit;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), pageLimit);
   }

   private int pageLimit = -1;
}
