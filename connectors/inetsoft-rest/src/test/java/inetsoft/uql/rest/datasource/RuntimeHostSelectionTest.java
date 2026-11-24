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
package inetsoft.uql.rest.datasource;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.datasource.mixpanel.MixpanelDataSource;
import inetsoft.uql.rest.datasource.mixpanel.MixpanelQuery;
import inetsoft.uql.rest.datasource.mixpanel.MixpanelRuntime;
import inetsoft.uql.rest.datasource.twilio.TwilioDataSource;
import inetsoft.uql.rest.datasource.twilio.TwilioQuery;
import inetsoft.uql.rest.datasource.twilio.TwilioRuntime;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mixpanel and Twilio choose the host of a request from the endpoint in their own runtime. The
 * expected hosts are from the vendors' API references: Mixpanel's data pipelines API is on
 * data.mixpanel.com and the rest of the query API on mixpanel.com; Twilio's Monitor API
 * (alerts and events) is on monitor.twilio.com and the REST API on api.twilio.com.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RuntimeHostSelectionTest {
   private static void fill(EndpointJsonQuery<?> query, String endpoint) {
      query.setJsonPath("$");
      query.setEndpoint(endpoint);
      RestParameters parameters = query.getParameters();

      for(RestParameter parameter : parameters.getParameters()) {
         if(parameter.isRequired()) {
            parameter.setValue("1");
         }
      }

      query.setParameters(parameters);
   }

   private static String hostOf(String url) {
      return URI.create(url).getHost();
   }

   @Test
   void mixpanelPipelineEndpointsUseTheDataHostAndTheRestUseTheMainHost() throws Exception {
      Map<String, String> hosts = new TreeMap<>();

      for(String name : new MixpanelQuery().getEndpointMap().keySet()) {
         MixpanelDataSource dataSource = new MixpanelDataSource();
         dataSource.setApiSecret("secret");
         MixpanelQuery query = new MixpanelQuery();
         query.setDataSource(dataSource);
         fill(query, name);
         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         new MixpanelRuntime() {
            @Override
            protected QueryRunner getQueryRunner(AbstractRestQuery q) {
               JsonTransformer transformer = new JsonTransformer();
               return new EndpointJsonQueryRunner(
                  (EndpointJsonQuery<?>) q, new JsonRestDataIteratorStrategyFactory(transformer, new HttpResponseParameterParser(transformer), () -> http), new LookupService(),
                  transformer);
            }
         }.runQuery(query, new VariableTable());
         hosts.put(name, http.requests().isEmpty() ? "(no request)" : hostOf(http.requests().get(0)));
      }

      Map<String, String> expected = new TreeMap<>();

      for(String name : hosts.keySet()) {
         expected.put(name, name.equals("Scheduled Pipelines") ? "data.mixpanel.com" :
                      "mixpanel.com");
      }

      assertEquals(expected, hosts);
   }

   @Test
   void twilioMonitorEndpointsUseTheMonitorHostAndTheRestUseTheApiHost() throws Exception {
      Map<String, String> hosts = new TreeMap<>();

      for(String name : new TwilioQuery().getEndpointMap().keySet()) {
         TwilioDataSource dataSource = new TwilioDataSource();
         dataSource.setUser("AC123");
         dataSource.setPassword("token");
         TwilioQuery query = new TwilioQuery();
         query.setDataSource(dataSource);
         fill(query, name);
         ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, "{}");
         new TwilioRuntime() {
            @Override
            protected QueryRunner getQueryRunner(AbstractRestQuery q) {
               JsonTransformer transformer = new JsonTransformer();
               return new EndpointJsonQueryRunner(
                  (EndpointJsonQuery<?>) q, new JsonRestDataIteratorStrategyFactory(transformer, new HttpResponseParameterParser(transformer), () -> http), new LookupService(),
                  transformer);
            }
         }.runQuery(query, new VariableTable());
         hosts.put(name, http.requests().isEmpty() ? "(no request)" : hostOf(http.requests().get(0)));
      }

      Map<String, String> expected = new TreeMap<>();

      for(String name : hosts.keySet()) {
         expected.put(name, Set.of("Alert", "Alerts", "Event", "Events").contains(name) ?
                      "monitor.twilio.com" : "api.twilio.com");
      }

      assertEquals(expected, hosts);
   }
}
