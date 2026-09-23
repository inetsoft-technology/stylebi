/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util.script;

/**
 * A span of script work that one thread runs against one script environment (bug #76960). It
 * is opened with {@link ScriptEnv#openSpan()} and closed in a try-with-resources or finally
 * on the same thread. An env with pooled worksheet contexts keeps one context for the whole
 * span, so script globals live for the span and the context is cleaned once at its end.
 * Other envs return {@link #NONE}.
 */
public interface ScriptSpan extends AutoCloseable {
   /**
    * The span of an env without pooled contexts: nothing to hold, no read-ahead.
    */
   ScriptSpan NONE = new ScriptSpan() {
      @Override
      public int batchRows() {
         return 0;
      }

      @Override
      public void close() {
      }
   };

   /**
    * @return the minimum number of rows a lens should populate under this span, so that one
    * context clean is shared by a batch of rows; 0 for no minimum.
    */
   int batchRows();

   @Override
   void close();
}
