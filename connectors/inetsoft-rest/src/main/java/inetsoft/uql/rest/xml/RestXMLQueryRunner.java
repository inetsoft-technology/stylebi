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
package inetsoft.uql.rest.xml;

import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.rest.xml.parse.*;
import inetsoft.uql.util.*;

import java.util.*;

public class RestXMLQueryRunner extends AbstractQueryRunner<RestXMLQuery> {
    public RestXMLQueryRunner(RestXMLQuery query, XMLRestDataIteratorStrategyFactory factory) {
        super(query);
        this.factory = factory;
    }

    @Override
    public XTableNode runStream() {
        final BaseJsonTable table = getTable();

        try(AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy =
               factory.createStrategy(query))
        {
            final AbstractXMLStreamTransformer transformer = strategy.getTransformer();
            transformer.initializeColumnTypes(table);
            table.applyQueryColumnTypes(query);
            table.beginStreamedLoading();

            while(hasNext(strategy, table)) {
                final Object data = strategy.next();

                if(data == null) {
                    break;
                }

               if(query instanceof EndpointQuery) {
                  doLookups((EndpointQuery) query, strategy);
               }

               table.loadStreamed(data);
            }
        }
        catch(Exception ex) {
            if(!(ex instanceof InterruptedException) || !isCancelled()) {
                logException(ex);
            }
        }

        return table;
    }

    private boolean hasNext(RestDataIteratorStrategy<Object> strategy, BaseJsonTable table) throws Exception {
        return !isCancelled() && (table.getMaxRows() <= 0 || table.size() < table.getMaxRows()) && strategy.hasNext();
    }

   private void doLookups(EndpointQuery query,
                          AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy)
      throws Exception
   {
      final AbstractXMLStreamTransformer transformer = strategy.getTransformer();

      for(JsonLookupQuery lookupQuery : query.getLookupQueries()) {
         final JsonLookupEndpoint endpoint = lookupQuery.getLookupEndpoint();

         // todo transformations can be memoized if endpoints repeat paths
         final List<MapNode> lookupEntities = transformer.getLookupEntities(endpoint.jsonPath());

         for(MapNode lookupEntity : lookupEntities) {
            doLookupOnEntity(query, lookupQuery, lookupEntity);
         }
      }
   }

   /**
    * @param lookupQuery the lookup query to do the lookup on.
    * @param map         the MapNode representing the entity to do the lookup on.
    */
   private void doLookupOnEntity(EndpointQuery parentQuery, JsonLookupQuery lookupQuery,
                                 MapNode map)
      throws Exception
   {
      final EndpointQuery queryFromLookupQuery =
         lookupService.createXmlLookupQuery((EndpointQuery) query, parentQuery, lookupQuery, map);

      if(queryFromLookupQuery == null) {
         return;
      }

      List<Object> lookupObj = runLookupQuery(queryFromLookupQuery);

      if(queryFromLookupQuery.isExpanded()) {
         final int expandLevels = queryFromLookupQuery.isExpandTop() ? 1 : 0;
         final LookupList lookupList = new LookupList(expandLevels);
         lookupList.addAll(lookupObj);
         lookupObj = lookupList;
      }

      map.put(queryFromLookupQuery.getEndpoint(), lookupObj);
   }

   /**
    * Run the lookup query and return the result.
    */
   private List<Object> runLookupQuery(EndpointQuery lookupQuery) throws Exception {
      final List<Object> data = new ArrayList<>();

      try(final AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy =
             factory.createStrategy((RestXMLQuery) lookupQuery))
      {
         strategy.setLiveMode(isLiveMode());
         strategy.setLookup(true);

         while(strategy.hasNext()) {
            final Object xml = strategy.next();

            if(xml == null) {
               break;
            }

            doLookups(lookupQuery, strategy);
            data.add(xml);
         }
      }

      return data;
   }

   private final XMLRestDataIteratorStrategyFactory factory;
   private final LookupService lookupService = new LookupService();
}
