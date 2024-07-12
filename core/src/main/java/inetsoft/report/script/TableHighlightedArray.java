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
package inetsoft.report.script;

import inetsoft.report.internal.TableElementDef;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.uql.XTable;
import inetsoft.util.script.ArrayObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This array provides access to highlighted status.
 */
public class TableHighlightedArray extends ScriptableObject implements ArrayObject {
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
   public boolean has(String id, Scriptable start) {
      return hlnames.contains(id);
   }

   @Override
   public Object get(String id, Scriptable start) {
      if(hlnames.contains(id)) {
         return hltable.isHighlighted(id);
      }

      return super.get(id, start);
   }

   @Override
   public Object[] getIds() {
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
}
