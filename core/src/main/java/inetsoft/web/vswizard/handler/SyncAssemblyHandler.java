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
package inetsoft.web.vswizard.handler;

import inetsoft.uql.ConditionList;
import inetsoft.uql.viewsheet.*;
import org.springframework.stereotype.Component;

@Component
public class SyncAssemblyHandler {
   public SyncAssemblyHandler() { }

   public void syncCondition(DataVSAssembly fromAssembly, DataVSAssembly targetAssembly) {
      ConditionList oldCondition = fromAssembly.getPreConditionList();

      if(oldCondition != null) {
         oldCondition = oldCondition.clone();
         targetAssembly.setPreConditionList(oldCondition);
      }
   }

   /**
    * Sync format.
    */
   public void syncFormat(AbstractVSAssembly fromAssembly, AbstractVSAssembly targetAssembly) {
      FormatInfo fromFormatInfo = fromAssembly.getFormatInfo();
      targetAssembly.setFormatInfo(fromFormatInfo.clone());
   }

   public static String updateScript(String script, String oname, String nname) {
      if(script == null) {
         return null;
      }

      if(nname != null && nname.indexOf(" ") != -1) {
         String pattern = "viewsheet\\['" + oname + "'\\]|viewsheet\\[\"" + oname + "\"\\]";
         script = script.replaceAll(pattern, oname);
         return script.replaceAll(oname, "viewsheet['" + nname + "']");
      }

      return script.replaceAll(oname, nname);
   }
}