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
package inetsoft.uql.rest.datasource.teamdesk;

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
class TeamDeskQueryFirstPageOffsetTest {
   @Test
   void offsetsStartAtZeroAndAdvanceByRecordCount() throws Exception {
      TeamDeskQuery query = new TeamDeskQuery();
      TeamDeskDataSource ds = new TeamDeskDataSource();
      ds.setURL("http://host");
      query.setDataSource(ds);
      query.setEndpoint("Select Table");
      RestParameters params = query.getParameters();

      for(RestParameter p : params.getParameters()) {
         if("Table Name".equals(p.getName())) {
            p.setValue("t");
         }
      }

      query.setParameters(params);

      String[] pages = { "[1,2,3]", "[4,5]", "[]" };
      List<String> skips = new ArrayList<>();
      IHttpHandler handler = new IHttpHandler() {
         @Override
         public HttpResponse executeRequest(RestRequest request) {
            skips.add(request.queryParameters().get("skip"));
            return new TestHttpResponse(pages[skips.size() - 1]);
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

      assertEquals(2, count);
      assertEquals(List.of("0", "3", "5"), skips);
   }
}
