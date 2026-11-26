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

                // A response was received and parsed. QueryRunner.run() collapses an empty result
                // to null, so say here that the run itself completed (see
                // TabularQuery.isRunCompleted); failures never reach this line.
                if(!isCancelled()) {
                   query.setRunCompleted(true);
                }

               if(isUnmatchedRoot(data)) {
                  // the xpath matched zero elements in this response - NoSchemaDocumentParser/
                  // SchemaDocumentParser's root EditableNode was never appended to, so
                  // toParsedNode() fell through to its bare ValueNode(null) fallback (see
                  // EditableNode.toParsedNode()). Skip it rather than handing it to
                  // table.loadStreamed(), which would otherwise treat this non-Map value as a
                  // scalar literal and fabricate a single spurious {"Column": null} row.
                  continue;
               }

               if(isEmptyExpandedPath(data)) {
                  // an element on the expanded path is empty (e.g. <Blobs/>), which the parser
                  // yields as ValueNode(null) rather than an empty list, so the table would
                  // otherwise build one all-null row from the rest of the response.
                  continue;
               }

               if(query instanceof EndpointQuery) {
                  doLookups((EndpointQuery) query, strategy, table);
               }

               table.loadStreamed(data);
            }

            reportRowLimit(strategy, table);
        }
        catch(Exception ex) {
            if(!(ex instanceof InterruptedException) || !isCancelled()) {
                logException(ex);

                // Not rethrown - runStream()'s contract is to return whatever table it managed
                // to load, even a partial/empty one. But a silent swallow here previously left
                // wizLoadColumnsError null for this failure: the empty/zero-size table this
                // returns collapses through QueryRunner.run()'s zero-size-to-null conversion and
                // TabularHandler.execute()'s null-table branch, both normal (non-exceptional)
                // returns, before TabularTableAssembly.loadColumnSelection()'s catch - the only
                // other writer of this property - ever gets a chance to see this exception (it
                // never throws). Set it directly here so the actual cause (e.g. an unresolvable
                // XSLT namespace prefix) survives to the add_table rejection message instead of
                // being reported as an unattributed "no columns" failure.
                final String detail = ex.getMessage();
                query.setProperty("wizLoadColumnsError",
                    detail == null || detail.isBlank() ? ex.getClass().getSimpleName() : detail);
            }
        }

        return table;
    }

    private boolean hasNext(RestDataIteratorStrategy<Object> strategy, BaseJsonTable table) throws Exception {
        return !isCancelled() && (table.getMaxRows() <= 0 || table.size() < table.getMaxRows()) && strategy.hasNext();
    }

    /**
     * True when {@code data} is the {@code EditableNode.toParsedNode()} fallback produced by a
     * root that was never appended to -- i.e. the query's xpath matched zero elements in the
     * response, as opposed to matching one real element whose own text happens to be empty
     * (which sets {@code EditableNode.value} to a real, non-null parsed value).
     */
    private boolean isUnmatchedRoot(Object data) {
        return data instanceof ValueNode && ((ValueNode) data).unwrap() == null;
    }

   /**
    * True when the query expands along an expanded path and an element before the last path
    * segment is empty. An absent key, a list or a single element is not empty, and neither is a
    * null at the final segment, which may be one real element with empty text. An absent key is
    * deliberately not treated as empty so a mistyped path keeps its current behaviour.
    */
   private boolean isEmptyExpandedPath(Object data) {
      final String path = query.getExpandedPath();

      if(!query.isExpanded() || query.isExpandTop() || path == null || path.isBlank() ||
         !(data instanceof MapNode))
      {
         return false;
      }

      final String[] segments = path.split("\\.");
      Object current = data;

      for(int i = 0; i < segments.length; i++) {
         if(!(current instanceof MapNode)) {
            return false;
         }

         current = ((MapNode) current).get(segments[i]);

         if(i < segments.length - 1 && current instanceof ValueNode &&
            ((ValueNode) current).unwrap() == null)
         {
            return true;
         }
      }

      return false;
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
