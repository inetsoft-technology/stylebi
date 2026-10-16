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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.script.ScriptException;

import java.util.Optional;

/**
 * Reads a table's rendered {@link VSTableLens} for the wiz writes that address a column, header
 * or cell by what it renders as, and turns "the table did not render" into a named 400.
 *
 * <p>A table whose script or condition throws while it loads has no lens. The sandbox reports that
 * two ways: the first read after the table was last cleared rethrows the {@link ScriptException},
 * and every later read returns {@code null} (the failed load is cached as NULL until the data map
 * is cleared). Dereferenced as-is the first is a raw 500 and the second an NPE or an
 * {@code IllegalStateException}, all of which {@code WizControllerErrorHandler} masks as an
 * unexpected server error; an {@link IllegalArgumentException} is the one it relays as a 400 with
 * its message.
 */
public final class TableLensGuard {
   private TableLensGuard() {
   }

   /**
    * @param displayName what the agent called the assembly, for the message.
    * @param purpose     what could not be done without the lens, e.g. {@code "its columns cannot
    *                    be resolved"}.
    *
    * @return the rendered lens, never {@code null}.
    *
    * @throws IllegalArgumentException when there is no sandbox, or the table did not render
    *         because of a {@link ScriptException} or a cached failed load. Any other failure is
    *         rethrown unchanged so it keeps its own handling.
    */
   public static VSTableLens requireRenderedLens(RuntimeViewsheet rvs, VSAssembly assembly,
                                                 String displayName, String purpose)
      throws Exception
   {
      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box == null || box.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + displayName + "' has no active render sandbox right now, so " + purpose + ".");
      }

      String oname = assembly.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      ScriptException failure = null;
      VSTableLens lens = null;

      try {
         lens = box.get().getVSTableLens(oname, detail);
      }
      catch(Exception e) {
         failure = scriptFailure(e);

         if(failure == null) {
            throw e;
         }
      }

      if(lens != null) {
         return lens;
      }

      throw new IllegalArgumentException(
         "'" + displayName + "' did not render" +
         (failure == null
            ? " (its last load failed, e.g. on a script or condition error)"
            : " (its script failed while the table loaded: " + failure.getMessage() + ")") +
         ", so " + purpose + ". Fix the table's script or conditions and retry.");
   }

   /**
    * The lens, or {@code null} when the table did not render -- including a {@link
    * ScriptException} on the first read, so a caller that already has a {@code null} branch
    * handles both outcomes the same way. Any other failure is rethrown.
    */
   public static VSTableLens lensOrNull(ViewsheetSandbox box, String name, boolean detail)
      throws Exception
   {
      try {
         return box.getVSTableLens(name, detail);
      }
      catch(Exception e) {
         if(scriptFailure(e) == null) {
            throw e;
         }

         return null;
      }
   }

   private static ScriptException scriptFailure(Throwable error) {
      for(Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
         if(t instanceof ScriptException script) {
            return script;
         }
      }

      return null;
   }
}
