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
package org.openrewrite.maven.internal;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.internal.grammar.VersionRangeLexer;
import org.openrewrite.maven.internal.grammar.VersionRangeParser;
import org.openrewrite.maven.internal.grammar.VersionRangeParserBaseVisitor;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

public class RequestedVersion {
    private static final Logger logger = LoggerFactory.getLogger(RequestedVersion.class);

    private final GroupArtifact groupArtifact;

    @Nullable
    private final RequestedVersion nearer;

    private final VersionSpec versionSpec;

    /**
     * @param groupArtifact The group and artifact of the requested version.
     * @param nearer        A version in the same group and artifact that is nearer the root, if any.
     * @param requested     Any valid version text that can be written in a POM
     *                      including a fixed version, a range, LATEST, or RELEASE.
     */
    public RequestedVersion(GroupArtifact groupArtifact, @Nullable RequestedVersion nearer, String requested) {
        this.groupArtifact = groupArtifact;
        this.nearer = nearer;

        if (requested.equals("LATEST")) {
            this.versionSpec = new DynamicVersion(DynamicVersion.Kind.LATEST);
        } else if (requested.equals("RELEASE")) {
            this.versionSpec = new DynamicVersion(DynamicVersion.Kind.RELEASE);
        } else if (requested.contains("[") || requested.contains("(")) {
            // for things like the profile activation block of where the range is unclosed but maven still handles it, e.g.
            // https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.12.0-rc2/jackson-databind-2.12.0-rc2.pom
            if (!(requested.contains("]") || requested.contains(")"))) {
                requested = requested + "]";
            }

            VersionRangeParser parser = new VersionRangeParser(new CommonTokenStream(new VersionRangeLexer(
                    CharStreams.fromString(requested))));

            parser.removeErrorListeners();
            parser.addErrorListener(new LoggingErrorListener());

            this.versionSpec = new VersionRangeParserBaseVisitor<VersionSpec>() {
                @Override
                public VersionSpec visitRequestedVersion(VersionRangeParser.RequestedVersionContext ctx) {
                    if (ctx.version() != null) {
                        return new SoftRequirement(new Version(ctx.version().getText()));
                    }

                    return new RangeSet(ctx.range().stream()
                            .map(range -> {
                                Version lower, upper;
                                if (range.bounds().boundedLower() != null) {
                                    Iterator<TerminalNode> versionIter = range.bounds().boundedLower().Version().iterator();
                                    lower = toVersion(versionIter.next());
                                    upper = versionIter.hasNext() ? toVersion(versionIter.next()) : null;
                                } else if (range.bounds().unboundedLower() != null) {
                                    lower = null;
                                    upper = toVersion(range.bounds().unboundedLower().Version());
                                } else {
                                    lower = toVersion(range.bounds().exactly().Version());
                                    upper = toVersion(range.bounds().exactly().Version());
                                }
                                return new Range(
                                        range.CLOSED_RANGE_OPEN() != null, lower,
                                        range.CLOSED_RANGE_CLOSE() != null, upper
                                );
                            })
                            .collect(toList())
                    );
                }

                private Version toVersion(TerminalNode version) {
                    return new Version(version.getText());
                }
            }.visit(parser.requestedVersion());
        } else {
            this.versionSpec = new SoftRequirement(new Version(requested));
        }
    }

    public boolean isRange() {
        return versionSpec instanceof RangeSet && (nearer == null || nearer.isRange());
    }

    public boolean isDynamic() {
        return !isRange() && (nearer == null ? versionSpec instanceof DynamicVersion : nearer.isDynamic());
    }

    /**
     * When the requested version is not a range, select the nearest version.
     */
    @Nullable
    public String nearestVersion() {
        if (isRange() || isDynamic()) {
            return null;
        } else if (nearer != null) {
            return nearer.nearestVersion();
        }
        return ((SoftRequirement) versionSpec).version.toString();
    }

    /**
     * When the requested version is a range set or dynamic, select the latest matching version.
     *
     * @param availableVersions The other versions listed in maven metadata.
     * @return The latest version matching the range set.
     */
    @Nullable
    public String selectFrom(Iterable<String> availableVersions) {
        Stream<Version> versionStream = StreamSupport.stream(availableVersions.spliterator(), false)
                .map(Version::new);
        return (isRange() ?
                versionStream.filter(this::rangeMatch) :
                versionStream
                        .filter(v -> ((DynamicVersion) versionSpec).kind.equals(DynamicVersion.Kind.LATEST) || !v.toString().endsWith("-SNAPSHOT"))

        ).max(Comparator.naturalOrder()).map(Version::toString).orElse(null);
    }

    private boolean rangeMatch(Version version) {
        if (!(versionSpec instanceof RangeSet)) {
            return true;
        }

        return ((RangeSet) versionSpec).ranges.stream()
                .anyMatch(range -> {
                    boolean lowerMatches = true;
                    if (range.lower != null) {
                        int lowComp = range.lower.compareTo(version);
                        lowerMatches = lowComp == 0 ? range.lowerClosed : lowComp < 0;
                    }

                    boolean upperMatches = true;
                    if (range.upper != null) {
                        int upperComp = range.upper.compareTo(version);
                        upperMatches = upperComp == 0 ? range.upperClosed : upperComp > 0;
                    }

                    return lowerMatches && upperMatches;
                }) && (nearer == null || nearer.rangeMatch(version));
    }

    interface VersionSpec {
    }

    private static class SoftRequirement implements VersionSpec {
        private final Version version;

        private SoftRequirement(Version version) {
            this.version = version;
        }
    }

    private static class DynamicVersion implements VersionSpec {
        private final Kind kind;

        private DynamicVersion(Kind kind) {
            this.kind = kind;
        }

        enum Kind {
            LATEST,
            RELEASE
        }
    }

    private static class RangeSet implements VersionSpec {
        private final List<Range> ranges;

        private RangeSet(List<Range> ranges) {
            this.ranges = ranges;
        }
    }

    private static class Range {
        private final boolean lowerClosed;
        private final boolean upperClosed;

        @Nullable
        private final Version lower;

        @Nullable
        private final Version upper;

        private Range(boolean lowerClosed,
                      @Nullable Version lower,
                      boolean upperClosed,
                      @Nullable Version upper) {
            this.lowerClosed = lowerClosed;
            this.lower = lower;
            this.upperClosed = upperClosed;
            this.upper = upper;
        }

        @Override
        public String toString() {
            return (lowerClosed ? "[" : "(") + lower + "," + upper +
                    (upperClosed ? ']' : ')');
        }
    }

    @Nullable
    public String resolve(MavenPomDownloader downloader, List<MavenRepository> repositories) {
        String selectedVersion = null;
        if (isRange() || isDynamic()) {
            MavenMetadata metadata = downloader.downloadMetadata(groupArtifact, null, repositories);
            if(metadata != null) {
                selectedVersion = selectFrom(metadata.getVersioning().getVersions());
            }
        } else {
            selectedVersion = nearestVersion();
        }

        // for debugging...
        //noinspection RedundantIfStatement
        if (selectedVersion == null) {
            //noinspection ConstantConditions
            assert selectedVersion != null;
        }

        return selectedVersion;
    }

    private static class LoggingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            logger.warn("Syntax error at line {}:{} {}", line, charPositionInLine, msg);
        }
    }
}
