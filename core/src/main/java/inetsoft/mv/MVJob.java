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
import inetsoft.mv.mr.*;
import inetsoft.mv.mr.internal.XMapTaskPool;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.TableAssembly;

/**
 * MVJob, the mv job to be executed at server node. Several sub mv tasks will
 * be executed at data nodes, and one mv reduce task will be executed at server
 * node. Finally the job result will be returned.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class MVJob extends AbstractJob {
   /**
    * Create an instance of MVJob.
    */
   public MVJob(TableAssembly table, String mv, VariableTable vars, XPrincipal user)
      throws Exception
   {
      super();
      this.table = table;
      this.vars = vars;
      this.user = user;
      setXFile(mv);

      init();
   }

   /**
    * Initialize this MVJob.
    */
   private void init() throws Exception {
      try {
         String name = getMV();
         String file = MVStorage.getFile(name);
         MV mv = MVStorage.getInstance().get(file);
         MVQueryBuilder builder = new MVQueryBuilder(table, mv, vars, user);
         MVQuery query = builder.getQuery();
         SubMVQuery squery = builder.getSubQuery();
         query.setJob(this);
         set("query", query);
         set("squery", squery);
      }
      catch(Exception ex) {
         throw new MVExecutionException(ex.getMessage());
      }
   }

   /**
    * Get the specified mv.
    */
   public String getMV() {
      return getXFile();
   }

   /**
    * Get the table assembly to be executed.
    */
   public TableAssembly getTable() {
      return table;
   }

   /**
    * Get the variable table.
    */
   public VariableTable getVariableTable() {
      return vars;
   }

   /**
    * Get the user who triggers this job.
    */
   public XPrincipal getUser() {
      return user;
   }

   /**
    * Create a map tack.
    */
   @Override
   protected XMapTask createMapper0() {
      SubMVTask task = new SubMVTask();
      task.setProperty("streaming", isStreaming() + "");
      return task;
   }

   /**
    * Create a reduce task.
    */
   @Override
   protected XReduceTask createReducer0() {
      if(get("squery") == null) {
         return null;
      }

      MVReduceTask task = new MVReduceTask();
      task.setProperty("streaming", isStreaming() + "");
      return task;
   }

   /**
    * Cancel this job.
    */
   @Override
   public void cancel() {
      super.cancel();
      XJobPool.cancel(getID());
      XMapTaskPool.cancel(getID());
   }

   /**
    * Whether the result can be streamed or it needs to wait for
    * all sub-jobs to complete.
    */
   @Override
   public boolean isStreaming() {
      MVQuery query = (MVQuery) get("query");
      return query != null && query.isDetail();
   }

   private TableAssembly table;
   private VariableTable vars;
   private XPrincipal user;
}
