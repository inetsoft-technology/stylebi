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
import inetsoft.uql.rest.datasource.chargify.ChargifyDataSource;
import inetsoft.uql.rest.datasource.gsearchconsole.GoogleSearchConsoleDataSource;
import inetsoft.uql.rest.datasource.linkedin.LinkedinDataSource;
import inetsoft.uql.rest.datasource.monday.MondayDataSource;
import inetsoft.uql.rest.datasource.quickbooks.QuickbooksReportsDataSource;
import inetsoft.uql.rest.datasource.servicenow.ServiceNowDataSource;
import inetsoft.uql.rest.datasource.shopify.ShopifyDataSource;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The code that is specific to ServiceNow, Chargify, LinkedIn, Monday, Google Search Console,
 * Shopify and QuickBooks: the URL, the headers, and what is saved.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class SecondTierDataSourcesTest {
   private static Map<String, String> headers(HttpParameter[] params) {
      Map<String, String> map = new LinkedHashMap<>();

      for(HttpParameter p : params) {
         assertEquals(HttpParameter.ParameterType.HEADER, p.getType(), p.getName());
         map.put(p.getName(), p.getValue());
      }

      return map;
   }

   @SuppressWarnings("unchecked")
   private static <T extends XMLSerializable> T roundTrip(T source, T target) throws Exception {
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      source.writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      target.parseXML(root);
      return target;
   }

   // ServiceNow

   @Test
   void serviceNowUrlHeaderAndInstanceRoundTrip() throws Exception {
      ServiceNowDataSource ds = new ServiceNowDataSource();
      assertEquals("https://[Instance].service-now.com/api", ds.getURL());
      ds.setInstance("dev123");
      assertEquals("https://dev123.service-now.com/api", ds.getURL());
      assertEquals(Map.of("Accept", "application/json"), headers(ds.getQueryHttpParameters()));
      assertEquals("dev123", roundTrip(ds, new ServiceNowDataSource()).getInstance());
   }

   // Chargify: the API key is the user name and the password is any text (Chargify docs: x)

   @Test
   void chargifyUsesTheApiKeyAsUserAndAFixedPassword() throws Exception {
      ChargifyDataSource ds = new ChargifyDataSource();
      ds.setSubdomain("acme");
      ds.setApiKey("key123");
      assertEquals("https://acme.chargify.com", ds.getURL());
      assertEquals("key123", ds.getUser());
      assertEquals("X", ds.getPassword());
      ds.setUser("other");
      ds.setPassword("other");
      assertEquals("key123", ds.getUser());
      assertEquals("X", ds.getPassword());
      assertEquals("application/json", headers(ds.getQueryHttpParameters()).get("Content-Type"));
      assertEquals("acme", roundTrip(ds, new ChargifyDataSource()).getSubdomain());
   }

   // LinkedIn

   @Test
   void linkedinSendsTheRestliAndVersionHeaders() {
      LinkedinDataSource ds = new LinkedinDataSource();
      ds.setAccessToken("li-token");
      ds.setRefreshToken("li-refresh");
      ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      Map<String, String> headers = headers(ds.getQueryHttpParameters());
      assertEquals("2.0.0", headers.get("X-RestLi-Protocol-Version"));
      assertEquals("application/json", headers.get("Accept"));
      assertEquals("Bearer li-token", headers.get("Authorization"));
      assertTrue(headers.get("Linkedin-Version").matches("\\d{6}"), headers.toString());
      assertEquals("https://api.linkedin.com", ds.getURL());
   }

   // Monday: the API token is the whole Authorization header value, without Bearer

   @Test
   void mondaySendsTheApiKeyAsTheAuthorizationHeader() {
      MondayDataSource ds = new MondayDataSource();
      ds.setApiKey("mon-key");
      assertEquals("https://api.monday.com/v2", ds.getURL());
      assertEquals("mon-key", headers(ds.getRequestParameters()).get("Authorization"));
   }

   // Google Search Console

   @Test
   void searchConsoleSendsABearerHeaderWithoutRefreshWhileValid() {
      GoogleSearchConsoleDataSource ds = new GoogleSearchConsoleDataSource();
      ds.setAccessToken("g-token");
      ds.setRefreshToken("g-refresh");
      ds.setTokenExpiration(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
      assertEquals(Map.of("Authorization", "Bearer g-token"), headers(ds.getQueryHttpParameters()));
      assertEquals("https://www.googleapis.com/webmasters", ds.getURL());
      assertEquals("google-search", ds.getServiceName());
   }

   // Shopify

   @Test
   void shopifyUrlsAndHeaderFollowTheShopAndVersion() {
      ShopifyDataSource ds = new ShopifyDataSource();
      ds.setShop("acme");
      ds.setApiVersion("2024-10");
      ds.setAccessToken("shpat");
      assertEquals("https://acme.myshopify.com/admin/api/2024-10/graphql.json", ds.getURL());
      assertEquals("https://acme.myshopify.com/admin/oauth/authorize", ds.getAuthorizationUri());
      assertEquals("https://acme.myshopify.com/admin/oauth/access_token", ds.getTokenUri());
      assertEquals("shpat", headers(ds.getRequestParameters()).get("X-Shopify-Access-Token"));
   }

   @Test
   void shopifySettingsRoundTrip() throws Exception {
      ShopifyDataSource ds = new ShopifyDataSource();
      ds.setShop("acme");
      ds.setApiVersion("2024-10");
      ds.setScope("read_orders read_products");
      ShopifyDataSource copy = roundTrip(ds, new ShopifyDataSource());
      assertEquals("acme", copy.getShop());
      assertEquals("2024-10", copy.getApiVersion());
      assertEquals("read_orders read_products", copy.getScope());
   }

   // QuickBooks

   @Test
   void quickbooksUrlFollowsTheModeAndCompany() {
      QuickbooksReportsDataSource ds = new QuickbooksReportsDataSource();
      ds.setCompanyId("9130");
      assertEquals("https://sandbox-quickbooks.api.intuit.com/v3/company/9130", ds.getURL());
      ds.setProduction(true);
      assertEquals("https://quickbooks.api.intuit.com/v3/company/9130", ds.getURL());
   }

   @Test
   void quickbooksSettingsRoundTrip() throws Exception {
      QuickbooksReportsDataSource ds = new QuickbooksReportsDataSource();
      ds.setCompanyId("9130");
      ds.setProduction(true);
      QuickbooksReportsDataSource copy = roundTrip(ds, new QuickbooksReportsDataSource());
      assertEquals("9130", copy.getCompanyId());
      assertTrue(copy.isProduction());
   }

   // XML 1.0: the text of an element may not contain a bare & or <
   @Test
   void shopifySettingsWithXmlSpecialCharactersStillSaveAsWellFormedXml() throws Exception {
      ShopifyDataSource ds = new ShopifyDataSource();
      ds.setShop("a&b<c");
      ds.setApiVersion("v&1");
      ds.setScope("read & write");
      assertEquals("a&b<c", roundTrip(ds, new ShopifyDataSource()).getShop());
   }

   // before the first authorization there is no company, and saving must not invent one
   @Test
   @Disabled("Bug #81964: a missing company id is saved as the text 'null'; enable when it is fixed")
   void quickbooksWithoutACompanyStaysWithoutOneAfterSaving() throws Exception {
      QuickbooksReportsDataSource copy =
         roundTrip(new QuickbooksReportsDataSource(), new QuickbooksReportsDataSource());
      assertNull(copy.getCompanyId());
   }
}
