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
package inetsoft.uql.rest.datasource.activecampaign;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("apiToken")
})
public class ActiveCampaignDataSource extends EndpointJsonDataSource<ActiveCampaignDataSource> {
   public static final String TYPE = "Rest.ActiveCampaign";

   public ActiveCampaignDataSource() {
      super(TYPE, ActiveCampaignDataSource.class);
      setURL("https://[Account Name].api-us1.com/api");
      setAuthType(AuthType.NONE);
   }

   @Property(label = "API Token", required = true)
   public String getApiToken() {
      HttpParameter param = getHttpParameter("Api-Token", HttpParameter.ParameterType.HEADER);
      return param != null ? param.getValue() : null;
   }

   public void setApiToken(String apiToken) {
      setHttpParameter("Api-Token", apiToken, HttpParameter.ParameterType.HEADER);
   }

   @Override
   protected String getTestSuffix() {
      return "/3/users/me";
   }
}