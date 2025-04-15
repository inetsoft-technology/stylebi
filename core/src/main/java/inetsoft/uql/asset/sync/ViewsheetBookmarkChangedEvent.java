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
package inetsoft.uql.asset.sync;

import at.favre.lib.bytes.BytesValidator;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetChangedEvent;
import inetsoft.uql.asset.AssetEntry;

public class ViewsheetBookmarkChangedEvent extends AssetChangedEvent {
   public ViewsheetBookmarkChangedEvent(AssetEntry viewsheetEntry) {
      super(viewsheetEntry);
   }

   public ViewsheetBookmarkChangedEvent(RuntimeViewsheet rvs, boolean deleted, String bookmark) {
      super(rvs.getEntry());

      rvsID = rvs.getID();
      this.deleted = deleted;
      this.bookmark = bookmark;
   }

   public ViewsheetBookmarkChangedEvent(RuntimeViewsheet rvs, String oname, String nname,
                                        IdentityID owner) {
      super(rvs.getEntry());

      rvsID = rvs.getID();
      this.deleted = false;
      this.oldBookmark = oname;
      this.bookmark = nname;
      this.owner = owner;
   }

   public String getID() {
      return rvsID;
   }

   public String getBookmark() {
      return bookmark;
   }

   public String getOldBookmark() {
      return oldBookmark;
   }

   public IdentityID getOwner() {
      return owner;
   }

   public boolean isDeleted() {
      return deleted;
   }

   public String rvsID;
   public boolean deleted = false;
   public String bookmark = "";
   public String oldBookmark = null;
   public IdentityID owner = null;
}
