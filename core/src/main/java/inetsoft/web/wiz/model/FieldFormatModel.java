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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A field's display format, as the composer's Format pane models it — the number/date half of
 * {@code inetsoft.web.adhoc.model.FormatInfoModel} ({@code format} / {@code formatSpec} /
 * {@code dateSpec} / {@code durationPadZeros}), which is exactly what
 * {@code VSWizardFormatService.updateFormat} consumes.
 *
 * <p>Deliberately NOT {@code FormatInfoModel} itself: that class carries the pane's whole style
 * payload (colors, borders, font, alignment) and is annotated
 * {@code @JsonTypeInfo(use = Id.CLASS, property = "type")}, which would require the caller to write
 * a Java class name into the JSON. Nothing on this path sets styling, so this is the format subset
 * on its own.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldFormatModel {
   /**
    * The format type, as an {@code XConstants} name: {@code DateFormat}, {@code CurrencyFormat},
    * {@code PercentFormat}, {@code DecimalFormat}, {@code CommaFormat}, {@code MessageFormat}, or
    * {@code DurationFormat}. Null or empty clears the field's user-defined format.
    */
   public String getFormat() { return format; }
   public void setFormat(String format) { this.format = format; }

   /**
    * The format pattern (e.g. {@code #,##0.0%}). Null falls back to the Format pane's default for
    * the chosen type, so a caller that only knows "percent" does not have to invent a pattern.
    */
   public String getFormatSpec() { return formatSpec; }
   public void setFormatSpec(String formatSpec) { this.formatSpec = formatSpec; }

   /**
    * DateFormat only: {@code FULL}, {@code LONG}, {@code MEDIUM}, {@code SHORT}, or {@code Custom}.
    * Anything other than {@code Custom} supplies the pattern itself, so formatSpec is ignored then —
    * the same rule VSWizardFormatService applies.
    */
   public String getDateSpec() { return dateSpec; }
   public void setDateSpec(String dateSpec) { this.dateSpec = dateSpec; }

   /** DurationFormat only: pad with leading zeros. Null is treated as true (FormatInfoModel's default). */
   public Boolean getDurationPadZeros() { return durationPadZeros; }
   public void setDurationPadZeros(Boolean durationPadZeros) { this.durationPadZeros = durationPadZeros; }

   private String format;
   private String formatSpec;
   private String dateSpec;
   private Boolean durationPadZeros;
}
