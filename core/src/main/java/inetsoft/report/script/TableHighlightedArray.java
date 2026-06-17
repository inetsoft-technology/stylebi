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
package inetsoft.report.script;

import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.XTable;
import inetsoft.util.script.ArrayObject;
import inetsoft.util.script.graal.ScriptArrayScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This array provides access to highlighted status.
 */
public class TableHighlightedArray implements ArrayObject, ScriptArrayScope {
   /**
    * Constructor.
    */
   public TableHighlightedArray(XTable table) {
      init(table);
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
      return hlnames.contains(id);
   }

   @Override
   public Object getMember(String id) {
      if(hlnames.contains(id)) {
         return hltable.isHighlighted(id);
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
   private void init(XTable table) {
      hltable = (TableHighlightAttr.HighlightTableLens)
         Util.getNestedTable(table, TableHighlightAttr.HighlightTableLens.class);

      if(hltable != null) {
         hlnames = hltable.getHighlightNames();
      }
   }

   private TableElementDef elem = null;
   private boolean inited = false;
   private List hlnames = new ArrayList();
   private TableHighlightAttr.HighlightTableLens hltable;
   private final Map<String, Object> members = new LinkedHashMap<>();
}
