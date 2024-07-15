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
package inetsoft.uql.rest.datasource.jive;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("user"),
   @View1("password")
})
public class JiveDataSource extends EndpointJsonDataSource<JiveDataSource> {
   static final String TYPE = "Rest.Jive";
   
   public JiveDataSource() {
      super(TYPE, JiveDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "Jive Community URL", required = true)
   public String getURL() {
      return super.getURL();
   }

   @Override
   protected String getTestSuffix() {
      return "/api/core/v3/contents";
   }
}
