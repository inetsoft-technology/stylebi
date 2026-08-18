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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.ChartBindingModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 2b/2c ownership split over {@code ChartBindingModel}, declared once.
 *
 * <p>{@code ChangeChartRefEvent} demands the whole model, so every data-binding write
 * round-trips the aesthetic fields spec 2c owns. Dropping or defaulting one silently destroys
 * the user's styling, and it surfaces much later as a 2c bug rather than as a fault in the
 * write that caused it. The merge logic and the preservation tests both read this list rather
 * than each keeping their own copy, so they cannot drift apart.
 *
 * <p><b>Any future spec that writes {@code ChartBindingModel} must be added here.</b>
 */
public final class ChartBindingFields {
   /** The thirteen fields spec 2c owns. Spec 2b must never write them. */
   public static final List<String> AESTHETIC = List.of(
      "colorField", "shapeField", "sizeField", "textField",
      "colorFrame", "shapeFrame", "lineFrame", "textureFrame", "sizeFrame",
      "nodeColorField", "nodeSizeField", "nodeColorFrame", "nodeSizeFrame");

   private ChartBindingFields() {
   }

   /**
    * Captures the aesthetic fields by identity so a test can assert a write left them
    * untouched. Compared with {@code equals}, which for these model objects is reference
    * equality — exactly what "the write did not replace them" means.
    */
   public static Map<String, Object> snapshotAesthetics(ChartBindingModel model) {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("colorField", model.getColorField());
      snapshot.put("shapeField", model.getShapeField());
      snapshot.put("sizeField", model.getSizeField());
      snapshot.put("textField", model.getTextField());
      snapshot.put("colorFrame", model.getColorFrame());
      snapshot.put("shapeFrame", model.getShapeFrame());
      snapshot.put("lineFrame", model.getLineFrame());
      snapshot.put("textureFrame", model.getTextureFrame());
      snapshot.put("sizeFrame", model.getSizeFrame());
      snapshot.put("nodeColorField", model.getNodeColorField());
      snapshot.put("nodeSizeField", model.getNodeSizeField());
      snapshot.put("nodeColorFrame", model.getNodeColorFrame());
      snapshot.put("nodeSizeFrame", model.getNodeSizeFrame());
      return snapshot;
   }
}
