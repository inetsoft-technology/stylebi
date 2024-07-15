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
package inetsoft.uql.rest.datasource.campaignmonitor;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;

@View(vertical = true, value = {
   @View1("URL"),
   @View1("apiKey")
})
public class CampaignMonitorDataSource extends EndpointJsonDataSource<CampaignMonitorDataSource> {
   public static final String TYPE = "Rest.CampaignMonitor";

   public CampaignMonitorDataSource() {
      super(TYPE, CampaignMonitorDataSource.class);
      setURL("https://api.createsend.com/api");
      setAuthType(AuthType.BASIC);
   }

   @Property(label = "API Key", required = true)
   public String getApiKey() {
      return getUser();
   }

   public void setApiKey(String apiKey) {
      setUser(apiKey);
   }

   @Override
   protected String getTestSuffix() {
      return "/v3.2/billingdetails.json";
   }
}
