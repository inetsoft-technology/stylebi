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

   /**
    * How a paged endpoint is read: {@link #PAGE_OFFSET} for the list endpoints that take
    * {@code limit} and {@code offset}, {@link #PAGE_LINK} for the one that hands back a
    * {@code links.next} URL (tasks). Only meaningful when the endpoint is {@code paged}.
    */
   public int getPageType() {
      return pageType;
   }

   public void setPageType(int pageType) {
      this.pageType = pageType;
   }

   /**
    * For {@link #PAGE_OFFSET} endpoints, the response property that holds the page's list, such as
    * {@code buckets}. A page with none left in it is the last one.
    */
   public String getPageItems() {
      return pageItems;
   }

   public void setPageItems(String pageItems) {
      this.pageItems = pageItems;
   }

   /**
    * True for the InfluxQL query endpoint, whose response is a columns array plus rows of arrays.
    */
   public boolean isInfluxql() {
      return influxql;
   }

   public void setInfluxql(boolean influxql) {
      this.influxql = influxql;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      if(!super.equals(o)) return false;
      InfluxDBEndpoint that = (InfluxDBEndpoint) o;
      return post == that.post && pageType == that.pageType && influxql == that.influxql &&
         Objects.equals(pageItems, that.pageItems);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), post, pageType, pageItems, influxql);
   }

   public static final int PAGE_OFFSET = 1;
   public static final int PAGE_LINK = 2;

   private boolean post;
   private int pageType;
   private String pageItems;
   private boolean influxql;
}
