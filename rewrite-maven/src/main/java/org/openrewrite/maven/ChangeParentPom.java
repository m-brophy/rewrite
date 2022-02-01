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
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.internal.MavenMetadata;
import org.openrewrite.maven.tree.Parent;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;
import org.openrewrite.xml.ChangeTagValueVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = true)
public class ChangeParentPom extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change Maven Parent Pom";
    }

    @Override
    public String getDescription() {
        return "Change the parent pom of a Maven pom.xml. Identifies the parent pom to be changed by its groupId and artifactId.";
    }

    @Option(displayName = "Old GroupId",
            description = "The groupId of the maven parent pom to be changed away from.",
            example = "org.springframework.boot")
    String oldGroupId;

    @Option(displayName = "New GroupId",
            description = "The groupId of the new maven parent pom to be adopted. If this argument is omitted it defaults to the value of `oldGroupId`.",
            example = "org.springframework.boot",
            required = false)
    @Nullable
    String newGroupId;

    @Option(displayName = "Old ArtifactId",
            description = "The artifactId of the maven parent pom to be changed away from.",
            example = "spring-boot-starter-parent")
    String oldArtifactId;

    @Option(displayName = "New ArtifactId",
            description = "The artifactId of the new maven parent pom to be adopted. If this argument is omitted it defaults to the value of `oldArtifactId`.",
            example = "spring-boot-starter-parent",
            required = false)
    @Nullable
    String newArtifactId;

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

    @Option(displayName = "Allow version downgrades",
            description = "If the new parent has the same group/artifact, this flag can be used to only upgrade the " +
                    "version if the target version is newer than the current.",
            example = "false",
            required = false)
    @Nullable
    boolean allowVersionDowngrades;

    public ChangeParentPom(String oldGroupId, @Nullable String newGroupId, String oldArtifactId, @Nullable String newArtifactId, String newVersion, @Nullable String versionPattern, @Nullable Boolean allowVersionDowngrades) {
        this.oldGroupId = oldGroupId;
        this.newGroupId = newGroupId;
        this.oldArtifactId = oldArtifactId;
        this.newArtifactId = newArtifactId;
        this.newVersion = newVersion;
        this.versionPattern = versionPattern;
        this.allowVersionDowngrades = allowVersionDowngrades != null && allowVersionDowngrades;
    }

    @Override
    protected MavenVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext executionContext) {
                Parent parent = getResolutionResult().getPom().getRequested().getParent();
                if (parent != null && oldArtifactId.equals(parent.getArtifactId()) && oldGroupId.equals(parent.getGroupId())) {
                    return document.withMarkers(document.getMarkers().searchResult());
                }
                return document;
            }
        };
    }

    @Override
    public Validated validate() {
        Validated validated = super.validate();
        //noinspection ConstantConditions
        if (newVersion != null) {
            validated = validated.and(Semver.validate(newVersion, versionPattern));
        }
        return validated;
    }

    @Override
    protected MavenVisitor<ExecutionContext> getVisitor() {
        VersionComparator versionComparator = Semver.validate(newVersion, versionPattern).getValue();
        assert versionComparator != null;

        return new MavenIsoVisitor<ExecutionContext>() {
            @Nullable
            private Collection<String> availableVersions;

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext executionContext) {
                Xml.Document m = super.visitDocument(document, executionContext);
                if(m != document) {
                    doAfterVisit(new UpdateMavenModel());
                }
                return m;
            }

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (isParentTag()) {
                    String targetGroupId = newGroupId == null ? oldGroupId : newGroupId;
                    String targetArtifactId = newArtifactId == null ? oldArtifactId : newArtifactId;
                    if (oldGroupId.equals(tag.getChildValue("groupId").orElse(null)) &&
                            oldArtifactId.equals(tag.getChildValue("artifactId").orElse(null))) {
                        tag.getChildValue("version")
                                .flatMap(parentVersion -> findNewerDependencyVersion(targetGroupId, targetArtifactId, parentVersion, ctx))
                                .ifPresent(newVersion -> {
                                    if (!oldGroupId.equals(targetGroupId)) {
                                        doAfterVisit(new ChangeTagValueVisitor<>(tag.getChild("groupId").get(), targetGroupId));
                                    }
                                    if (!oldArtifactId.equals(targetArtifactId)) {
                                        doAfterVisit(new ChangeTagValueVisitor<>(tag.getChild("artifactId").get(), targetArtifactId));
                                    }
                                    if (!newVersion.equals(tag.getChildValue("version").orElse(null))) {
                                        doAfterVisit(new ChangeTagValueVisitor<>(tag.getChild("version").get(), newVersion));
                                    }
                                    doAfterVisit(new RemoveRedundantDependencyVersions());
                                });
                    }
                }

                return super.visitTag(tag, ctx);
            }

            private Optional<String> findNewerDependencyVersion(String groupId, String artifactId, String currentVersion,
                                                                ExecutionContext ctx) {
                if (availableVersions == null) {
                    MavenMetadata mavenMetadata = downloadMetadata(groupId, artifactId, ctx);
                    if (mavenMetadata == null) {
                        availableVersions = emptyList();
                    } else {
                        availableVersions = mavenMetadata.getVersioning().getVersions().stream()
                                .filter(v -> versionComparator.isValid(currentVersion, v))
                                .filter(v -> allowVersionDowngrades || versionComparator.compare(currentVersion, currentVersion, v) < 0)
                                .collect(Collectors.toList());
                    }
                }
                return allowVersionDowngrades ? availableVersions.stream().max((v1, v2) -> versionComparator.compare(currentVersion, v1, v2)) : versionComparator.upgrade(currentVersion, availableVersions);
            }
        };
    }
}

