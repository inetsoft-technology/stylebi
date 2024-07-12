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

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.uql.XRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Filter Context save the context for filters.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FilterContext {
   /**
    * Filter context contains the context used in filter pane creation.
    */
   public FilterContext(DesignSession xsession, ReportElement elem,
                        XRepository xrepository)
   {
      this.xsession = xsession;
      this.elem = (BindableElement) elem;
      this.xrepository = xrepository;
   }

   /**
    * Get the design session object.
    */
   public DesignSession getDesignSession() {
      return xsession;
   }

   /**
    * Get the report element object.
    */
   public BindableElement getReportElement() {
      return elem;
   }

   /**
    * Get the report associated with this context.
    */
   public ReportSheet getReport() {
      try {
         return elem.getReport();
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Get the XRepository object.
    */
   public XRepository getRepository() {
      return xrepository;
   }

   /**
    * Get the hint by key.
    */
   public Object get(String key) {
      return map.get(key);
   }

   /**
    * Set the hint by key.
    */
   public void put(String key, Object val) {
      map.put(key, val);
   }

   /**
    * Batch set hints.
    */
   public void putAll(Map<String, Object> map) {
      if(map != null) {
         this.map.putAll(map);
      }
   }

   private XRepository xrepository;
   private DesignSession xsession;
   private BindableElement elem;
   private Map<String, Object> map = new HashMap<>();
}
