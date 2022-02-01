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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.internal.MavenMetadata;
import org.openrewrite.maven.search.FindPlugin;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * Upgrade the version of a plugin using Node Semver
 * <a href="https://github.com/npm/node-semver#advanced-range-syntax">advanced range selectors</a>, allowing
 * more precise control over version updates to patch or minor releases.
 */
@Incubating(since = "7.7.0")
@Value
@EqualsAndHashCode(callSuper = true)
public class UpgradePluginVersion extends Recipe {
    @Option(displayName = "Group",
            description = "The first part of a dependency coordinate 'org.openrewrite.maven:rewrite-maven-plugin:VERSION'.",
            example = "org.openrewrite.maven")
    String groupId;

    @Option(displayName = "Artifact",
            description = "The second part of a dependency coordinate 'org.openrewrite.maven:rewrite-maven-plugin:VERSION'.",
            example = "rewrite-maven-plugin")
    String artifactId;

    @Option(displayName = "New version",
            description = "An exact version number, or node-style semver selector used to select the version number.",
            example = "29.X")
    String newVersion;

    @Option(displayName = "Version pattern",
            description = "Allows version selection to be extended beyond the original Node Semver semantics. So for example," +
                    "Setting 'version' to \"25-29\" can be paired with a metadata pattern of \"-jre\" to select Guava 29.0-jre",
            example = "-jre",
            required = false)
    @Nullable
    String versionPattern;

    // needs implementation, left here as syntactic placeholder // todo
    @Option(displayName = "Trust parent POM",
            description = "Even if the parent suggests a version that is older than what we are trying to upgrade to, trust it anyway. " +
                    "Useful when you want to wait for the parent to catch up before upgrading. The parent is not trusted by default.",
            example = "false",
            required = false)
    @Nullable
    Boolean trustParent;

    @SuppressWarnings("ConstantConditions")
    @Override
    public Validated validate() {
        Validated validated = super.validate();
        if (newVersion != null) {
            validated = validated.and(Semver.validate(newVersion, versionPattern));
        }
        return validated;
    }

    @Override
    public String getDisplayName() {
        return "Upgrade Maven plugin version";
    }

    @Override
    public String getDescription() {
        return "Upgrade the version of a plugin using Node Semver advanced range selectors, " +
                "allowing more precise control over version updates to patch or minor releases.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new FindPlugin(groupId, artifactId).getVisitor();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        final VersionComparator versionComparator = Semver.validate(newVersion, versionPattern).getValue();
        assert versionComparator != null;

        return new MavenVisitor<ExecutionContext>() {
            @Nullable
            private Collection<String> availableVersions;

            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml xml = super.visitDocument(document, ctx);
                FindPlugin.find((Xml.Document) xml, groupId, artifactId).forEach(plugin ->
                        maybeChangePluginVersion(plugin, ctx));
                return xml;
            }

            private void maybeChangePluginVersion(Xml.Tag model, ExecutionContext ctx) {
                Optional<Xml.Tag> versionTag = model.getChild("version");
                versionTag.flatMap(Xml.Tag::getValue).ifPresent(versionValue -> {
                    String versionLookup = versionValue.startsWith("${")
                            ? super.getResolutionResult().getPom().getValue(versionValue.trim())
                            : versionValue;

                    if (versionLookup != null) {
                        findNewerDependencyVersion(groupId, artifactId, versionLookup, ctx).ifPresent(newer -> {
                            ChangePluginVersionVisitor changeDependencyVersion = new ChangePluginVersionVisitor(groupId, artifactId, newer);
                            doAfterVisit(changeDependencyVersion);
                        });
                    }

                });
            }

            private Optional<String> findNewerDependencyVersion(String groupId, String artifactId, String currentVersion, ExecutionContext ctx) {
                if (availableVersions == null) {
                    MavenMetadata mavenMetadata = downloadMetadata(groupId, artifactId, ctx);
                    if (mavenMetadata != null) {
                        availableVersions = new ArrayList<>();
                        for (String v : mavenMetadata.getVersioning().getVersions()) {
                            if (versionComparator.isValid(currentVersion, v)) {
                                availableVersions.add(v);
                            }
                        }
                    }
                }
                return versionComparator.upgrade(currentVersion, availableVersions);
            }
        };
    }

    private static class ChangePluginVersionVisitor extends MavenVisitor<ExecutionContext> {
        private final String groupId;
        private final String artifactId;
        private final String newVersion;

        private ChangePluginVersionVisitor(String groupId, String artifactId, String newVersion) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.newVersion = newVersion;
        }

        @Override
        public Xml visitTag(Xml.Tag tag, ExecutionContext ctx) {
            if (isPluginTag(groupId, artifactId)) {
                Optional<Xml.Tag> versionTag = tag.getChild("version");
                if (versionTag.isPresent()) {
                    String version = versionTag.get().getValue().orElse(null);
                    if (version != null) {
                        if (version.trim().startsWith("${") && !newVersion.equals(getResolutionResult().getPom().getValue(version.trim()))) {
                            doAfterVisit(new ChangePropertyValue(version, newVersion, false));
                        } else if (!newVersion.equals(version)) {
                            doAfterVisit(new ChangeTagValueVisitor<>(versionTag.get(), newVersion));
                        }
                    }
                }
            }

            return super.visitTag(tag, ctx);
        }
    }
}
