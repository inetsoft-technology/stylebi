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

package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.web.wiz.model.*;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
public class CreateVsService {
   public CreateVsService(ViewsheetService viewsheetService, AssetRepository engine) {
      this.viewsheetService = viewsheetService;
      this.engine = engine;
   }

   public void createViewsheet(CreateVisualizationModel model, Principal user) throws Exception {
      if(Tool.isEmptyString(model.getRuntimeId())) {
         throw new Exception("Runtime Viewsheet is empty");
      }

      RuntimeViewsheet viewsheet = viewsheetService.getViewsheet(model.getRuntimeId(), user);

      if(viewsheet == null) {
         throw new Exception("Runtime Viewsheet not found");
      }

      VisualizationConfig config = model.getConfig();
      String title = config != null && config.getTitle() != null && !config.getTitle().isEmpty()
         ? config.getTitle()
         : "vs_" + System.currentTimeMillis();

      AssetEntry sourceWs = null;

      if(config == null || config.getData() == null) {
         throw new Exception("Invalid configuration, missing source");
      }

      try {
         sourceWs = AssetEntry.createAssetEntry(config.getData().getSource());
      }
      catch(Exception e) {
         throw new Exception("Datasource is invalid", e);
      }

      AbstractSheet sheet = engine.getSheet(sourceWs, user, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet worksheet)) {
         throw new Exception("Cannot find worksheet");
      }

      WSAssembly primaryAssembly = worksheet.getPrimaryAssembly();

      Viewsheet vs = new Viewsheet();
      VSAssembly assembly = createAssembly(vs, model.getVisualizationType(), title, config, primaryAssembly.getName());

      if(assembly == null) {
         throw new RuntimeException("Unsupported visualization type: " + model.getVisualizationType());
      }

