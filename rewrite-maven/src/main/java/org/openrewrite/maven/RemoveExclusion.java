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
package org.openrewrite.maven;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = true)
public class RemoveExclusion extends Recipe {
    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "com.google.guava")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "guava")
    String artifactId;

    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "com.google.guava")
    String exclusionGroupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate 'com.google.guava:guava:VERSION'.",
            example = "guava")
    String exclusionArtifactId;

    @Override
    public String getDisplayName() {
        return "Remove exclusion";
    }

    @Override
    public String getDescription() {
        return "Remove a single exclusion from on a particular dependency.";
    }

    @Override
    protected MavenVisitor getVisitor() {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (isDependencyTag(groupId, artifactId)) {
                    Optional<Xml.Tag> maybeExclusions = tag.getChild("exclusions");
                    if (maybeExclusions.isPresent()) {
                        return tag.withContent(ListUtils.map(tag.getChildren(), child -> {
                            if(child.getName().equals("exclusions")) {
                                Xml.Tag e = child;
                                if (e.getContent() != null) {
                                    e = e.withContent(ListUtils.map(e.getChildren(), exclusion -> {
                                        if (exclusion.getName().equals("exclusion") &&
                                                exclusion.getChildValue("groupId").map(g -> g.equals(exclusionGroupId)).orElse(false) &&
                                                exclusion.getChildValue("artifactId").map(g -> g.equals(exclusionArtifactId)).orElse(false)) {
                                            return null;
                                        }
                                        return exclusion;
                                    }));

                                    if (e.getContent() == null || e.getContent().isEmpty()) {
                                        return null;
                                    }
                                }
                                return e;
                            }
                            return child;
                        }));
                    }
                }
                return super.visitTag(tag, executionContext);
            }
        };
    }
}
