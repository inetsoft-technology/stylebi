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
import org.mozilla.javascript.ScriptableObject;
import org.rosuda.REngine.Rserve.RConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RScope extends ScriptableObject {
   public RScope(RConnection connection) {
      this.connection = new RConnectionScriptable(connection);
      super.put("r", this, this.connection);
      defineFunctionProperties(new String[] { "log", "logTable" }, RScope.class, 0);
   }

   @Override
   public String getClassName() {
      return "R";
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

   private final RConnectionScriptable connection;
   private static final Logger LOG = LoggerFactory.getLogger(RScope.class);
}
