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
package inetsoft.report.script.formula;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableArray;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.util.script.DynamicScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A scriptable used as the container for all data tables in an asset query.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class AssetQueryScope implements DynamicScope, Cloneable {
   /**
    * Create a scope for an asset query.
    */
   public AssetQueryScope(AssetQuerySandbox box) {
      this.box = box;
      setVariableTable(box.getVariableTable());
   }

   /**
    * Get the mode.
    * @return the mode of the scope.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Set the mode.
    * @param mode the specified mode of the scope.
    */
   public void setMode(int mode) {
      this.mode = mode;
   }

   /**
    * Set the parameters.
    */
   public void setVariableTable(VariableTable vars) {
      putMember("parameter", new VariableScriptable(vars));
   }

   /**
    * Set the parameters.
    */
   public VariableTable getVariableTable() {
      Object currentVarTable = getMember("parameter");

      if(currentVarTable instanceof VariableScriptable) {
         currentVarTable = ((VariableScriptable) currentVarTable).unwrap();
      }

      if(currentVarTable instanceof VariableTable) {
         return (VariableTable) currentVarTable;
      }

      return null;
   }

   /**
    * Set the parameters.
    */
   public void mergeVariableTable(VariableTable vars) throws Exception {
      VariableTable currentVarTable = getVariableTable();

      if(currentVarTable != null) {
         currentVarTable.addAll(vars);
      }
   }

   /**
    * Get a property value.
    */
   @Override
   public Object getMember(String id) {
      try {
         Worksheet ws = box.getWorksheet();

         if(ws != null) {
            Object val = tablemap.get(id);

            if(val == null) {
               if(ws.getAssembly(id) instanceof TableAssembly) {
                  val = new TableAssemblyScriptable(id, box, mode);
                  tablemap.put(id, val);
               }
               else {
                  tablemap.put(id, NOT_TABLE);
               }
            }

            if(val instanceof TableArray) {
               return val;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get property from asset query: " + id, ex);
      }

      // the dynamic scope fallback (executing scope) is now provided
      // centrally by BindingRootProxy
      return members.get(id);
   }

   @Override
   public boolean hasMember(String id) {
      try {
         Worksheet ws = box.getWorksheet();

         if(ws != null && ws.getAssembly(id) instanceof TableAssembly) {
            return true;
         }
      }
      catch(Exception ex) {
         // ignore
      }

      return members.containsKey(id);
   }

   @Override
   public void putMember(String id, Object value) {
      members.put(id, value);
   }

   @Override
   public boolean removeMember(String id) {
      return members.remove(id) != null;
   }

   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray(new Object[0]);
   }

   /**
    * Get the name of this scriptable.
    */
   public String getClassName() {
      return "AssetQuerySandbox";
   }

   /**
    * Make a copy of this scope.
    */
   @Override
   public Object clone() {
      try {
         AssetQueryScope obj = (AssetQueryScope) super.clone();
         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private static Object NOT_TABLE = new String("NOT_TABLE");
   private int mode;
   private AssetQuerySandbox box;
   private Map tablemap = new HashMap();
   private final Map<String, Object> members = new LinkedHashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetQueryScope.class);
}
