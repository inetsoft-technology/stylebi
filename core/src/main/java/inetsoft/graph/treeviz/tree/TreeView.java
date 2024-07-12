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

import java.awt.event.MouseEvent;

/**
 * TreeView.
 *
 * @author Werner Randelshofer, Staldenmattweg 2, CH-6410 Goldau
 * @version 2.0 2009-01-30 Added maxDepth property.
 * <br>1.0 2008-10-22 Created.
 */
public interface TreeView {

   int getMaxDepth();

   void setMaxDepth(int newValue);

   boolean isToolTipEnabled();

   void setToolTipEnabled(boolean newValue);

   String getInfoText(MouseEvent evt);

   void repaintView();
}
