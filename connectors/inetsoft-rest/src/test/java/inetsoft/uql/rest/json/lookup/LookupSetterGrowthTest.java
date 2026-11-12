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
package inetsoft.uql.rest.json.lookup;

import inetsoft.test.*;
import inetsoft.uql.rest.json.RestJsonQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The indexed lookup setters grow their list through the overridable addLookupQuery(). A query
 * whose override does not grow the list (an EndpointJsonQuery with no lookups) must make the
 * setter return, not loop forever.
 *
 * <p>The timeout runs the call on its own thread and abandons it, so a regression fails the test
 * instead of hanging the build; the abandoned thread keeps spinning until the JVM exits.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class LookupSetterGrowthTest {
   private static final Duration LIMIT = Duration.ofSeconds(5);

   @Test
   void endpointQuerySetLookupIgnoreBaseUrlReturns() {
      TestQuery query = new TestQuery();

      assertTimeoutPreemptively(LIMIT, () -> query.setLookupIgnoreBaseUrl(0, true));

      assertFalse(query.getLookupIgnoreBaseUrl(0));
   }

   @Test
   void endpointQuerySetLookupUrlReturns() {
      TestQuery query = new TestQuery();

      assertTimeoutPreemptively(LIMIT, () -> query.setLookupURL(0, "http://x"));

      assertNull(query.getLookupURL(0));
   }

   @Test
   void endpointQuerySetLookupJsonPathReturns() {
      TestQuery query = new TestQuery();

      assertTimeoutPreemptively(LIMIT, () -> query.setLookupJsonPath(0, "$.a"));

      assertNull(query.getLookupJsonPath(0));
   }

   @Test
   void endpointQuerySetLookupKeyReturns() {
      TestQuery query = new TestQuery();

      assertTimeoutPreemptively(LIMIT, () -> query.setLookupKey(0, "k"));

      assertNull(query.getLookupKey(0));
   }

   @Test
   void restQueryStillGrowsAndStoresIndexesBelowTheLimit() {
      RestJsonQuery query = new RestJsonQuery();

      for(int i = 0; i < 5; i++) {
         query.setLookupURL(i, "u" + i);
         query.setLookupJsonPath(i, "p" + i);
         query.setLookupKey(i, "k" + i);
         query.setLookupIgnoreBaseUrl(i, true);
      }

      for(int i = 0; i < 5; i++) {
         assertEquals("u" + i, query.getLookupURL(i));
         assertEquals("p" + i, query.getLookupJsonPath(i));
         assertEquals("k" + i, query.getLookupKey(i));
         assertTrue(query.getLookupIgnoreBaseUrl(i));
      }
   }

   @Test
   void restQueryIgnoresIndexAtTheLimit() {
      RestJsonQuery query = new RestJsonQuery();

      query.setLookupURL(5, "u");
      query.setLookupJsonPath(5, "p");
      query.setLookupKey(5, "k");
      query.setLookupIgnoreBaseUrl(5, true);

      assertNull(query.getLookupURL(5));
      assertNull(query.getLookupJsonPath(5));
      assertNull(query.getLookupKey(5));
      assertFalse(query.getLookupIgnoreBaseUrl(5));
   }

   @Test
   void restQueryGrowsAListThatIsLongerThanTheUrlList() {
      RestJsonQuery query = new RestJsonQuery();
      query.setLookupURL(0, "u0");
      // trims only the url list; the other lists keep their slot 0
      query.setLookupURL(0, null);

      query.setLookupJsonPath(1, "p1");
      query.setLookupKey(1, "k1");

      assertEquals("p1", query.getLookupJsonPath(1));
      assertEquals("k1", query.getLookupKey(1));
   }
}
