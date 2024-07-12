/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.auth;

import inetsoft.uql.rest.json.RestJsonDataSource;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TwoStepAuthTest {
   private CloseableHttpAsyncClient client;

   @BeforeEach
   public void createClient() {
      client = HttpAsyncClients.createDefault();
      client.start();
   }

   @AfterEach
   public void closeClient() throws IOException {
      client.close();
   }

   @Test
   void validCreation() {
      final RestJsonDataSource datasource = createValidDatasource();
      RestAuthenticatorFactory.createFrom(datasource, client);
   }

   @Test
   void emptyAuthURL() {
      final RestJsonDataSource datasource = createValidDatasource();
      datasource.setAuthURL(null);

      assertThrows(
         IllegalArgumentException.class,
         () -> RestAuthenticatorFactory.createFrom(datasource, client));
   }

   @Test
   public void emptyTokenPattern() {
      final RestJsonDataSource datasource = createValidDatasource();
      datasource.setTokenPattern(null);

      assertThrows(
         IllegalArgumentException.class,
         () -> RestAuthenticatorFactory.createFrom(datasource, client));
   }

   private RestJsonDataSource createValidDatasource() {
      final RestJsonDataSource restJsonDataSource = new RestJsonDataSource();
      restJsonDataSource.setAuthType(AuthType.TWO_STEP);
      restJsonDataSource.setAuthURL("auth url");
      restJsonDataSource.setTokenPattern("token");
      return restJsonDataSource;
   }
}
