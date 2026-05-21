/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.event;

import java.io.Serializable;
import java.util.List;

/**
 * Event sent to bulk-add visualizations to a wiz dashboard by their AssetEntry identifiers.
 * Used when the composer is opened with {@code wizComposer=true} and pre-selected
 * visualization identifiers ({@code wizVizIds}).
 */
public class AddVisualizationsByIdsEvent implements Serializable {
   public List<String> getIdentifiers() {
      return identifiers;
   }

   public void setIdentifiers(List<String> identifiers) {
      this.identifiers = identifiers;
   }

   private List<String> identifiers;
}
