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
package inetsoft.uql.rest.xml;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.RestRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82284: an expandedPath over an empty element (parsed as ValueNode(null), not an empty
 * list) previously produced one all-null row instead of zero rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class RestXmlEmptyExpandedPathTest {
   private static final String BLOB_PATH = "EnumerationResults.Blobs.Blob";

   @Test
   void azureEmptyBlobsYieldsNoRows() throws Exception {
      assertEquals(0, dataRows(run(BLOB_PATH,
         "<EnumerationResults ServiceEndpoint=\"s\" ContainerName=\"c\"><Prefix/><Marker/>" +
         "<Blobs/><NextMarker/></EnumerationResults>")));
   }

   @Test
   void emptyPageListYieldsNoRows() throws Exception {
      assertEquals(0, dataRows(run("PageList.PageRange", "<PageList/>")));
   }

   @Test
   void emptyCommittedAndUncommittedBlocksYieldNoRows() throws Exception {
      assertEquals(0, dataRows(run("BlockList.CommittedBlocks.Block",
         "<BlockList><CommittedBlocks/><UncommittedBlocks/></BlockList>")));
   }

   @Test
   void singleElementYieldsOneRow() throws Exception {
      assertEquals(1, dataRows(run(BLOB_PATH, blobs(1))));
   }

   @Test
   void nElementsYieldNRows() throws Exception {
      assertEquals(3, dataRows(run(BLOB_PATH, blobs(3))));
   }

   @Test
   void emptyExpandedPathIsUnchanged() throws Exception {
      assertEquals(1, dataRows(run("",
         "<EnumerationResults ServiceEndpoint=\"s\"><Blobs/></EnumerationResults>")));
   }

   @Test
   void nullAtFinalSegmentIsNotSkipped() throws Exception {
      // the final segment may be one real element with empty text, so it must not be skipped
      assertEquals(1, dataRows(run("EnumerationResults.Blobs",
         "<EnumerationResults ServiceEndpoint=\"s\"><Blobs/></EnumerationResults>")));
   }

   @Test
   void emptyCommittedWithNonEmptyUncommittedYieldsNoRowsForCommittedPath() throws Exception {
      assertEquals(0, dataRows(run("BlockList.CommittedBlocks.Block",
         "<BlockList><CommittedBlocks/><UncommittedBlocks><Block><Name>a</Name></Block>" +
         "</UncommittedBlocks></BlockList>")));
   }

   @Test
   void clearRangeOnlyDiffStillYieldsOneRow() throws Exception {
      // Known gap: a prevsnapshot diff holding only <ClearRange> has no PageRange key at all.
      // An absent key is deliberately not treated as empty (it could be a mistyped path), so
      // this stays at one row of ClearRange columns.
      assertEquals(1, dataRows(run("PageList.PageRange",
         "<PageList><ClearRange><Start>0</Start><End>511</End></ClearRange></PageList>")));
   }

   @Test
   void allSkippedResponseGivesNullTableAndCompletedRun() throws Exception {
      final RestXMLQuery query = createQuery("PageList.PageRange");
      assertNull(createRunner(query, "<PageList/>").run());
      assertTrue(query.isRunCompleted());
   }

   private static String blobs(int n) {
      final StringBuilder sb = new StringBuilder(
         "<EnumerationResults ServiceEndpoint=\"s\"><Blobs>");

      for(int i = 0; i < n; i++) {
         sb.append("<Blob><Name>b").append(i).append("</Name></Blob>");
      }

      return sb.append("</Blobs></EnumerationResults>").toString();
   }

   private static int dataRows(XTableNode table) {
      if(table == null) {
         return 0;
      }

      final XNodeTableLens lens = new XNodeTableLens(table);
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount() - 1;
   }

   private XTableNode run(String path, String fixture) throws Exception {
      return createRunner(createQuery(path), fixture).run();
   }

   private RestXMLQuery createQuery(String path) {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://azure.example/c");
      query.setDataSource(dataSource);
      query.setXpath("*");
      query.setExpanded(true);
      query.setExpandTop(false);
      query.setExpandedPath(path);
      return query;
   }

   private RestXMLQueryRunner createRunner(RestXMLQuery query, String fixture) throws Exception {
      final List<RequestResponse> requestResponses = new ArrayList<>();
      requestResponses.add(new RequestResponse(RestRequest.fromQuery(query),
         new TestHttpResponse(fixture)));
      return new RestXMLQueryRunner(query,
         new XMLRestDataIteratorStrategyFactory(new TestHttpHandler(requestResponses)));
   }
}
