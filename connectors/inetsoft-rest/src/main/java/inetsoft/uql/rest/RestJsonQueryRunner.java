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
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;

public class RestJsonQueryRunner extends AbstractQueryRunner<RestJsonQuery> {
    public RestJsonQueryRunner(RestJsonQuery query,
                               RestDataIteratorStrategyFactory<RestJsonQuery, Object> factory,
                               LookupService lookupService,
                               JsonTransformer transformer)
    {
        super(query);
        this.factory = factory;
       this.lookupService = lookupService;
        this.transformer = transformer;
        this.jsonProvider = JsonTransformer.getJsonProvider();
    }

    @Override
    public XTableNode runStream() {
        final BaseJsonTable table = getTable();
        table.beginStreamedLoading();

        try(RestDataIteratorStrategy<Object> strategy = factory.createStrategy(query)) {
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

    protected boolean hasNext(RestDataIteratorStrategy<Object> strategy, BaseJsonTable table) throws Exception {
        return !isCancelled() && (table.getMaxRows() <= 0 || table.size() < table.getMaxRows()) && strategy.hasNext();
    }

    protected void doLookups(RestJsonQuery query, Object json) throws Exception {
        for(JsonLookupQuery lookupQuery : query.getLookupQueries()) {
            final JsonLookupEndpoint endpoint = lookupQuery.getLookupEndpoint();
            // todo transformations can be memoized if endpoints repeat paths
            final Object transformedJson;

            try {
                transformedJson = transformer.transform(json, endpoint.jsonPath());
            }
            catch(PathNotFoundException e) {
                // Path is absent because it is empty or null
                return;
            }

            if(isCancelled()) {
                break;
            }

            if(jsonProvider.isMap(transformedJson)) {
                doLookupOnEntity(query, lookupQuery, transformedJson);
            }
            else if(jsonProvider.isArray(transformedJson)) {
                final BaseJsonTable table = getTable();
                final int maxRows = table.getMaxRows();
                final int lookups = jsonProvider.length(transformedJson);
                final int length = maxRows > 0 ? Math.min(maxRows - table.size(), lookups) : lookups;

                for(int i = 0; i < length; i++) {
                    final Object jsonEntity = jsonProvider.getArrayIndex(transformedJson, i);
                    final Object newEntity = doLookupOnEntity(query, lookupQuery, jsonEntity);

                    if(jsonEntity instanceof String) {
                       jsonProvider.setArrayIndex(json, i, newEntity);
                    }
                }
            }
        }
    }

    /**
     * @param lookupQuery the lookup query to do the lookup on.
     * @param jsonEntity  a json object on which to look for a lookup key.
     */
    private Object doLookupOnEntity(RestJsonQuery parentQuery, JsonLookupQuery lookupQuery,
                                  Object jsonEntity) throws Exception
    {
        RestJsonQuery queryFromLookupQuery;

        if(query instanceof EndpointJsonQuery) {
            final EndpointQuery endpointQuery =
               lookupService.createQueryFromLookupQuery((EndpointJsonQuery<?>) query,
                                                        (EndpointJsonQuery<?>) parentQuery,
                                                        lookupQuery, jsonEntity);

           if(!(endpointQuery instanceof EndpointJsonQuery<?>)) {
               return null;
           }

            queryFromLookupQuery = (EndpointJsonQuery<?>) endpointQuery;
        }
        else {
            queryFromLookupQuery = lookupService.createQueryFromLookupQuery(query, parentQuery,
                                                                lookupQuery, jsonEntity);
        }

        Object lookupJson = runLookupQuery(queryFromLookupQuery);

        if(jsonProvider.isMap(lookupJson) && queryFromLookupQuery.isExpanded()) {
            final int expandLevels = queryFromLookupQuery.isExpandTop() ? 1 : 0;
            final LookupMap lookupMap = new LookupMap(expandLevels);
            lookupMap.putAll(((Map<?, ?>) lookupJson));

            lookupJson = lookupMap;
        }
        else if(jsonProvider.isArray(lookupJson) && queryFromLookupQuery.isExpanded()) {
            final int expandLevels = queryFromLookupQuery.isExpandTop() ? 1 : 0;
            final LookupList lookupList = new LookupList(expandLevels);
            jsonProvider.toIterable(lookupJson).forEach(lookupList::add);

            lookupJson = lookupList;
        }

        // todo property name conflicts
        if(queryFromLookupQuery instanceof EndpointJsonQuery<?>) {
           jsonProvider.setProperty(jsonEntity, ((EndpointJsonQuery<?>)queryFromLookupQuery).getEndpoint(),
                                    lookupJson);
        }
       else if(!(jsonEntity instanceof String)) {
          jsonProvider.setProperty(jsonEntity,
                                   "lookup" + (queryFromLookupQuery.getLookupDepth()),
                                   lookupJson);
       }

       return lookupJson;
    }

    /**
     * Run the lookup query and return the result.
     */
    private Object runLookupQuery(RestJsonQuery lookupQuery) throws Exception {
        final List<Object> data = new ArrayList<>();

        try(final RestDataIteratorStrategy<Object> strategy = factory.createStrategy(lookupQuery)) {
            strategy.setLiveMode(isLiveMode());
            strategy.setLookup(true);

            while(strategy.hasNext()) {
                final Object json = strategy.next();

                if(json == null || isCancelled()) {
                    break;
                }

                doLookups(lookupQuery, json);
                final Object selectedJson = selectData(json, lookupQuery.getValidJsonPath(), transformer);

                if(jsonProvider.isArray(selectedJson)) {
                    jsonProvider.toIterable(selectedJson).forEach(data::add);
                }
                else if(jsonProvider.isMap(selectedJson)) {
                    data.add(selectedJson);
                }
                else if(selectedJson != null) {
                    LOG.debug("Lookup returned non-JSON value: {}", data);
                    data.add(Collections.singletonMap("Value", data));
                }
            }
        }
        catch(NullPointerException ex) {
            logException(ex);
        }

        return data;
    }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected final RestDataIteratorStrategyFactory<RestJsonQuery, Object> factory;
   private final LookupService lookupService;
   protected final JsonTransformer transformer;
   private final JsonProvider jsonProvider;
}
