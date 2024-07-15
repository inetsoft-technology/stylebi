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
package inetsoft.uql.jdbc.drivers;

import org.springframework.asm.*;

import static org.springframework.asm.Opcodes.*;

/**
 * {@code DriverServiceGenerator} generates the byte code for the driver service class of a user
 * generated driver plugin.
 */
public class DriverServiceGenerator {
   /**
    * Generates the byte code for an implementation of {@link inetsoft.uql.jdbc.DriverService} with
    * the specified class name. The class must not be in the root of the classpath.
    *
    * @param className the fully qualified class name.
    *
    * @return the class byte code.
    */
   public byte[] generateDriverServiceClass(String className) {
      int index = className.lastIndexOf('.');

      if(index < 0) {
         throw new IllegalArgumentException("Cannot generate driver services in root");
      }

      String fileName = className.substring(index + 1) + ".java";

      if(fileName.equals(".java")) {
         throw new IllegalArgumentException("Invalid driver service class name");
      }

      String classPath = className.replace('.', '/');

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
         V1_8, ACC_PUBLIC | ACC_SUPER, classPath, null,
         "inetsoft/uql/jdbc/drivers/AutoDriverService", null);
      classWriter.visitSource(fileName, null);

      {
         methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
         methodVisitor.visitCode();
         Label label0 = new Label();
         methodVisitor.visitLabel(label0);
         methodVisitor.visitLineNumber(17, label0);
         methodVisitor.visitVarInsn(ALOAD, 0);
         methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "inetsoft/uql/jdbc/drivers/AutoDriverService", "<init>", "()V", false);
         methodVisitor.visitInsn(RETURN);
         Label label1 = new Label();
         methodVisitor.visitLabel(label1);
         methodVisitor.visitLocalVariable("this", "L" + classPath + ";", null, label0, label1, 0);
         methodVisitor.visitMaxs(1, 1);
         methodVisitor.visitEnd();
      }

      classWriter.visitEnd();
      return classWriter.toByteArray();
   }
}
