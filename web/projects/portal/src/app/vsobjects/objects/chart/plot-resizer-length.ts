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
import type { VSChartModel } from "../../model/vs-chart-model";

/** Longest the slider is ever drawn, in px. */
const MAX_LENGTH = 150;
/** Below this the plot is too small to resize meaningfully, so no slider is drawn. */
const MIN_LENGTH = 80;
/** Share of the plot edge the slider may occupy before the cap applies. */
const EDGE_RATIO = 0.6;

/**
 * Length of a plot-resize slider along the plot edge it sits on, or null when the edge is too
 * short for the slider to be worth drawing. Computed here rather than in CSS because the vertical
 * slider is the horizontal one under rotate(-90deg): its visual length runs along the parent's
 * height while a CSS percentage would resolve against its own width.
 */
export function plotResizerLength(edge: number | null | undefined): number | null {
   if(!edge || edge <= 0) {
      return null;
   }

   const length = Math.min(MAX_LENGTH, edge * EDGE_RATIO);
   return length >= MIN_LENGTH ? length : null;
}

/**
 * Whether the plot can carry a horizontal slider at all: the axis is resizable and the edge
 * clears the floor. Separate from whether one is drawn, because Resize Plot has to ask this
 * before it turns the sliders on.
 */
export function horizontalPlotResizerFits(model: VSChartModel): boolean {
   return !!model?.horizontallyResizable &&
      plotResizerLength(model.plot?.layoutBounds?.width) != null;
}

/** Vertical counterpart of horizontalPlotResizerFits. */
export function verticalPlotResizerFits(model: VSChartModel): boolean {
   return !!model?.verticallyResizable &&
      plotResizerLength(model.plot?.layoutBounds?.height) != null;
}

/**
 * Whether the horizontal slider is drawn. It takes the row the mini toolbar sits in, so the
 * toolbar keys off this rather than off showPlotResizers, which can be set on a plot that draws
 * no horizontal slider.
 */
export function horizontalPlotResizerShown(model: VSChartModel): boolean {
   return !!model?.showPlotResizers && horizontalPlotResizerFits(model);
}

/** Vertical counterpart of horizontalPlotResizerShown. */
export function verticalPlotResizerShown(model: VSChartModel): boolean {
   return !!model?.showPlotResizers && verticalPlotResizerFits(model);
}
