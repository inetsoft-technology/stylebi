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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.*;
import inetsoft.report.internal.AllCompositeTextFormat;
import inetsoft.report.internal.AllLegendDescriptor;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.css.CSSParameter;
import inetsoft.util.script.ScriptException;
import inetsoft.web.adhoc.model.*;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.command.AddLayoutObjectCommand;
import inetsoft.web.composer.vs.objects.command.SetCurrentFormatCommand;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.composer.vs.objects.event.GetVSObjectFormatEvent;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;
import java.text.Format;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Controller that provides a REST endpoint for manipulating object format.
 */
@Controller
public class FormatPainterController {
   /**
    * Creates a new instance of <tt>FormatPainterController</tt>.
    */
   @Autowired
   public FormatPainterController(RuntimeViewsheetRef runtimeViewsheetRef,
                                  FormatPainterServiceProxy formatPainterService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.formatPainterService = formatPainterService;
   }

   /**
    * Get the format of the currently selected object to fill the toolbar.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get viewsheet or objects
    */
   @MessageMapping("composer/viewsheet/getFormat")
   public void getFormat(@Payload GetVSObjectFormatEvent event, Principal principal,
                         CommandDispatcher commandDispatcher) throws Exception
   {
      formatPainterService.getFormat(this.runtimeViewsheetRef.getRuntimeId(),
                                     event, principal, commandDispatcher);
   }

   /**
    * Set the format of the given data paths.
    *
    * @param principal a principal identifying the current user.
    *
    * @throws Exception if unable to get viewsheet or objects
    */
   @Undoable
   @LoadingMask
   @MessageMapping("composer/viewsheet/format")
   public void setFormat(@Payload FormatVSObjectEvent event, Principal principal,
                         CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      formatPainterService.setFormat(this.runtimeViewsheetRef.getRuntimeId(), event,
                                     principal, commandDispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final FormatPainterServiceProxy formatPainterService;
}
