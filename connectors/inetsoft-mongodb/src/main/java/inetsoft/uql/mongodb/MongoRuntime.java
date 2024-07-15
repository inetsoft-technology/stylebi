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
package inetsoft.uql.mongodb;

import com.mongodb.client.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.JsonTable;
import inetsoft.util.ResourceCache;
import inetsoft.util.Tool;
import org.apache.commons.lang3.ArrayUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class MongoRuntime extends TabularRuntime {
   @SuppressWarnings("unchecked")
   public Object getQueryResults(Document query, MongoDatabase db) {
      // cannot run a command as this will be batched and that isn't supported
      // by the runcommand method
      if(query.get("aggregate") != null) {
         String collectionName = (String) query.get("aggregate");
         MongoCollection<Document> collection = db.getCollection(collectionName);
         ArrayList<Document> docs = new ArrayList<>();

         Object queryPipeline = query.get("pipeline");
         List<Document> pipelines = new ArrayList<>();

         if(queryPipeline instanceof List) {
            pipelines = (List<Document>) queryPipeline;
         }

         AggregateIterable<Document> result = collection.aggregate(pipelines);

         if(query.containsKey("allowDiskUse")) {
            result.allowDiskUse(query.getBoolean("allowDiskUse"));
         }

         if(query.containsKey("bypassDocumentValidation")) {
            result.bypassDocumentValidation(query.getBoolean("bypassDocumentValidation"));
         }

         if(query.containsKey("maxTimeMS")) {
            result.maxTime(query.getLong("maxTimeMS"), TimeUnit.MILLISECONDS);
         }

         result.forEach((java.util.function.Consumer<Document>) docs::add);
         return docs;
      }
      else {
         Document results = db.runCommand(query);

         // if inline is set in the query, the data returned is in 'results' instead
         // of 'result'
         if(query.get("mapReduce") != null) {
            boolean isInline = false;

            if(query.get("out") instanceof Document) {
               Document outDoc = (Document) query.get("out");
               isInline = outDoc.get("inline") != null;
            }

            return isInline ? results.get("results") : results.get("result");
         }

         if(query.get("group") != null) {
            return results.get("retval");
         }

         if(query.get("distinct") != null) {
            return results.get("values");
         }

         if(query.get("count") != null) {
            return results.get("n");
         }

         return results.get("result");
      }
   }

   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      MongoQuery query0 = (MongoQuery) query;
      MongoDataSource ds = (MongoDataSource) query.getDataSource();
      String qstr = query0.getQueryString();

      try {
         com.mongodb.MongoClient mongoClient = pool.get(ds);

         MongoDatabase db = mongoClient.getDatabase(ds.getDB());
         Document queryDoc = Document.parse(qstr);

         final JsonTable table = new JsonTable();
         table.setMaxRows(query.getMaxRows());
         table.applyQueryColumnTypes(query);
         table.load(getQueryResults(queryDoc, db));
         return table;
      }
      catch(Exception ex) {
         LOG.warn("Failed to execute MongoDB query: " + qstr, ex);
         Tool.addUserMessage("Failed to execute MongoDB query: " + qstr +
                             " (" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }

      return null;
   }

   public void testDataSource(TabularDataSource<?> ds0, VariableTable params) throws Exception {
      MongoDataSource ds = (MongoDataSource) ds0;
      com.mongodb.MongoClient mongoClient = pool.get(ds);
      mongoClient.listDatabaseNames().first();

      String db = ds.getDB();
      String[] dbs = ds.getDatabaseNames();

      if(db != null && !db.isEmpty() && dbs.length > 0 && !ArrayUtils.contains(dbs, db)) {
         throw new RuntimeException("Database is not found: " + db);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MongoRuntime.class.getName());
   private static final ResourceCache<MongoDataSource, com.mongodb.MongoClient> pool =
      new ResourceCache<MongoDataSource, com.mongodb.MongoClient>(100, 60000 * 5) {
         protected com.mongodb.MongoClient create(MongoDataSource key) throws Exception {
            return key.getMongoClient();
         }

         protected void processRemoved(MongoClient value) {
            value.close();
         }
      };
}
