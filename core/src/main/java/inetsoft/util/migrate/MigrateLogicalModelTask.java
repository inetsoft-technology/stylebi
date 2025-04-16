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

package inetsoft.util.migrate;

import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.AbstractIdentity;
import org.w3c.dom.Element;

public class MigrateLogicalModelTask extends MigrateDocumentTask {
   public MigrateLogicalModelTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateLogicalModelTask(AssetEntry entry, String ooname, String nname) {
      super(entry, ooname, nname);
   }

   public MigrateLogicalModelTask(AssetEntry entry, String ooname, String nname, Organization currOrg) {
      super(entry, ooname, nname, currOrg);
   }

   @Override
   protected void processAssemblies(Element elem) {
      updateDrillPaths(elem);
   }
}
