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
package inetsoft.web.vswizard.command;

import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import inetsoft.web.vswizard.model.recommender.VSRecommendationModel;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

public class RefreshRecommendCommand implements TimeSensitiveCommand {

   public RefreshRecommendCommand() {
      super();
   }

   public RefreshRecommendCommand(VSRecommendationModel recommenderModel) {
      this.recommenderModel = recommenderModel;
   }

   public VSRecommendationModel getRecommenderModel() {
      return recommenderModel;
   }

   public void setRecommenderModel(VSRecommendationModel recommenderModel) {
      this.recommenderModel = recommenderModel;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      if(recommenderModel != null) {
         recommenderModel.writeXML(writer);
      }
   }

   @Override
   public void parseContents(Element elem) throws Exception {
      Element enode = Tool.getChildNodeByTagName(elem, "vsRecommendationModel");

      if(enode != null) {
         recommenderModel = new VSRecommendationModel();
         recommenderModel.parseXML(enode);
      }
   }

   private VSRecommendationModel recommenderModel;
}
