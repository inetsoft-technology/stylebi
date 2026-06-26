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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * One join condition between two worksheet tables, mirroring the TypeScript {@code JoinPath} shape.
 * <ul>
 *   <li>{@code joinType}     – "inner" | "left" | "right" | "full" | "cross"</li>
 *   <li>{@code joinOperator} – "=" | ">" | "<" | ">=" | "<=" | "<>"</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JoinPath implements Serializable {
   public JoinPath() {
   }

   public String getLeftTable() {
      return leftTable;
   }

   public void setLeftTable(String leftTable) {
      this.leftTable = leftTable;
   }

   public String getLeftKey() {
      return leftKey;
   }

   public void setLeftKey(String leftKey) {
      this.leftKey = leftKey;
   }

   public String getRightTable() {
      return rightTable;
   }

   public void setRightTable(String rightTable) {
      this.rightTable = rightTable;
   }

   public String getRightKey() {
      return rightKey;
   }

   public void setRightKey(String rightKey) {
      this.rightKey = rightKey;
   }

   public String getJoinType() {
      return joinType;
   }

   public void setJoinType(String joinType) {
      this.joinType = joinType;
   }

   public String getJoinOperator() {
      return joinOperator;
   }

   public void setJoinOperator(String joinOperator) {
      this.joinOperator = joinOperator;
   }

   private String leftTable;
   private String leftKey;
   private String rightTable;
   private String rightKey;
   private String joinType;
   private String joinOperator;
}
