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
package inetsoft.web.composer.vs;

import inetsoft.web.viewsheet.model.VSObjectModel;

import java.util.List;

/**
 * Model representing a node in the object tree.
 *
 * @since 12.3
 */
public class VSObjectTreeNode {
   public VSObjectModel getModel() {
      return model;
   }

   public void setModel(VSObjectModel model) {
      this.model = model;
   }

   public boolean isExpanded() {
      return expanded;
   }

   public void setExpanded(boolean expanded) {
      this.expanded = expanded;
   }

   public List<VSObjectTreeNode> getChildren() {
      return children;
   }

   public void setChildren(List<VSObjectTreeNode> children) {
      this.children = children;
   }

   public String getNodeLabel() { return nodeLabel; }

   public void setNodeLabel(String nodeLabel) { this.nodeLabel = nodeLabel; }

   private VSObjectModel model;
   private boolean expanded;
   private List<VSObjectTreeNode> children;
   private String nodeLabel;
}
