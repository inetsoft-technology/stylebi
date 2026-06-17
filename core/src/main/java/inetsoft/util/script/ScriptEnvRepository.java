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
package inetsoft.util.script;

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
         Class.forName("inetsoft.util.script.graal.GraalJavaScriptEngine");
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
            String cls = "inetsoft.util.script.graal.GraalJavaScriptEnv";

            return (ScriptEnv) Class.forName(cls).newInstance();
         }
         catch(Throwable e) {
         }
      }

      return null;
   }
}

