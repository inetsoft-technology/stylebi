/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.profile;

import inetsoft.util.audit.ExecutionBreakDownRecord;

import java.util.*;
import java.util.stream.Collectors;

public class ProfileInfo {
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


   public ProfileInfo() {
   }

   public void addRecord(ExecutionBreakDownRecord record) {
      String name = record.getObjectName();

      if(profilingData.get(name) == null) {
         ProfileData pData = new ProfileData();
         pData.addRecord(record);
         profilingData.put(name, pData);
         //recordSize++;
      }
      else {
         profilingData.get(name).addRecord(record);
         //recordSize++;
      }
   }

   public void removeProfileData(String name) {
      profilingData.remove(name);
   }

   public long getCycleReport(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCycleReport();
      }

      return 0;
   }

   public long getCycleViewsheet(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCycleViewsheet();
      }

      return 0;
   }

   public long getCycleQuery(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCycleQuery();
      }

      return 0;
   }

   public long getCyclePost(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCyclePost();
      }

      return 0;
   }

   public long getCycleJavaScript(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCycleJavaScript();
      }

      return 0;
   }

   public long getCycleUI(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getCycleUI();
      }

      return 0;
   }

   public List<ExecutionBreakDownRecord> getProfileRecords(String name) {
      if(profilingData.get(name) != null) {
         return profilingData.get(name).getDetailRecords();
      }

      return null;
   }

   private final Map<String, ProfileData> profilingData = new HashMap<>();

   static final class ProfileData {
      public ProfileData() {
      }

      public void addRecord(ExecutionBreakDownRecord record) {
         if(!onlyTotal()) {
            detailRecords.add(record);
         }

         long time = record.getEndTimestamp() - record.getStartTimestamp();
         totalTime(record.getCycleName(), time);
      }

      private boolean onlyTotal() {
         return detailRecords.size() >= 1000;
      }

      public void totalTime(String type, long time) {
         switch(type) {
         case OBJECT_TYPE_REPORT:
            cycleReport += time;
            break;
         case OBJECT_TYPE_VIEWSHEET:
            cycleViewsheet += time;
            break;
         case QUERY_EXECUTION_CYCLE:
            cycleQuery += time;
            break;
         case POST_PROCESSING_CYCLE:
            cyclePost += time;
            break;
         case JAVASCRIPT_PROCESSING_CYCLE:
            cycleJavaScript += time;
            break;
         case UI_PROCESSING_CYCLE:
            cycleUI += time;
            break;
         }
      }

      public long getCycleReport() {
         return cycleReport;
      }

      public long getCycleViewsheet() {
         return cycleViewsheet;
      }

      public long getCycleQuery() {
         return cycleQuery;
      }

      public long getCyclePost() {
         return cyclePost;
      }

      public long getCycleJavaScript() {
         return cycleJavaScript;
      }

      public long getCycleUI() {
         return cycleUI;
      }

      public List<ExecutionBreakDownRecord> getDetailRecords() {
         return this.detailRecords;
      }

      private final List<ExecutionBreakDownRecord> detailRecords = new ArrayList<>();
      private long cycleReport = 0;
      private long cycleViewsheet = 0;
      private long cycleQuery = 0;
      private long cyclePost = 0;
      private long cycleJavaScript = 0;
      private long cycleUI = 0;
   }
}