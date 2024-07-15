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

import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JsonProvider;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.json.JsonTransformer;
import inetsoft.uql.rest.json.RestDataIteratorStrategyFactory;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.util.BaseJsonTable;
import inetsoft.uql.util.LookupList;
import inetsoft.uql.util.LookupMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;

/**
 * Query runner for {@link EndpointJsonQuery}. Handles recursively processing all selected lookup queries and adding
 * their responses to the primary response object.
 */
public class EndpointJsonQueryRunner extends RestJsonQueryRunner {
   public EndpointJsonQueryRunner(EndpointJsonQuery<?> query,
                                  RestDataIteratorStrategyFactory<RestJsonQuery, Object> factory,
                                  LookupService lookupService,
                                  JsonTransformer transformer)
   {
      super(query, factory, lookupService, transformer);
   }

   @Override
   public XTableNode runStream() {
      final BaseJsonTable table = getTable();
      table.beginStreamedLoading();

      try(RestDataIteratorStrategy<Object> strategy = factory.createStrategy(query)) {
         strategy.setLiveMode(isLiveMode());

         while(hasNext(strategy, table)) {
            final Object json = strategy.next();

            if(json == null) {
               break;
            }

            doLookups(query, json);

            final Object selectedData = selectData(json, query.getValidJsonPath(), transformer);
            table.loadStreamed(selectedData);
         }
      }
      catch(Exception ex) {
         if(!(ex instanceof InterruptedException) || !isCancelled()) {
            logException(ex);
         }
      }

      return table;
   }
}
