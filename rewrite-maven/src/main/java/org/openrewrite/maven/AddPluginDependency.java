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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.xml.AutoFormatVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

import static org.openrewrite.xml.AddToTagVisitor.addToTag;
import static org.openrewrite.xml.MapTagChildrenVisitor.mapTagChildren;
import static org.openrewrite.xml.SemanticallyEqual.areEqual;

@Value
@EqualsAndHashCode(callSuper = true)
public class AddPluginDependency extends Recipe {
    private static final XPathMatcher PLUGINS_MATCHER = new XPathMatcher("/project/build/plugins");

    @Option(displayName = "Plugin Group",
            description = "GroupId of the plugin to which the dependency will be added. " +
                    "A GroupId is the first part of a dependency coordinate 'org.openrewrite.maven:rewrite-maven-plugin:VERSION'.",
            example = "org.openrewrite.maven")
    String pluginGroupId;

    @Option(displayName = "Plugin Artifact",
            description = "ArtifactId of the plugin to which the dependency will be added." +
                    "The second part of a dependency coordinate 'org.openrewrite.maven:rewrite-maven-plugin:VERSION'.",
            example = "rewrite-maven-plugin")
    String pluginArtifactId;

    @Option(displayName = "Group",
            description = "The GroupId of the dependency to add.",
            example = "org.openrewrite.recipe")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The ArtifactId of the dependency to add.",
            example = "org.openrewrite.recipe")
    String artifactId;

    @Option(displayName = "Version",
            description = "The Version of the dependency to add.",
            example = "org.openrewrite.recipe")
    @Nullable
    String version;


    @Override
    public String getDisplayName() {
        return "Add Maven plugin dependencies";
    }

    @Override
    public String getDescription() {
        return "Adds the specified dependencies to a Maven plugin. Will not add the plugin if it does not already exist in the pom.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag plugins = (Xml.Tag) super.visitTag(tag, ctx);
                if (!PLUGINS_MATCHER.matches(getCursor())) {
                    return plugins;
                }
                Optional<Xml.Tag> maybePlugin = plugins.getChildren().stream()
                        .filter(plugin ->
                                plugin.getName().equals("plugin") &&
                                        pluginGroupId.equals(plugin.getChildValue("groupId").orElse(null)) &&
                                        pluginArtifactId.equals(plugin.getChildValue("artifactId").orElse(null))
                        )
                        .findAny();
                if (!maybePlugin.isPresent()) {
                    return plugins;
                }
                Xml.Tag plugin = maybePlugin.get();
                Optional<Xml.Tag> maybeDependencies = plugin.getChild("dependencies");
                Xml.Tag dependencies;
                boolean formatAllDependencies = false;
                if(maybeDependencies.isPresent()) {
                    dependencies = maybeDependencies.get();
                } else {
                    formatAllDependencies = true;
                    dependencies = Xml.Tag.build("<dependencies />").withPrefix("\n");
                    plugins = addToTag(plugins, plugin, dependencies, getCursor());
                }
                Xml.Tag newDependencyTag = Xml.Tag.build("<dependency>\n<groupId>" + groupId + "</groupId>\n<artifactId>"
                        + artifactId +"</artifactId>" + ((version == null) ? "\n" : "\n<version>" +  version + "</version>\n") + "</dependency>")
                        .withPrefix("\n");

                // The dependency being added may already exist and may or may not need its version updated
                Optional<Xml.Tag> maybeExistingDependency  = dependencies.getChildren()
                        .stream()
                        .filter(it -> groupId.equals(it.getChildValue("groupId").orElse(null))
                                && artifactId.equals(it.getChildValue("artifactId").orElse(null)))
                        .findAny();
                if(maybeExistingDependency.isPresent() && areEqual(newDependencyTag, maybeExistingDependency.get())) {
                    return plugins;
                }
                if(maybeExistingDependency.isPresent()) {
                    plugins = mapTagChildren(plugins, dependencies, it -> {
                        if(it == maybeExistingDependency.get()) {
                            return newDependencyTag;
                        }
                        return it;
                    });
                } else {
                    plugins = addToTag(plugins, dependencies, newDependencyTag, getCursor());
                }
                if(formatAllDependencies) {
                    plugins = (Xml.Tag) new AutoFormatVisitor<ExecutionContext>(dependencies)
                            .visitNonNull(plugins, ctx, getCursor().getParentOrThrow());
                } else {
                    plugins = (Xml.Tag) new AutoFormatVisitor<ExecutionContext>(newDependencyTag)
                            .visitNonNull(plugins, ctx, getCursor().getParentOrThrow());
                }
                return plugins;
            }
        };
    }
}
