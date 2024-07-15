/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.json;

import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

class RestJsonDataSourceTest {
   @Test
   void nullSerialization() {
      final RestJsonDataSource ds = new RestJsonDataSource();

      ds.setURL(null);
      ds.setPassword(null);
      ds.setAuthURL(null);
      ds.setAuthenticationHttpParameters(new HttpParameter[] {null});
      ds.setContentType(null);
      ds.setBody(null);
      ds.setTokenPattern(null);
      ds.setQueryHttpParameters(new HttpParameter[]{null});

      final PrintWriter writer = new PrintWriter(new StringWriter());
      ds.writeContents(writer);
      // No exception should be thrown.
   }
}
