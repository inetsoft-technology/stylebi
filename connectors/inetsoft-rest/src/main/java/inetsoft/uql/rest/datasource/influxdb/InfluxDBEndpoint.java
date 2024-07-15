/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.uql.rest.json.AbstractEndpoint;

import java.util.Objects;

public class InfluxDBEndpoint extends AbstractEndpoint {
   public boolean isPost() {
      return post;
   }

   public void setPost(boolean post) {
      this.post = post;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      if(!super.equals(o)) return false;
      InfluxDBEndpoint that = (InfluxDBEndpoint) o;
      return post == that.post;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), post);
   }

   private boolean post;
}
