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
package inetsoft.report.composition.execution;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O2 probe (Testing #77123): the cost per row of a formula column whose script throws, pool
 * off vs on. Only runs with {@code -Drel.long=true}; prints ms/row and pool counters.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelErrorPathProbeTest {
   @AfterEach
   public void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);
      SreeEnv.remove("script.max.errors");
   }

   /**
    * The cost of one claim + clean, after an exec that succeeds, one that throws, and none.
    */
   @Test
   public void cleanCost() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"));
      int n = Integer.getInteger("rel.rows", 5000);
      AssetQuerySandbox box = sandbox(true);

      try {
         WorksheetScriptEnv env = (WorksheetScriptEnv) box.getScriptEnv();
         Object ok = env.compile("1 + 1");
         Object bad = env.compile("unknownName + 1");

         for(int round = 0; round < 2; round++) {
            for(String mode : new String[] { "none", "ok", "bad" }) {
               long t0 = System.nanoTime();

               for(int i = 0; i < n; i++) {
                  try(SlotClaim claim = env.claimSlot()) {
                     if(!mode.equals("none")) {
                        env.exec(mode.equals("ok") ? ok : bad, null, null, null);
                     }
                  }
                  catch(Exception ex) {
                     // expected for bad
                  }
               }

               double ms = (System.nanoTime() - t0) / 1e6;
               System.out.printf("O2CLEAN round=%d mode=%-4s n=%d ms/claim=%.4f%n",
                                 round, mode, n, ms / n);
            }

            // the same execs inside one claim: no clean per exec
            for(String mode : new String[] { "ok", "bad" }) {
               long t0 = System.nanoTime();

               try(SlotClaim claim = env.claimSlot()) {
                  for(int i = 0; i < n; i++) {
                     try {
                        env.exec(mode.equals("ok") ? ok : bad, null, null, null);
                     }
                     catch(Exception ex) {
                        // expected for bad
                     }
                  }
               }

               double ms = (System.nanoTime() - t0) / 1e6;
               System.out.printf("O2CLEAN round=%d mode=%-4s one-claim n=%d ms/exec=%.4f%n",
                                 round, mode, n, ms / n);
            }
         }
      }
      finally {
         box.dispose();
      }
   }

   @Test
   public void probe() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"));
      int rows = Integer.getInteger("rel.rows", 10000);
      int runs = Integer.getInteger("rel.runs", 3);
      String only = System.getProperty("rel.only");
      String maxErrors = System.getProperty("rel.maxErrors");

      if(maxErrors != null) {
         SreeEnv.setProperty("script.max.errors", maxErrors);
      }
      Map<String, String> scripts = new LinkedHashMap<>();
      scripts.put("ok", "field['value'] * 2");
      scripts.put("ref", "unknownName + field['value']");
      scripts.put("type", "var o = null; o.foo + field['value']");
      scripts.put("syntax", "eval('1 +') + field['value']");
      scripts.put("pct1", "if(field['value'] % 100 == 0) { throw new Error('x'); } field['value'] * 2");

      // warm up both modes
      if(!Boolean.getBoolean("rel.nowarm")) {
         run("ok", scripts.get("ok"), 2000, false);
         run("ok", scripts.get("ok"), 2000, true);
         run("ref", scripts.get("ref"), 2000, false);
         run("ref", scripts.get("ref"), 2000, true);
      }

      for(Map.Entry<String, String> e : scripts.entrySet()) {
         if(only != null && !only.contains(e.getKey())) {
            continue;
         }

         for(boolean pool : new boolean[] { false, true }) {
            for(int i = 0; i < runs; i++) {
               run(e.getKey(), e.getValue(), rows, pool);
            }
         }
      }
   }

   private static void run(String name, String script, int rows, boolean pool)
      throws Exception
   {
      AssetQuerySandbox box = sandbox(pool);

      try {
         Object[][] data = new Object[rows + 1][];
         data[0] = new Object[] {"value"};

         for(int i = 1; i <= rows; i++) {
            data[i] = new Object[] {i};
         }

         FormulaTableLens lens = new FormulaTableLens(new DefaultTableLens(data),
            new String[] {"f"}, new String[] {script}, box.getScriptEnv(), box.getScope());
         PoolMetrics m = pool ? ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics() : null;
         long cleans0 = m == null ? 0 : m.getCleans();
         long creations0 = m == null ? 0 : m.getCreations();
         int errors = 0;
         int values = 0;
         StringBuilder nulls = new StringBuilder();
         StringBuilder errs = new StringBuilder();
         long t0 = System.nanoTime();

         for(int r = 1; ; r++) {
            try {
               if(!lens.moreRows(r)) {
                  break;
               }

               if(lens.getObject(r, 1) != null) {
                  values++;
               }
               else if(nulls.length() < 400) {
                  nulls.append(r).append(' ');
               }
            }
            catch(Exception ex) {
               errors++;

               if(errs.length() < 400) {
                  errs.append(r).append(' ');
               }
            }
         }

         double ms = (System.nanoTime() - t0) / 1e6;
         System.out.printf("O2PROBE %-6s pool=%-5s rows=%d ms=%.0f ms/row=%.4f errors=%d values=%d" +
                              " cleans=%d creations=%d%n",
                           name, pool, rows, ms, ms / rows, errors, values,
                           m == null ? 0 : m.getCleans() - cleans0,
                           m == null ? 0 : m.getCreations() - creations0);

         if(Boolean.getBoolean("rel.rowsdump")) {
            System.out.println("O2NULLS " + name + " pool=" + pool + " " + nulls);
            System.out.println("O2ERRS  " + name + " pool=" + pool + " " + errs);
         }
      }
      finally {
         box.dispose();
      }
   }

   private static AssetQuerySandbox sandbox(boolean pool) {
      SreeEnv.setProperty(PoolConfig.ENABLED, String.valueOf(pool));
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      assertEquals(pool, box.getScriptEnv() instanceof WorksheetScriptEnv);
      return box;
   }
}
