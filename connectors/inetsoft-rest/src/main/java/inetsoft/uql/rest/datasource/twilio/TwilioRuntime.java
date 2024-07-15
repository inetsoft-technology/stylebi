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
package inetsoft.uql.rest.datasource.twilio;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.uql.tabular.TabularQuery;

import java.util.*;

public class TwilioRuntime extends EndpointJsonRuntime {
   @Override
   public XTableNode runQuery(TabularQuery tabularQuery, VariableTable params) {
      TwilioQuery query = (TwilioQuery) tabularQuery.clone();
      TwilioDataSource dataSource = (TwilioDataSource) query.getDataSource().clone();
      query.setDataSource(dataSource);

      if(monitorEndpoints.contains(query.getEndpoint())) {
         dataSource.setMonitorRequest(true);
      }

      return super.runQuery(query, params);
   }

   private static final Set<String> monitorEndpoints = new HashSet<>(Arrays.asList(
      "Alert", "Alerts", "Event", "Events"
   ));
}
