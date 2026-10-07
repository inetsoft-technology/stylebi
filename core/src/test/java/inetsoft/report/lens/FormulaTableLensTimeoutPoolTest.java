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
package inetsoft.report.lens;

import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The cases of {@link FormulaTableLensTimeoutTest} with the worksheet script context pool on
 * (script.ws.contextPool, the default), whose lens batches take another path: no engine lock,
 * a claimed span per batch, and the owned-object snapshot of a batch that ends at a stopped
 * row (bug #77949).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaTableLensTimeoutPoolTest extends FormulaTableLensTimeoutTest {
   @Override
   GraalJavaScriptEnv createEnv(Stops stops) {
      return new StoppingPoolEnv(stops);
   }

   @AfterEach
   public void retirePool() {
      ((WorksheetScriptEnv) env).retire();
   }

   @Override
   boolean pooled() {
      return true;
   }

   @Test
   public void lensTakesThePooledPath() {
      assertFalse(env.usesExecutionLock());
   }

   /**
    * A pooled env whose execs are stopped as {@link Stops} says.
    */
   private static final class StoppingPoolEnv extends WorksheetScriptEnv {
      StoppingPoolEnv(Stops stops) {
         super(PoolConfig.defaults());
         this.stops = stops;
      }

      @Override
      public Object compile(String cmd, boolean fieldOnly) throws Exception {
         return stops.compiled(cmd, super.compile(cmd, fieldOnly));
      }

      @Override
      public Object exec(Object script, Object scope, Object rscope, Object target)
         throws Exception
      {
         if(loop == null) {
            loop = super.compile(ScriptStopTestSupport.LOOP, false);
         }

         return super.exec(stops.select(script, loop), scope, rscope, target);
      }

      private final Stops stops;
      private Object loop;
   }
}
