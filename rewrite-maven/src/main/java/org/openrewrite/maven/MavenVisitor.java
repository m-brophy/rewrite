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
package org.openrewrite.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.internal.MavenMetadata;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.*;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.openrewrite.internal.StringUtils.matchesGlob;

@SuppressWarnings("NotNullFieldNotInitialized")
public class MavenVisitor<P> extends XmlVisitor<P> {
    private static final XPathMatcher DEPENDENCY_MATCHER = new XPathMatcher("/project/dependencies/dependency");
    private static final XPathMatcher MANAGED_DEPENDENCY_MATCHER = new XPathMatcher("/project/dependencyManagement/dependencies/dependency");
    private static final XPathMatcher PROPERTY_MATCHER = new XPathMatcher("/project/properties/*");
    private static final XPathMatcher PLUGIN_MATCHER = new XPathMatcher("/project/*/plugins/plugin");
    private static final XPathMatcher PARENT_MATCHER = new XPathMatcher("/project/parent");

    private transient MavenResolutionResult resolutionResult;
    private transient List<MavenResolutionResult> modules;

    @Override
    public String getLanguage() {
        return "maven";
    }

    @Override
    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
        return super.isAcceptable(sourceFile, ctx) &&
                sourceFile.getMarkers().findFirst(MavenResolutionResult.class).isPresent();
    }

    protected MavenResolutionResult getResolutionResult() {
        //noinspection ConstantConditions
        if (resolutionResult == null) {
            resolutionResult = ((Xml.Document) getCursor()
                    .getPath(Xml.Document.class::isInstance)
                    .next())
                    .getMarkers().findFirst(MavenResolutionResult.class)
                    .orElseThrow(() -> new IllegalStateException("Maven visitors should not be visiting XML documents without a Maven marker"));
        }
        return resolutionResult;
    }

    public boolean isPropertyTag() {
        return PROPERTY_MATCHER.matches(getCursor());
    }

    public boolean isDependencyTag() {
        return DEPENDENCY_MATCHER.matches(getCursor());
    }

    public boolean isDependencyTag(String groupId, @Nullable String artifactId) {
        return isDependencyTag() && hasGroupAndArtifact(groupId, artifactId);
    }

    public boolean isManagedDependencyTag() {
        return MANAGED_DEPENDENCY_MATCHER.matches(getCursor());
    }

    public boolean isManagedDependencyTag(String groupId, @Nullable String artifactId) {
        return isManagedDependencyTag() && hasGroupAndArtifact(groupId, artifactId);
    }

    public boolean isPluginTag() {
        return PLUGIN_MATCHER.matches(getCursor());
    }

    public boolean isPluginTag(String groupId, @Nullable String artifactId) {
        return isPluginTag() && hasGroupAndArtifact(groupId, artifactId);
    }

    public boolean isParentTag() {
        return PARENT_MATCHER.matches(getCursor());
    }

    public boolean hasGroupAndArtifact(String groupId, @Nullable String artifactId) {
        Xml.Tag tag = getCursor().getValue();
        Map<Scope, List<ResolvedDependency>> dependencies = getResolutionResult().getDependencies();
        for (Scope scope : Scope.values()) {
            if (dependencies.containsKey(scope)) {
                for (ResolvedDependency resolvedDependency : dependencies.get(scope)) {
                    Dependency req = resolvedDependency.getRequested();
                    String reqGroup = req.getGroupId();
                    return (reqGroup == null || reqGroup.equals(tag.getChildValue("groupId").orElse(null))) &&
                            req.getArtifactId().equals(tag.getChildValue("artifactId").orElse(null)) &&
                            scope == Scope.fromName(tag.getChildValue("scope").orElse("compile"));
                }
            }
        }
        return false;
    }

    @Nullable
    public ResolvedDependency findDependency(Xml.Tag tag) {
        Map<Scope, List<ResolvedDependency>> dependencies = getResolutionResult().getDependencies();
        for (Scope scope : Scope.values()) {
            if (dependencies.containsKey(scope)) {
                for (ResolvedDependency resolvedDependency : dependencies.get(scope)) {
                    Dependency req = resolvedDependency.getRequested();
                    String reqGroup = req.getGroupId();
                    String reqVersion = req.getVersion();
                    if ((reqGroup == null || reqGroup.equals(tag.getChildValue("groupId").orElse(null))) &&
                            req.getArtifactId().equals(tag.getChildValue("artifactId").orElse(null)) &&
                            (reqVersion == null || reqVersion.equals(tag.getChildValue("version").orElse(null))) &&
                            (req.getClassifier() == null || req.getClassifier().equals(tag.getChildValue("classifier").orElse(null))) &&
                            scope == Scope.fromName(tag.getChildValue("scope").orElse("compile"))) {
                        return resolvedDependency;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public ResolvedManagedDependency findManagedDependency(Xml.Tag tag) {
        for (ResolvedManagedDependency d : getResolutionResult().getPom().getDependencyManagement()) {
            if (tag.getChildValue("groupId").orElse(getResolutionResult().getPom().getGroupId()).equals(d.getGroupId()) &&
                    tag.getChildValue("artifactId").orElse(getResolutionResult().getPom().getArtifactId()).equals(d.getArtifactId())) {
                return d;
            }
        }
        return null;
    }

    @Nullable
    public ResolvedDependency findDependency(Xml.Tag tag, @Nullable Scope inClasspathOf) {
        for (Map.Entry<Scope, List<ResolvedDependency>> scope : getResolutionResult().getDependencies().entrySet()) {
            if (inClasspathOf == null || scope.getKey() == inClasspathOf || scope.getKey().isInClasspathOf(inClasspathOf)) {
                for (ResolvedDependency d : scope.getValue()) {
                    if (tag.getChildValue("groupId").orElse(getResolutionResult().getPom().getGroupId()).equals(d.getGroupId()) &&
                            tag.getChildValue("artifactId").orElse(getResolutionResult().getPom().getArtifactId()).equals(d.getArtifactId())) {
                        return d;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds dependencies in the model that match the provided group and artifact ids.
     *
     * @param groupId    The groupId to match
     * @param artifactId The artifactId to match.
     * @return dependencies (including transitive dependencies) with any version matching the provided group and artifact id, if any.
     */
    public Collection<ResolvedDependency> findDependencies(String groupId, String artifactId) {
        return findDependencies(d -> matchesGlob(d.getGroupId(), groupId) && matchesGlob(d.getArtifactId(), artifactId));
    }

    /**
     * Finds dependencies in the model that match the given predicate.
     *
     * @param matcher A dependency test
     * @return dependencies (including transitive dependencies) with any version matching the given predicate.
     */
    public Collection<ResolvedDependency> findDependencies(Predicate<ResolvedDependency> matcher) {
        List<ResolvedDependency> found = null;
        for (List<ResolvedDependency> scope : getResolutionResult().getDependencies().values()) {
            for (ResolvedDependency d : scope) {
                if (matcher.test(d)) {
                    if (found == null) {
                        found = new ArrayList<>();
                    }
                    found.add(d);
                }
            }
        }
        return found == null ? emptyList() : found;
    }

    public MavenMetadata downloadMetadata(String groupId, String artifactId, ExecutionContext ctx) {
        return new MavenPomDownloader(emptyMap(), ctx)
                .downloadMetadata(new GroupArtifact(groupId, artifactId), null, getResolutionResult().getPom().getRepositories());
    }
}
