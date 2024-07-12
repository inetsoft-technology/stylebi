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
package inetsoft.web.viewsheet.model.table;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.TableDataPath;
import inetsoft.web.viewsheet.model.ModelPrototype;
import inetsoft.web.viewsheet.model.VSFormatModel;

/**
 * Prototype fields for the {@link BaseTableCellModel}.
 *
 * These fields are marked as JsonInclude non-empty since when creating the concrete implementation
 * we'll clear the fields from the original model. This should be refactored to be easier to use
 * but currently it would require a more substantial change.
 */
public interface BaseTableCellModelPrototype extends ModelPrototype {
   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   TableDataPath getDataPath();

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   VSFormatModel getVsFormatModel();

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   String getField();

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   Integer getBindingType();
}
