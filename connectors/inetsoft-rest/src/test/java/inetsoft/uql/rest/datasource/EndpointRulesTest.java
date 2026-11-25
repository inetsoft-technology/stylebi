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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins what each rule in {@link EndpointRules} accepts and rejects, with made-up input and no
 * Spring context, so a rule that is loosened by mistake fails here at once. The "bad" shapes are
 * the kinds of mistake found in the real endpoint definitions.
 */
class EndpointRulesTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "/v0/base/table/record",
      "/a?x=1",
      "/a?x=1&y=2",
      "/a?x=",
      "/a?tweet.fields=1&user.fields=1",
      "/a?conditions[x][from_date]=1&conditions[y]=2",
      "/a/b/c?fields=1"
   })
   void aWellFormedSuffixIsAccepted(String suffix) {
      assertNull(EndpointRules.malformed(suffix));
   }

   @ParameterizedTest
   @NullAndEmptySource
   void anEmptySuffixIsRejected(String suffix) {
      assertEquals("empty", EndpointRules.malformed(suffix));
   }

   @ParameterizedTest(name = "[{index}] {0} -> {1}")
   @CsvSource(delimiter = '|', value = {
      "/a?x=1&&y=2                | bad parameter",
      "/a?&x=1                    | bad parameter",
      "/a?x=1&                    | bad parameter",
      "/a?x                       | bad parameter",
      "/a?x{y}=1                  | bad parameter",
      "/a?{y}=                    | bad parameter",
      "/a?x=1?y=2                 | second '?'",
      "/a?                        | empty query string",
      "/a/{b}/c                   | bad path",
      "/a}?x=1                    | bad path",
      "/a//b?x=1                  | bad path",
      "/a/b&c?x=1                 | bad path"
   })
   void aMalformedPathOrQueryStringIsRejected(String suffix, String expected) {
      String problem = EndpointRules.malformed(suffix.trim());
      assertNotNull(problem, suffix);
      assertTrue(problem.contains(expected), problem);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "/a /b?x=1",
      "/a?x=1&\ny=2",
      "/a?x=1&\r\ny=2",
      "/a?x=1&\ty=2",
      "/a?x=1&End Date=2",
      "/a/b c?x=1"
   })
   void anyWhitespaceInASuffixIsRejected(String suffix) {
      String problem = EndpointRules.malformed(suffix);
      assertNotNull(problem, suffix);
      assertTrue(problem.startsWith("whitespace"), problem);
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "/v0/{Base ID}/{Table Name}/{Record ID}",
      "/v0/{Base ID}/{Table Name}?fields={Fields?:Name,Description,...}&view={View?}" +
         "&format={Cell Format?:string|json}",
      "/2/users/{User ID}?expansions={Expansions?}&tweet.fields={Tweet Fields?}",
      "/a?start={Start?:YYYY-MM-DDTHH:MM:SSZ}&n={N?}"
   })
   void aWellFormedTemplateBuilds(String template) {
      assertNull(EndpointRules.buildProblem(template));
   }

   /** The templates are shortened copies of ones that were wrong in the real definitions. */
   @ParameterizedTest
   @ValueSource(strings = {
      // missing '=' before a variable
      "/collections/suites?rsids={RSIDs?}&rsidContains{RSID Contains?}",
      // missing ':' before an option list
      "/2.0/search?query={Query}&content_types={Content Types?name|description|tag}",
      "/2.0/folders/trash/items?marker={Marker?}&usemarker={Use Marker?false|true}",
      // stray '}'
      "/crm-objects/v1/change-log/line_items}?timestamp={Timestamp?}",
      // '?' where '&' belongs, and missing '&'
      "/v1/deals/timeline?start_date={Start Date:YYYY-MM-DD}?interval={Interval:day|week}",
      "/reports/List?docnum={DocNum?}start_moddate={Start ModDate?}",
      "/now/stats/{Table Name}?sysparm_sum={Sum Fields?}sysparm_min_fields={Min Fields?}",
      // '?' in the middle of the path
      "/v2/cash-drawers/shifts/{Shift ID}?/events?location_id={Location ID}",
      // double slash and spaces in the path or in a parameter name
      "/v3.0//Contacts/{Contact ID}/FileAttachments?a={A?}",
      "/api/core/v3 /dms/{Direct Message ID}/comments?filter={Filter?}",
      "/v1/Alerts?StartDate={Start Date?}&End Date={EndDate?}",
      // empty parameters
      "/api/v2/sources?&org={Organization Name?}",
      "/content/api/v2/blog-posts?archived={Archived?:true|false}&&created={Created?}",
      // a variable with no parameter name
      "/2/users?{User IDs}&expansions={Expansions?}",
      // a newline after '&'
      "/v3/users.json?conditions[email]={Email Address?}&\nconditions[admin]={Admin?}"
   })
   void aTemplateWrittenWrongDoesNotBuildCleanly(String template) {
      assertNotNull(EndpointRules.buildProblem(template), template);
   }

   @Test
   void aTemplateThatCannotBeParsedIsReportedNotThrown() {
      assertNotNull(EndpointRules.buildProblem(""));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "/a/{X}/b",
      "/a/{X}?b={Y?}",
      "/a?x={Y}&z={W?}",
      // a name used twice is required if either use is: the optional use must not hide it
      "/collectors/{Collector ID}/responses?collector_id={Collector ID?}"
   })
   void requiredAndOptionalParametersAreHandledAsDeclared(String template) {
      assertEquals(List.of(), EndpointRules.requiredParameterProblems(template));
   }

   @ParameterizedTest
   @ValueSource(strings = { "Records", "Search for Content", "Q&A" })
   void aPlainNameIsAccepted(String name) {
      assertNull(EndpointRules.nameProblem(name));
   }

   @ParameterizedTest
   @NullAndEmptySource
   @ValueSource(strings = { "  " })
   void aBlankNameIsRejected(String name) {
      assertEquals("blank name", EndpointRules.nameProblem(name));
   }

   @ParameterizedTest
   @ValueSource(strings = { " Records", "Records ", "Contacts Informations", "Records " })
   void aNameWithSurroundingOrNonBreakingSpaceIsRejected(String name) {
      assertNotNull(EndpointRules.nameProblem(name), name);
   }

   @Test
   void aLookupToAMissingEndpointIsReported() {
      assertEquals(List.of("Gone"),
                   EndpointRules.unknownLookups(List.of("Here", "Gone"), Set.of("Here", "Other")));
      assertEquals(List.of(), EndpointRules.unknownLookups(List.of("Here"), Set.of("Here")));
      assertEquals(List.of(), EndpointRules.unknownLookups(List.of(), Set.of()));
   }
}
