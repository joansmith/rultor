/**
 * Copyright (c) 2009-2014, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.agents;

import co.stateful.Counters;
import co.stateful.RtSttc;
import co.stateful.cached.CdSttc;
import co.stateful.retry.ReSttc;
import com.jcabi.aspects.Cacheable;
import com.jcabi.aspects.Immutable;
import com.jcabi.github.Github;
import com.jcabi.github.RtGithub;
import com.jcabi.http.wire.RetryWire;
import com.jcabi.manifests.Manifests;
import com.jcabi.s3.Region;
import com.jcabi.s3.retry.ReRegion;
import com.jcabi.urn.URN;
import com.rultor.agents.daemons.ArchivesDaemon;
import com.rultor.agents.daemons.EndsDaemon;
import com.rultor.agents.daemons.KillsDaemon;
import com.rultor.agents.daemons.StartsDaemon;
import com.rultor.agents.github.Question;
import com.rultor.agents.github.Reports;
import com.rultor.agents.github.StartsTalks;
import com.rultor.agents.github.Understands;
import com.rultor.agents.github.qtn.QnAskedBy;
import com.rultor.agents.github.qtn.QnDeploy;
import com.rultor.agents.github.qtn.QnHello;
import com.rultor.agents.github.qtn.QnIfContains;
import com.rultor.agents.github.qtn.QnMerge;
import com.rultor.agents.github.qtn.QnReferredTo;
import com.rultor.agents.req.EndsRequest;
import com.rultor.agents.req.StartsRequest;
import com.rultor.agents.shells.RegistersShell;
import com.rultor.agents.shells.RemovesShell;
import com.rultor.spi.Agent;
import com.rultor.spi.Profile;
import com.rultor.spi.SuperAgent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;

/**
 * Agents.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@Immutable
@ToString
@EqualsAndHashCode
@SuppressWarnings("PMD.ExcessiveImports")
public final class Agents {

    /**
     * Create super agents, starters.
     * @return List of them
     */
    public Collection<SuperAgent> starters() {
        return Arrays.<SuperAgent>asList(
            new StartsTalks(Agents.github(), Agents.counters())
        );
    }

    /**
     * Create super agents, closers.
     * @return List of them
     */
    public Collection<SuperAgent> closers() {
        return Arrays.<SuperAgent>asList(
            new DeactivatesTalks()
        );
    }

    /**
     * Create them for a talk.
     * @param profile Profile
     * @return List of them
     * @throws IOException If fails
     */
    public Collection<Agent> agents(final Profile profile)
        throws IOException {
        final Collection<Agent> agents = new LinkedList<Agent>();
        final Github github = Agents.github();
        agents.addAll(
            Arrays.asList(
                new Understands(
                    github,
                    new QnReferredTo(
                        github.users().self().login(),
                        new QnAskedBy(
                            Collections.singleton("yegor256"),
                            new Question.FirstOf(
                                Arrays.<Question>asList(
                                    new QnIfContains("hello", new QnHello()),
                                    new QnIfContains("merge", new QnMerge()),
                                    new QnIfContains("deploy", new QnDeploy())
                                )
                            )
                        )
                    )
                ),
                new StartsRequest(profile),
                new RegistersShell(
                    // @checkstyle MagicNumber (1 line)
                    "b1.rultor.com", 22,
                    "rultor",
                    IOUtils.toString(
                        this.getClass().getResourceAsStream("rultor.key"),
                        CharEncoding.UTF_8
                    )
                ),
                new StartsDaemon(profile),
                new KillsDaemon(),
                new EndsDaemon(),
                new EndsRequest(),
                new Reports(github),
                new RemovesShell(),
                new ArchivesDaemon(
                    new ReRegion(
                        new Region.Simple(
                            Manifests.read("Rultor-S3Key"),
                            Manifests.read("Rultor-S3Secret")
                        )
                    ).bucket(Manifests.read("Rultor-S3Bucket"))
                )
            )
        );
        return agents;
    }

    /**
     * Make github.
     * @return Github
     */
    @Cacheable(forever = true)
    private static Github github() {
        return new RtGithub(
            new RtGithub(
                Manifests.read("Rultor-GithubToken")
            ).entry().through(RetryWire.class)
        );
    }

    /**
     * Sttc counter.
     * @return Counter
     */
    @Cacheable(forever = true)
    private static Counters counters() {
        try {
            return new CdSttc(
                new ReSttc(
                    RtSttc.make(
                        URN.create(Manifests.read("Rultor-SttcUrn")),
                        Manifests.read("Rultor-SttcToken")
                    )
                )
            ).counters();
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
