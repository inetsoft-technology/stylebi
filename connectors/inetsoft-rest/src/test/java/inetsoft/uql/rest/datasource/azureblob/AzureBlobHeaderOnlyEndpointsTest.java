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
package inetsoft.uql.rest.datasource.azureblob;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.AbstractEndpoint;
import inetsoft.uql.rest.pagination.HttpHeader;
import inetsoft.uql.rest.xml.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for Bug #82282: the Azure Blob operations Account, Container Properties,
 * Container Metadata and Blob Metadata answer 200 with an empty body and carry their data only in
 * response headers, so add_table on them built nothing ("returned no columns").
 *
 * <p>The axis the fix varies with is the response kind of the operation (success, data only in
 * headers), switched on per endpoint by {@code responseInHeaders}; the endpoint names are only
 * where the flag is set today.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class AzureBlobHeaderOnlyEndpointsTest {
   /** Endpoint name plus the parameter name/value pairs the endpoint needs. */
   static Stream<List<String>> headerOnlyEndpoints() {
      return Stream.of(
         List.of("Account"),
         List.of("Container Properties", "Container", "c1"),
         List.of("Container Metadata", "Container", "c1"),
         List.of("Blob Metadata", "Container", "c1", "Blob", "b1"));
   }

   /** Header-only responses give exactly one row, whether the entity is empty or absent. */
   @ParameterizedTest(name = "{0}")
   @MethodSource("headerOnlyEndpoints")
   void headerOnlyEndpointYieldsOneRow(List<String> endpoint) throws Exception {
      for(boolean emptyEntity : new boolean[] { true, false }) {
         final XTableNode table = run(endpoint.toArray(new String[0]),
            headerResponse(emptyEntity,
               h("x-ms-meta-owner", "alice"), h("x-ms-lease-status", "unlocked"),
               h("x-ms-sku-name", "Standard_LRS"), h("x-ms-account-kind", "StorageV2"),
               h("ETag", "\"0x8D\""), h("Last-Modified", "Mon, 01 Jan 2026 00:00:00 GMT")));

         assertNotNull(table, endpoint.get(0) + " emptyEntity=" + emptyEntity);
         final Map<String, Object> row = singleRow(table);
         assertEquals("alice", row.get("Response.x-ms-meta-owner"));
         assertEquals("unlocked", row.get("Response.x-ms-lease-status"));
         assertEquals("Standard_LRS", row.get("Response.x-ms-sku-name"));
         assertEquals("StorageV2", row.get("Response.x-ms-account-kind"));
         assertEquals("\"0x8D\"", row.get("Response.etag"));
         assertEquals("Mon, 01 Jan 2026 00:00:00 GMT", row.get("Response.last-modified"));
      }
   }

   @Test
   void everyBlobMetadataHeaderIsAColumnAndTransportHeadersAreNot() throws Exception {
      final XTableNode table = run(new String[] { "Blob Metadata", "Container", "c1", "Blob", "b1" },
         headerResponse(true,
            h("x-ms-meta-Owner", "alice"), h("x-ms-meta-cost_center", "42"),
            h("x-ms-meta-owner", "second"),
            h("ETag", "\"1\""),
            h("Date", "Mon, 01 Jan 2026 00:00:00 GMT"), h("Server", "Windows-Azure-Blob/1.0"),
            h("x-ms-request-id", "r-1"), h("x-ms-client-request-id", "c-1"),
            h("x-ms-version", "2019-07-07"), h("x-ms-date", "d"),
            h("Content-Length", "0"), h("Content-Type", "text/plain")));

      assertNotNull(table);
      // names are lower-cased; "x-ms-meta-Owner" and "x-ms-meta-owner" collide, the first one wins
      final Map<String, Object> row = singleRow(table);
      assertEquals(new TreeSet<>(List.of("Response.x-ms-meta-owner", "Response.x-ms-meta-cost_center",
                                         "Response.etag")),
                   new TreeSet<>(row.keySet()));
      assertEquals("42", row.get("Response.x-ms-meta-cost_center"));
      assertTrue("alice".equals(row.get("Response.x-ms-meta-owner")) ||
                 "second".equals(row.get("Response.x-ms-meta-owner")));
   }

   /** A header name need not be a valid XML name and a value may hold markup. */
   @Test
   void hostileHeaderNameAndValueDoNotBreakTheRecord() throws Exception {
      final String value = "<a href=\"x\">&amp; 'q' ]]></a>";
      final XTableNode table = run(new String[] { "Account" },
         headerResponse(true, h("x-ms-meta-a.b<c>d&e", value), h("x-ms-sku-name", "S")));

      assertNotNull(table);
      final Map<String, Object> row = singleRow(table);
      assertEquals(value, row.get("Response.x-ms-meta-a_b_c_d_e"));
      assertEquals("S", row.get("Response.x-ms-sku-name"));
   }

   /** Rule: zero rows must keep failing -- no data header means no row, not an empty row. */
   @Test
   void flagOnButNoDataHeaderGivesNoTable() throws Exception {
      final XTableNode table = run(new String[] { "Account" },
         headerResponse(true, h("Date", "x"), h("Server", "y"), h("x-ms-request-id", "r")));

      assertTrue(table == null || !table.next());
   }

   /** Flag off: an empty body with data-looking headers stays an empty result. */
   @Test
   void flagOffEndpointWithEmptyBodyIsUnchanged() throws Exception {
      final XTableNode table = run(new String[] { "Blob Service Properties" },
         headerResponse(true, h("x-ms-meta-owner", "alice"), h("ETag", "\"1\"")));

      assertTrue(table == null || !table.next());
   }

   @Test
   void flagOffEndpointWithNullEntityIsUnchanged() throws Exception {
      final XTableNode table = run(new String[] { "Blob Service Properties" },
         headerResponse(false, h("x-ms-meta-owner", "alice"), h("ETag", "\"1\"")));

      assertTrue(table == null || !table.next());
   }

   /** The paged XML endpoints keep reading their body. */
   @Test
   void containersAndBlobsAreUnchanged() throws Exception {
      final XTableNode containers = run(new String[] { "Containers" },
         new TestHttpResponse("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<EnumerationResults ServiceEndpoint=\"x\"><Containers><Container><Name>c1</Name>" +
            "<Properties><Last-Modified>Mon</Last-Modified><LeaseStatus>unlocked</LeaseStatus>" +
            "</Properties></Container></Containers><NextMarker/></EnumerationResults>",
            List.of(h("x-ms-meta-ignored", "ignored"), h("ETag", "\"ignored\""))));

      assertNotNull(containers);
      assertEquals(List.of("EnumerationResults.NextMarker", "EnumerationResults.@ServiceEndpoint",
                           "EnumerationResults.Containers.Container.Name",
                           "EnumerationResults.Containers.Container.Properties.Last-Modified",
                           "EnumerationResults.Containers.Container.Properties.LeaseStatus"),
                   columns(containers));
      final Map<String, Object> row = singleRow(containers);
      assertEquals("c1", row.get("EnumerationResults.Containers.Container.Name"));

      final XTableNode blobs = run(new String[] { "Blobs", "Container", "c1" },
         new TestHttpResponse("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<EnumerationResults ServiceEndpoint=\"x\" ContainerName=\"c1\"><Blobs><Blob>" +
            "<Name>b1</Name><Properties><Content-Length>7</Content-Length></Properties></Blob>" +
            "</Blobs><NextMarker/></EnumerationResults>",
            List.of(h("x-ms-meta-ignored", "ignored"))));

      assertNotNull(blobs);
      assertEquals("b1", singleRow(blobs).get("EnumerationResults.Blobs.Blob.Name"));

      for(String column : columns(blobs)) {
         assertFalse(column.contains("ignored") || column.startsWith("Response."), column);
      }
   }

   /** The generic REST XML connector shares the strategy and must keep an empty body empty. */
   @Test
   void genericRestXmlQueryWithEmptyBodyIsUnchanged() throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://generic.example/v1/empty");
      query.setDataSource(dataSource);
      final List<RequestResponse> rr = new ArrayList<>();
      rr.add(new RequestResponse(RestRequest.fromQuery(query),
         headerResponse(true, h("x-ms-meta-owner", "alice"), h("ETag", "\"1\""))));

      final XTableNode table = new RestXMLQueryRunner(query,
         new XMLRestDataIteratorStrategyFactory(new TestHttpHandler(rr))).run();

      assertTrue(table == null || !table.next());
   }

   /** The flag is parsed from endpoints.json and set on exactly these four endpoints. */
   @Test
   void flagIsSetOnExactlyTheHeaderOnlyEndpoints() {
      final Map<String, AzureBlobEndpoint> endpoints = new AzureBlobQuery().getEndpointMap();
      final Set<String> flagged = new TreeSet<>();

      for(AzureBlobEndpoint endpoint : endpoints.values()) {
         if(endpoint.isResponseInHeaders()) {
            flagged.add(endpoint.getName());
            assertNotNull(endpoint.getResponseSchema(), endpoint.getName());
            assertNotNull(endpoint.getDescription(), endpoint.getName());
         }
      }

      assertEquals(new TreeSet<>(List.of("Account", "Container Properties", "Container Metadata",
                                         "Blob Metadata")), flagged);
   }

   @Test
   void flagParticipatesInEndpointIdentity() {
      final AbstractEndpoint a = new AbstractEndpoint();
      final AbstractEndpoint b = new AbstractEndpoint();
      assertEquals(a, b);
      b.setResponseInHeaders(true);
      assertNotEquals(a, b);
      assertNotEquals(a.hashCode(), b.hashCode());
   }

   // ---- helpers ----

   private static HttpHeader h(String name, String value) {
      return new HttpHeader(name, value);
   }

   /** A 200 response with no body: a zero-length entity (as Azure sends) or no entity at all. */
   private static HttpResponse headerResponse(boolean emptyEntity, HttpHeader... headers) {
      final List<HttpHeader> list = Arrays.asList(headers);
      return emptyEntity ? new TestHttpResponse(new ByteArrayInputStream(new byte[0]), list, 0) :
         new TestHttpResponse(list);
   }

   /** Test double that points the datasource at a fixed host so no real Azure is needed. */
   public static class TestAzureBlobDataSource extends AzureBlobDataSource {
      @Override
      public String getAccountName() {
         return "acct";
      }

      @Override
      public void setAccountName(String accountName) {
         // fixed
      }

      @Override
      public String getURL() {
         return "https://acct.blob.core.windows.net";
      }
   }

   private XTableNode run(String[] endpoint, HttpResponse response) throws Exception {
      final AzureBlobDataSource dataSource = new TestAzureBlobDataSource();
      dataSource.setSignatureServicesVersion("2019-07-07");
      dataSource.setSignatureServices("b");
      dataSource.setSignatureResourceTypes("sco");
      dataSource.setSignatureStartTime("2026-01-01T00:00:00Z");
      dataSource.setSignatureExpiryTime("2027-01-01T00:00:00Z");
      dataSource.setSignaturePermissions("lr");
      dataSource.setSignatureProtocol("https");
      dataSource.setSignature("sig");

      final AzureBlobQuery query = new AzureBlobQuery();
      query.setDataSource(dataSource);
      query.setEndpoint(endpoint[0]);
      final RestParameters parameters = query.getParameters();

      for(int i = 1; i + 1 < endpoint.length; i += 2) {
         for(RestParameter parameter : parameters.getParameters()) {
            if(parameter.getName().equals(endpoint[i])) {
               parameter.setValue(endpoint[i + 1]);
            }
         }
      }

      query.setParameters(parameters);
      return new RestXMLQueryRunner(query,
         new XMLRestDataIteratorStrategyFactory(new FixedResponseHandler(response))).run();
   }

   /**
    * Answers every request with one response. {@link TestHttpHandler} cannot be used here: it keys
    * on the whole request, and an Azure request carries a signing timestamp header that differs
    * between building the key and running the query.
    */
   private static final class FixedResponseHandler implements IHttpHandler {
      FixedResponseHandler(HttpResponse response) {
         this.response = response;
      }

      @Override
      public HttpResponse executeRequest(RestRequest request) {
         return response;
      }

      @Override
      public void close() {
         // no-op
      }

      private final HttpResponse response;
   }

   private static List<String> columns(XTableNode table) {
      final List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      return names;
   }

   /** The table's only row, by column name; fails if there is not exactly one. */
   private static Map<String, Object> singleRow(XTableNode table) {
      assertTrue(table.next(), "expected one row");
      final Map<String, Object> row = new LinkedHashMap<>();

      for(int i = 0; i < table.getColCount(); i++) {
         row.put(table.getName(i), table.getObject(i));
      }

      assertFalse(table.next(), "expected exactly one row");
      return row;
   }
}
