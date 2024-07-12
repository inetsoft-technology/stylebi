/*
 * inetsoft-r - StyleBI is a business intelligence web application.
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
package com.inetsoft.connectors.script;

import com.inetsoft.connectors.RRuntime;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XTable;
import inetsoft.util.script.ScriptUtil;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class RConnectionScriptable extends ScriptableObject {
   public RConnectionScriptable(RConnection connection) {
      this.connection = connection;
   }

   @Override
   public String getClassName() {
      return "RConnection";
   }

   @Override
   public Object get(String name, Scriptable start) {
      if(super.has(name, start)) {
         return super.get(name, start);
      }
      else if(scriptExecuted || remoteProperties.contains(name)) {
         try {
            if(RRuntime.isList(connection, name)) {
               XTable table = RRuntime.transferTable(connection, name);
               return new TableArray(table);
            }
            else {
               REXP expr = connection.get(name, null, true);
               return decoder.decode(expr);
            }
         }
         catch(REngineException e) {
            LOG.debug("Failed to get value of symbol '{}' from R server", name, e);
            return Scriptable.NOT_FOUND;
         }
      }

      return Scriptable.NOT_FOUND;
   }

   @Override
   public boolean has(String name, Scriptable start) {
      if(super.has(name, start) || remoteProperties.contains(name)) {
         return true;
      }
      else if(scriptExecuted) {
         try {
            REXP expr = connection.get(name, null, true);
            return expr != null && !expr.isNull();
         }
         catch(Exception ignore) {
         }
      }

      return false;
   }

   @Override
   public void put(String name, Scriptable start, Object value) {
      remoteProperties.add(name);

      try {
         Object unwrapped = ScriptUtil.unwrap(value);

         if(unwrapped instanceof XTable) {
            RRuntime.transferTable((XTable) unwrapped, connection, name);
         }
         else {
            connection.assign(name, encoder.encode(unwrapped));
         }
      }
      catch(RserveException e) {
         throw new RuntimeException("Failed to assign value to R symbol " + name, e);
      }
   }

   public void setScriptExecuted(boolean scriptExecuted) {
      this.scriptExecuted = scriptExecuted;
   }

   private final RConnection connection;
   private final REXPDecoder decoder = new REXPDecoder();
   private final REXPEncoder encoder = new REXPEncoder();
   private final Set<String> remoteProperties = new HashSet<>();
   private boolean scriptExecuted = false;

   private static final Logger LOG = LoggerFactory.getLogger(RConnectionScriptable.class);
}
