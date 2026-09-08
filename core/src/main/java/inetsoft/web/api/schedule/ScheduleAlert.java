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
package inetsoft.web.api.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * ScheduleAlert describes the name and associated assembly id for an alert that triggers when a highlight's conditions are met.
 */
@Validated
@Schema(description = "Describes the name and associated assembly id for a highlight alert.")
public class ScheduleAlert {
   /**
    * Creates a new instance of {@code ScheduleAlert}.
    */
   public ScheduleAlert() {
   }

   /**
    * Creates a new instance of {@code ScheduleAlert}.
    *
    * @param elementId the save to server file format.
    * @param highlightName   the save to server file path.
    */
   public ScheduleAlert(String elementId, String highlightName) {
      this.elementId = elementId;
      this.highlightName = highlightName;
   }

   /**
    * Gets the element id of the assembly that the highlight is applied to.
    *
    * @return the element id of the assembly that the highlight is applied to.
    */
   @NotNull
   @Schema(description = "The element id of the assembly that the highlight is applied to.", example = "Table1")
   public String getElementId() {
      return elementId;
   }

   /**
    * Sets the element id of the assembly that the highlight is applied to.
    *
    * @param elementId the element id of the assembly that the highlight is applied to.
    */
   public void setElementId(String elementId) {
      this.elementId = elementId;
   }

   /**
    * Gets the name of the highlight used for the alert.
    *
    * @return the name of the highlight used for the alert.
    */
   @NotNull
   @Schema(
      description = "The name of the highlight used for the alert.",
      example = "Restock Alert")
   public String getHighlightName() {
      return highlightName;
   }

   /**
    * Sets the name of the highlight used for the alert.
    *
    * @param highlightName the name of the highlight used for the alert.
    */
   public void setHighlightName(String highlightName) {
      this.highlightName = highlightName;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      ScheduleAlert that = (ScheduleAlert) o;
      return Objects.equals(elementId, that.elementId) && Objects.equals(highlightName, that.highlightName);
   }

   @Override
   public int hashCode() {
      return Objects.hash(elementId, highlightName);
   }

   @Override
   public String toString() {
      return "ScheduleAlert{" +
         "elementId='" + elementId + '\'' +
         ", highlightName='" + highlightName + '\'' +
         '}';
   }

   private String elementId;
   private String highlightName;
}
