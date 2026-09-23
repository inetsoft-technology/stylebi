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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.graal.GraalJavaScriptEnv;

import java.util.concurrent.locks.Lock;

/**
 * The worksheet script environment of a sandbox in context pool mode (bug #76960). Skeleton:
 * it still runs on one context; it only has no execution lock, so a condition filter over it
 * takes none.
 */
public class WorksheetScriptEnv extends GraalJavaScriptEnv {
   public WorksheetScriptEnv(PoolConfig config) {
      this.config = config;
   }

   public PoolConfig getConfig() {
      return config;
   }

   /**
    * Called by the sandbox when it drops this env.
    */
   public void retire() {
      reset();
   }

   /**
    * @return {@code null}: pooled contexts are never waited for (spec §5.1, §7).
    */
   @Override
   public Lock getExecutionLock() {
      return null;
   }

   private final PoolConfig config;
}
