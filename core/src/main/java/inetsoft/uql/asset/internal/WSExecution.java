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
package inetsoft.uql.asset.internal;

import inetsoft.mv.MVSession;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.Worksheet;

/**
 * Information about current worksheet execution environment.
 * @version 12.2
 * @author InetSoft Technology Corp.
 */
public class WSExecution {
   /**
    * Set the current worksheet that's been executed.
    */
   public static void setAssetQuerySandbox(AssetQuerySandbox box) {
      if(box == null) {
         curr.remove();
      }
      else {
         curr.set(box);
      }

      MVSession.setCurrentSession(box != null ? box.getMVSession() : null);
   }

   /**
    * Get the current worksheet that's been executed.
    */
   public static Worksheet getWorksheet() {
      AssetQuerySandbox box = curr.get();
      return (box != null) ? box.getWorksheet() : null;
   }

   /**
    * Get the current worksheet execution context.
    */
   public static AssetQuerySandbox getAssetQuerySandbox() {
      return curr.get();
   }

   private static ThreadLocal<AssetQuerySandbox> curr = new ThreadLocal<>();
}
