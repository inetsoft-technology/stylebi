/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.uql.tabular.oauth;

import java.util.Objects;

public class CreateJobResponse {
   public String getAuthorizationPageUrl() {
      return authorizationPageUrl;
   }

   public void setAuthorizationPageUrl(String authorizationPageUrl) {
      this.authorizationPageUrl = authorizationPageUrl;
   }

   public String getDataUrl() {
      return dataUrl;
   }

   public void setDataUrl(String dataUrl) {
      this.dataUrl = dataUrl;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof CreateJobResponse)) {
         return false;
      }

      CreateJobResponse that = (CreateJobResponse) o;
      return Objects.equals(authorizationPageUrl, that.authorizationPageUrl) &&
         Objects.equals(dataUrl, that.dataUrl);
   }

   @Override
   public int hashCode() {
      return Objects.hash(authorizationPageUrl, dataUrl);
   }

   @Override
   public String toString() {
      return "CreateJobResponse{" +
         "authorizationPageUrl='" + authorizationPageUrl + '\'' +
         ", dataUrl='" + dataUrl + '\'' +
         '}';
   }

   private String authorizationPageUrl;
   private String dataUrl;
}
