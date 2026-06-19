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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.script.graal.ReportGraalJavaScriptEngine;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Report-level script environment, backed by the GraalJS engine.
 *
 * @version 6.1, 6/20/2004
 * @author InetSoft Technology Corp
 */
public class ReportJavaScriptEnv extends GraalJavaScriptEnv
   implements ReportScriptEnv
{
   /**
    * Create a script engine. The setReport() method must be called
    * before it's used.
    */
   public ReportJavaScriptEnv() {
   }

   /**
    * Reset the scripting environment.
    */
   @Override
   public synchronized void reset() {
      if(rengine != null) {
         try {
            rengine.setReport(report);
            rengine.init(vars);
         }
         catch(Exception ex) {
            LOG.error("Failed to initialize script engine when " +
               "resetting environment", ex);
         }
      }
   }

   /**
    * Define a variable in the report scope.
    * @param name variable name.
    * @param obj variable value.
    */
   @Override
   public void put(String name, Object obj) {
      init();
      super.put(name, obj);
   }

   /**
    * Set the report to use with this script env.
    */
   @Override
   public void setReport(ReportSheet report) {
      this.report = report;

      if(rengine != null) {
         rengine.setReport(report);
      }
   }

   /**
    * Execute a script.
    * @param elem element this script is attached to. Intentionally ignored —
    * element-keyed scope resolution is obsolete (see the note in the method
    * body); retained only for the {@link ReportScriptEnv} interface contract.
    * @param script script object.
    * @param scope scope this script should execute in. Using report
    * scope if the value is null.
    */
   @Override
   public Object exec(ReportElement elem, Object script, Object scope) throws Exception {
      init();

      // The Rhino engine resolved an element's own scriptable via
      // engine.getScriptable(elem.getID(), null) when scope was null. That
      // element-keyed resolution is intentionally NOT reimplemented: report
      // elements are no longer authored with scripts — the report engine is
      // used only to render generated viewsheet exports — so a null scope
      // simply executes against the report/global scope.
      final Object execScope = scope;

      return ProfileUtils.addExecutionBreakDownRecord(report,
         ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
            return exec(script, args[0], null, report);
         }, execScope);
   }

   /**
    * Find the type of the specified object.
    */
   @Override
   public Class getType(Object id, Object scope) {
      init();
      // Report-element script autocomplete/property typing is obsolete (report
      // elements are no longer scripted; the report engine only renders
      // generated viewsheet exports), so no type is resolved.
      return null;
   }

   /**
    * Initialize the script engine.
    */
   @Override
   public synchronized void init() {
      if(rengine == null) {
         rengine = (ReportGraalJavaScriptEngine) createScriptEngine();
         engine = rengine;

         try {
            rengine.setReport(report);
            rengine.setSQL(sql);
            rengine.init(vars);
         }
         catch(Exception e) {
            LOG.error("Failed to initialize script engine when " +
               "initializing environment", e);
         }
      }
   }

   /**
    * Run cleanup tasks for the javascript engine.
    */
   @Override
   public void dispose() {
      if(rengine != null) {
         rengine.close();
         rengine = null;
         engine = null;
      }
   }

   /**
    * Create a script engine instance.
    */
   @Override
   protected GraalJavaScriptEngine createScriptEngine() {
      return new ReportGraalJavaScriptEngine();
   }

   private ReportGraalJavaScriptEngine rengine = null;
   private ReportSheet report;

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportJavaScriptEnv.class);
}
