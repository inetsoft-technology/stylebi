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
package inetsoft.report.internal;

import inetsoft.report.ReportElement;
import inetsoft.report.internal.binding.BindingAttr;
import inetsoft.report.internal.binding.SourceAttr;

/**
 * This is the base class of all binable element classes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface BindableElement extends ReportElement {
   /**
    * Get the binding attr.
    */
   public BindingAttr getBindingAttr();
}