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
package inetsoft.uql.rest.datasource.nicereply;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1("apiKey")
})
public class NicereplyDataSource extends EndpointJsonDataSource<NicereplyDataSource> {
   public static final String TYPE = "Rest.Nicereply";

   public NicereplyDataSource() {
      super(TYPE, NicereplyDataSource.class);
      setURL("https://api.nicereply.com");
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "API Key", required = true, password = true)
   public String getApiKey() {
      return getPassword();
   }

   public void setApiKey(String apiKey) {
      setPassword(apiKey);
   }

   @Override
   protected String getTestSuffix() {
      return "/v1/users";
   }
}
