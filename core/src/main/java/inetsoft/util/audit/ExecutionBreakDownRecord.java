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
package inetsoft.util.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.util.GroupedThread;
import inetsoft.util.log.LogContext;
import org.apache.commons.lang3.StringUtils;

import java.sql.Date;
import java.util.*;

/**
 * ExecutionBreakDownRecord is a in memory representation of a record in EXECUTION_BREAKDOWN.
 * table. It can be used to write record to table.
 *
 * @author InetSoft Technology Corp.
 * @version 13.1, 10/11/2018
 */
public class ExecutionBreakDownRecord implements AuditRecord {
   /**
    * Object type report.
    */
   public static final String OBJECT_TYPE_REPORT = "Report";
   /**
    * Object type viewsheet.
    */
   public static final String OBJECT_TYPE_VIEWSHEET = "Viewsheet";
   /**
    * The Query Execution cycle.
    */
   public static final String QUERY_EXECUTION_CYCLE = "Query Execution";
   /**
    * The Post Processing cycle.
    */
   public static final String POST_PROCESSING_CYCLE = "Post Processing";
   /**
    * The JavaScript Processing cycle.
    */
   public static final String JAVASCRIPT_PROCESSING_CYCLE = "JavaScript Processing";
   /**
    * The UI Processing cycle.
    */
   public static final String UI_PROCESSING_CYCLE = "UI Processing";

   /**
    * Create an empty instance of ExecutionBreakDownRecord.
    */
   public ExecutionBreakDownRecord() {
      super();
   }

   /**
    * Create a new instance of ExecutionBreakDownRecord and set data.
    * @param  objectName    name of the specified report/viewsheet(include path).
    * @param  cycleName     the execution cycle name.
    * @param  startTimestamp the specified execution start timestamp.
    * @param  endTimestamp   the specified execution end timestamp.
    */
   public ExecutionBreakDownRecord(String objectName, String cycleName,
                                   long startTimestamp, long endTimestamp) {
      super();
      this.setObjectName(objectName);
      this.setCycleName(cycleName);
      this.setStartTimestamp(startTimestamp);
      this.setEndTimestamp(endTimestamp);
   }

   /**
    * Check if the record is a valid one.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @JsonIgnore
   @Override
   public boolean isValid() {
      return !StringUtils.isEmpty(objectName) &&
        !StringUtils.isEmpty(cycleName) && startTimestamp != 0 &&
        endTimestamp != 0;
   }

   /**
    * Set the object name.
    * @param name the specified report/viewsheet name(inclide path).
    */
   public void setObjectName(String name) {
      this.objectName = name;
   }

   /**
    * Get the object name.
    * @return name the specified report/viewsheet name(inclide path).
    */
   public String getObjectName() {
      return this.objectName;
   }

   /**
    * Get the cycle name.
    */
   public String getCycleName() {
      return this.cycleName;
   }

   /**
    * Get the object type.
    */
   public void setCycleName(String cycleName) {
      this.cycleName = cycleName;
   }

   /**
    * Get start timestamp of the cycle execution.
    */
   public long getStartTimestamp() {
      return this.startTimestamp;
   }

   /**
    * Set start timestamp of the cycle execution.
    */
   public void setStartTimestamp(long timestamp) {
      this.startTimestamp = timestamp;
   }

   /**
    * Get end timestamp of the cycle execution.
    */
   public long getEndTimestamp() {
      return this.endTimestamp;
   }

   /**
    * Set end timestamp of the cycle execution.
    */
   public void setEndTimestamp(long timestamp) {
      this.endTimestamp = timestamp;
   }

   public void recordContext() {
      contextRecords.clear();

      if(Thread.currentThread() instanceof GroupedThread) {
         recordContext(LogContext.DASHBOARD);
         recordContext(LogContext.QUERY);
         recordContext(LogContext.MODEL);
         recordContext(LogContext.WORKSHEET);
         recordContext(LogContext.SCHEDULE_TASK);
         recordContext(LogContext.ASSEMBLY);
         recordContext(LogContext.TABLE);
      }
   }

   private void recordContext(LogContext context) {
      String record = ((GroupedThread) Thread.currentThread()).getRecord(context);

      if(record != null) {
         contextRecords.put(context, record);
      }
   }

   public Set<LogContext> getContexts() {
      return contextRecords.keySet();
   }

   public String getContext(LogContext context) {
      return contextRecords.get(context);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return getObjectName() + ", " + getCycleName() + ", " +
         new Date(getStartTimestamp())  + ", " + new Date(getEndTimestamp());
   }

   private String objectName;
   private String cycleName;
   private long startTimestamp;
   private long endTimestamp;
   private final Map<LogContext, String> contextRecords = new HashMap<>();
}
