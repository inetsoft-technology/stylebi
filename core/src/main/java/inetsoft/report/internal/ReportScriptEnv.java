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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.util.script.ScriptEnv;

/**
 * Encapsulate a script engine.
 *
 * @version 6.1, 6/20/2004
 * @author InetSoft Technology Corp
 */
public interface ReportScriptEnv extends ScriptEnv {
   /**
    * Set the report to use with this script env.
    */
   void setReport(ReportSheet report);
   
   /**
    * Execute a script.
    * @param elem element this script is attached to.
    * @param script script object.
    * @param scope scope this script should execute in. Using report
    * scope if the value is null.
    */
   Object exec(ReportElement elem, Object script, Object scope)
      throws Exception;
      
   /**
    * Find the scope of the specified object.
    */
   Class getType(Object id, Object scope);

   /**
    * Run cleanup tasks for the script environment
    */
   void dispose();
}

