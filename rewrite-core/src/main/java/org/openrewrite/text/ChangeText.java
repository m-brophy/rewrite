/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.text;

import org.openrewrite.*;

import java.util.Collections;
import java.util.Set;

import static org.openrewrite.Validated.required;

public class ChangeText extends Recipe {

    @Option(displayName = "Text after change",
            description = "The text file will have only this text after the change.",
            example = "Some text.")
    private final String toText;

    public ChangeText(String toText) {
        this.toText = toText;
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("plain text");
    }

    @Override
    public String getDisplayName() {
        return "Change text";
    }

    @Override
    public String getDescription() {
        return "Completely replaces the contents of the text file with other text.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangeTextVisitor();
    }

    @Override
    public Validated validate() {
        return required("toText", toText);
    }

    private class ChangeTextVisitor extends PlainTextVisitor<ExecutionContext> {
        @Override
        public PlainText preVisit(PlainText tree, ExecutionContext ctx) {
            return tree.withText(toText);
        }
    }
}
