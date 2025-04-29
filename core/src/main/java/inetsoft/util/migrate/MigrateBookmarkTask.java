/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.migrate;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.util.Tool;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MigrateBookmarkTask extends MigrateDocumentTask {
   public MigrateBookmarkTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateBookmarkTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg, Document document) {
      super(entry, oOrg, nOrg, document);
   }

   public MigrateBookmarkTask(AssetEntry entry, String oname, String nname) {
      super(entry, oname, nname);
   }

   public MigrateBookmarkTask(AssetEntry entry, String oname, String nname, Organization currOrg) {
      super(entry, oname, nname, currOrg);
   }

   @Override
   void processAssemblies(Element elem) {
      String defaultBookmarkUser = elem.getAttribute("defaultBookmarkUser");

      if(!Tool.isEmptyString(defaultBookmarkUser)) {
         IdentityID user = IdentityID.getIdentityIDFromKey(defaultBookmarkUser);

         if(user != null && getOldOrganization() != null && getNewOrganization() != null &&
            Tool.equals(user.getOrgID(), getOldOrganization().getOrganizationID()))
         {
            user = new IdentityID(user.getName(), getNewOrganization().getOrganizationID());
            elem.setAttribute("defaultBookmarkUser", user.convertToKey());
         }
      }
   }
}
