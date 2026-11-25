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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.EndpointsTestConfig;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobDataSource;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lookups of an XML data source, with the real Azure Blob configuration (Containers, then the
 * Blobs of each container) and the responses scripted. The XML runner has its own copy of the lookup
 * code, so the same questions as for JSON are asked of it: a row without the key, a lookup that
 * fails, the row limit, a field of the parent with the same name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, EndpointsTestConfig.class,
               SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
class XmlLookupReplayTest {
   @Test
   void theBlobsOfEveryContainerAreLookedUp() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/c1?restype=container", blobs("b1"))
         .on("/c2?restype=container", blobs("b2", "b3"))
         .on("comp=list&timeout", containers("<Name>c1</Name>", "<Name>c2</Name>"))
         .on("?comp=list", containers("<Name>c1</Name>", "<Name>c2</Name>"));

      String dump = dump(run(containersWithBlobs(), http));

      assertNotNull(dump);
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertTrue(dump.contains("b1") && dump.contains("b2") && dump.contains("b3"), dump);
   }

   @Test
   void aContainerWithoutANameIsSkippedAndItsNeighboursAreNot() throws Exception {
      // the JSON runner skips a row whose key is missing; the XML one has to as well
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/c2?restype=container", blobs("b2"))
         .on("?comp=list", containers("<Other>x</Other>", "<Name>c2</Name>"));

      String dump = dump(run(containersWithBlobs(), http));

      assertNotNull(dump, "one container without a name made the whole query fail");
      assertTrue(dump.contains("b2"), dump);
   }

   @Test
   void aFailingLookupFailsTheWholeQueryEvenForTheHealthyNeighbours() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .onReplies(url -> url.contains("/c1?restype=container"),
                    ScriptedHttpHandler.failure(new IOException("HTTP 500")))
         .on("/c2?restype=container", blobs("b2"))
         .on("?comp=list", containers("<Name>c1</Name>", "<Name>c2</Name>"));

      String dump = dump(run(containersWithBlobs(), http));

      // the same as the JSON runner: nothing is returned and c2 is never asked for; this pins that
      assertNull(dump, dump);
      assertTrue(http.requests().stream().noneMatch(u -> u.contains("/c2?")),
                 http.requests().toString());
   }

   @Test
   void theRowLimitStopsTheLookupsWhenEachContainerIsARow() throws Exception {
      // with expandTop off each container is its own row, so only the first container's blobs are
      // needed for a row limit of 1. In the default shape (expandTop on) the whole page is one row
      // and every container's lookup feeds that row, so all of them are made.
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/c1?restype=container", blobs("b1"))
         .on("/c2?restype=container", blobs("b2"))
         .on("/c3?restype=container", blobs("b3"))
         .on("?comp=list", containers("<Name>c1</Name>", "<Name>c2</Name>", "<Name>c3</Name>"));
      AzureBlobQuery query = containersWithBlobs();
      query.setExpandTop(false);
      query.setMaxRows(1);

      String dump = dump(run(query, http));

      long blobRequests = http.requests().stream().filter(u -> u.contains("restype=container"))
         .count();
      assertEquals(1, blobRequests, "looked up the blobs of containers beyond the row limit: " +
                   http.requests());
      assertNotNull(dump);
      String firstRow = dump.substring(0, dump.indexOf('}') + 1);
      assertTrue(firstRow.contains("Container.Name=c1") && firstRow.contains("b1"),
                 "the kept row lost the blobs of c1: " + dump);
   }

   @Test
   void aLookupDoesNotOverwriteAnElementOfTheParentThatHasTheSameName() throws Exception {
      ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("/c1?restype=container", blobs("b1"))
         .on("?comp=list", containers("<Name>c1</Name><Blobs>own value</Blobs>"));

      String dump = dump(run(containersWithBlobs(), http));

      assertNotNull(dump);
      assertTrue(dump.contains("own value"), "the element of the container was overwritten: " + dump);
   }

   private static AzureBlobQuery containersWithBlobs() {
      AzureBlobDataSource dataSource = new AzureBlobDataSource();
      dataSource.setAccountName("acct");
      dataSource.setStorageAccountKey("a2V5");
      AzureBlobQuery query = new AzureBlobQuery();
      query.setDataSource(dataSource);
      query.setEndpoint("Containers");
      query.setXpath("/EnumerationResults/Containers/Container");
      query.setLookupEndpoint("Blobs", 0);
      return query;
   }

   private static String containers(String... containers) {
      StringBuilder xml = new StringBuilder("<EnumerationResults><Containers>");

      for(String container : containers) {
         xml.append("<Container>").append(container).append("</Container>");
      }

      return xml.append("</Containers><NextMarker/></EnumerationResults>").toString();
   }

   private static String blobs(String... names) {
      StringBuilder xml = new StringBuilder("<EnumerationResults><Blobs>");

      for(String name : names) {
         xml.append("<Blob><Name>").append(name).append("</Name></Blob>");
      }

      return xml.append("</Blobs><NextMarker/></EnumerationResults>").toString();
   }

   private static XTableNode run(AzureBlobQuery query, ScriptedHttpHandler http) {
      XTableNode table = new ScriptedHttpHandler.XmlReplayRuntime(http)
         .runQuery(query, new VariableTable());
      http.assertNoFailures();
      return table;
   }

   private static String dump(XTableNode table) {
      if(table == null) {
         return null;
      }

      StringBuilder out = new StringBuilder();

      while(table.next()) {
         out.append('{');

         for(int i = 0; i < table.getColCount(); i++) {
            out.append(table.getName(i)).append('=').append(table.getObject(i)).append(';');
         }

         out.append('}');
      }

      return out.toString();
   }
}
