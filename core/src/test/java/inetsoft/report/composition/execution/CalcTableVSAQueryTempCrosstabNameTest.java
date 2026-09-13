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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76614: two concurrent {@link CalcTableVSAQuery#getTableLens()}
 * invocations for the same calc table must never build the same temp crosstab assembly name,
 * because the sandbox write lock is transiently dropped mid-invocation (see
 * {@code VSAQuery#getDataWithoutSandboxLock}), letting one invocation's temp crosstab be
 * removed by another invocation's add/remove (or failure-path cleanup) cycle if the two ever
 * share a name.
 *
 * <p>This exercises the actual name-generation logic used by {@code getTableLens()} /
 * {@code createCrosstabAssemblies0()} / {@code removeTempAssembly()}
 * ({@link CalcTableVSAQuery#nextInvocationId()} and
 * {@link CalcTableVSAQuery#getTempCrosstabName(String, long, int)}) directly, under real
 * concurrency, rather than standing up a full {@code ViewsheetSandbox}/{@code RuntimeViewsheet}
 * harness to force two threads through the lock-drop window live — which would require
 * scaffolding (a latched fake {@code AssetDataCache}, or an instrumented
 * {@code getDataWithoutSandboxLock}) disproportionate to proving the naming fix. This is a
 * structural fix verified at the naming-logic level, not a live/concurrency reproduction of the
 * full NPE stack.
 */
@Tag("core")
public class CalcTableVSAQueryTempCrosstabNameTest {
   /**
    * Simulates the pre-fix (bug #76614) deterministic naming scheme: identical calc table name
    * and crosstab index always produce the same assembly name, with no per-invocation
    * distinction at all.
    */
   private static String preFixName(String assemblyName, int ct) {
      return CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + assemblyName + "_Crosstab_" + ct;
   }

   @Test
   public void preFixNamingSchemeCollidesAcrossConcurrentInvocations() {
      // Demonstrates the actual defect: two independent invocations for the same calc table,
      // using the old naming scheme, produce identical names -- this is the collision that let
      // one invocation's temp crosstab be removed by another's cleanup.
      String invocationAName = preFixName("vs_calc1", 0);
      String invocationBName = preFixName("vs_calc1", 0);

      assertEquals(invocationAName, invocationBName,
                   "pre-fix naming scheme was expected to collide across invocations");
   }

   @Test
   public void fixedNamingSchemeNeverCollidesForSameCalcTableAndIndex() {
      long invocationA = CalcTableVSAQuery.nextInvocationId();
      long invocationB = CalcTableVSAQuery.nextInvocationId();

      assertNotEquals(invocationA, invocationB);

      String nameA = CalcTableVSAQuery.getTempCrosstabName("vs_calc1", invocationA, 0);
      String nameB = CalcTableVSAQuery.getTempCrosstabName("vs_calc1", invocationB, 0);

      assertNotEquals(nameA, nameB,
                       "two invocations for the same calc table/index must not share a temp " +
                       "crosstab name");
   }

   /**
    * Drives many threads concurrently through the same name-generation path that
    * {@code getTableLens()} uses (allocate an invocation id, then build the same set of
    * per-crosstab names that {@code createCrosstabAssemblies0()} would create and
    * {@code removeTempAssembly()} would later sweep), for the *same* calc table name, and
    * asserts that no two threads ever produce the same assembly name -- i.e. concurrent
    * invocations can never collide, satisfying the bug #76614 fix requirement.
    */
   @Test
   public void concurrentInvocationsNeverProduceCollidingNames() throws Exception {
      final String calcTableName = "vs_calc1";
      final int threadCount = 32;
      final int invocationsPerThread = 200;
      final int crosstabsPerInvocation = 3;

      ExecutorService pool = Executors.newFixedThreadPool(threadCount);
      // putIfAbsent returns non-null exactly when a name was already used by another
      // (or the same) invocation -- collecting those tells us if any collision ever happened.
      ConcurrentMap<String, Boolean> seenNames = new ConcurrentHashMap<>();
      List<String> collisions = new CopyOnWriteArrayList<>();
      CyclicBarrier barrier = new CyclicBarrier(threadCount);
      List<Future<?>> futures = new ArrayList<>();

      for(int t = 0; t < threadCount; t++) {
         futures.add(pool.submit(() -> {
            barrier.await();

            for(int i = 0; i < invocationsPerThread; i++) {
               // Mirrors CalcTableVSAQuery.getTableLens(): one invocation id per invocation,
               // shared by every temp crosstab it creates during that invocation.
               long invocationId = CalcTableVSAQuery.nextInvocationId();

               for(int ct = 0; ct < crosstabsPerInvocation; ct++) {
                  String name =
                     CalcTableVSAQuery.getTempCrosstabName(calcTableName, invocationId, ct);

                  if(seenNames.putIfAbsent(name, Boolean.TRUE) != null) {
                     collisions.add(name);
                  }
               }
            }

            return null;
         }));
      }

      for(Future<?> f : futures) {
         f.get(60, TimeUnit.SECONDS);
      }

      pool.shutdown();

      assertTrue(collisions.isEmpty(),
                 "expected no temp crosstab name collisions across concurrent invocations, " +
                 "but found: " + collisions);

      int expectedTotalNames = threadCount * invocationsPerThread * crosstabsPerInvocation;
      assertEquals(expectedTotalNames, seenNames.size(),
                   "every concurrent invocation's temp crosstab names must be distinct");
   }

   @Test
   public void removeTempAssemblySweepPatternMatchesOnlyOwnInvocation() {
      // removeTempAssembly() reconstructs names for ct = 0, 1, 2, ... for one invocation id
      // and stops at the first missing name. Simulate two concurrent invocations' assembly sets
      // and confirm invocation A's sweep can never touch invocation B's names, even though both
      // are for the same calc table and use the same ct indices.
      String calcTableName = "vs_calc1";
      long invocationA = CalcTableVSAQuery.nextInvocationId();
      long invocationB = CalcTableVSAQuery.nextInvocationId();

      Set<String> liveAssemblies = ConcurrentHashMap.newKeySet();

      for(int ct = 0; ct < 3; ct++) {
         liveAssemblies.add(CalcTableVSAQuery.getTempCrosstabName(calcTableName, invocationA, ct));
         liveAssemblies.add(CalcTableVSAQuery.getTempCrosstabName(calcTableName, invocationB, ct));
      }

      // Simulate invocation A's removeTempAssembly() cleanup sweep.
      for(int ct = 0; ; ct++) {
         String name = CalcTableVSAQuery.getTempCrosstabName(calcTableName, invocationA, ct);

         if(!liveAssemblies.contains(name)) {
            break;
         }

         liveAssemblies.remove(name);
      }

      // Invocation B's three assemblies must all have survived invocation A's sweep untouched.
      for(int ct = 0; ct < 3; ct++) {
         assertTrue(liveAssemblies.contains(
                       CalcTableVSAQuery.getTempCrosstabName(calcTableName, invocationB, ct)),
                    "invocation B's temp crosstab must not be removed by invocation A's sweep");
      }
   }
}
