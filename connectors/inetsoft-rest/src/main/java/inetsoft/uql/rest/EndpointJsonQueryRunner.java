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
import inetsoft.sree.SreeEnv;
import inetsoft.uql.util.BaseJsonTable;
import inetsoft.uql.util.JsonRowSampler;
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
         boolean sampled = false;

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

            // Sample the ROWS, and note that every "before" in the comment above is an "after" here.
            // The shape answers where the rows are; the sample answers what is in them, so it has to
            // be taken from what jsonPath actually selected and after doLookups has grafted the
            // lookup fields in -- otherwise it would report values the built table does not have.
            //
            // Still the FIRST page only, and for a different reason than the shape: later pages are
            // more of the same data, and this is a sample rather than a read. Also before
            // loadStreamed, because that flattens nested objects into columns (JsonTable.walkRecord)
            // and the response's own structure cannot be recovered from the result.
            if(!sampled) {
               sampled = true;
               sampleRows(selectedData);
            }

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

   /**
    * Record a bounded sample of the selected rows on the query, swallowing anything that goes wrong
    * — a by-product, on the same terms as {@link #shapeResponse}.
    *
    * <p>NOTHING IS SAMPLED UNLESS THE CALLER ASKED. {@code TabularQuery.getSampleRowLimit} is 0 by
    * default, so every path that does not set it — the composer dialog, every later render, any
    * caller that only wanted columns — runs exactly the request it ran before and pays for exactly
    * the response it did before. Rows are returned to whoever asked for them and are spent tokens
    * in that answer, which makes the count the caller's decision rather than a default.</p>
    *
    * <p>{@code rest.sample.rows} is the CEILING on that decision, and at {@code 0} the switch that
    * stops any response value leaving the connector no matter what is requested. So the deployment
    * bounds the exposure and the caller bounds the cost; neither can override the other. A value
    * that is not a number lands in the catch below and yields no sample, which is the right way to
    * be wrong about a knob that governs customer data.</p>
    */
   private void sampleRows(Object selectedData) {
      try {
         int requested = query.getSampleRowLimit();

         if(requested <= 0) {
            return;
         }

         int ceiling = Integer.parseInt(
            SreeEnv.getProperty(SAMPLE_ROWS_PROPERTY,
                                Integer.toString(JsonRowSampler.DEFAULT_MAX_ROWS)));

         if(ceiling <= 0) {
            return;
         }

         JsonRowSampler.Result result =
            JsonRowSampler.sample(selectedData, Math.min(requested, ceiling));
         query.setSampleRows(result.getRows(), result.isTruncated());
      }
      catch(Exception ex) {
         LOG.debug("Failed to sample rows for " + query.getClass().getSimpleName(), ex);
      }
   }

   /**
    * The most rows a caller may have reported back, and at 0 the switch that turns sampling off
    * entirely. A ceiling, not a default: a caller that asks for nothing gets nothing.
    */
   private static final String SAMPLE_ROWS_PROPERTY = "rest.sample.rows";

   private static final Logger LOG =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
