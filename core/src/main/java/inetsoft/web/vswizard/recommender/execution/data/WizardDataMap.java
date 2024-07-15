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
package inetsoft.web.vswizard.recommender.execution.data;

import java.util.HashMap;
import java.util.Iterator;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class WizardDataMap implements java.io.Serializable {
   public WizardDataMap() {
   }

   public void put(String key, WizardData data) {
      map.put(key, data);
   }

   public WizardData get(String key) {
      return map.get(key);
   }

   public String toString() {
      Iterator it = map.keySet().iterator();
      StringBuffer buf = new StringBuffer();
      int i = 0;

      while(it.hasNext()) {
         String field = (String) it.next();
         buf.append("\n" + i + ": " + map.get(field));
      }

      return buf.toString();
   }

   private HashMap<String, WizardData> map = new HashMap<>();
}