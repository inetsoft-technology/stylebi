/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.uql.rest.datasource.adobeanalytics.AdobeAnalyticsDataSource;
import inetsoft.uql.rest.datasource.hubspot.HubSpotDataSource;
import inetsoft.uql.rest.datasource.shopify.ShopifyDataSource;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text settings of REST data sources must survive a save and load through XML, whatever
 * characters they contain, and a missing value must load as missing (Bugs #81963, #81965,
 * #81966).
 */
@EndpointsSpringContext
@SreeHome
class RestDataSourceXmlTextFieldsTest {
   // Bug #81963
   @Test
   void shopifySettingsWithXmlSpecialCharactersSurviveSaving() throws Exception {
      final ShopifyDataSource dataSource = new ShopifyDataSource();
      dataSource.setShop(SPECIAL);
      dataSource.setApiVersion("v&1");
      dataSource.setScope(CDATA_END);

      final ShopifyDataSource loaded = roundTrip(dataSource, ShopifyDataSource::new);

      assertEquals(SPECIAL, loaded.getShop());
      assertEquals("v&1", loaded.getApiVersion());
      assertEquals(CDATA_END, loaded.getScope());
   }

   // Bug #81963
   @Test
   void shopifyMissingSettingsStayMissingAfterSaving() throws Exception {
      final ShopifyDataSource dataSource = new ShopifyDataSource();
      dataSource.setShop(null);
      dataSource.setApiVersion(null);
      dataSource.setScope(null);

      final ShopifyDataSource loaded = roundTrip(dataSource, ShopifyDataSource::new);

      assertNull(loaded.getShop());
      assertNull(loaded.getApiVersion());
      assertNull(loaded.getScope());
   }

   // Bug #81965
   @Test
   void adobeAnalyticsSettingsWithXmlSpecialCharactersSurviveSaving() throws Exception {
      final AdobeAnalyticsDataSource dataSource = new AdobeAnalyticsDataSource();
      dataSource.setGlobalCompanyId(SPECIAL);
      dataSource.setApiKey(CDATA_END);

      final AdobeAnalyticsDataSource loaded =
         roundTrip(dataSource, AdobeAnalyticsDataSource::new);

      assertEquals(SPECIAL, loaded.getGlobalCompanyId());
      assertEquals(CDATA_END, loaded.getApiKey());
   }

   // Bug #81965
   @Test
   void adobeAnalyticsMissingSettingsStayMissingAfterSaving() throws Exception {
      final AdobeAnalyticsDataSource dataSource = new AdobeAnalyticsDataSource();
      dataSource.setApiKey(null);

      final AdobeAnalyticsDataSource loaded =
         roundTrip(dataSource, AdobeAnalyticsDataSource::new);

      assertNull(loaded.getGlobalCompanyId());
      assertNull(loaded.getApiKey());
   }

   // Bug #81965: a source saved without a company id must still compare equal to its copy
   @Test
   void adobeAnalyticsLoadedWithoutCompanyIdComparesEqual() throws Exception {
      final AdobeAnalyticsDataSource dataSource = new AdobeAnalyticsDataSource();
      final AdobeAnalyticsDataSource first =
         roundTrip(dataSource, AdobeAnalyticsDataSource::new);
      final AdobeAnalyticsDataSource second =
         roundTrip(dataSource, AdobeAnalyticsDataSource::new);

      assertNull(first.getGlobalCompanyId());
      assertTrue(first.equals(second));
   }

   // Bug #81966
   @Test
   void kerberosSettingsWithXmlSpecialCharactersSurviveSaving() throws Exception {
      final HubSpotDataSource dataSource = new HubSpotDataSource();
      dataSource.setServicePrincipalName("HTTP/a&b<c");
      dataSource.setImpersonatePrincipal("u&v<w");
      dataSource.setConfigurationServiceName("svc" + CDATA_END);

      final HubSpotDataSource loaded = roundTrip(dataSource, HubSpotDataSource::new);

      assertEquals("HTTP/a&b<c", loaded.getServicePrincipalName());
      assertEquals("u&v<w", loaded.getImpersonatePrincipal());
      assertEquals("svc" + CDATA_END, loaded.getConfigurationServiceName());
   }

   /**
    * Saves the data source and loads it into a new instance the way the product does:
    * {@code writeXML}, then {@code CoreTool.parseXML}, then {@code parseXML}.
    */
   private static <T extends TabularDataSource<T>> T roundTrip(T dataSource, Supplier<T> factory)
      throws Exception
   {
      final StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         dataSource.writeXML(writer);
      }

      final Document document = CoreTool.parseXML(new StringReader(buffer.toString()));
      final T copy = factory.get();
      copy.parseXML(document.getDocumentElement());
      return copy;
   }

   private static final String SPECIAL = "a&b<c>\"d'e";
   private static final String CDATA_END = "x]]>y&<z";
}
