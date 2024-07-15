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
package inetsoft.report.event;

import inetsoft.report.ReportSheet;
import inetsoft.uql.VariableTable;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Execute event is fired when a report is executed through the
 * XSessionManager.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ExecuteEvent extends java.util.EventObject {
   /**
    * Create an execute event.
    * @param source event source, a StylePage.
    * @param report the report to be executed.
    * @param vars report parameters and values.
    */
   public ExecuteEvent(Object source, ReportSheet report, Hashtable vars) {
      super(source);
      this.report = report;
      this.vars = vars;
   }

   /**
    * Create an execute event.
    * @param source event source, a StylePage.
    * @param report the report to be executed.
    * @param vars report parameters and values.
    */
   public ExecuteEvent(Object source, ReportSheet report, VariableTable vars) 
         throws Exception
   {
      this(source, report, (Hashtable) null);

      this.vars = new Hashtable();
      
      Enumeration keys = vars.keys();
      
      while(keys.hasMoreElements()) {
         Object key = keys.nextElement();
         Object value = vars.get((String) key);
         
         this.vars.put(key, value);
      }
   }

   /**
    * Return the report that is executed.
    */
   public ReportSheet getReport() {
      return report;
   }

   /**
    * Get a parameter value.
    */
   public Object getParameterValue(String name) {
      return vars.get(name);
   }

   /**
    * Get all parameter names.
    */
   public Enumeration getParameterNames() {
      return vars.keys();
   }

   public String toString() {
      return "ExecuteEvent[" + getSource() + ", " + report + "]";
   }

   private ReportSheet report;
   private Hashtable vars;
}

