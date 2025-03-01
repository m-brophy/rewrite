/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.EmptyBlockStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.Optional;

public class SimplifyConstantIfBranchExecution extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify constant if branch execution";
    }

    @Override
    public String getDescription() {
        return "Checks for if expressions that are always `true` or `false` and simplifies them.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new SimplifyConstantIfBranchExecutionVisitor();
    }

    private static class SimplifyConstantIfBranchExecutionVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitBlock(J.Block block, ExecutionContext executionContext) {
            J.Block bl = (J.Block) super.visitBlock(block, executionContext);
            if (bl != block) {
                bl = (J.Block) new RemoveUnneededBlock.RemoveUnneededBlockStatementVisitor()
                    .visitNonNull(bl, executionContext, getCursor().getParentOrThrow());
                EmptyBlockStyle style = ((SourceFile) getCursor().firstEnclosingOrThrow(JavaSourceFile.class))
                    .getStyle(EmptyBlockStyle.class);
                if (style == null) {
                    style = Checkstyle.emptyBlock();
                }
                bl = (J.Block) new EmptyBlockVisitor<>(style)
                    .visitNonNull(bl, executionContext, getCursor().getParentOrThrow());
            }
            return bl;
        }

        @SuppressWarnings("unchecked")
        private <E extends Expression> E cleanupBooleanExpression(
            E expression, ExecutionContext context
        ) {
            final E ex1 =
                    (E) new UnnecessaryParenthesesVisitor<>(Checkstyle.unnecessaryParentheses())
                            .visitNonNull(expression, context, getCursor().getParentOrThrow());
            final E ex2 =
                    (E) new SimplifyBooleanExpressionVisitor<>()
                            .visitNonNull(ex1, context, getCursor().getParentOrThrow());
            return ex2;
        }

        @Override
        public J visitIf(J.If if_, ExecutionContext context) {
            J.If if__ = (J.If) super.visitIf(if_, context);
            J.ControlParentheses<Expression> cp =
                    cleanupBooleanExpression(if__.getIfCondition(), context);
            // The compile-time constant value of the if condition control parentheses.
            final Optional<Boolean> compileTimeConstantBoolean;
            if (isLiteralTrue(cp.getTree())) {
                compileTimeConstantBoolean = Optional.of(true);
            } else if (isLiteralFalse(cp.getTree())) {
                compileTimeConstantBoolean = Optional.of(false);
            } else {
                // The condition is not a literal, so we can't simplify it.
                compileTimeConstantBoolean = Optional.empty();
            }

            // The simplification process did not result in resolving to a single 'true' or 'false' value
            if (!compileTimeConstantBoolean.isPresent()) {
                return if__; // Return the visited `if`
            } else if (compileTimeConstantBoolean.get()) {
                // True branch
                // Only keep the `then` branch, and remove the `else` branch.
                Statement s = if__.getThenPart();
                return maybeAutoFormat(
                    if__,
                    s,
                    context,
                    getCursor().getParentOrThrow()
                );
            } else {
                // False branch
                // Only keep the `else` branch, and remove the `then` branch.
                if (if__.getElsePart() != null) {
                    // The `else` part needs to be kept
                    Statement s = if__.getElsePart().getBody();
                    return maybeAutoFormat(
                        if__,
                        s,
                        context,
                        getCursor().getParentOrThrow()
                    );
                }
                /*
                 * The `else` branch is not present, therefore, the `if` can be removed.
                 * Temporarily return an empty block that will (most likely) later be removed.
                 * We need to return an empty block, in the following cases:
                 * ```
                 * if (a) a();
                 * else if (false) { }
                 * ```
                 * Failing to return an empty block here would result in the following code being emitted:
                 * ```
                 * if (a) a();
                 * else
                 * ```
                 * The above is not valid java and will cause later processing errors.
                 */
                return emptyJBlock();
            }
        }

        /**
         * An empty {@link J.Block} with no contents.
         */
        private static J.Block emptyJBlock() {
            return new J.Block(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                JRightPadded.build(false),
                Collections.emptyList(),
                Space.EMPTY
            );
        }

        private static boolean isLiteralTrue(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
        }

        private static boolean isLiteralFalse(@Nullable Expression expression) {
            return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
        }
    }

}
