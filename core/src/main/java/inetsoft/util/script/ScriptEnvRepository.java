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
package inetsoft.util.script;

import inetsoft.sree.SreeEnv;

/**
 * ScriptEnvRepository creates script environment.
 *
 * @version 6.1, 7/20/2004
 * @author InetSoft Technology Corp
 */
public class ScriptEnvRepository {
   static boolean found = false;
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

         Class.forName("inetsoft.util.script.JavaScriptEngine");
         found = true;
      }
      catch(Throwable e) {
      }
   }

   /**
    * Create a scripting environment for a report.
    */
   public static ScriptEnv getScriptEnv() {
      if(found) {
         try {
            String cls = "inetsoft.util.script.JavaScriptEnv";

            return (ScriptEnv) Class.forName(cls).newInstance();
         }
         catch(Throwable e) {
         }
      }

      return null;
   }
}

