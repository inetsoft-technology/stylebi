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

import inetsoft.report.*;
import inetsoft.report.script.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.JavaScriptEnv;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulate a script engine.
 *
 * @version 6.1, 6/20/2004
 * @author InetSoft Technology Corp
 */
public class ReportJavaScriptEnv extends JavaScriptEnv
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
            rengine.init(report, vars);
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
   }

   /**
    * Execute a script.
    * @param elem element this script is attached to.
    * @param script script object.
    * @param scope scope this script should execute in. Using report
    * scope if the value is null.
    */
   @Override
   public Object exec(ReportElement elem, Object script, Object scope) throws Exception {
      init();

      if(scope == null) {
         if(elem != null) {
            // use the element scope for execution
            scope = engine.getScriptable(elem.getID(), null);
         }
         else {
            // use the report scope if element is not passed in
            scope = engine.getScriptable(null, null);
         }
      }

      return ProfileUtils.addExecutionBreakDownRecord(report,
         ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
            return exec(script, args[0], null, report);
         }, scope);

      //return exec(script, scope);
   }

   /**
    * Find the scope of the specified object.
    */
   @Override
   public Class getType(Object id, Object scope) {
      init();
      return rengine.getType(id, (Scriptable) scope);
   }

   /**
    * Find the scope of the specified object.
    */
   @Override
   public Object getScope(Object id, Object scope) {
      init();
      return engine.getScriptable(id, (Scriptable) scope);
   }

   /**
    * Initialize the script engine.
    */
   @Override
   public synchronized void init() {
      if(rengine == null) {
         rengine = (ReportJavaScriptEngine) createScriptEngine();
         engine = rengine;

         try {
            rengine.init(report, vars);
         }
         catch(Exception e) {
            LOG.error("Failed to initialize script engine when " +
               "initializing environment", e);
         }
      }
   }

   /**
    * Run cleanup tasks for the javascript engine
    */
   public void dispose() {
      if(rengine != null) {
         rengine.dispose();
         rengine = null;
      }
   }

   /**
    * Create a script engine instance.
    */
   @Override
   protected JavaScriptEngine createScriptEngine() {
      return new ReportJavaScriptEngine();
   }

   private ReportJavaScriptEngine rengine = null;
   private ReportSheet report;

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportJavaScriptEnv.class);
}