      vs.addAssembly(assembly);
      assembly.setPrimary(true);
      viewsheet.setViewsheet(vs);
   }

   private VSAssembly createAssembly(Viewsheet vs, String type, String name,
                                     VisualizationConfig config, String tname)
   {
      if(type == null) {
         return null;
      }

      VSAssembly vsAssembly;

      int chartType = getChartType(type);

      if(chartType >= 0) {
         vsAssembly = createChartAssembly(vs, name, chartType, config);
      }
      else {
         vsAssembly = switch(type) {
            case "table" -> createTableAssembly(vs, name, config);
            case "crosstab" -> createCrosstabAssembly(vs, name, config);
            case "gauge" -> createGaugeAssembly(vs, name);
            case "text" -> createTextAssembly(vs, name);
            case "image" -> createImageAssembly(vs, name, config);
            default -> null;
         };
      }

      if(vsAssembly instanceof DataVSAssembly dataVSAssembly) {
         dataVSAssembly.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, tname));
      }
      else if(vsAssembly instanceof OutputVSAssembly outputVSAssembly && config.getBindingInfo() instanceof OutputBinding outputBinding) {
         ScalarBindingInfo sbinfo = outputVSAssembly.getScalarBindingInfo();
         sbinfo.setTableName(tname);
         sbinfo.setColumnValue(outputBinding.getField().getField());

         if(outputBinding.getField().getAggregateFormula() != null) {
            sbinfo.setAggregateValue(outputBinding.getField().getAggregateFormula());
         }
      }

      return vsAssembly;
   }

   /**
    * Returns the GraphTypes constant for chart types, or -1 for non-chart types.
    */
   private int getChartType(String type) {
      return switch(type) {
         case "bar" -> GraphTypes.CHART_BAR;
         case "3d_bar" -> GraphTypes.CHART_3D_BAR;
         case "area" -> GraphTypes.CHART_AREA;
         case "point" -> GraphTypes.CHART_POINT;
         case "step_area" -> GraphTypes.CHART_STEP_AREA;
         case "interval" -> GraphTypes.CHART_INTERVAL;
         case "line" -> GraphTypes.CHART_LINE;
         case "step_line" -> GraphTypes.CHART_STEP;
         case "jump_line" -> GraphTypes.CHART_JUMP;
         case "pie" -> GraphTypes.CHART_PIE;
         case "3d_pie" -> GraphTypes.CHART_3D_PIE;
         case "donut" -> GraphTypes.CHART_DONUT;
         case "radar" -> GraphTypes.CHART_RADAR;
         case "filled_radar" -> GraphTypes.CHART_FILL_RADAR;
         case "scatter_contour" -> GraphTypes.CHART_SCATTER_CONTOUR;
         case "stock" -> GraphTypes.CHART_STOCK;
         case "candle" -> GraphTypes.CHART_CANDLE;
         case "boxplot" -> GraphTypes.CHART_BOXPLOT;
         case "waterfall" -> GraphTypes.CHART_WATERFALL;
         case "pareto" -> GraphTypes.CHART_PARETO;
         case "treemap" -> GraphTypes.CHART_TREEMAP;
         case "sunburst" -> GraphTypes.CHART_SUNBURST;
         case "circle_packing" -> GraphTypes.CHART_CIRCLE_PACKING;
         case "icircle" -> GraphTypes.CHART_ICICLE;
         case "marimekko" -> GraphTypes.CHART_MEKKO;
         case "gantt" -> GraphTypes.CHART_GANTT;
         case "funnel" -> GraphTypes.CHART_FUNNEL;
         case "tree" -> GraphTypes.CHART_TREE;
         case "network" -> GraphTypes.CHART_NETWORK;
         case "circular_network" -> GraphTypes.CHART_CIRCULAR;
         case "contour_map" -> GraphTypes.CHART_MAP_CONTOUR;
         case "map" -> GraphTypes.CHART_MAP;
         default -> -1;
      };
   }

   private ChartVSAssembly createChartAssembly(Viewsheet vs, String name, int chartType,
                                               VisualizationConfig config)
   {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      VSChartInfo chartInfo = createVSChartInfo(chartType);
      chartInfo.setChartType(chartType);
      chart.setVSChartInfo(chartInfo);

      if(config != null && config.getBindingInfo() instanceof ChartBinding binding) {
         applyChartBinding(chartInfo, binding);
      }

      return chart;
   }

   /**
    * Creates the appropriate VSChartInfo subclass for the given chart type.
    */
   private VSChartInfo createVSChartInfo(int chartType) {
      return switch(chartType) {
         case GraphTypes.CHART_STOCK -> new StockVSChartInfo();
         case GraphTypes.CHART_CANDLE -> new CandleVSChartInfo();
         case GraphTypes.CHART_RADAR, GraphTypes.CHART_FILL_RADAR -> new RadarVSChartInfo();
         case GraphTypes.CHART_TREE, GraphTypes.CHART_NETWORK, GraphTypes.CHART_CIRCULAR ->
            new RelationVSChartInfo();
         case GraphTypes.CHART_GANTT -> new GanttVSChartInfo();
         case GraphTypes.CHART_MAP, GraphTypes.CHART_MAP_CONTOUR -> new VSMapInfo();
         default -> new DefaultVSChartInfo();
      };
   }

   private void applyChartBinding(VSChartInfo chartInfo, ChartBinding binding) {
      // X fields (longitude for map)
      if(binding.getX() != null) {
         for(SimpleFieldInfo field : binding.getX()) {
            chartInfo.addXField(createChartRef(field));
         }
      }

      // Y fields (latitude for map)
      if(binding.getY() != null) {
         for(SimpleFieldInfo field : binding.getY()) {
            chartInfo.addYField(createChartRef(field));
         }
      }

      // Group fields
      if(binding.getGroup() != null) {
         for(SimpleFieldInfo field : binding.getGroup()) {
            chartInfo.addGroupField(createChartRef(field));
         }
      }

      // T fields (tree dimension for TreeMap charts)
      if(binding.getT() != null) {
         for(DimensionFieldInfo field : binding.getT()) {
            chartInfo.addXField(createVSChartDimensionRef(field));
         }
      }

      // Aesthetic fields
      if(binding.getColor() != null) {
         chartInfo.setColorField(createAestheticRef(binding.getColor()));
      }

      if(binding.getShape() != null) {
         chartInfo.setShapeField(createAestheticRef(binding.getShape()));
      }

      if(binding.getSize() != null) {
         chartInfo.setSizeField(createAestheticRef(binding.getSize()));
      }

      if(binding.getText() != null) {
         chartInfo.setTextField(createAestheticRef(binding.getText()));
      }

      if(binding.getPath() != null) {
         chartInfo.setPathField(createChartRef(binding.getPath()));
      }

      // Stock / Candle specific fields
      if(chartInfo instanceof CandleVSChartInfo candleInfo) {
         if(binding.getHigh() != null) {
            candleInfo.setHighField(createVSChartAggregateRef(binding.getHigh()));
         }

         if(binding.getLow() != null) {
            candleInfo.setLowField(createVSChartAggregateRef(binding.getLow()));
         }

         if(binding.getClose() != null) {
            candleInfo.setCloseField(createVSChartAggregateRef(binding.getClose()));
         }

         if(binding.getOpen() != null) {
            candleInfo.setOpenField(createVSChartAggregateRef(binding.getOpen()));
         }
      }

      // Gantt specific fields
      if(chartInfo instanceof GanttVSChartInfo ganttInfo) {
         if(binding.getStart() != null) {
            ganttInfo.setStartField(createVSChartDimensionRef(binding.getStart()));
         }

         if(binding.getEnd() != null) {
            ganttInfo.setEndField(createVSChartDimensionRef(binding.getEnd()));
         }

         if(binding.getMilestone() != null) {
            ganttInfo.setMilestoneField(createVSChartDimensionRef(binding.getMilestone()));
         }
      }

      // Tree / Network / CircularNetwork specific fields
      if(chartInfo instanceof RelationVSChartInfo relationInfo) {
         if(binding.getSource() != null) {
            relationInfo.setSourceField(createVSChartDimensionRef(binding.getSource()));
         }

         if(binding.getTarget() != null) {
            relationInfo.setTargetField(createVSChartDimensionRef(binding.getTarget()));
         }

         if(binding.getNode() != null) {
            ChartBinding.NodeBinding node = binding.getNode();

            if(node.getColor() != null) {
               relationInfo.setNodeColorField(createAestheticRef(node.getColor()));
            }

            if(node.getSize() != null) {
               relationInfo.setNodeSizeField(createAestheticRef(node.getSize()));
            }
         }
      }

      // Map specific fields (geo)
      if(chartInfo instanceof VSMapInfo mapInfo) {
         if(binding.getGeo() != null) {
            for(SimpleFieldInfo field : binding.getGeo()) {
               VSChartGeoRef geoRef = new VSChartGeoRef();
               geoRef.setGroupColumnValue(field.getField());
               mapInfo.addGeoField(geoRef);
            }
         }
      }
   }

   private AestheticRef createAestheticRef(SimpleFieldInfo field) {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(createChartRef(field));
      return ref;
   }

   private TableVSAssembly createTableAssembly(Viewsheet vs, String name,
                                               VisualizationConfig config)
   {
      TableVSAssembly table = new TableVSAssembly(vs, name);

      if(config != null && config.getBindingInfo() instanceof TableBinding binding
         && binding.getDetails() != null)
      {
         ColumnSelection columns = new ColumnSelection();

         for(SimpleFieldInfo field : binding.getDetails()) {
            AttributeRef attr = new AttributeRef(null, field.getField());

            if(field.getType() != null) {
               attr.setDataType(field.getType());
            }

            columns.addAttribute(new ColumnRef(attr));
         }

         table.setColumnSelection(columns);
      }

      return table;
   }

   private CrosstabVSAssembly createCrosstabAssembly(Viewsheet vs, String name,
                                                     VisualizationConfig config)
   {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);

      if(config != null && config.getBindingInfo() instanceof CrosstabBinding binding) {
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

         if(binding.getRows() != null) {
            DataRef[] rows = binding.getRows().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignRowHeaders(rows);
         }

         if(binding.getCols() != null) {
            DataRef[] cols = binding.getCols().stream()
               .map(this::createVSDimensionRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignColHeaders(cols);
         }

         if(binding.getAggregates() != null) {
            DataRef[] aggrs = binding.getAggregates().stream()
               .map(this::createVSAggregateRef)
               .toArray(DataRef[]::new);
            cinfo.setDesignAggregates(aggrs);
         }

         crosstab.setVSCrosstabInfo(cinfo);
      }

      return crosstab;
   }

   private GaugeVSAssembly createGaugeAssembly(Viewsheet vs, String name)
   {
      return new GaugeVSAssembly(vs, name);
   }

   private TextVSAssembly createTextAssembly(Viewsheet vs, String name)
   {
      return new TextVSAssembly(vs, name);
   }

   private ImageVSAssembly createImageAssembly(Viewsheet vs, String name,
                                               VisualizationConfig config)
   {
      ImageVSAssembly image = new ImageVSAssembly(vs, name);

      if(config != null && config.getBindingInfo() instanceof ImageBinding binding
         && binding.getImage() != null)
      {
         image.setValue(binding.getImage());
      }

      return image;
   }

   private ChartRef createChartRef(SimpleFieldInfo field) {
      if(field instanceof MeasureFieldInfo measure) {
         return createVSChartAggregateRef(measure);
      }

      return createVSChartDimensionRef(field instanceof DimensionFieldInfo dim ? dim : null, field);
   }

   private VSChartAggregateRef createVSChartAggregateRef(MeasureFieldInfo field) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setColumnValue(field.getField());

      if(field.getAggregateFormula() != null) {
         ref.setFormulaValue(field.getAggregateFormula());
      }

      return ref;
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo field) {
      return createVSChartDimensionRef(field, field);
   }

   private VSChartDimensionRef createVSChartDimensionRef(DimensionFieldInfo dim, SimpleFieldInfo base) {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue(base.getField());

      if(dim != null && dim.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(dim.getDateGroupLevel())));
      }

      if(dim != null && dim.getRanking() != null) {
         Ranking ranking = dim.getRanking();
         ref.setRankingN(ranking.getRankingN());
         ref.setRankingCol(ranking.getRankingCol());
         ref.setOrder(ranking.getOptionValue());
      }

      return ref;
   }

   private VSDimensionRef createVSDimensionRef(DimensionFieldInfo field) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setGroupColumnValue(field.getField());

      if(field.getDateGroupLevel() != null) {
         ref.setDateLevelValue(String.valueOf(getDateGroupLevel(field.getDateGroupLevel())));
      }

      if(field.getRanking() != null) {
         Ranking ranking = field.getRanking();
         ref.setRankingN(ranking.getRankingN());
         ref.setRankingCol(ranking.getRankingCol());
         ref.setOrder(ranking.getOptionValue());
      }

      return ref;
   }

   private VSAggregateRef createVSAggregateRef(MeasureFieldInfo field) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setColumnValue(field.getField());

      if(field.getAggregateFormula() != null) {
         ref.setFormulaValue(field.getAggregateFormula());
      }

      return ref;
   }

   private int getDateGroupLevel(String level) {
      if(level == null) {
         return XConstants.NONE_DATE_GROUP;
      }

      return switch(level) {
         case "Year" -> XConstants.YEAR_DATE_GROUP;
         case "Quarter" -> XConstants.QUARTER_DATE_GROUP;
         case "Month" -> XConstants.MONTH_DATE_GROUP;
         case "Week" -> XConstants.WEEK_DATE_GROUP;
         case "Day" -> XConstants.DAY_DATE_GROUP;
         case "Hour" -> XConstants.HOUR_DATE_GROUP;
         default -> XConstants.NONE_DATE_GROUP;
      };
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository engine;
}
