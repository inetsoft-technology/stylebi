/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

/**
 * Refresh viewsheet object command.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RefreshVSObjectCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public RefreshVSObjectCommand() {
      super();
   }

   public RefreshVSObjectCommand(VSAssemblyInfo info, String sharedHint,
                                 boolean shrink, ViewsheetSandbox box) {
      this();

      if(info instanceof OutputVSAssemblyInfo && box != null) {
         box = getSandbox(box, info.getAbsoluteName());
         ((OutputVSAssemblyInfo) info).setLinkVarTable(box.getAllVariables());
         ((OutputVSAssemblyInfo) info).setLinkSelections(box.getSelections());
      }

      put("info", info);

      if(sharedHint != null) {
         put("SHARED_HINT", sharedHint);
      }

      if(!shrink) {
         put("NO_SHRINK", "true");
      }
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      AssemblyInfo info = getAssemblyInfo(this);
      RefreshVSObjectCommand cmd2 = (RefreshVSObjectCommand) obj;
      AssemblyInfo info2 = getAssemblyInfo(cmd2);

      // In ApplyVSAssemblyInfoEvent, there maybe two RefreshVSObjectCommand: one is
      // called in execute, the other is layoutviewsheet. The new command should not cover
      // old, but add to the AssetCommand. If cover old, its position in AssetCommand
      //  will be before the AddVSObjectCommand, and its size will not right.
      if(!info.getPixelSize().equals(info2.getPixelSize())) {
         return false;
      }

      return true;
   }
}
