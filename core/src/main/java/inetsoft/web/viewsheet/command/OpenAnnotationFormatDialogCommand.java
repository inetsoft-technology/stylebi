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
package inetsoft.web.viewsheet.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.dialog.AnnotationFormatDialogModel;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableOpenAnnotationFormatDialogCommand.class)
public abstract class OpenAnnotationFormatDialogCommand implements ViewsheetCommand {
   public abstract AnnotationFormatDialogModel getFormatDialogModel();

   // The name of the annotation that is being formatted
   public abstract String getAssemblyName();

   // type of annotation used to control visible fields on the dialog
   public abstract int annotationType();

   // if this base assembly of the annotation is chart.
   public abstract boolean forChart();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableOpenAnnotationFormatDialogCommand.Builder {
   }
}
