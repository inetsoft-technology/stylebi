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
package inetsoft.report.script.formula;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77910, end to end through the real GraalJS engine: a script that reads a worksheet table
 * whose swap file is lost must fail with the swap file read failure, not with a script error
 * that hides it from the caller or a wrong number from a value read as null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptTableReadSwapLostTest {
   private static final String SUM =
      "var t = 0; for(var i = 1; i < Query1.length; i++) { t += Query1[i][1]; } t";
   private static final Object[][] DATA = { { "a", "b" }, { "x", 2 }, { "y", 4 } };

   @BeforeEach
   void setUp() {
      senv = new GraalJavaScriptEnv();
      senv.init();
      lost = new LostSwapFile();
   }

   @AfterEach
   void tearDown() {
      lost.close();
   }

   @Test
   void tableLengthThrowsTheLostSwapFile() throws Exception {
      AssetQueryScope scope = worksheetScope(new LostSwapFile.Table(DATA, 1, true, lost));

      assertLost(() -> senv.exec(senv.compile("Query1.length"), scope, null, null));
   }

   @Test
   void columnReadThrowsTheLostSwapFile() throws Exception {
      AssetQueryScope scope = worksheetScope(new LostSwapFile.Table(DATA, 1, true, lost));

      assertLost(() -> senv.exec(senv.compile("Query1['b']"), scope, null, null));
   }

   @Test
   void cellSumThrowsTheLostSwapFile() throws Exception {
      AssetQueryScope scope = worksheetScope(new LostSwapFile.Table(DATA, 1, false, lost));

      assertLost(() -> senv.exec(senv.compile(SUM), scope, null, null));
   }

   @Test
   void cellSumReadsTheTable() throws Exception {
      LostSwapFile.Table table = new LostSwapFile.Table(DATA, 1, false, lost);
      table.lost = false;
      AssetQueryScope scope = worksheetScope(table);

      assertEquals(6, ((Number) senv.exec(senv.compile(SUM), scope, null, null)).intValue());
   }

   @Test
   void tableLengthReadsTheTable() throws Exception {
      LostSwapFile.Table table = new LostSwapFile.Table(DATA, 1, true, lost);
      table.lost = false;
      AssetQueryScope scope = worksheetScope(table);

      assertEquals(3, ((Number) senv.exec(senv.compile("Query1.length"), scope, null, null))
         .intValue());
   }

   private void assertLost(Executable script) {
      Throwable thrown = assertThrows(Throwable.class, script);
      SwapFileReadException swap = SwapFileReadException.find(thrown);
      assertNotNull(swap, () -> "not a lost swap file: " + thrown);
      assertEquals(lost.getFile(), swap.getFile());
   }

   static AssetQueryScope worksheetScope(TableLens table) throws Exception {
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAssembly("Query1")).thenReturn(mock(EmbeddedTableAssembly.class));
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getTableLens(eq("Query1"), anyInt(), any())).thenReturn(table);
      return new AssetQueryScope(box);
   }

   private GraalJavaScriptEnv senv;
   private LostSwapFile lost;
}
