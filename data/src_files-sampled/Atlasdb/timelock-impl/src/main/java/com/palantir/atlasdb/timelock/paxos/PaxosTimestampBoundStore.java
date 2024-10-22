package com.palantir.atlasdb.timelock.paxos;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.timelock.paxos.PaxosQuorumCheckingCoalescingFunction.PaxosContainer;
import com.palantir.common.remoting.ServiceNotAvailableException;
import com.palantir.leader.NotCurrentLeaderException;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.paxos.ImmutablePaxosLong;
import com.palantir.paxos.PaxosAcceptor;
import com.palantir.paxos.PaxosAcceptorNetworkClient;
import com.palantir.paxos.PaxosLearner;
import com.palantir.paxos.PaxosLearnerNetworkClient;
import com.palantir.paxos.PaxosLong;
import com.palantir.paxos.PaxosProposer;
import com.palantir.paxos.PaxosResponses;
import com.palantir.paxos.PaxosRoundFailureException;
import com.palantir.paxos.PaxosValue;
import com.palantir.timestamp.DebugLogger;
import com.palantir.timestamp.MultipleRunningTimestampServiceError;
import com.palantir.timestamp.TimestampBoundStore;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import org.immutables.value.Value;

public class PaxosTimestampBoundStore implements TimestampBoundStore {
    private static final SafeLogger log = SafeLoggerFactory.get(PaxosTimestampBoundStore.class);

    private final PaxosProposer proposer;
    private final PaxosLearner knowledge;

    private final PaxosAcceptorNetworkClient acceptorNetworkClient;
    private final PaxosLearnerNetworkClient learnerClient;
    private final long maximumWaitBeforeProposalMs;

    @GuardedBy("this")
    private SequenceAndBound agreedState;

    public PaxosTimestampBoundStore(
            PaxosProposer proposer,
            PaxosLearner knowledge,
            PaxosAcceptorNetworkClient acceptorNetworkClient,
            PaxosLearnerNetworkClient learnerClient,
            long maximumWaitBeforeProposalMs) {
        DebugLogger.logger.info(
                "Creating PaxosTimestampBoundStore. The UUID of my proposer is {}. "
                        + "Currently, I believe the timestamp bound is {}.",
                SafeArg.of("proposerUuid", proposer.getUuid()),
                SafeArg.of("timestampBound", knowledge.getGreatestLearnedValue()));
        this.proposer = proposer;
        this.knowledge = knowledge;
        this.acceptorNetworkClient = acceptorNetworkClient;
        this.learnerClient = learnerClient;
        this.maximumWaitBeforeProposalMs = maximumWaitBeforeProposalMs;
    }

    @Override
    public synchronized long getUpperLimit() {
        List<PaxosLong> responses = getLatestSequenceNumbersFromAcceptors();
        PaxosLong max = Ordering.natural().onResultOf(PaxosLong::getValue).max(responses);
        agreedState = getAgreedState(max.getValue());
        return agreedState.getBound();
    }

    private List<PaxosLong> getLatestSequenceNumbersFromAcceptors() {
        PaxosResponses<PaxosLong> responses = acceptorNetworkClient.getLatestSequencePreparedOrAccepted();
        if (!responses.hasQuorum()) {
            throw new ServiceNotAvailableException("could not get a quorum");
        }
        return responses.get();
    }

    @VisibleForTesting
    SequenceAndBound getAgreedState(long seq) {
        final Optional<SequenceAndBound> state = getLearnedState(seq);
        if (state.isPresent()) {
            return state.get();
        }

        Optional<SequenceAndBound> lastState = getLearnedState(seq - 1);
        if (!lastState.isPresent()) {
            SequenceAndBound forced = forceAgreedState(seq - 2, null);
            lastState = Optional.of(forceAgreedState(seq - 1, forced.getBound()));
        }

        return forceAgreedState(seq, lastState.get().getBound());
    }

    @VisibleForTesting
    SequenceAndBound forceAgreedState(long seq, @Nullable Long oldState) {
        if (seq <= PaxosAcceptor.NO_LOG_ENTRY) {
            return ImmutableSequenceAndBound.of(PaxosAcceptor.NO_LOG_ENTRY, 0L);
        }

        Optional<SequenceAndBound> state = getLearnedState(seq);
        if (state.isPresent()) {
            return state.get();
        }

        while (true) {
            try {
                byte[] acceptedValue = proposer.propose(seq, oldState == null ? null : PtBytes.toBytes(oldState));
                Preconditions.checkNotNull(acceptedValue, "Proposed value can't be null, but was in sequence %s", seq);
                return ImmutableSequenceAndBound.of(seq, PtBytes.toLong(acceptedValue));
            } catch (PaxosRoundFailureException e) {
                waitForRandomBackoff(e, Thread::sleep);
            }
        }
    }

