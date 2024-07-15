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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

/**
 * Add viewVsheet object command.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class AddVSObjectCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public AddVSObjectCommand() {
      super();
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param mode the mode.
    */
   public AddVSObjectCommand(VSAssemblyInfo info, int mode) {
      this(info, mode, null);
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param mode the mode.
    */
   public AddVSObjectCommand(VSAssemblyInfo info, int mode,
                             ViewsheetSandbox box)
   {
      this();

      if(info instanceof OutputVSAssemblyInfo && box != null) {
         box = getSandbox(box, info.getAbsoluteName());
         ((OutputVSAssemblyInfo) info).setLinkVarTable(box.getAllVariables());
         ((OutputVSAssemblyInfo) info).setLinkSelections(box.getSelections());
      }

      put("info", info);
      put("mode", "" + mode);
   }
}
