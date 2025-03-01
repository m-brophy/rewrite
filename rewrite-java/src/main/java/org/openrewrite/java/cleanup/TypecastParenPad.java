/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.SpacesVisitor;
import org.openrewrite.java.style.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

public class TypecastParenPad extends Recipe {
    @Override
    public String getDisplayName() {
        return "Typecast parenthesis padding";
    }

    @Override
    public String getDescription() {
        return "Fixes whitespace padding between a typecast type identifier and the enclosing left and right parenthesis. " +
                "For example, when configured to remove spacing, `( int ) 0L;` becomes `(int) 0L;`.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new TypecastParenPadVisitor();
    }

    private static class TypecastParenPadVisitor extends JavaIsoVisitor<ExecutionContext> {
        SpacesStyle spacesStyle;
        TypecastParenPadStyle typecastParenPadStyle;

        @Nullable
        EmptyForInitializerPadStyle emptyForInitializerPadStyle;

        @Nullable
        EmptyForIteratorPadStyle emptyForIteratorPadStyle;

        @Override
        public JavaSourceFile visitJavaSourceFile(JavaSourceFile javaSourceFile, ExecutionContext ctx) {
            SourceFile cu = (SourceFile)javaSourceFile;
            spacesStyle = cu.getStyle(SpacesStyle.class) == null ? IntelliJ.spaces() : cu.getStyle(SpacesStyle.class);
            typecastParenPadStyle = cu.getStyle(TypecastParenPadStyle.class) == null ? Checkstyle.typecastParenPadStyle() : cu.getStyle(TypecastParenPadStyle.class);
            emptyForInitializerPadStyle = cu.getStyle(EmptyForInitializerPadStyle.class);
            emptyForIteratorPadStyle = cu.getStyle(EmptyForIteratorPadStyle.class);

            spacesStyle = spacesStyle.withWithin(spacesStyle.getWithin().withTypeCastParentheses(typecastParenPadStyle.getSpace()));
            return super.visitJavaSourceFile((JavaSourceFile)cu, ctx);
        }

        @Override
        public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
            J.TypeCast tc = super.visitTypeCast(typeCast, ctx);
            doAfterVisit(new SpacesVisitor<>(spacesStyle, emptyForInitializerPadStyle, emptyForIteratorPadStyle, tc));
            return tc;
        }
    }

}
