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

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.datasource.copper.CopperDataSource;
import inetsoft.uql.rest.datasource.freshsales.FreshsalesDataSource;
import inetsoft.uql.rest.datasource.gosquared.GoSquaredDataSource;
import inetsoft.uql.rest.datasource.harvest.HarvestDataSource;
import inetsoft.uql.rest.datasource.mailchimp.MailchimpDataSource;
import inetsoft.uql.rest.datasource.pipedrive.PipedriveDataSource;
import inetsoft.uql.rest.datasource.pipelinecrm.PipelineCRMDataSource;
import inetsoft.uql.rest.datasource.prometheus.PrometheusDataSource;
import inetsoft.uql.rest.datasource.square.SquareDataSource;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Headers, query parameters and hosts that the vendors' API references prescribe.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class VendorHeadersAndUrlsTest {
   private static Map<String, String> params(HttpParameter[] parameters, HttpParameter.ParameterType type) {
      Map<String, String> map = new LinkedHashMap<>();

      for(HttpParameter p : parameters) {
         if(p.getType() == type) {
            map.put(p.getName(), p.getValue());
         }
      }

      return map;
   }

   @Test
   void freshsalesUsesTheTokenSchemeOnTheDomain() {
      FreshsalesDataSource ds = new FreshsalesDataSource();
      ds.setDomain("acme");
      ds.setApiKey("fs-key");
      ds.setDomainSuffix(".freshsales.io");
      assertEquals("Token token=fs-key",
                   params(ds.getQueryHttpParameters(), HttpParameter.ParameterType.HEADER)
                      .get("Authorization"));
      assertEquals("https://acme.freshsales.io/api", ds.getURL());
   }

   @Test
   void harvestSendsTheAccountIdBearerTokenAndAUserAgent() {
      HarvestDataSource ds = new HarvestDataSource();
      ds.setAccountId("12345");
      ds.setAccessToken("hv-token");
      ds.setTokenExpiration(System.currentTimeMillis() + 3600000L);
      Map<String, String> headers = params(ds.getQueryHttpParameters(),
                                            HttpParameter.ParameterType.HEADER);
      assertEquals("12345", headers.get("Harvest-Account-ID"));
      assertEquals("Bearer hv-token", headers.get("Authorization"));
      assertEquals(true, headers.containsKey("User-Agent"));
   }

   @Test
   void copperSendsTheAccessTokenApplicationAndUserEmailHeaders() {
      CopperDataSource ds = new CopperDataSource();
      ds.setApiKey("cp-key");
      ds.setUserEmail("me@example.com");
      Map<String, String> headers = params(ds.getQueryHttpParameters(),
                                            HttpParameter.ParameterType.HEADER);
      assertEquals("cp-key", headers.get("X-PW-AccessToken"));
      assertEquals("me@example.com", headers.get("X-PW-UserEmail"));
      assertEquals("developer_api", headers.get("X-PW-Application"));
   }

   @Test
   void goSquaredSendsTheKeyAndSiteTokenAsQueryParameters() {
      GoSquaredDataSource ds = new GoSquaredDataSource();
      ds.setApiKey("gs-key");
      ds.setSiteToken("GSN-1");
      Map<String, String> query = params(ds.getQueryHttpParameters(),
                                         HttpParameter.ParameterType.QUERY);
      assertEquals("gs-key", query.get("api_key"));
      assertEquals("GSN-1", query.get("site_token"));
   }

   @Test
   void pipedriveAndPipelineCrmSendTheirKeysAsQueryParameters() {
      PipedriveDataSource pipedrive = new PipedriveDataSource();
      pipedrive.setDomain("acme");
      pipedrive.setApiToken("pd-token");
      assertEquals("pd-token", params(pipedrive.getQueryHttpParameters(),
                                      HttpParameter.ParameterType.QUERY).get("api_token"));
      assertEquals("https://acme.pipedrive.com", pipedrive.getURL());
      PipelineCRMDataSource crm = new PipelineCRMDataSource();
      crm.setApiKey("pc-key");
      crm.setAppKey("pc-app");
      Map<String, String> query = params(crm.getQueryHttpParameters(),
                                         HttpParameter.ParameterType.QUERY);
      assertEquals("pc-key", query.get("api_key"));
      assertEquals("pc-app", query.get("app_key"));
   }

   @Test
   void mailchimpHostIsTheDataCenterOfTheKey() {
      MailchimpDataSource ds = new MailchimpDataSource();
      ds.setDatacenter("us19");
      assertEquals("https://us19.api.mailchimp.com", ds.getURL());
   }

   @Test
   void squareSandboxUsesTheSandboxHost() {
      SquareDataSource ds = new SquareDataSource();
      assertEquals("https://connect.squareup.com", ds.getURL());
      ds.setSandbox(true);
      assertEquals("https://connect.squareupsandbox.com", ds.getURL());
   }

   @Test
   void prometheusUrlHasHostAndPort() {
      PrometheusDataSource ds = new PrometheusDataSource();
      ds.setHost("prom.example.com");
      ds.setPort(9090);
      assertEquals("http://prom.example.com:9090/api", ds.getURL());
   }
}
