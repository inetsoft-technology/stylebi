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
package inetsoft.uql.util;

import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;

import java.util.Date;

/**
 * The QueryInfo defines detail informations of a executing query.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class QueryInfo {
   /**
    * Constructor.
    */
   public QueryInfo(String id, String threadId, String name, IdentityID user,
                    String asset, int rowCount, Date dateCreated) {
      super();
      this.id = id;
      this.threadId = threadId;
      this.name = name;
      this.user = user;
      this.asset = asset;
      this.rowCount = rowCount;
      this.dateCreated = dateCreated;
   }

   /**
    * Get the date and time at which the query execution started.
    * @return the created date.
    */
   public Date getDateCreated() {
      return dateCreated;
   }

   /**
    * Set the date and time at which the query execution started.
    * @param date the created date.
    */
   public void setDateCreated(Date date) {
      dateCreated = date;
   }

   /**
    * Get the unique identifier for the query.
    * @return query id.
    */
   public String getId() {
      return id;
   }

   /**
    * Set the unique identifier for the query.
    * @param id id to set.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Get the name of the query.
    * @return the name of the query.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name of the query.
    * @param name the name of the query.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the number of rows that has been processed.
    * @return the row count.
    */
   public int getRowCount() {
      if(counter != null) {
         int count = counter.getRowCount();
         return rowCount = count < 0 ? -count - 1 : count;
      }

      return rowCount;
   }

   /**
    * Set the number of rows that has been processed.
    * @param rowCount the row count.
    */
   public void setRowCount(int rowCount) {
      this.rowCount = rowCount;
   }

   /**
    * Get the identifier of the query in which the query is executing.
    * @return the thread id.
    */
   public String getThreadId() {
      return threadId;
   }

   /**
    * Set the identifier of the query in which the query is executing.
    * @param tId the thread id.
    */
   public void setThreadId(String tId) {
      threadId = tId;
   }

   /**
    * Get the name of the user that executed the query.
    * @return the user name
    */
   public IdentityID getUser() {
      return user;
   }

   /**
    * Set the name of the user that executed the query.
    * @param user the user name
    */
   public void setUser(IdentityID user) {
      this.user = user;
   }

   /**
    * Get user description for query monitoring.
    */
   public String getMonitorUser() {
      if(task == null) {
         return user.convertToKey();
      }

      String taskLabel = Catalog.getCatalog().getString("Task");
      String taskDesc = taskLabel + ": " + task;

      return user == null ? taskDesc : user.convertToKey() + "(" + taskDesc + ")";
   }

   /**
    * Get the task name.
    * @return the task name.
    */
   public String getTask() {
      return task;
   }

   /**
    * Set the task name.
    * @param task the task name.
    */
   public void setTask(String task) {
      this.task = task;
   }

   /**
    * Get the name of report or viewsheet in which the query was executed.
    * @return the asset name
    */
   public String getAsset() {
      return asset;
   }

   /**
    * Set the name of report or viewsheet in which the query was executed.
    * @param asset the asset name
    */
   public void setAsset(String asset) {
      this.asset = asset;
   }

   /**
    * Get the query manager for tracking queries.
    */
   public QueryManager getQueryManager() {
      return queryMgr;
   }

   /**
    * Set the query manager for tracking queries.
    */
   public void setQueryManager(QueryManager mgr) {
      this.queryMgr = mgr;
   }

   /**
    * Set the row counter which used to get the row count.
    */
   public void setRowCounter(RowCounter counter) {
      this.counter = counter;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      QueryInfo info = new QueryInfo(id, threadId, name, user, asset,
         getRowCount(), dateCreated);

      if(task != null) {
         info.setTask(task);
      }

      return info;
   }

   private Date dateCreated;
   private String id;
   private String name;
   private int rowCount;
   private String threadId;
   private IdentityID user;
   private String task;
   private String asset;
   private QueryManager queryMgr;
   private RowCounter counter;
}
