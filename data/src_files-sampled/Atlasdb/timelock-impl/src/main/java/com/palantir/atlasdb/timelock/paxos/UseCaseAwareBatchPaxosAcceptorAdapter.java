package com.palantir.atlasdb.timelock.paxos;

import static com.palantir.atlasdb.timelock.paxos.BatchPaxosAcceptorRpcClient.CACHE_KEY_NOT_FOUND;

import com.google.common.collect.SetMultimap;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.paxos.BooleanPaxosResponse;
import com.palantir.paxos.Client;
import com.palantir.paxos.PaxosPromise;
import com.palantir.paxos.PaxosProposal;
import com.palantir.paxos.PaxosProposalId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class UseCaseAwareBatchPaxosAcceptorAdapter implements BatchPaxosAcceptor {

    private static final SafeLogger log = SafeLoggerFactory.get(UseCaseAwareBatchPaxosAcceptorAdapter.class);

    private final PaxosUseCase useCase;
    private final BatchPaxosAcceptorRpcClient rpcClient;

    private UseCaseAwareBatchPaxosAcceptorAdapter(PaxosUseCase useCase, BatchPaxosAcceptorRpcClient rpcClient) {
        this.useCase = useCase;
        this.rpcClient = rpcClient;
    }

    @Override
    public SetMultimap<Client, WithSeq<PaxosPromise>> prepare(
            SetMultimap<Client, WithSeq<PaxosProposalId>> promiseWithSeqRequestsByClient) {
        return rpcClient.prepare(useCase, promiseWithSeqRequestsByClient);
    }

    @Override
    public SetMultimap<Client, WithSeq<BooleanPaxosResponse>> accept(
            SetMultimap<Client, PaxosProposal> proposalRequestsByClientAndSeq) {
        return rpcClient.accept(useCase, proposalRequestsByClientAndSeq);
    }

    @Override
    public AcceptorCacheDigest latestSequencesPreparedOrAccepted(
            Optional<AcceptorCacheKey> cacheKey, Set<Client> clients) throws InvalidAcceptorCacheKeyException {
        try {
            return rpcClient.latestSequencesPreparedOrAccepted(useCase, cacheKey.orElse(null), clients);
        } catch (RuntimeException e) {
            if (isMissingOrInvalidCacheKey(e)) {
                throw new InvalidAcceptorCacheKeyException(cacheKey.orElse(null));
            }

            log.warn("received unexpected runtime exception that is not a cache key miss", e);
            throw e;
        }
    }

    @Override
    public Optional<AcceptorCacheDigest> latestSequencesPreparedOrAcceptedCached(AcceptorCacheKey cacheKey)
            throws InvalidAcceptorCacheKeyException {
        try {
            return rpcClient.latestSequencesPreparedOrAcceptedCached(useCase, cacheKey);
        } catch (RuntimeException e) {
            if (isMissingOrInvalidCacheKey(e)) {
                throw new InvalidAcceptorCacheKeyException(cacheKey);
            }

            log.warn("received unexpected runtime exception that is not a cache key miss", e);
            throw e;
        }
    }

    private static boolean isMissingOrInvalidCacheKey(RuntimeException e) {
        if (e instanceof RemoteException) {
            return ((RemoteException) e).getError().errorName().equals(CACHE_KEY_NOT_FOUND.name());
        } else {
            return e.getMessage().contains(CACHE_KEY_NOT_FOUND.name());
        }
    }

    public static List<WithDedicatedExecutor<BatchPaxosAcceptor>> wrap(
            PaxosUseCase useCase, List<WithDedicatedExecutor<BatchPaxosAcceptorRpcClient>> remotes) {
        return remotes.stream()
                .map(withExecutor -> withExecutor.transformService(rpcClient -> wrapInAdapter(useCase, rpcClient)))
                .collect(Collectors.toList());
    }

    private static BatchPaxosAcceptor wrapInAdapter(PaxosUseCase useCase, BatchPaxosAcceptorRpcClient rpcClient) {
        return new UseCaseAwareBatchPaxosAcceptorAdapter(useCase, rpcClient);
    }
}
