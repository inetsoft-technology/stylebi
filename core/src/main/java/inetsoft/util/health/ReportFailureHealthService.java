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
package inetsoft.util.health;

import inetsoft.sree.SreeEnv;
import inetsoft.util.SingletonManager;
import inetsoft.util.audit.ExecutionRecord;
import inetsoft.util.audit.ExecutionRecordDispatcher;

import java.util.HashMap;
import java.util.Map;

public class ReportFailureHealthService
   implements AutoCloseable, ExecutionRecordDispatcher.ExecutionRecordListener
{
   public ReportFailureHealthService() {
      dispatcher = ExecutionRecordDispatcher.getInstance();
      dispatcher.addListener(this);
   }

   public static ReportFailureHealthService getInstance() {
      return SingletonManager.getInstance(ReportFailureHealthService.class);
   }

   public ReportFailureStatus getStatus() {
      int count = getFailureCount();
      int threshold =
         Integer.parseInt(SreeEnv.getProperty("health.reportFailure.threshold", "20"));
      return new ReportFailureStatus(count > threshold, count);
   }

   @Override
   public void recordInserted(ExecutionRecordDispatcher.ExecutionRecordEvent event) {
      ExecutionRecord record = event.getRecord();

      if(ExecutionRecord.EXEC_STATUS_FAILURE.equals(record.getExecStatus())) {
         synchronized(failures) {
            failures.put(
               record.getObjectType() + ":" + record.getObjectName(),
               System.currentTimeMillis());
         }
      }
      else if(ExecutionRecord.EXEC_STATUS_SUCCESS.equals(record.getExecStatus())) {
         synchronized(failures) {
            failures.remove(record.getObjectType() + ":" + record.getObjectName());
         }
      }
   }

   @Override
   public void close() throws Exception {
      dispatcher.removeListener(this);
   }

   private int getFailureCount() {
      long failureTime =
         Long.parseLong(SreeEnv.getProperty("health.reportFailure.time", "300000"));

      synchronized(failures) {
         long threshold = System.currentTimeMillis() - failureTime;
         failures.values().removeIf(ts -> ts < threshold);
         return failures.size();
      }
   }

   private final ExecutionRecordDispatcher dispatcher;
   private final Map<String, Long> failures = new HashMap<>();

}
