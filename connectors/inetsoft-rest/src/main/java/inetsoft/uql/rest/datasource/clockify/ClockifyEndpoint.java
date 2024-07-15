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
package inetsoft.uql.rest.datasource.clockify;

import inetsoft.uql.rest.json.AbstractEndpoint;

import java.util.Objects;

public class ClockifyEndpoint extends AbstractEndpoint {
   public boolean isPost() {
      return post;
   }

   public void setPost(boolean post) {
      this.post = post;
   }

   public String getUrl() {
      return url;
   }

   public void setUrl(String url) {
      this.url = url;
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
      ClockifyEndpoint endpoint = (ClockifyEndpoint) o;
      return post == endpoint.post &&
         Objects.equals(url, endpoint.url) &&
         Objects.equals(bodyTemplate, endpoint.bodyTemplate);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), post, url, bodyTemplate);
   }

   private boolean post;
   private String url;
   private String bodyTemplate;
}
