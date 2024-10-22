package com.palantir.timelock.paxos;

import com.palantir.atlasdb.timelock.paxos.PaxosUseCase;
import com.palantir.paxos.BooleanPaxosResponse;
import com.palantir.paxos.PaxosAcceptor;
import com.palantir.paxos.PaxosPromise;
import com.palantir.paxos.PaxosProposal;
import com.palantir.paxos.PaxosProposalId;

public final class TimelockPaxosAcceptorAdapter implements PaxosAcceptor {
    private final PaxosUseCase paxosUseCase;
    private final String client;
    private final TimelockPaxosAcceptorRpcClient timelockPaxosAcceptorRpcClient;

    TimelockPaxosAcceptorAdapter(
            PaxosUseCase paxosUseCase, String client, TimelockPaxosAcceptorRpcClient timelockPaxosAcceptorRpcClient) {
        this.paxosUseCase = paxosUseCase;
        this.client = client;
        this.timelockPaxosAcceptorRpcClient = timelockPaxosAcceptorRpcClient;
    }

    @Override
    public PaxosPromise prepare(long seq, PaxosProposalId pid) {
        return timelockPaxosAcceptorRpcClient.prepare(paxosUseCase, client, seq, pid);
    }

    @Override
    public BooleanPaxosResponse accept(long seq, PaxosProposal proposal) {
        return timelockPaxosAcceptorRpcClient.accept(paxosUseCase, client, seq, proposal);
    }

    @Override
    public long getLatestSequencePreparedOrAccepted() {
        return timelockPaxosAcceptorRpcClient.getLatestSequencePreparedOrAccepted(paxosUseCase, client);
    }
}
