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
package inetsoft.uql.rest.json;

import inetsoft.uql.tabular.HttpParameter;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SuffixTemplateTest {
   @Test
   void repeatedParameterKeepsEveryValueInOrder() {
      assertEquals("/v4/projects/1/issues?iids[]=1&iids[]=2",
                   new SuffixTemplate("/v4/projects/1/issues?iids[]=1&iids[]=2").build());
   }

   @Test
   void repeatedParameterKeepsSwappedOrder() {
      assertEquals("/v4/projects/1/issues?iids[]=2&iids[]=1",
                   new SuffixTemplate("/v4/projects/1/issues?iids[]=2&iids[]=1").build());
   }

   @Test
   void singleParameterUnchanged() {
      assertEquals("/v4/projects/1/issues?iids[]=1",
                   new SuffixTemplate("/v4/projects/1/issues?iids[]=1").build());
   }

   @Test
   void noQueryUnchanged() {
      assertEquals("/v4/projects/1/issues",
                   new SuffixTemplate("/v4/projects/1/issues").build());
   }

   @Test
   void repeatedNameWithVariablesEmitsBoth() {
      SuffixTemplate t = new SuffixTemplate("/x?a={x}&b=1&a={y}");
      t.setVariable("x", "1");
      t.setVariable("y", "2");
      assertEquals("/x?a=1&b=1&a=2", t.build());
   }

   @Test
   void repeatedNameWithUnsetOptionalVariableEmitsOther() {
      SuffixTemplate t = new SuffixTemplate("/x?a={x?}&a={y}");
      t.setVariable("y", "2");
      assertEquals("/x?a=2", t.build());
   }

   @Test
   void repeatedNameWithMissingRequiredVariableThrows() {
      SuffixTemplate t = new SuffixTemplate("/x?a={x}&a={y}");
      t.setVariable("y", "2");
      assertThrows(IllegalStateException.class, t::build);
   }

   @Test
   void suffixAndAdditionalParameterWithSameNameAreBothSent() {
      SuffixTemplate t = new SuffixTemplate("/x?k=1");
      t.withAdditionalParameters(new HttpParameter[] { HttpParameter.builder().name("k").value("2").build() });
      assertEquals("/x?k=1&k=2", t.build());
   }

   @Test
   void getQueryIsLastWinsView() {
      Map<String, TemplateComponent> query =
         new SuffixTemplate("/x?a=1&b=2&a=3").getQuery();
      assertEquals(2, query.size());
      assertEquals("3", query.get("a").getValue());
      assertEquals("2", query.get("b").getValue());
   }

   @Test
   void paginationParameterReplacesAllSameNameValuesAndKeepsRepeatedOthers() throws Exception {
      String built = new SuffixTemplate("/x?page=2&page=3&x=1&x=2").build();
      String url = new URIBuilder("http://h" + built).setParameter("page", "5").toString();
      assertEquals(1, count(url, "page="));
      assertTrue(url.contains("page=5"));
      assertTrue(url.contains("x=1") && url.contains("x=2"));
   }

   private static int count(String s, String sub) {
      int n = 0;

      for(int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) {
         n++;
      }

      return n;
   }
}
