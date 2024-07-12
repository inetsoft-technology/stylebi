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
package inetsoft.report.internal;

import inetsoft.report.ReportSheet;
import inetsoft.sree.SreeEnv;
import inetsoft.util.script.TimeoutContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ScriptEnvRepository creates script environment.
 *
 * @version 6.1, 7/20/2004
 * @author InetSoft Technology Corp
 */
public class ReportScriptEnvRepository {
   static boolean found = false;
   private static final Logger LOG =
      LoggerFactory.getLogger(ReportScriptEnvRepository.class);
   
   static {
      try {
         try {
            int timeout = Integer.parseInt(
               SreeEnv.getProperty("script.execution.timeout"));
            TimeoutContext.setTimeout(timeout);
            int maxiStackDepth = Integer.parseInt(
               SreeEnv.getProperty("script.execution.stackdepth"));
            TimeoutContext.setStackDepth(maxiStackDepth);
         }
         catch(NumberFormatException ex) {
            // ign
         }

         Class.forName("inetsoft.report.script.ReportJavaScriptEngine");
         found = true;
      }
      catch(Throwable e) {
         LOG.error("Report script engine class not found", e);
      }
   }

   /**
    * Create a scripting environment for a report.
    */
   public static ReportScriptEnv getScriptEnv(ReportSheet report) {
      if(found) {
         try {
            String cls = "inetsoft.report.internal.ReportJavaScriptEnv";

            ReportScriptEnv env =
               (ReportScriptEnv) Class.forName(cls).newInstance();
            env.setReport(report);

            return env;
         }
         catch(Throwable e) {
            LOG.error("Failed to instantiate the report script engine class", e);
         }
      }

      return null;
   }
}

