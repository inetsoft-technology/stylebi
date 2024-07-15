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
package inetsoft.uql.rest;

import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractRestDataIteratorStrategy<Q extends AbstractRestQuery, T extends InputTransformer>
   implements RestDataIteratorStrategy<Object>
{
   protected AbstractRestDataIteratorStrategy(Q query, T transformer) {
      this.query = query;
      this.transformer = transformer;
   }

   /**
    * Check the query/header parameters for the max result parameter. If it's specified then use
    * that value for the max results instead of the value in the pagination spec.
    */
   protected int getMaxResultsPerPage(PaginationSpec spec) {
      PaginationParameter maxResultsParam = spec.getMaxResultsPerPageParam();

      if(!isEmpty(maxResultsParam)) {
         if(maxResultsParam.getType() == PaginationParamType.HEADER) {
            HttpParameter[] params = ((AbstractRestDataSource) query.getDataSource())
               .getQueryHttpParameters();
            Optional<HttpParameter> headerParam = Arrays.stream(params)
               .filter((param) -> param.getType() == HttpParameter.ParameterType.HEADER &&
                  maxResultsParam.getValue().equals(param.getName()) &&
                  !isEmpty(param.getValue())).findFirst();

            if(headerParam.isPresent()) {
               try {
                  return Integer.parseInt(headerParam.get().getValue());
               }
               catch(NumberFormatException e) {
                  LOG.error("Failed to parse the value of " + maxResultsParam.getValue() +
                               " header parameter.", e);
               }
            }
         }
         else {
            try {
               URL url = URLCreator.fromQuery(query);
               Map<String, String[]> queryParams = Tool.parseQueryString(url.getQuery());
               String[] values = queryParams.get(maxResultsParam.getValue());

               if(values != null && values.length > 0) {
                  return Integer.parseInt(values[0]);
               }
            }
            catch(Exception e) {
               LOG.error("Failed to parse the value of " + maxResultsParam.getValue() +
                            " query parameter.", e);
            }
         }
      }

      return spec.getMaxResultsPerPage();
   }

   /**
    * Checks if the max results parameter is already specified in the query/header parameters and
    * if not then adds it and sets its value to spec.getMaxResultsPerPage(). Requires the
    * maxResultsPerPageParam to be set in the pagination spec.
    */
   protected boolean addMaxResultsParameter(PaginationSpec spec) {
      if(!(query instanceof EndpointJsonQuery)) {
         return false;
      }

      EndpointJsonQuery<?> endpointJsonQuery = (EndpointJsonQuery<?>) query;
      PaginationParameter maxResultsParam = spec.getMaxResultsPerPageParam();

      if(!isEmpty(maxResultsParam)) {
         if(maxResultsParam.getType() == PaginationParamType.HEADER) {
            HttpParameter[] params = ((AbstractRestDataSource) query.getDataSource())
               .getQueryHttpParameters();
            Optional<HttpParameter> headerParam = Arrays.stream(params)
               .filter((param) -> param.getType() == HttpParameter.ParameterType.HEADER &&
                  maxResultsParam.getValue().equals(param.getName()) &&
                  !isEmpty(param.getValue())).findFirst();

            if(!headerParam.isPresent()) {
               AbstractRestDataSource dataSource = (AbstractRestDataSource) query.getDataSource();
               dataSource.setHttpParameter(maxResultsParam.getValue(),
                                           spec.getMaxResultsPerPage() + "",
                                           HttpParameter.ParameterType.HEADER);
               return true;
            }
         }
         else if(maxResultsParam.getType() == PaginationParamType.JSON_PATH &&
            query.getContentType().equals("application/json"))
         {
            String content = query.getRequestBody();
            content = transformer.updateOutputString(content, maxResultsParam.getValue(), spec.getMaxResultsPerPage());
            query.setRequestBody(content);
         }
         else {
            try {
               URL url = URLCreator.fromQuery(query);
               Map<String, String[]> queryParams = Tool.parseQueryString(url.getQuery());
               String[] values = queryParams.get(maxResultsParam.getValue());

               if(values == null || values.length == 0) {
                  HttpParameter[] parameters = endpointJsonQuery.getAdditionalParameters();
                  HttpParameter newParam = HttpParameter.builder()
                     .type(HttpParameter.ParameterType.QUERY)
                     .name(maxResultsParam.getValue())
                     .value(spec.getMaxResultsPerPage() + "")
                     .build();

                  if(parameters == null) {
                     parameters = new HttpParameter[]{ newParam };
                  }
                  else {
                     parameters = Arrays.copyOf(
                        parameters, parameters.length + 1);
                     parameters[parameters.length - 1] = newParam;
                  }

                  endpointJsonQuery.setAdditionalParameters(parameters);
                  return true;
               }
            }
            catch(Exception e) {
               // do nothing
            }
         }
      }

      return false;
   }

   protected boolean isEmpty(PaginationParameter param) {
      if(param == null) {
         return false;
      }

      return isEmpty(param.getValue());
   }

   protected boolean isEmpty(String s) {
      return s == null || s.isEmpty();
   }

   public T getTransformer() {
      return transformer;
   }

   public boolean isLiveMode() {
      return livemode;
   }

   public void setLiveMode(boolean livemode) {
      this.livemode = livemode;
   }

   @Override
   public boolean isLookup() {
      return lookup;
   }

   @Override
   public void setLookup(boolean lookup) {
      this.lookup = lookup;
   }

   protected RestErrorHandler errorHandler;

   protected final Q query;
   protected final T transformer;
   private boolean livemode;
   private boolean lookup;

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
