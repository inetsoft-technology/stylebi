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
package inetsoft.web.viewsheet.command;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.web.viewsheet.model.VSObjectModel;

/**
 * Class used to instruct the client to add an assembly object.
 */
public class AddVSObjectCommand implements ViewsheetCommand {
   public Mode getMode() {
      return mode;
   }

   public void setMode(Mode mode) {
      this.mode = mode;
   }

   public VSObjectModel<?> getModel() {
      return model;
   }

   public void setModel(VSObjectModel<?> model) {
      this.model = model;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getParent() {
      return parent;
   }

   public void setParent(String parent) {
      this.parent = parent;
   }

   /**
    * Check if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    * @return <tt>true</tt> if temporary, <tt>false</tt> otherwise.
    */
   public boolean isWizardTemporary() {
      return wizardTemporary;
   }

   /**
    * Set if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    * @return <tt>true</tt> if temporary, <tt>false</tt> otherwise.
    */
   public void setWizardTemporary(boolean wizardTemporary) {
      this.wizardTemporary = wizardTemporary;
   }

   @Override
   public boolean isValid() {
      return model != null;
   }

   private String name;
   private Mode mode;
   private VSObjectModel<?> model;
   private String parent = "";
   private boolean wizardTemporary;

   public enum Mode {
      DESIGN_MODE(AssetQuerySandbox.DESIGN_MODE),
      LIVE_MODE(AssetQuerySandbox.LIVE_MODE),
      RUNTIME_MODE(AssetQuerySandbox.RUNTIME_MODE),
      EMBEDDED_MODE(AssetQuerySandbox.EMBEDDED_MODE),
      BROWSE_MODE(AssetQuerySandbox.BROWSE_MODE);

      private final int code;

      Mode(int code) {
         this.code = code;
      }

      public int code() {
         return code;
      }

      public static Mode fromCode(int code) {
         Mode result = null;

         for(Mode mode : values()) {
            if(mode.code == code) {
               result = mode;
               break;
            }
         }

         if(result == null) {
            throw new IllegalArgumentException("Invalid mode code: " + code);
         }

         return result;
      }
   }
}
