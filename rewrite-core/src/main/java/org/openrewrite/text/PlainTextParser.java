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
package org.openrewrite.text;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.Tree.randomId;

public class PlainTextParser implements Parser<PlainText> {
    @Override
    public List<PlainText> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo,
                                       ExecutionContext ctx) {
        List<PlainText> plainTexts = new ArrayList<>();
        for (Input source : sources) {
            plainTexts.add(new PlainText(randomId(),
                    relativeTo == null ?
                            source.getPath() :
                            relativeTo.relativize(source.getPath()).normalize(),
                    source.getSource().getCharset().name(),
                    source.getSource().isCharsetBomMarked(),
                    Markers.EMPTY,
                    source.getSource().readFully()));
        }
        return plainTexts;
    }

    @Override
    public boolean accept(Path path) {
        return true;
    }

    @Override
    public Path sourcePathFromSourceText(Path prefix, String sourceCode) {
        return prefix.resolve("file.txt");
    }
}
