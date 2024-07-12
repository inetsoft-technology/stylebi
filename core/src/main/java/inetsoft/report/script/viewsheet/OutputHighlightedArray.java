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
package inetsoft.report.script.viewsheet;

import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.util.script.ArrayObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.*;

/**
 * This array provides access to highlighted status.
 */
public class OutputHighlightedArray extends ScriptableObject implements ArrayObject {
   /**
    * Constructor.
    */
   public OutputHighlightedArray(OutputVSAssemblyInfo assembly) {
      this.assembly = assembly;
   }

   /**
    * Get array element type.
    */
   @Override
   public Class getType() {
      return Boolean.class;
   }

   @Override
   public boolean has(String id, Scriptable start) {
      init();
      return hlnames.contains(id);
   }

   @Override
   public Object get(String id, Scriptable start) {
      init();

      if(hlnames.contains(id)) {
	 HighlightGroup highlights = assembly.getHighlightGroup();
	 Highlight hl = highlights.findGroup(assembly.getValue());

	 return hl != null && hl.getName().equals(id);
      }

      return super.get(id, start);
   }

   @Override
   public Object[] getIds() {
      init();
      return hlnames.toArray();
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[highlightname]";
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[]";
   }

   @Override
   public String getClassName() {
      return "Highlighted";
   }

   /**
    * Init properties.
    */
   private void init() {
      if(inited || assembly == null) {
         return;
      }

      inited = true;
      HighlightGroup highlights = assembly.getHighlightGroup();
      hlnames = Arrays.asList(highlights.getNames());
   }

   private OutputVSAssemblyInfo assembly;
   private boolean inited = false;
   private List hlnames = new ArrayList();
}
