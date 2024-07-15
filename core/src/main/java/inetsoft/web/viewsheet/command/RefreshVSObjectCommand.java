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

import inetsoft.web.viewsheet.model.VSObjectModel;

/**
 * Command that instructs the client to refresh an assembly object.
 *
 * @since 12.3
 */
public class RefreshVSObjectCommand implements ViewsheetCommand {
   public VSObjectModel getInfo() {
      return info;
   }

   public void setInfo(VSObjectModel info) {
      this.info = info;
   }

   /**
    * Gets if force to refresh chart
    * (it's true when click manual refresh status's refresh button).
    *
    * @return true if should force refresh.
    */
   public boolean isForce() {
      return this.force;
   }

   public void setForce(boolean force) {
      this.force = force;
   }

   public String getShared() {
      return shared;
   }

   /**
    * For shared filter.
    */
   public void setShared(String shared) {
      this.shared = shared;
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
      return this.info != null;
   }

   private VSObjectModel info;
   private boolean force;
   private String shared;
   private boolean wizardTemporary;
}
