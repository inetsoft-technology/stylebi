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
package inetsoft.uql.rest.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.datasource.graphql.GraphQLDataSource;
import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.tabular.HttpParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.util.Map;

/**
 * Strategy takes the page number and inserts it into the GraphQL variables
 */
public class GraphQLIteratorStrategy extends HttpRestDataIteratorStrategy<GraphQLQuery, JsonTransformer> {

   public GraphQLIteratorStrategy(GraphQLQuery query,
                                  JsonTransformer transformer,
                                  IHttpHandler httpHandler,
                                  RestErrorHandler errorHandler)
   {
      super(query, transformer, httpHandler, errorHandler);
      final PaginationSpec spec = query.getPaginationSpec();
      pageNumber = spec.getFirstPageIndex();
   }

   @Override
   protected Object fetchNextResult() throws Exception {
      final PaginationSpec spec = query.getPaginationSpec();

      try {
         final ObjectMapper mapper = new ObjectMapper();

         if(query.isPostRequest()) {
            String variables = query.getVariables();
            final Map<String, Object> vars =
               mapper.readValue(variables, new TypeReference<Map<String, Object>>() {});
            vars.put(spec.getPageNumberParamToWrite().getValue(), pageNumber);
            query.setVariables(mapper.writeValueAsString(vars));
         }
         else {
            final GraphQLDataSource dataSource = (GraphQLDataSource) query.getDataSource();
            final HttpParameter[] queryHttpParameters = dataSource.getQueryHttpParameters();

            for(HttpParameter queryHttpParameter : queryHttpParameters) {
               if(GraphQLDataSource.VARIABLE_KEY.equals(queryHttpParameter.getName())) {
                  final String variables = queryHttpParameter.getValue();
                  final Map<String, Object> vars =
                     mapper.readValue(variables, new TypeReference<Map<String, Object>>() {});
                  vars.put(spec.getPageNumberParamToWrite().getValue(), pageNumber);
                  queryHttpParameter.setValue(mapper.writeValueAsString(vars));
               }
            }
         }
      }
      catch(IOException e) {
         LOG.error("Failed to parse JSON from GraphQL variables", e);
      }

      pageNumber++;
      final RestRequest request = RestRequest.builder().query(query).build();
      final Object json = handleRequest(request).getHandledResult();

      if(json != null && isEmpty(json, spec)) {
         return null;
      }

      return json;
   }

   private boolean isEmpty(Object json, PaginationSpec spec) {
      try {
         Object result = transformer.transform(json, spec.getRecordCountPath());

         if(result != null) {
            if(result.getClass().isArray()) {
               int size = Array.getLength(result);

               if(size > 0) {
                  Object value = Array.get(result, 0);

                  if(value instanceof Number) {
                     return ((Number) value).intValue() == 0;
                  }
               }
            }
            else if(result instanceof Number) {
               return ((Number) result).intValue() == 0;
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to determine if results are empty", e);
         return true;
      }

      return true;
   }

   private int pageNumber;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
