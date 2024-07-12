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
package inetsoft.web.graph.model;

import inetsoft.web.adhoc.model.FormatInfoModel;

import java.awt.geom.RectangularShape;
import java.util.ArrayList;
import java.util.List;

public interface ChartModel {
   public long getGenTime();
   public int getChartType();
   public List<Axis> getAxes();
   public List<Facet> getFacets();
   public Plot getPlot();
   public List<Title> getTitles();
   public List<LegendContainer> getLegends();
   public RectangularShape getLegendsBounds();
   public boolean isMaxMode();
   public boolean isAxisHidden();
   public boolean isTitleHidden();
   public boolean isLegendHidden();
   boolean isMapInfo();
   void setMapInfo(boolean mapInfo);
   public boolean isMultiStyles();
   public boolean isEnableAdhoc();
   public boolean isShowValues();
   public boolean isInvalid();
   public int getLegendOption();
   public ArrayList<String> getStringDictionary();
   public ArrayList<RegionMeta> getRegionMetaDictionary();
   public RectangularShape getContentBounds();
   public boolean isChangedByScript();
   public boolean isHasLegend();
   public void setGenTime(long genTime);
   public void setChartType(int type);
   public void setAxes(List<Axis> axes);
   public void setFacets(List<Facet> facets);
   public void setPlot(Plot plot);
   public void setTitles(List<Title> titles);
   public void setLegends(List<LegendContainer> legends);
   public void setLegendsBounds(RectangularShape bounds);
   public void setMaxMode(boolean maxMode);
   public void setShowValues(boolean showValues);
   public void setAxisHidden(boolean visible);
   public void setTitleHidden(boolean visible);
   public void setLegendHidden(boolean visible);
   public void setMultiStyles(boolean multi);
   public void setLegendOption(int legendOption);
   public void setStringDictionary(ArrayList<String> stringDictionary);
   public void setContentBounds(RectangularShape contentBounds);
   public void setInvalid(boolean invalid);
   public void setChangedByScript(boolean invalid);
   public void setHasLegend(boolean hasLegend);
   public boolean isPlotHighlightEnabled();
   public void setPlotHighlightEnabled(boolean plotHighlightEnabled);
   public boolean isWebMap();
   public void setWebMap(boolean webMap);
   public boolean isScatterMatrix();
   public void setScatterMatrix(boolean scatterMatrix);
   public boolean isNavEnabled();
   public void setNavEnabled(boolean navEnabled);
   public boolean isNoData();
   public void setNoData(boolean noData);
   public FormatInfoModel getErrorFormat();
   public void setErrorFormat(FormatInfoModel errorFormat);
}
