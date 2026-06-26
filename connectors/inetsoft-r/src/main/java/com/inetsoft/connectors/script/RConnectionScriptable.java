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
package com.inetsoft.connectors.script;

import com.inetsoft.connectors.RRuntime;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XTable;
import inetsoft.util.script.ScriptUtil;
import inetsoft.util.script.graal.ScriptScope;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RConnectionScriptable implements ScriptScope {
   public RConnectionScriptable(RConnection connection) {
      this.connection = connection;
   }

   public String getClassName() {
      return "RConnection";
   }

   @Override
   public Object getMember(String name) {
      if(members.containsKey(name)) {
         return members.get(name);
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
            return null;
         }
      }

      return null;
   }

   @Override
   public boolean hasMember(String name) {
      if(members.containsKey(name) || remoteProperties.contains(name)) {
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
   public void putMember(String name, Object value) {
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

   @Override
   public boolean removeMember(String name) {
      boolean had = members.remove(name) != null;
      had |= remoteProperties.remove(name);
      return had;
   }

   @Override
   public Object[] getMemberKeys() {
      Map<String, Object> keys = new LinkedHashMap<>(members);

      for(String name : remoteProperties) {
         keys.putIfAbsent(name, null);
      }

      return keys.keySet().toArray();
   }

   public void setScriptExecuted(boolean scriptExecuted) {
      this.scriptExecuted = scriptExecuted;
   }

   private final RConnection connection;
   private final REXPDecoder decoder = new REXPDecoder();
   private final REXPEncoder encoder = new REXPEncoder();
   private final Set<String> remoteProperties = new LinkedHashSet<>();
   private final Map<String, Object> members = new LinkedHashMap<>();
   private boolean scriptExecuted = false;

   private static final Logger LOG = LoggerFactory.getLogger(RConnectionScriptable.class);
}
