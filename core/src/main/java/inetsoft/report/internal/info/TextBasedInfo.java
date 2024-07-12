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
package inetsoft.report.internal.info;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.PresenterRef;

/**
 * This interface has the methods for accessing text base element info
 * contents and attributes.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface TextBasedInfo {
   /**
    * Set the highlight group setting.
    */
   public HighlightGroup getHighlightGroup();

   /**
    * Get the highlight group setting.
    */
   public void setHighlightGroup(HighlightGroup group);

   /**
    * Get the presenter to be used in this element.
    */
   public PresenterRef getPresenter();
   
   /**
    * Set the presenter to be used in this element.
    */
   public void setPresenter(PresenterRef ref);
}
