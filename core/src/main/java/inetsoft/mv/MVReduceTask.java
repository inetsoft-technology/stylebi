/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.mr.AbstractReduceTask;
import inetsoft.mv.mr.XMapResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVReduceTask, the reduce task to be executed at server node. It will execute
 * a MVQuery to generate final data.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class MVReduceTask extends AbstractReduceTask {
   /**
    * Create an instance of MVReduceTask.
    */
   public MVReduceTask() {
      super();
   }

   /**
    * Get the mv query.
    */
   public MVQuery getQuery() {
      return (MVQuery) get("query");
   }

   /**
    * Set the mv query.
    */
   public void setQuery(MVQuery query) {
      set("query", query);
   }

   /**
    * Add one map result to this reduce task.
    */
   @Override
   public void add(XMapResult result) {
      MVQuery query = getQuery();
      SubMVResult sresult = (SubMVResult) result;
      String id = sresult.getXBlock();
      SubTableBlock data = sresult.getData();
      boolean streaming = "true".equals(getProperty("streaming"));

      // fix bug1429753586720, if the subtableblock is empty, still need to
      // add the empty block to mvquery to avoid npe problem.
      if(streaming && data != null && data.moreRows(0)) {
         final int cnt = 20000;

         // add data in small chunks when streaming to allow the data
         // to be made available as soon as possible
         for(int i = 0; data.moreRows(i); i += cnt) {
            query.add(id + i, new PartTableBlock(data, i, i + cnt));
         }
      }
      else {
         query.add(id, data);
      }
   }

   /**
    * Get the final result.
    */
   @Override
   public Object getResult() {
      MVQuery query = getQuery();

      if(!query.isCompleted()) {
         // if cancelled, just return whatever result instead of throwing
         // an exception since the data will be refreshed by the next query 
         if(query.isCancelled()) {
            LOG.debug("Query cancelled, returning partial result: " + this);
            query.complete(true);
         }
         else {
            throw new RuntimeException("Reduce task is not completed: " + this);
         }
      }

      return query.getData();
   }

   /**
    * Cancel this reduce task.
    */
   @Override
   public void cancel() {
      MVQuery query = getQuery();
      query.cancel();
   }

   /**
    * Complete this reduce task.
    * @param all true if all blocks are added, false if still streaming.
    */
   @Override
   public void complete(boolean all) {
      MVQuery query = getQuery();
      query.complete(all);
   }

   /**
    * Check if this reduce task is completed.
    */
   @Override
   public boolean isCompleted() {
      MVQuery query = getQuery();
      return query.isCompleted();
   }

   /**
    * Check if the entire task (all sub-tasks) are completed. For example,
    * if the number of rows in the result reaches max rows, no additional
    * tasks need to be executed.
    */
   @Override
   public boolean isFulfilled() {
      MVQuery query = getQuery();
      return query.isFulfilled();
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVReduceTask.class);
}
