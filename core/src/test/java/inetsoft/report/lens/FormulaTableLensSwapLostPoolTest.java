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

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cases of {@link FormulaTableLensSwapLostTest} with the worksheet script context pool on
 * (script.ws.contextPool, the default), whose lens batches take another path: no engine lock,
 * a claimed span per batch, pooled batch sizes, and the owned-object snapshot of a batch that
 * ends at a failed row (bug #77912).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FormulaTableLensSwapLostPoolTest extends FormulaTableLensSwapLostTest {
   @Override
   GraalJavaScriptEnv createEnv(Faults faults) {
      return new FailingPoolEnv(faults);
   }

   @AfterEach
   public void retirePool() {
      ((WorksheetScriptEnv) env).retire();
   }

   /**
    * The cases here do run the pooled batch path: no engine lock, and a pooled look-ahead.
    */
   @Test
   public void lensTakesThePooledPath() throws Exception {
      FormulaTableLens lens = new FormulaTableLens(
         new DefaultTableLens(new Object[][] { { "value" }, { 1 }, { 2 } }),
         new String[] { "f" }, new String[] { "field['value']" }, env, null);

      assertFalse(env.usesExecutionLock());
      assertFalse(lens.moreRows(TableLens.EOT));
      Field poolBatch = FormulaTableLens.class.getDeclaredField("poolBatch");
      poolBatch.setAccessible(true);
      assertTrue(poolBatch.getInt(lens) > 0, "a pooled batch sized its look-ahead");
   }

   /**
    * A pooled env whose execs fail as {@link Faults} says.
    */
   private static final class FailingPoolEnv extends WorksheetScriptEnv {
      FailingPoolEnv(Faults faults) {
         super(PoolConfig.defaults());
         this.faults = faults;
      }

      @Override
      public Object exec(Object script, Object scope, Object rscope, Object target)
         throws Exception
      {
         faults.check();
         return super.exec(script, scope, rscope, target);
      }

      private final Faults faults;
   }
}
