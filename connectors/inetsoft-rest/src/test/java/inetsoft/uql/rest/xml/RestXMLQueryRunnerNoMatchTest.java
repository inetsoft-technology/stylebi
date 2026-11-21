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
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81916: an XPath that matches nothing must yield "no data" (null from run()), the same as
 * the JSON runner, instead of a placeholder one-row table with a single null "Column".
 *
 * <p>The runner is tested directly and through the real runtime. A page of a paged query that
 * matches nothing is skipped, so the paging must go on after it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class RestXMLQueryRunnerNoMatchTest {
   @Test
   void xpathMatchingNothingIsNoData() throws Exception {
      final XTableNode data = run("//Missing", "<Root><Item><id>1</id></Item></Root>");
      assertNull(data);
   }

   @Test
   void xpathWithMatchesStillLoadsRows() throws Exception {
      final XTableNode data = run("//Item", "<Root><Item><id>1</id></Item><Item><id>2</id></Item></Root>");
      assertNotNull(data);
      final XNodeTableLens lens = new XNodeTableLens(data);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(3, lens.getRowCount());
      assertEquals(1, ((Number) lens.getObject(1, 0)).intValue());
   }

   @Test
   void matchedElementWithEmptyValueIsStillARow() throws Exception {
      final XTableNode data = run("//Item", "<Root><Item><id/></Item></Root>");
      assertNotNull(data);
      final XNodeTableLens lens = new XNodeTableLens(data);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(2, lens.getRowCount());
   }

   @Test
   void xpathMatchingNothingIsNoDataThroughTheRuntimeToo() throws Exception {
      final ScriptedHttpHandler http = new ScriptedHttpHandler()
         .on("xml.test", "<root><item><id>1</id></item></root>");

      final XTableNode data = new ScriptedHttpHandler.XmlReplayRuntime(http)
         .runQuery(pagedQuery("/root/missing", PaginationType.NONE), new VariableTable());

      http.assertNoFailures();
      assertNull(data);
   }

   @Test
   void aPageThatMatchesNothingDoesNotEndThePaging() throws Exception {
      // the second of three pages has the page count but no item: the third is still read
      final ScriptedHttpHandler http = new ScriptedHttpHandler().on("xml.test",
         "<root><pages>3</pages><item><id>1</id></item></root>",
         "<root><pages>3</pages></root>",
         "<root><pages>3</pages><item><id>3</id></item></root>");

      final XTableNode data = new ScriptedHttpHandler.XmlReplayRuntime(http)
         .runQuery(pagedQuery("/root/item", PaginationType.PAGE_COUNT), new VariableTable());

      http.assertNoFailures();
      assertEquals(3, http.requests().size(), http.requests().toString());
      assertEquals(List.of(1, 3), ids(data));
   }

   @Test
   void everyPageMatchingNothingIsNoData() throws Exception {
      final ScriptedHttpHandler http = new ScriptedHttpHandler().on("xml.test",
         "<root><pages>2</pages></root>", "<root><pages>2</pages></root>");

      final XTableNode data = new ScriptedHttpHandler.XmlReplayRuntime(http)
         .runQuery(pagedQuery("/root/item", PaginationType.PAGE_COUNT), new VariableTable());

      http.assertNoFailures();
      assertEquals(2, http.requests().size(), http.requests().toString());
      assertNull(data);
   }

   private static RestXMLQuery pagedQuery(String xpath, PaginationType paging) {
      final RestXMLDataSource datasource = new RestXMLDataSource();
      datasource.setURL(paging == PaginationType.NONE ? "https://xml.test/items"
                                                      : "https://xml.test/items?p={{page}}");
      final RestXMLQuery query = new RestXMLQuery();
      query.setDataSource(datasource);
      query.setXpath(xpath);

      if(paging != PaginationType.NONE) {
         query.setPaginationType(paging);
         query.setPageCountXpathValue("/root/pages");
         query.setPageNumberURLVariableValue("page");
      }

      return query;
   }

   /** The values of the id column of the table, in order. */
   private static List<Object> ids(XTableNode table) {
      assertNotNull(table);
      final List<Object> ids = new ArrayList<>();

      while(table.next()) {
         for(int i = 0; i < table.getColCount(); i++) {
            if(table.getName(i).endsWith("id")) {
               ids.add(table.getObject(i));
            }
         }
      }

      return ids;
   }

   private XTableNode run(String xpath, String response) throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource datasource = new RestXMLDataSource();
      datasource.setURL("http://host/data");
      query.setDataSource(datasource);
      query.setXpath(xpath);

      final List<RequestResponse> requestResponses = new ArrayList<>();
      requestResponses.add(new RequestResponse(RestRequest.fromQuery(query), new TestHttpResponse(response)));
      final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
      final RestXMLQueryRunner runner =
         new RestXMLQueryRunner(query, new XMLRestDataIteratorStrategyFactory(httpHandler));
      return runner.run();
   }
}
