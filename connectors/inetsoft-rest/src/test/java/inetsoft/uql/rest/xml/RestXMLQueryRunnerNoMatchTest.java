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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81916: an XPath that matches nothing must yield "no data" (null from run()), the same as
 * the JSON runner, instead of a placeholder one-row table with a single null "Column".
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
