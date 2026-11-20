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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            strategy.setTouchTimestamp(getTouchTimestamp());
            final AbstractXMLStreamTransformer transformer = strategy.getTransformer();
            transformer.initializeColumnTypes(table);
            table.applyQueryColumnTypes(query);
            table.beginStreamedLoading();

            while(hasNext(strategy, table)) {
                final Object data = strategy.next();

                if(data == null) {
                    break;
                }

                // an XPath/response that matched nothing parses to an empty root ValueNode(null);
                // a matched element with a null value is a MapNode child of the root, never this
                if(data instanceof ValueNode && ((ValueNode) data).unwrap() == null) {
                    continue;
                }

               if(query instanceof EndpointQuery) {
                  doLookups((EndpointQuery) query, strategy, table);
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

   /**
    * @param mainTable the table of the top-level query, used to stop the lookups at the row
    *                  limit, or null for a nested lookup.
    */
   private void doLookups(EndpointQuery query,
                          AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy,
                          BaseJsonTable mainTable)
      throws Exception
   {
      final AbstractXMLStreamTransformer transformer = strategy.getTransformer();

      for(JsonLookupQuery lookupQuery : query.getLookupQueries()) {
         final JsonLookupEndpoint endpoint = lookupQuery.getLookupEndpoint();

         // todo transformations can be memoized if endpoints repeat paths
         final List<MapNode> lookupEntities = transformer.getLookupEntities(endpoint.jsonPath());

         int n = lookupEntities.size();

         // each entity is its own row (or more), so an entity past the row limit only feeds
         // rows that are dropped and its lookup is not needed
         if(mainTable != null && mainTable.getMaxRows() > 0 && isOneRowPerEntity(mainTable, endpoint.jsonPath())) {
            n = Math.max(0, Math.min(n, mainTable.getMaxRows() - mainTable.size()));
         }

         for(int i = 0; i < n; i++) {
            doLookupOnEntity(query, lookupQuery, lookupEntities.get(i));
         }

         // entities past the row limit get no lookup, but their element with the lookup's
         // name is moved the same way so that the column stays consistent
         for(int i = n; i < lookupEntities.size(); i++) {
            moveCollidingField(lookupEntities.get(i), endpoint.endpoint());
         }
      }
   }

   /**
    * Check if every lookup entity at the path is expanded into its own row(s) of the main table.
    * The entity list is expanded when the expanded path is empty, the entity path or under it.
    */
   private boolean isOneRowPerEntity(BaseJsonTable mainTable, String entityPath) {
      if(!(mainTable instanceof ExpandedJsonTable) || !query.isExpanded() || query.isExpandTop()) {
         return false;
      }

      final String expandedPath = query.getExpandedPath();

      if(expandedPath == null || expandedPath.isEmpty()) {
         return true;
      }

      final StringJoiner column = new StringJoiner(".");

      for(String tag : entityPath.split("/")) {
         if(!tag.isEmpty()) {
            column.add(tag);
         }
      }

      final String col = column.toString();
      return expandedPath.equals(col) || expandedPath.startsWith(col + ".");
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
      // move after the key is read, before a skipped lookup returns
      moveCollidingField(map, lookupQuery.getLookupEndpoint().endpoint());

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
    * Move a parent element that has the lookup's name so the lookup result doesn't overwrite it.
    */
   private void moveCollidingField(MapNode map, String lookupName) {
      final String newName = lookupService.moveCollidingField(map, lookupName);

      if(newName != null && !collisionWarned) {
         collisionWarned = true;
         LOG.warn("The element \"{}\" has the name of a lookup and is renamed to \"{}\"",
                  lookupName, newName);
      }
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
         strategy.setTouchTimestamp(getTouchTimestamp());
         strategy.setLookup(true);

         while(strategy.hasNext()) {
            final Object xml = strategy.next();

            if(xml == null) {
               break;
            }

            doLookups(lookupQuery, strategy, null);
            data.add(xml);
         }
      }

      return data;
   }

   private final XMLRestDataIteratorStrategyFactory factory;
   private final LookupService lookupService = new LookupService();
   private boolean collisionWarned;
   private static final Logger LOG = LoggerFactory.getLogger(RestXMLQueryRunner.class);
}
