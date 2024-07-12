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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.Property;

public class RestJsonDataSource extends AbstractRestDataSource<RestJsonDataSource> {
   public static final String TYPE = "Rest"; // Not Rest.JSON for backwards compatibility.

   public RestJsonDataSource() {
      super(TYPE, RestJsonDataSource.class);
   }

   @Property(label="URL", required = true, checkEnvVariables = true)
   @Override
   public String getURL() {
      return super.getURL();
   }

   @Property(label = "Authorization URI", required = true)
   @Override
   public String getAuthorizationUri() {
      return super.getAuthorizationUri();
   }

   @Property(label = "Token URI", required = true)
   @Override
   public String getTokenUri() {
      return super.getTokenUri();
   }

   @Property(label = "Scope", required = true)
   @Override
   public String getScope() {
      return super.getScope();
   }

   @Property(label = "OAuth Flags")
   @Override
   public String getOauthFlags() {
      return super.getOauthFlags();
   }
}
