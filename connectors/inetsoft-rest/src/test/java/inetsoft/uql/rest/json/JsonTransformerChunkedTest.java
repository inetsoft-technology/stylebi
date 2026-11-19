/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chunked InfluxDB v1 query answers with one JSON object per series on separate lines. All of
 * them must be read, not just the first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTransformerChunkedTest {
   private static final String PATH = "$.results[0].series[*].values[*]";

   private static InputStream stream(String json) {
      return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
   }

   private static String series(String host, int... values) {
      final StringBuilder rows = new StringBuilder();

      for(int v : values) {
         rows.append(rows.length() == 0 ? "" : ",").append("[\"t\",").append(v).append("]");
      }

      return "{\"results\":[{\"statement_id\":0,\"series\":[{\"name\":\"cpu\",\"tags\":{\"host\":\""
         + host + "\"},\"columns\":[\"time\",\"usage_user\"],\"values\":[" + rows + "]}]}]}";
   }

   @Test
   void chunkedResponseReadsEverySeries() {
      final String body = series("db-1", 1, 2) + "\n" + series("db-2", 3) + "\n" +
         series("db-3", 4, 5, 6) + "\n";

      final List<?> rows = (List<?>) new JsonTransformer().transform(stream(body), PATH);

      assertEquals(6, rows.size());
   }

   @Test
   void chunkedResponseKeepsSeriesAsOneList() throws IOException {
      final String body = series("db-1", 1) + "\n" + series("db-2", 2) + "\n";

      final List<?> series = (List<?>) new JsonTransformer()
         .transform(stream(body), "$.results[0].series");

      assertEquals(2, series.size());
   }

   @Test
   void unchunkedResponseIsUnchanged() {
      final String body = "{\"results\":[{\"statement_id\":0,\"series\":["
         + "{\"name\":\"cpu\",\"values\":[[\"t\",1],[\"t\",2]]},"
         + "{\"name\":\"cpu\",\"values\":[[\"t\",3]]}]}]}\n";

      final List<?> rows = (List<?>) new JsonTransformer().transform(stream(body), PATH);

      assertEquals(3, rows.size());
   }

   @Test
   void otherJsonOnlyReadsFirstValue() {
      final Object json = new JsonTransformer().transform(stream("{\"a\":1}\n{\"a\":2}"));

      assertEquals(1, ((java.util.Map<?, ?>) json).get("a"));
   }

   @Test
   void chunkedResponseWithNonResultsValueFailsLoudly() {
      final String body = series("db-1", 1) + "\n{\"error\":\"boom\"}\n";

      assertThrows(RuntimeException.class,
                   () -> new JsonTransformer().transform(stream(body), PATH));
   }
}
