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
import org.openrewrite.TreeVisitor;

public class SimplifyCompoundStatement extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify compound statement";
    }

    @Override
    public String getDescription() {
        return "Fixes or removes useless compound statements. For example, removing `b &= true`, and replacing `b &= false` with `b = false`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyCompoundVisitor<>();
    }
}
