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

import inetsoft.report.TableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76905 (reopened): two concurrent
 * {@link CalcTableVSAQuery#getTableLens()} invocations on the no-crosstab path must not process
 * the same {@link CalcTableLens}.
 *
 * <p>On that path the calc assembly passed to the lens-building step is the shared original.
 * {@code getTableLens()} used to build the lens in one {@code synchronized(cassembly)} block and
 * read it back with {@code cassembly.getBaseTable()} in a later one, so an invocation that ran in
 * between replaced the base table and both invocations processed the same lens. Its
 * {@code synchronized process0()} runs GraalJS, which makes that monitor one more lock a
 * non-script thread holds while waiting for the script engine lock, and that a script thread
 * (holding the engine lock) can wait for.
 *
 * <p>Like {@link CalcTableVSAQueryTempCrosstabNameTest}, this checks the lens-building step
 * ({@link CalcTableVSAQuery#createCalcLenses}) directly instead of forcing two threads through a
 * full sandbox/worksheet harness at the exact interleaving.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalcTableVSAQueryLensIsolationTest {
   @Test
   void eachInvocationProcessesTheLensItCreated() {
      CalcTableVSAssembly cassembly = new CalcTableVSAssembly(new Viewsheet(), "Calc1");
      TableLens data = new DefaultTableLens(new Object[][] { { "col" }, { "a" }, { "b" } });

      // invocation A builds its lens; invocation B builds its own before A processes
      List<TableLens> lensesA = CalcTableVSAQuery.createCalcLenses(
         cassembly, List.of(cassembly), List.of(data), new VariableTable(), false);
      List<TableLens> lensesB = CalcTableVSAQuery.createCalcLenses(
         cassembly, List.of(cassembly), List.of(data), new VariableTable(), false);

      assertInstanceOf(CalcTableLens.class, lensesA.get(0));
      assertInstanceOf(CalcTableLens.class, lensesB.get(0));
      assertNotSame(lensesA.get(0), lensesB.get(0),
                    "each invocation must get the lens it created");

      // the shared assembly now reports B's lens; re-reading it (the pre-fix code) would have
      // handed A the lens B is processing
      assertSame(lensesB.get(0), cassembly.getBaseTable());
      assertNotSame(cassembly.getBaseTable(), lensesA.get(0));
   }
}
