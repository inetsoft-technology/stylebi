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
package inetsoft.uql.rest.datasource.fortytwomatters;

import inetsoft.uql.rest.json.AbstractEndpoint;

import java.util.Objects;

public final class FortyTwoMattersEndpoint extends AbstractEndpoint {
   public String getPagePath() {
      return pagePath;
   }

   public void setPagePath(String pagePath) {
      this.pagePath = pagePath;
   }

   public int getPageLimit() {
      return pageLimit;
   }

   public void setPageLimit(int pageLimit) {
      this.pageLimit = pageLimit;
   }

   public int getFreePageLimit() {
      return freePageLimit;
   }

   public void setFreePageLimit(int freePageLimit) {
      this.freePageLimit = freePageLimit;
   }

   public boolean isPost() {
      return post;
   }

   public void setPost(boolean post) {
      this.post = post;
   }

   public String getBodyTemplate() {
      return bodyTemplate;
   }

   public void setBodyTemplate(String bodyTemplate) {
      this.bodyTemplate = bodyTemplate;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      if(!super.equals(o)) return false;
      FortyTwoMattersEndpoint that = (FortyTwoMattersEndpoint) o;
      return pageLimit == that.pageLimit &&
         freePageLimit == that.freePageLimit &&
         post == that.post &&
         Objects.equals(pagePath, that.pagePath) &&
         Objects.equals(bodyTemplate, that.bodyTemplate);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), pagePath, pageLimit, freePageLimit, post, bodyTemplate);
   }

   private String pagePath;
   private int pageLimit;
   private int freePageLimit;
   private boolean post;
   private String bodyTemplate;
}
