/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database.cube.xmla;

import inetsoft.web.composer.model.TreeNodeModel;

public class CubeMetaModel {
   public TreeNodeModel getCubeTree() {
      return cubeTree;
   }

   public void setCubeTree(TreeNodeModel cubeTree) {
      this.cubeTree = cubeTree;
   }

   public DomainModel getDomain() {
      return domain;
   }

   public void setDomain(DomainModel domain) {
      this.domain = domain;
   }

   private TreeNodeModel cubeTree;
   private DomainModel domain;
}