    private Optional<SequenceAndBound> getLearnedState(long seq) {
        if (seq <= PaxosAcceptor.NO_LOG_ENTRY) {
            return Optional.of(ImmutableSequenceAndBound.of(PaxosAcceptor.NO_LOG_ENTRY, 0L));
        }

        PaxosResponses<PaxosContainer<Optional<PaxosLong>>> responses = learnerClient.getLearnedValue(
                seq,
                maybeValue -> PaxosContainer.of(
                        maybeValue.map(PaxosValue::getData).map(PtBytes::toLong).map(ImmutablePaxosLong::of)));

        return responses.stream()
                .map(PaxosContainer::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(PaxosLong::getValue)
                .<SequenceAndBound>map(value -> ImmutableSequenceAndBound.of(seq, value))
                .findFirst();
    }

    @Override
    public synchronized void storeUpperLimit(long limit) throws MultipleRunningTimestampServiceError {
        long newSeq = PaxosAcceptor.NO_LOG_ENTRY + 1;
        if (agreedState != null) {
            Preconditions.checkArgument(
                    limit >= agreedState.getBound(),
                    "Tried to store an upper limit %s less than the current limit %s",
                    limit,
                    agreedState.getBound());
            newSeq = agreedState.getSeqId() + 1;
        }
        while (true) {
            try {
                proposer.propose(newSeq, PtBytes.toBytes(limit));
                PaxosValue value = knowledge
                        .getLearnedValue(newSeq)
                        .orElseThrow(() -> new SafeIllegalStateException("Timestamp bound store: Paxos proposal"
                                + " returned without learning a value. This is unexpected and would suggest a bug in"
                                + " AtlasDB code. Please contact support."));
                checkAgreedBoundIsOurs(limit, newSeq, value);
                long newLimit = PtBytes.toLong(value.getData());
                agreedState = ImmutableSequenceAndBound.of(newSeq, newLimit);
                if (newLimit < limit) {
                    log.warn(
                            "It appears we updated the timestamp limit to {}, which was less than our target {}."
                                    + " This suggests we have another timestamp service running; possibly because we"
                                    + " lost and regained leadership. For safety, we are now stopping this service.",
                            SafeArg.of("newLimit", newLimit),
                            SafeArg.of("target", limit));
                    throw new NotCurrentLeaderException(String.format(
                            "We updated the timestamp limit to %s, which was less than our target %s.",
                            newLimit, limit));
                }
                return;
            } catch (PaxosRoundFailureException e) {
                waitForRandomBackoff(e, this::wait);
            }
        }
    }

    private void checkAgreedBoundIsOurs(long limit, long newSeq, PaxosValue value) throws NotCurrentLeaderException {
        if (!proposer.getUuid().equals(value.getLeaderUUID())) {
            String errorMsg = String.format(
                    "Timestamp limit changed from under us for sequence '%s' (proposer with UUID '%s' changed"
                            + " it, our UUID is '%s'). This suggests that we have lost leadership, and another timelock"
                            + " server has gained leadership and updated the timestamp bound."
                            + " The offending bound was '%s'; we tried to propose"
                            + " a bound of '%s'. (The offending Paxos value was '%s'.)",
                    newSeq, value.getLeaderUUID(), proposer.getUuid(), PtBytes.toLong(value.getData()), limit, value);
            throw new NotCurrentLeaderException(errorMsg);
        }
        DebugLogger.logger.info(
                "Trying to store limit '{}' for sequence '{}' yielded consensus on the value '{}'.",
                SafeArg.of("limit", limit),
                SafeArg.of("paxosSequenceNumber", newSeq),
                SafeArg.of("paxosValue", value));
    }

    private void waitForRandomBackoff(PaxosRoundFailureException paxosException, BackoffAction backoffAction) {
        long backoffTime = getRandomBackoffTime();
        log.info(
                "Paxos proposal couldn't complete, because we could not connect to a quorum of nodes. We"
                        + " will retry in {} ms.",
                SafeArg.of("backoffTime", backoffTime),
                paxosException);
        try {
            backoffAction.backoff(backoffTime);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private long getRandomBackoffTime() {
        return (long) (maximumWaitBeforeProposalMs * Math.random() + 1);
    }

    @Value.Immutable
    interface SequenceAndBound {
        @Value.Parameter
        long getSeqId();

        @Value.Parameter
        long getBound();
    }

    private interface BackoffAction {
        void backoff(long backoffTime) throws InterruptedException;
    }
}
