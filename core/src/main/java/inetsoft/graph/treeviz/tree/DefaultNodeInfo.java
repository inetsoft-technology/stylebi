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
package inetsoft.graph.treeviz.tree;

import inetsoft.graph.treemap.TreeModel;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * DefaultNodeInfo.
 *
 * @author Werner Randelshofer
 * @version 1.0 Jan 26, 2008 Created.
 */
public class DefaultNodeInfo implements NodeInfo {
   private EventListenerList listenerList = new EventListenerList();
   private ChangeEvent changeEvent;

   @Override
   public String getName(TreePath2<TreeNode> path) {
      return path.getLastPathComponent().toString();
   }

   @Override
   public Color getColor(TreePath2<TreeNode> path) {
      return Color.DARK_GRAY;
   }

   @Override
   public double getWeight(TreePath2<TreeNode> path) {
      TreeNode last = path.getLastPathComponent();

      if(last instanceof TreeModel) {
         return ((TreeModel) last).sum();
      }

      return 1;
   }

   @Override
   public String getTooltip(TreePath2<TreeNode> path) {
      StringBuilder buf = new StringBuilder();

      TreePath2<TreeNode> parentPath = path;
      do {
         buf.insert(0, "<br>");
         buf.insert(0, getName(parentPath));
         parentPath = parentPath.getParentPath();
      }
      while(parentPath != null);
      buf.insert(0, "<html>");
      buf.append("<br>");

      TreeNode node = path.getLastPathComponent();
      if(node.getAllowsChildren()) {
         buf.append(DecimalFormat.getIntegerInstance().format(node.children().size()));
         buf.append(" Files");
         buf.append("<br>");
      }

      double w = getWeight(path);
      buf.append(DecimalFormat.getIntegerInstance().format(w));
      return buf.toString();
   }

   @Override
   public Image getImage(TreePath2<TreeNode> path) {
      return null;
   }

   @Override
   public void init(TreeNode root) {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public Weighter getWeighter() {
      return null;
   }

   @Override
   public Colorizer getColorizer() {
      return null;
   }

   @Override
   public void addChangeListener(ChangeListener l) {
      listenerList.add(ChangeListener.class, l);
   }

   /**
    * Removes a change listener from the button.
    */
   @Override
   public void removeChangeListener(ChangeListener l) {
      listenerList.remove(ChangeListener.class, l);
   }

   /*
    * Notify all listeners that have registered interest for
    * notification on this event type.  The event instance
    * is lazily created using the parameters passed into
    * the fire method.
    *
    * @see EventListenerList
    */
   protected void fireStateChanged() {
      // Guaranteed to return a non-null array
      Object[] listeners = listenerList.getListenerList();
      // Process the listeners last to first, notifying
      // those that are interested in this event
      for(int i = listeners.length - 2; i >= 0; i -= 2) {
         if(listeners[i] == ChangeListener.class) {
            // Lazily create the event:
            if(changeEvent == null) {
               changeEvent = new ChangeEvent(this);
            }
            ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
         }
      }
   }

   @Override
   public String getWeightFormatted(TreePath2<TreeNode> path) {
      return DecimalFormat.getIntegerInstance().format(getWeight(path));
   }

   @Override
   public void toggleColorWeighter() {
      // do nothing
   }

   @Override
   public double getCumulatedWeight(TreePath2<TreeNode> path) {
      return getWeight(path);
   }

   @Override
   public Action[] getActions(TreePath2<TreeNode> path) {
      return new Action[0];
   }

}
