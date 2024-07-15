/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import inetsoft.util.DataSpace;
import org.xmlunit.matchers.CompareMatcher;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;

public abstract class XmlProviderTestBase {
   protected InputStream openTestXml(String test) {
      return getClass().getResourceAsStream(
         String.format("%s.%s.xml", getClass().getSimpleName(), test));
   }

   protected void assertXmlEquals(String file, String test) throws IOException {
      try(InputStream expected = dataSpace.getInputStream(null, file);
          InputStream actual = openTestXml(test))
      {
         CompareMatcher matcher = CompareMatcher
            .isSimilarTo(expected)
            .ignoreComments()
            .ignoreWhitespace()
            .throwComparisonFailure();
         assertThat(actual, matcher);
      }
   }

   protected DataSpace dataSpace;
}
