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
package inetsoft.uql.rest.datasource.servicenow;

import inetsoft.test.*;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.rest.json.JsonPagedOffsetIteratorStrategy;
import inetsoft.uql.rest.json.JsonTransformer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EndpointsSpringContext
@SreeHome
@Tag("core")
class ServiceNowOneRecordPageTest {
   @Test
   void singleRecordResultIsReturnedFromOffsetZero() throws Exception {
      Result r = run("{\"result\":[{\"a\":1}]}", "{\"result\":[]}");
      assertEquals(1, r.count);
      assertEquals(List.of("0", "1"), r.offsets);
   }

   @Test
   void offsetsAdvanceByRecordCount() throws Exception {
      Result r = run("{\"result\":[{\"a\":1},{\"a\":2},{\"a\":3}]}",
                     "{\"result\":[{\"a\":4},{\"a\":5}]}", "{\"result\":[]}");
      assertEquals(2, r.count);
      assertEquals(List.of("0", "3", "5"), r.offsets);
   }

   private Result run(String... pages) throws Exception {
      ServiceNowQuery query = new ServiceNowQuery();
      ServiceNowDataSource ds = new ServiceNowDataSource();
      ds.setURL("http://host");
      query.setDataSource(ds);
      query.setEndpoint("Standard Changes");

      List<String> offsets = new ArrayList<>();
      IHttpHandler handler = new IHttpHandler() {
         @Override
         public HttpResponse executeRequest(RestRequest request) {
            offsets.add(request.queryParameters().get("sysparm_offset"));
            return new TestHttpResponse(pages[offsets.size() - 1]);
         }

         @Override
         public void close() {
         }
      };

      JsonPagedOffsetIteratorStrategy strategy = new JsonPagedOffsetIteratorStrategy(
         query, new JsonTransformer(), handler, new RestErrorHandler());
      int count = 0;

      while(strategy.hasNext()) {
         assertNotNull(strategy.next());
         count++;
      }

      return new Result(count, offsets);
   }

   private static final class Result {
      Result(int count, List<String> offsets) {
         this.count = count;
         this.offsets = offsets;
      }

      final int count;
      final List<String> offsets;
   }
}
