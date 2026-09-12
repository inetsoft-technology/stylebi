/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.test;

import inetsoft.uql.viewsheet.*;

import java.awt.*;

/**
 * Factory methods for constructing {@link VSAssembly} instances in tests.
 *
 * <p>Centralises the boilerplate of creating and positioning viewsheet assemblies so that
 * individual tests can express their intent instead of repeating construction code.
 *
 * <p>Usage:
 * <pre>{@code
 * ImageVSAssembly image = VSAssemblyFixture.image(viewsheet, "Image1", 100, 100);
 * GaugeVSAssembly gauge = VSAssemblyFixture.gauge(viewsheet, "Gauge1", 200, 200);
 * }</pre>
 */
public final class VSAssemblyFixture {

   private VSAssemblyFixture() {
   }

   /**
    * Creates an {@link ImageVSAssembly} positioned at {@code (x, y)}.
    *
    * @param viewsheet the parent viewsheet
    * @param name      assembly name
    * @param x         pixel x offset
    * @param y         pixel y offset
    * @return the configured assembly
    */
   public static ImageVSAssembly image(Viewsheet viewsheet, String name, int x, int y) {
      ImageVSAssembly assembly = new ImageVSAssembly(viewsheet, name);
      assembly.setPixelOffset(new Point(x, y));
      return assembly;
   }

   /**
    * Creates a {@link GaugeVSAssembly} positioned at {@code (x, y)}.
    *
    * @param viewsheet the parent viewsheet
    * @param name      assembly name
    * @param x         pixel x offset
    * @param y         pixel y offset
    * @return the configured assembly
    */
   public static GaugeVSAssembly gauge(Viewsheet viewsheet, String name, int x, int y) {
      GaugeVSAssembly assembly = new GaugeVSAssembly(viewsheet, name);
      assembly.setPixelOffset(new Point(x, y));
      return assembly;
   }

   /**
    * Creates a {@link TextVSAssembly} with a preset display text.
    *
    * @param viewsheet   the parent viewsheet
    * @param name        assembly name
    * @param displayText text to display
    * @return the configured assembly
    */
   public static TextVSAssembly text(Viewsheet viewsheet, String name, String displayText) {
      TextVSAssembly assembly = new TextVSAssembly(viewsheet, name);
      assembly.setTextValue(displayText);
      return assembly;
   }
}
