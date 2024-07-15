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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TemplateParserTest {
   private List<TemplateComponent> pathComponents;
   private Map<String, TemplateComponent> queryComponents;
   private TemplateParser parser;

   @BeforeEach
   void createParser() {
      pathComponents = new ArrayList<>();
      queryComponents = new HashMap<>();
      parser = new TemplateParser(pathComponents, queryComponents);
   }

   @Test
   void givenPathOnlyShouldParse() {
      String template = "static/{Parent}/{Child:placeholder}";
      parser.parse(template);
      List<TemplateComponent> expected = Arrays.asList(
         new TemplateComponent(false, false, false, "static", null, null),
         new TemplateComponent(true, true, false, "Parent", null, null),
         new TemplateComponent(true, true, false, "Child", null, null)
      );
      assertAll(
         () -> assertEquals(expected, pathComponents),
         () -> assertTrue(queryComponents.isEmpty())
      );
   }

   @Test
   void givenPathOnlyWhenOptionalShouldParse() {
      String template = "static/{Parent}/{Child?:placeholder}";
      parser.parse(template);
      List<TemplateComponent> expected = Arrays.asList(
         new TemplateComponent(false, false, false, "static", null, null),
         new TemplateComponent(true, true, false, "Parent", null, null),
         new TemplateComponent(true, false, false, "Child", null, null)
      );
      assertAll(
         () -> assertEquals(expected, pathComponents),
         () -> assertTrue(queryComponents.isEmpty())
      );
   }

   @Test
   void givenPathOnlyWhenExtensionShouldParse() {
      String template = "static/{Parent}/{Child}.json";
      parser.parse(template);
      List<TemplateComponent> expected = Arrays.asList(
         new TemplateComponent(false, false, false, "static", null, null),
         new TemplateComponent(true, true, false, "Parent", null, null),
         new TemplateComponent(true, true, false, "Child", null, ".json")
      );
      assertAll(
         () -> assertEquals(expected, pathComponents),
         () -> assertTrue(queryComponents.isEmpty())
      );
   }

   @Test
   void givenQueryOnlyShouldParse() {
      String template = "?static=1&var1={First}&var2={Second & Third?,:val1,val2,...}";
      parser.parse(template);
      Map<String, TemplateComponent> expected = new HashMap<>();
      expected.put("static", new TemplateComponent(false, false, false, "1", null, null));
      expected.put("var1", new TemplateComponent(true, true, false, "First", null, null));
      expected.put("var2", new TemplateComponent(true, false, true, "Second & Third", null, null));
      assertAll(
         () -> assertTrue(pathComponents.isEmpty()),
         () -> assertEquals(expected, queryComponents)
      );
   }

   @Test
   void givenPathAndQueryShouldParse() {
      String template = "static/{Parent}/{Child?:placeholder}?static=1&var1={First}&var2={Second & Third?,:val1,val2,...}";
      parser.parse(template);
      List<TemplateComponent> expectedPath = Arrays.asList(
         new TemplateComponent(false, false, false, "static", null, null),
         new TemplateComponent(true, true, false, "Parent", null, null),
         new TemplateComponent(true, false, false, "Child", null, null)
      );
      Map<String, TemplateComponent> expectedQuery = new HashMap<>();
      expectedQuery.put("static", new TemplateComponent(false, false, false, "1", null, null));
      expectedQuery.put("var1", new TemplateComponent(true, true, false, "First", null, null));
      expectedQuery.put("var2", new TemplateComponent(true, false, true, "Second & Third", null, null));
      assertAll(
         () -> assertEquals(expectedPath, pathComponents),
         () -> assertEquals(expectedQuery, queryComponents)
      );
   }
}
