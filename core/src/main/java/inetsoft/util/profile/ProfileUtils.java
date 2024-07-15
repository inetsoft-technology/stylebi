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
package inetsoft.util.profile;

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.internal.BaseElement;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.audit.*;

import java.security.Principal;

public final class ProfileUtils {
   /**
    * Add execution record.
    * @param  obj           the target object to add record(ReportElement or Viewsheet).
    * @param  cycleName     the execution cycle name.
    */
   public static Object addExecutionBreakDownRecord(Object obj,
                                                    String cycleName,
                                                    ThrowingSupplier<Object> func)
      throws Exception
   {
      String objectName = getObjectName(obj);
      return addExecutionBreakDownRecord(objectName, cycleName, func);
   }

   /**
    * Add execution record.
    * @param  obj           the target object to add record(ReportElement or Viewsheet).
    * @param  cycleName     the execution cycle name.
    */
   public static void addExecutionBreakDownRecord(Object obj, String cycleName,
                                                  ThrowingConsumer<Object> func,
                                                  Object... funcInputs)
      throws Exception
   {
      String objectName = getObjectName(obj);
      addExecutionBreakDownRecord(objectName, cycleName, func, funcInputs);
   }

   /**
    * Add execution record.
    * @param  obj           the target object to add record(ReportElement or Viewsheet).
    * @param  cycleName     the execution cycle name.
    */
   public static Object addExecutionBreakDownRecord(Object obj, String cycleName,
                                                    ThrowingFunction<Object, Object> func,
                                                    Object... funcInputs)
      throws Exception
   {
      String objectName = getObjectName(obj);
      return addExecutionBreakDownRecord(objectName, cycleName, func, funcInputs);
   }

   /**
    * Add execution record.
    * @param  cycleName     the execution cycle name.
    */
   public static Object addExecutionBreakDownRecord(String objectName,
                                                    String cycleName,
                                                    ThrowingSupplier<Object> func)
      throws Exception
   {
      long startTimestamp = System.currentTimeMillis();
      Object result = func.get();
      long endTimestamp = System.currentTimeMillis();
      addExecutionBreakDownRecord(objectName, cycleName, startTimestamp, endTimestamp);

      return result;
   }

   /**
    * Add execution record.
    * @param  cycleName     the execution cycle name.
    */
   public static void addExecutionBreakDownRecord(String objectName, String cycleName,
                                                  ThrowingConsumer<Object> func,
                                                  Object... funcInputs)
      throws Exception
   {
      long startTimestamp = System.currentTimeMillis();
      func.accept(funcInputs);
      long endTimestamp = System.currentTimeMillis();
      addExecutionBreakDownRecord(objectName, cycleName, startTimestamp, endTimestamp);
   }

   /**
    * Add execution record.
    * @param  cycleName     the execution cycle name.
    */
   public static Object addExecutionBreakDownRecord(String objectName, String cycleName,
                                                    ThrowingFunction<Object, Object> func,
                                                    Object... funcInputs)
      throws Exception
   {
      long startTimestamp = System.currentTimeMillis();
      Object result = func.apply(funcInputs);
      long endTimestamp = System.currentTimeMillis();
      addExecutionBreakDownRecord(objectName, cycleName, startTimestamp, endTimestamp);

      return result;
   }

   /**
    * Add execution record.
    * @param  objectName    name of the specified report/viewsheet(include path).
    * @param  cycleName     the execution cycle name.
    * @param  startTimestamp the specified execution start timestamp.
    * @param  endTimestamp   the specified execution end timestamp.
    */
   public static void addExecutionBreakDownRecord(String objectName, String cycleName,
                                                  long startTimestamp, long endTimestamp)
   {
      if(objectName == null) {
         return;
      }

      Principal principal = ThreadContext.getContextPrincipal();

      if(!(principal instanceof XPrincipal)) {
         return;
      }

      boolean profiling = ((XPrincipal) principal).isProfiling();

      if(!profiling) {
         return;
      }

      if(startTimestamp == endTimestamp) {
         return;
      }

      ExecutionBreakDownRecord record = new ExecutionBreakDownRecord(objectName,
              cycleName, startTimestamp, endTimestamp);

      Profile.getInstance().addRecord(record);
   }

   /**
    * @return     return the report path or viewsheet path.
    */
   public static String getObjectName(Object obj) {
    if(obj == null) {
         return null;
      }

      if(obj instanceof Viewsheet) {
         return getViewsheetName((Viewsheet) obj);
      }

      return getReportSheetName(obj);
   }

   public static String getObjectType(Object obj) {
      if(obj == null) {
         return null;
      }

      if(obj instanceof Viewsheet) {
         return ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET;
      }

      if(obj instanceof ReportSheet || obj instanceof ReportElement) {
         return ExecutionBreakDownRecord.OBJECT_TYPE_REPORT;
      }

      return null;
   }

   public static String getViewsheetName(Viewsheet vs) {
      if(vs == null) {
         return null;
      }

      if(vs.isEmbedded()) {
         return getViewsheetName(vs.getViewsheet());
      }

      AssetEntry entry = vs.getRuntimeEntry();

      if(entry != null) {
         String name = entry.getScope() == AssetRepository.TEMPORARY_SCOPE ?
            null : vs.getRuntimeEntry().getPath();

         if(name != null && entry.getScope() != AssetRepository.GLOBAL_SCOPE) {
            name = "My Viewsheet/" + name;
         }

         return name;
      }

      if(vs.getName() != null) {
         return vs.getName();
      }

      return null;
   }

   public static String getReportSheetName(Object obj) {
      ReportSheet sheet = null;

      if(obj instanceof ReportSheet) {
         sheet = (ReportSheet) obj;
      }

      if(obj instanceof BaseElement) {
         BaseElement elem = (BaseElement) obj;
         sheet = elem.getReport();
      }

      if(sheet == null) {
         return null;
      }

      String name = sheet.getContextName();

      if(name != null && name.lastIndexOf(':') != -1) {
         int idx = name.lastIndexOf(':');
         name = name.substring(idx + 1).trim();
      }

      // don't add record for temporary preview report.
      if(name != null && name.contains(SUtil.ANALYTIC_REPORT)) {
         return null;
      }

      return name;
   }
}
