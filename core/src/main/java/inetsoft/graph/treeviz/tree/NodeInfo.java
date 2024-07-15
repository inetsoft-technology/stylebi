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
package inetsoft.graph.treeviz.tree;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * NodeInfo is used to interpret the data stored in a {@link TreeNode}.
 * <p>
 * All methods of NodeInfo take a path to a node as a parameter. This allows
 * to get an interperation of a node based on more criteria than just on the
 * node alone.
 *
 * @author Werner Randelshofer
 * @version 1.2 2011-01-11 Adds support for actions.
 * <br>1.1 2008-07-04 Adds support for change listeners.
 * <br>1.0 September 21, 2007 Created.
 */
public interface NodeInfo {

   /**
    * Initializes the node info.
    *
    * @param root
    */
   void init(TreeNode root);

   /**
    * Returns the name of the node.
    */
   String getName(TreePath2<TreeNode> path);

   /**
    * Returns the color of the node.
    */
   Color getColor(TreePath2<TreeNode> path);

   /**
    * Returns the weight of a node.
    *
    * @return The weight between 0 and Double.MAX_VALUE.
    */
   double getWeight(TreePath2<TreeNode> path);

   /**
    * Returns the cumulated weight of a node (the sum of the weights of this
    * node and of all its children).
    *
    * @return The weight between 0 and Double.MAX_VALUE.
    */
   double getCumulatedWeight(TreePath2<TreeNode> path);

   /**
    * Returns the string formatted weight of a node.
    */
   String getWeightFormatted(TreePath2<TreeNode> path);

   /**
    * Returns the tooltip of the node.
    */
   String getTooltip(TreePath2<TreeNode> path);

   /**
    * Returns actions for the specified node.
    *
    * @return An array of action objects. Returns an empty array if no
    * actions are available. Never returns null.
    */
   Action[] getActions(TreePath2<TreeNode> path);

   /**
    * Returns the image of the node.
    */
   Image getImage(TreePath2<TreeNode> path);

   Weighter getWeighter();

   Colorizer getColorizer();

   void addChangeListener(ChangeListener l);

   void removeChangeListener(ChangeListener l);

   void toggleColorWeighter();

}
