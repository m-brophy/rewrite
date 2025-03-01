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
package org.openrewrite.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;

public class RandomizeId extends Recipe {
    @Override
    public String getDisplayName() {
        return "Randomize tree IDs";
    }

    @Override
    public String getDescription() {
        return "Scramble the IDs. This was intended as a utility to test _en masse_ " +
                "different techniques for UUID generation and compare their relative performance " +
                "outside of a microbenchmark.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new RandomizeIdVisitor<>();
    }
}
