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

import inetsoft.test.TestEndpoint;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EndpointJsonQueryTest {
   private TestQuery query;

   @BeforeEach
   void setUp() {
      query = new TestQuery();
   }

   @Test
   void getParametersWithoutTokensReturnsEmpty() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Empty");
      expected.getParameters(); // ensure parameter list is instantiated
      query.setEndpoint("Empty");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Empty", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithoutTokensReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Empty");
      query.setEndpoint("Empty");
      query.setParameters(parameters);
      String expected = "/parents";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithEntirePathOneTokenReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Path");
      expected.getParameters().add(createParentId());
      query.setEndpoint("Path");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Path", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithEntirePathOneTokenReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Path");
      parameters.getParameters().add(createParentId());
      parameters.findParameter("Parent ID").setValue("100");
      query.setEndpoint("Path");
      query.setParameters(parameters);
      String expected = "100";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithLeadingPathReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Leading Path");
      expected.getParameters().add(createParentId());
      query.setEndpoint("Leading Path");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Leading Path", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithLeadingPathReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Leading Path");
      parameters.getParameters().add(createParentId());
      parameters.findParameter("Parent ID").setValue("100");
      query.setEndpoint("Leading Path");
      query.setParameters(parameters);
      String expected = "100/children";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithMiddlePathReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Middle Path");
      expected.getParameters().add(createParentId());
      query.setEndpoint("Middle Path");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Middle Path", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithMiddlePathReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Middle Path");
      parameters.getParameters().add(createParentId());
      parameters.findParameter("Parent ID").setValue("100");
      query.setEndpoint("Middle Path");
      query.setParameters(parameters);
      String expected = "/parents/100/children";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithTrailingPathReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Trailing Path");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createChildId());
      query.setEndpoint("Trailing Path");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Trailing Path", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithTrailingPathReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Trailing Path");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createChildId());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Child ID").setValue("50");
      query.setEndpoint("Trailing Path");
      query.setParameters(parameters);
      String expected = "/parents/100/children/50";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingPathThrowsException() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Trailing Path");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createChildId());
      parameters.findParameter("Child ID").setValue("50");
      query.setEndpoint("Trailing Path");
      query.setParameters(parameters);
      assertThrows(IllegalStateException.class, () -> query.getSuffix());
   }

   @Test
   void getParametersWithQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Query");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      query.setEndpoint("Query");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Query", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Query");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      query.setEndpoint("Query");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingQueryThrowsException() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Query");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.findParameter("Parent ID").setValue("100");
      query.setEndpoint("Query");
      query.setParameters(parameters);
      assertThrows(IllegalStateException.class, () -> query.getSuffix());
   }

   @Test
   void getParametersWithOptionalQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Optional");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createDateCreated());
      query.setEndpoint("Optional");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Optional", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithOptionalQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createDateCreated());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Date Created").setValue("2020-01-01");
      query.setEndpoint("Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?created=2020-01-01";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingOptionalQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createDateCreated());
      parameters.findParameter("Parent ID").setValue("100");
      query.setEndpoint("Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithOptionalLeadingQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Leading Optional");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createDateCreated());
      expected.getParameters().add(createActive());
      query.setEndpoint("Leading Optional");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Leading Optional", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithOptionalLeadingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Leading Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createDateCreated());
      parameters.getParameters().add(createActive());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Date Created").setValue("2020-01-01");
      parameters.findParameter("Active").setValue("true");
      query.setEndpoint("Leading Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?created=2020-01-01&active=true";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingLeadingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Leading Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createDateCreated());
      parameters.getParameters().add(createActive());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      query.setEndpoint("Leading Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithOptionalMiddleQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Middle Optional");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      expected.getParameters().add(createDateCreated());
      expected.getParameters().add(createFlag());
      query.setEndpoint("Middle Optional");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Middle Optional", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithOptionalMiddleQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Middle Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createDateCreated());
      parameters.getParameters().add(createFlag());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Date Created").setValue("2020-01-01");
      parameters.findParameter("Active").setValue("true");
      parameters.findParameter("Other Flag").setValue("n");
      query.setEndpoint("Middle Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true&created=2020-01-01&flag=n";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingOptionalMiddleQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Middle Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createDateCreated());
      parameters.getParameters().add(createFlag());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      parameters.findParameter("Other Flag").setValue("n");
      query.setEndpoint("Middle Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true&flag=n";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithOptionalTrailingQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Trailing Optional");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      expected.getParameters().add(createDateCreated());
      query.setEndpoint("Trailing Optional");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Trailing Optional", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithOptionalTrailingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Trailing Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createDateCreated());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      parameters.findParameter("Date Created").setValue("2020-01-01");
      query.setEndpoint("Trailing Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true&created=2020-01-01";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getSuffixWithMissingOptionalTrailingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Trailing Optional");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createDateCreated());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      query.setEndpoint("Trailing Optional");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithSplitQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Split");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createSplit());
      query.setEndpoint("Split");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Split", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithSplitQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Split");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createSplit());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("State").setValue("open,closed");
      query.setEndpoint("Split");
      query.setParameters(parameters);
      String expected = "/parents/100/children?state=open&state=closed";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithLeadingSplitQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Leading Split");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createSplit());
      expected.getParameters().add(createActive());
      query.setEndpoint("Leading Split");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Leading Split", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithSplitLeadingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Leading Split");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createSplit());
      parameters.getParameters().add(createActive());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("State").setValue("open,closed");
      parameters.findParameter("Active").setValue("true");
      query.setEndpoint("Leading Split");
      query.setParameters(parameters);
      String expected = "/parents/100/children?state=open&state=closed&active=true";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithMiddleSplitQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Middle Split");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      expected.getParameters().add(createSplit());
      expected.getParameters().add(createDateCreated());
      query.setEndpoint("Middle Split");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Middle Split", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithMiddleLeadingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Middle Split");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createSplit());
      parameters.getParameters().add(createDateCreated());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      parameters.findParameter("State").setValue("open,closed");
      parameters.findParameter("Date Created").setValue("2020-01-01");
      query.setEndpoint("Middle Split");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true&state=open&state=closed&created=2020-01-01";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithTrailingSplitQueryReturnsValid() {
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Trailing Split");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      expected.getParameters().add(createSplit());
      query.setEndpoint("Trailing Split");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Trailing Split", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   @Test
   void getSuffixWithTrailingLeadingQueryReturnsValid() {
      RestParameters parameters = new RestParameters();
      parameters.setEndpoint("Trailing Split");
      parameters.getParameters().add(createParentId());
      parameters.getParameters().add(createActive());
      parameters.getParameters().add(createSplit());
      parameters.findParameter("Parent ID").setValue("100");
      parameters.findParameter("Active").setValue("true");
      parameters.findParameter("State").setValue("open,closed");
      query.setEndpoint("Trailing Split");
      query.setParameters(parameters);
      String expected = "/parents/100/children?active=true&state=open&state=closed";
      String actual = query.getSuffix();
      assertEquals(expected, actual);
   }

   @Test
   void getParametersWithBundleReturnsValid() throws IOException {
      ResourceBundle bundle = new PropertyResourceBundle(new StringReader(BUNDLE));
      query.setBundle(bundle);
      RestParameters expected = new RestParameters();
      expected.setEndpoint("Trailing Optional");
      expected.getParameters().add(createParentId());
      expected.getParameters().add(createActive());
      expected.getParameters().add(createDateCreated());
      expected.findParameter("Parent ID").setLabel("Localized Parent ID");
      expected.findParameter("Active").setLabel("Localized Active");
      expected.findParameter("Date Created").setLabel("Localized Date Created");
      query.setEndpoint("Trailing Optional");
      RestParameters actual = query.getParameters();
      assertAll(
         () -> assertEquals("Trailing Optional", query.getEndpoint()),
         () -> assertEquals(expected, actual));
   }

   private RestParameter createParentId() {
      RestParameter parameter = new RestParameter();
      parameter.setName("Parent ID");
      parameter.setLabel("Parent ID");
      parameter.setRequired(true);
      parameter.setPlaceholder("0");
      return parameter;
   }

   private RestParameter createChildId() {
      RestParameter parameter = new RestParameter();
      parameter.setName("Child ID");
      parameter.setLabel("Child ID");
      parameter.setRequired(true);
      parameter.setPlaceholder("0");
      return parameter;
   }

   private RestParameter createActive() {
      RestParameter parameter = new RestParameter();
      parameter.setName("Active");
      parameter.setLabel("Active");
      parameter.setRequired(true);
      parameter.setPlaceholder("true/false");
      return parameter;
   }

   private RestParameter createDateCreated() {
      RestParameter parameter = new RestParameter();
      parameter.setName("Date Created");
      parameter.setLabel("Date Created");
      parameter.setRequired(false);
      parameter.setPlaceholder("yyyy-MM-dd");
      return parameter;
   }

   private RestParameter createFlag() {
      RestParameter parameter = new RestParameter();
      parameter.setName("Other Flag");
      parameter.setLabel("Other Flag");
      parameter.setRequired(true);
      parameter.setPlaceholder("y/n");
      return parameter;
   }

   private RestParameter createSplit() {
      RestParameter parameter = new RestParameter();
      parameter.setName("State");
      parameter.setLabel("State");
      parameter.setRequired(true);
      parameter.setSplit(true);
      parameter.setPlaceholder("open,closed,deferred");
      return parameter;
   }

   private static final class TestEndpoints extends EndpointJsonQuery.Endpoints<TestEndpoint> {
   }

   private static final class TestQuery extends EndpointJsonQuery<TestEndpoint> {
      public TestQuery() {
         super("TEST");
      }

      public void setBundle(ResourceBundle bundle) {
         this.bundle = bundle;
      }

      @Override
      protected ResourceBundle getResourceBundle(Locale locale) {
         return bundle == null ? super.getResourceBundle(locale) : bundle;
      }

      @Override
      public Map<String, TestEndpoint> getEndpointMap() {
         return Singleton.INSTANCE.endpoints;
      }

      private ResourceBundle bundle = null;

      enum Singleton {
         INSTANCE;
         Map<String, TestEndpoint> endpoints = Endpoints.load(TestEndpoints.class);
      }
   }

   private static final String BUNDLE = "Parent\\ ID=Localized Parent ID\n" +
      "Child\\ ID=Localized Child ID\n" +
      "Active=Localized Active\n" +
      "Date\\ Created=Localized Date Created\n" +
      "Other\\ Flag=Localized Other Flag";
}