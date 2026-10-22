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
package inetsoft.uql.odata;

import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.TabularUtil;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.core.ODataClientFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79001: the OData filter is substituted OData-aware. A variable inside a single-quoted
 * literal has its quotes doubled, a variable outside quotes is embedded as is, and nothing is
 * URL-encoded by the substitution, because the URI builder encodes the filter once.
 */
class ODataQueryVariableEscapeTest {
   private static String filter(String template, String name, Object value) {
      ODataQuery query = new ODataQuery();
      query.setFilter(template);
      VariableTable vars = new VariableTable();

      if(name != null) {
         vars.put(name, value);
      }

      TabularUtil.replaceVariables(query, vars);
      return query.getFilter();
   }

   @Test
   void quoteInQuotedLiteralIsDoubled() {
      assertEquals("Name eq 'O''Brien'", filter("Name eq '$(p)'", "p", "O'Brien"));
   }

   @Test
   void injectionStaysInsideTheLiteral() {
      assertEquals("Name eq 'x'' or Name ne '''",
                   filter("Name eq '$(p)'", "p", "x' or Name ne '"));
   }

   @Test
   void quotedValueIsNotUrlEncoded() {
      assertEquals("Name eq 'a b&c=d'", filter("Name eq '$(p)'", "p", "a b&c=d"));
   }

   @Test
   void quoteInsideFunctionCallLiteralIsDoubled() {
      assertEquals("contains(Name,'x''y')", filter("contains(Name,'$(p)')", "p", "x'y"));
   }

   @Test
   void escapedQuoteInTemplateKeepsLiteralState() {
      assertEquals("Name eq 'it''s O''Brien'",
                   filter("Name eq 'it''s $(p)'", "p", "O'Brien"));
   }

   @Test
   void unquotedValuesAreEmbeddedAsIs() {
      assertEquals("Active eq true", filter("Active eq $(p)", "p", "true"));
      assertEquals("Active eq true", filter("Active eq $(p)", "p", Boolean.TRUE));
      assertEquals("Id eq a1b2c3d4-0000-1111-2222-333344445555",
                   filter("Id eq $(p)", "p", "a1b2c3d4-0000-1111-2222-333344445555"));
      assertEquals("At gt 2020-01-01T00:00:00Z",
                   filter("At gt $(p)", "p", "2020-01-01T00:00:00Z"));
      assertEquals("Price gt 12", filter("Price gt $(p)", "p", 12));
      assertEquals("Price gt 12.5", filter("Price gt $(p)", "p", "12.5"));
      assertEquals("Name eq 'x'", filter("$(p) eq 'x'", "p", "Name"));
      assertEquals("Day eq 2020-01-01",
                   filter("Day eq $(p)", "p", Date.valueOf("2020-01-01")));
   }

   @Test
   void unquotedValueKeepsItsOwnQuotes() {
      assertEquals("Name eq 'O''Brien' and x",
                   filter("Name eq $(p) and x", "p", "'O''Brien'"));
   }

   @Test
   void arraysAreJoinedWithCommas() {
      assertEquals("Id in (1,2)", filter("Id in ($(p))", "p", new int[] { 1, 2 }));
      assertEquals("Region in ('a,b')",
                   filter("Region in ('$(p)')", "p", new String[] { "a", "b" }));
   }

   @Test
   void variableWithoutValueIsLeftAsWritten() {
      assertEquals("Name eq '$(p)'", filter("Name eq '$(p)'", null, null));
      assertEquals("Name eq $(p)", filter("Name eq $(p)", null, null));
   }

   @Test
   void otherQueryOptionsKeepTheDefaultSubstitution() {
      ODataQuery query = new ODataQuery();
      query.setSelect("$(p)");
      VariableTable vars = new VariableTable();
      vars.put("p", "a b");
      TabularUtil.replaceVariables(query, vars);
      assertEquals("a%20b", query.getSelect());
   }

   @Test
   void filterIsEncodedExactlyOnceOnTheWire() {
      ODataClient client = ODataClientFactory.getClient();
      String spaced = filter("Name eq '$(p)'", "p", "a b&c=d");
      URI uri = client.newURIBuilder("http://localhost/svc").appendEntitySetSegment("People")
         .filter(spaced).build();
      assertEquals("http://localhost/svc/People?%24filter=Name%20eq%20'a%20b%26c%3Dd'",
                   uri.toASCIIString());

      String apostrophe = filter("Name eq '$(p)'", "p", "O'Brien");
      uri = client.newURIBuilder("http://localhost/svc").appendEntitySetSegment("People")
         .filter(apostrophe).build();
      assertEquals("http://localhost/svc/People?%24filter=Name%20eq%20'O''Brien'",
                   uri.toASCIIString());
   }
}
