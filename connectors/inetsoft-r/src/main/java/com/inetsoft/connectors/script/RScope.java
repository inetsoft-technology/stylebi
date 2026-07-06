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

import inetsoft.report.internal.Util;
import inetsoft.uql.XTable;
import inetsoft.util.script.ScriptUtil;
import inetsoft.util.script.graal.ScriptFunction;
import inetsoft.util.script.graal.ScriptScope;
import org.rosuda.REngine.Rserve.RConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class RScope implements ScriptScope {
   public RScope(RConnection connection) {
      this.connection = new RConnectionScriptable(connection);
      members.put("r", this.connection);
      addFunctions();
   }

   public String getClassName() {
      return "R";
   }

   @Override
   public Object getMember(String name) {
      return members.get(name);
   }

   @Override
   public boolean hasMember(String name) {
      return members.containsKey(name);
   }

   @Override
   public void putMember(String name, Object value) {
      members.put(name, value);
   }

   @Override
   public boolean removeMember(String name) {
      return members.remove(name) != null;
   }

   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray();
   }

   @Override
   public ScriptScope getParentScope() {
      return parent;
   }

   public void setParentScope(ScriptScope parent) {
      this.parent = parent;
   }

   @SuppressWarnings("unused")
   public void log(String message) {
      LOG.info(message);
   }

   @SuppressWarnings("unused")
   public void logTable(Object table) {
      XTable xtable = (XTable) ScriptUtil.unwrap(table);
      LOG.info(Util.tableToString(xtable));
   }

   public void setScriptExecuted(boolean scriptExecuted) {
      connection.setScriptExecuted(scriptExecuted);
   }

   private void addFunctions() {
      // Feature #75423: native functions exposed via ScriptFunction (GraalJS).
      try {
         members.put("log", new ScriptFunction(this, getClass(), "log", String.class));
         members.put("logTable", new ScriptFunction(this, getClass(), "logTable", Object.class));
      }
      catch(Exception e) {
         LOG.warn("Failed to register R scope functions", e);
      }
   }

   private final RConnectionScriptable connection;
   private final Map<String, Object> members = new LinkedHashMap<>();
   private ScriptScope parent;
   private static final Logger LOG = LoggerFactory.getLogger(RScope.class);
}
