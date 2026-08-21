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
import inetsoft.uql.util.JsonShapeDistiller;
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
         strategy.setTouchTimestamp(getTouchTimestamp());

         boolean shaped = false;

         while(hasNext(strategy, table)) {
            final Object json = strategy.next();

            if(json == null) {
               break;
            }

            // Distil the response's SHAPE before jsonPath narrows it, and before doLookups mutates
            // it by grafting lookup responses in. Three things make this the right point and no
            // other:
            //
            //  - It costs NO EXTRA REQUEST. This page has already been fetched and paid for. The
            //    alternative -- RestJsonQuery.getJsonMetadata() -- dials the endpoint a second time
            //    from inside a getter, caches a failure permanently as "{}", persists real records
            //    into the worksheet XML, and changes the column set by merging the metadata table's
            //    own headers in. None of that applies here.
            //  - It is BEFORE selectData, so a wrong jsonPath cannot corrupt it. That is the point:
            //    when the caller guessed the row path wrong and built a table of envelope columns,
            //    this shape is what tells it where the rows actually are.
            //  - It is the FIRST page only. Later pages repeat the same structure, and a shape is a
            //    statement about the endpoint rather than about how much of it was read.
            //
            // Values are dropped by the distiller, never carried here: the shape is a property of
            // the connector, the body is customer data. See JsonShapeDistiller.
            if(!shaped) {
               shaped = true;
               shapeResponse(json);
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

   /**
    * Record the response shape on the query, swallowing anything that goes wrong.
    *
    * <p>The shape is a BY-PRODUCT. A malformed response, an unexpected object model, a cap that
    * fires -- none of it may cost the caller the table it asked for, which is the thing it actually
    * requested and already paid a request for. So a failure here is logged and dropped, and the
    * caller simply sees no shape.</p>
    */
   private void shapeResponse(Object json) {
      try {
         JsonShapeDistiller.Result result = JsonShapeDistiller.distill(json);
         query.setResponseShape(result.getShape(), result.isTruncated());
      }
      catch(Exception ex) {
         LOG.debug("Failed to distil the response shape for " + query.getClass().getSimpleName(), ex);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
