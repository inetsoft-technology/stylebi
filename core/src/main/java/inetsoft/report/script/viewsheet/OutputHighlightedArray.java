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
package inetsoft.report.script.viewsheet;

import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.graal.ScriptArrayScope;

import java.util.*;

/**
 * This array provides access to highlighted status.
 */
public class OutputHighlightedArray implements ArrayObject, ScriptArrayScope {
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
   public boolean hasMember(String id) {
      init();
      return hlnames.contains(id);
   }

   @Override
   public Object getMember(String id) {
      init();

      if(hlnames.contains(id)) {
	 HighlightGroup highlights = assembly.getHighlightGroup();
	 Highlight hl = highlights.findGroup(assembly.getValue());

	 return hl != null && hl.getName().equals(id);
      }

      return members.get(id);
   }

   @Override
   public void putMember(String id, Object value) {
      members.put(id, value);
   }

   @Override
   public Object getArrayElement(long index) {
      return null;
   }

   @Override
   public long getArraySize() {
      return 0;
   }

   @Override
   public Object[] getMemberKeys() {
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
   private final Map<String, Object> members = new LinkedHashMap<>();
}
