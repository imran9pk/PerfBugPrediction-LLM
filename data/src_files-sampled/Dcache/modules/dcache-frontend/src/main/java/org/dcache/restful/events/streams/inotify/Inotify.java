package org.dcache.restful.events.streams.inotify;

import static diskCacheV111.namespace.EventNotifier.INOTIFY_PATH;
import static org.dcache.auth.attributes.Activity.READ_METADATA;
import static org.dcache.namespace.FileAttribute.PNFSID;
import static org.dcache.namespace.FileAttribute.TYPE;
import static org.dcache.restful.util.transfers.Json.readFromJar;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import diskCacheV111.namespace.EventNotifier;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.NotDirCacheException;
import diskCacheV111.util.PermissionDeniedCacheException;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.PnfsId;
import dmg.cells.nucleus.CellAddressCore;
import dmg.cells.nucleus.CellIdentityAware;
import dmg.cells.nucleus.CellLifeCycleAware;
import dmg.cells.nucleus.CellMessageReceiver;
import dmg.cells.nucleus.NoRouteToCellException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.nodes.PersistentNode;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.dcache.acl.enums.AccessMask;
import org.dcache.auth.attributes.Restriction;
import org.dcache.cells.CuratorFrameworkAware;
import org.dcache.events.Event;
import org.dcache.events.NotificationMessage;
import org.dcache.events.SystemEvent;
import org.dcache.http.PathMapper;
import org.dcache.namespace.FileAttribute;
import org.dcache.namespace.FileType;
import org.dcache.namespace.events.EventType;
import org.dcache.namespace.events.InotifyEvent;
import org.dcache.restful.events.spi.EventStream;
import org.dcache.restful.events.spi.SelectedEventStream;
import org.dcache.restful.events.spi.SelectionContext;
import org.dcache.restful.events.spi.SelectionResult;
import org.dcache.restful.util.RequestUser;
import org.dcache.util.RepeatableTaskRunner;
import org.dcache.vehicles.FileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

public class Inotify implements EventStream, CellMessageReceiver,
      CuratorFrameworkAware, CellIdentityAware, CellLifeCycleAware {

    private class InotifySelection implements SelectedEventStream {

        private final BiConsumer<String, JsonNode> eventReceiver;
        private final WatchIdentity identity;
        private final Restriction restriction;
        private final Queue<JsonNode> in = new ArrayBlockingQueue(1000);
        private final RepeatableTaskRunner sender = new RepeatableTaskRunner(executor,
              this::sendQueuedEvents);
        private Set<EventType> selectedEvents;
        private InotifySelector selector;
        private boolean isClosed;
        private boolean isOverflow;

        private InotifySelection(BiConsumer<String, JsonNode> receiver,
              InotifySelector selector, WatchIdentity id) throws CacheException,
              InterruptedException, NoRouteToCellException {
            this.eventReceiver = receiver;
            this.selector = selector;
            identity = id;
            restriction = RequestUser.getRestriction();
            selectedEvents = AddWatchFlag.asEventType(selector.flags());
        }

        public void accept(InotifySelector selector) throws CacheException,
              InterruptedException, NoRouteToCellException {
            if (selector.flags().contains(AddWatchFlag.IN_MASK_ADD)) {
                InotifySelector combinedSelector = new InotifySelector();
                combinedSelector.setPath(selector.getPath());
                combinedSelector.setFsPath(selector.getFsPath());

                EnumSet<AddWatchFlag> newFlags = EnumSet.copyOf(selector.flags());
                newFlags.addAll(this.selector.flags());
                newFlags.remove(AddWatchFlag.IN_MASK_ADD);
                combinedSelector.setSuppliedFlags(newFlags);

                this.selector = combinedSelector;
            } else {
                this.selector = selector;
            }
            selectedEvents = AddWatchFlag.asEventType(selector.flags());
        }

        @Override
        public String getId() {
            return identity.selectionId();
        }

        private Set<EventType> selectedEvents() {
            return selectedEvents;
        }

        @Override
        public JsonNode selector() {
            return mapper.convertValue(selector, JsonNode.class);
        }

        public synchronized void requestClose() {
            if (!isClosed) {
                queueEvent(EventStream.CLOSE_STREAM);
            }
        }

        @Override
        public void close() {
            boolean justClosed;

            synchronized (this) {
                justClosed = !isClosed;

                if (justClosed) {
                    queueEvent(IN_IGNORED_JSON);
                    isClosed = true;
                }
            }

            if (justClosed) {
                synchronized (Inotify.this) {
                    selectionByWatch.remove(identity);
                    selectionByPnfsId.remove(identity.pnfsid(), this);
                    try {
                        updateZK();
                    } catch (CacheException e) {
                        LOGGER.error("Unsubscription failed: {}", e.toString());
                    }
                }
            }
        }

        @GuardedBy("this")
        private void queueEvent(JsonNode json) {
            if (in.offer(json)) {
                sender.start();
            } else {
                LOGGER.warn("Dropping events: inotify receipient too slow");
                isOverflow = true;
            }
        }

        public synchronized boolean isSendableEvent(EventType type, String filename) {
            FsPath target = selector.getFsPath();

            boolean isRestricted = filename == null
                  ? restriction.isRestricted(READ_METADATA, target.parent(), target.name())
                  : restriction.isRestricted(READ_METADATA, target, filename);

            return !isClosed && selectedEvents.contains(type) && !isRestricted;
        }

        public synchronized boolean isClosed() {
            return isClosed;
        }

        private synchronized void acceptEvent(JsonNode json) {
            queueEvent(json);

            if (selector.flags().contains(AddWatchFlag.IN_ONESHOT)) {
                requestClose();
            }
        }

        private synchronized void acceptEventIfNotClosed(JsonNode json) {
            if (!isClosed) {
                acceptEvent(json);
            }
        }

        private synchronized void acceptEventIfSendable(EventType type, String filename,
              JsonNode json) {
            if (isSendableEvent(type, filename)) {
                acceptEvent(json);
            }
        }

        public synchronized List<JsonNode> drainQueuedEvents() {
            int count = in.size();
            if (isOverflow) {
                count++;
            }

            if (count == 0) {
                return Collections.emptyList();
            }

            List<JsonNode> todo = new ArrayList<>(count);
            todo.addAll(in);
            in.clear();
            if (isOverflow) {
                todo.add(IN_OVERFLOW_JSON);
                isOverflow = false;
            }

            return todo;
        }

        private void sendQueuedEvents() {
            LOGGER.debug("Starting send run");
            List<JsonNode> events = drainQueuedEvents();
            events.forEach(json -> {
                eventReceiver.accept(identity.selectionId(), json);
            });
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Inotify.class);
    private static final ObjectNode SELECTOR_SCHEMA = readFromJar(
          "/org/dcache/frontend/inotify/selector-schema.json");
    private static final ObjectNode EVENT_SCHEMA = readFromJar(
          "/org/dcache/frontend/inotify/event-schema.json");

    public static final JsonNode IN_IGNORED_JSON =
          new ObjectMapper().convertValue(new InotifyEventPayload(EventFlag.IN_IGNORED),
                JsonNode.class);
    public static final JsonNode IN_OVERFLOW_JSON =
          new ObjectMapper().convertValue(new InotifyEventPayload(EventFlag.IN_Q_OVERFLOW),
                JsonNode.class);

    private final Map<WatchIdentity, InotifySelection> selectionByWatch = new ConcurrentHashMap<>();
    private final SetMultimap<PnfsId, InotifySelection> selectionByPnfsId =
          Multimaps.synchronizedSetMultimap(MultimapBuilder.hashKeys().hashSetValues().build());
    private final ObjectMapper mapper = new ObjectMapper();

    private PnfsHandler pnfsHandler;
    private CuratorFramework curator;
    private String subscriptionPath;
    private PersistentNode subscriptions;
    private Executor executor;

    @Inject
    private PathMapper pathMapper;

    @Override
    public void setCuratorFramework(CuratorFramework client) {
        curator = client;
    }

    @Override
    public String eventType() {
        return "inotify";
    }

    @Override
    public String description() {
        return "notification of namespace activity, modelled after inotify(7)";
    }

    @Required
    public void setPnfsHandler(PnfsHandler handler) {
        pnfsHandler = handler;
    }

    @Required
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public ObjectNode selectorSchema() {
        return SELECTOR_SCHEMA;
    }

    @Override
    public ObjectNode eventSchema() {
        return EVENT_SCHEMA;
    }

    @Override
    public void setCellAddress(CellAddressCore address) {
        subscriptionPath = ZKPaths.makePath(INOTIFY_PATH, address.toString());
    }

    @Override
    public void afterStart() {
        try {
            curator.create().withMode(CreateMode.PERSISTENT).forPath(INOTIFY_PATH);
        } catch (NodeExistsException e) {
            } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unable to create 'inotify' node: " + e);
        }
    }


    private void updateZK() throws CacheException {
        try {
            if (selectionByPnfsId.isEmpty()) {
                if (subscriptions != null) {
                    LOGGER.debug("Deleting ZK node.");
                    subscriptions.close();
                    subscriptions = null;
                }
            } else {
                byte[] data = encodeSubscriptions();
                if (subscriptions == null) {
                    LOGGER.debug("Creating new ZK node with {} bytes.",
                          data.length);
                    subscriptions = new PersistentNode(curator,
                          CreateMode.EPHEMERAL, false, subscriptionPath, data);
                    subscriptions.start();
                    subscriptions.waitForInitialCreate(10, TimeUnit.SECONDS);
                } else {
                    LOGGER.debug("Updating data in ZK node to contain {} bytes.",
                          data.length);
                    subscriptions.setData(data);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            throw new CacheException("Failed to create ZooKeeper node: " + e);
        } catch (Exception e) {
            throw new CacheException("Failed to update ZooKeeper: " + e);
        }
    }

    private byte[] encodeSubscriptions() {
        Map<PnfsId, Collection<EventType>> selectedEvents = new HashMap<>();

        synchronized (selectionByPnfsId) {
            selectionByPnfsId.asMap().forEach((id, selections) -> {
                Collection<EventType> events = EnumSet.of(EventType.IN_DELETE_SELF);
                selections.forEach(s -> events.addAll(s.selectedEvents()));
                selectedEvents.put(id, events);
            });
        }

        return EventNotifier.toZkData(selectedEvents);
    }

    public void messageArrived(NotificationMessage message) {
        message.forEachEvent(this::acceptEvent);
    }

    private void acceptEvent(Event rawEvent) {
        LOGGER.debug("accepting event: {}", rawEvent);

        switch (rawEvent.getCategory()) {

            case "inotify":
                InotifyEvent inotifyEvent = (InotifyEvent) rawEvent;

                Collection<InotifySelection> targetSelections;
                if (inotifyEvent.getTarget() == null) {
                    targetSelections = selectionByWatch.values();
                } else {
                    synchronized (selectionByPnfsId) {
                        Set<InotifySelection> targetSelectionsView = selectionByPnfsId.get(
                              inotifyEvent.getTarget());
                        targetSelections = new ArrayList(targetSelectionsView);
                    }
                }
                sendEventToSelections(inotifyEvent, targetSelections);
                break;

            case "SYSTEM":
                SystemEvent systemEvent = (SystemEvent) rawEvent;
                JsonNode json = mapper.convertValue(new InotifyEventPayload(systemEvent),
                      JsonNode.class);
                selectionByWatch.values().forEach(s -> s.acceptEventIfNotClosed(json));
                break;
        }
    }

    private void sendEventToSelections(InotifyEvent event,
          Collection<InotifySelection> selections) {
        EventType type = event.getEventType();
        String filename = event.getName();

        if (selections.stream().anyMatch(s -> s.isSendableEvent(type, filename))) {
            JsonNode json = mapper.convertValue(new InotifyEventPayload(event),
                  JsonNode.class);
            selections.forEach(s -> s.acceptEventIfSendable(type, filename, json));
        }

        if (type.equals(EventType.IN_DELETE_SELF)) {
            selections.forEach(InotifySelection::requestClose);
        }
    }

    @Override
    public SelectionResult select(SelectionContext context, BiConsumer<String, JsonNode> receiver,
          JsonNode serialisedSelector) {
        try {
            InotifySelector selector = mapper.readerFor(InotifySelector.class)
                  .readValue(serialisedSelector);
            String clientPath = selector.getPath();
            FsPath dCachePath = pathMapper.asDcachePath(context.httpServletRequest(),
                  clientPath, PermissionDeniedCacheException::new);
            selector.setFsPath(dCachePath);
            return selector.validationError().orElseGet(() ->
                  select(context.channelId(), receiver, selector));
        } catch (PermissionDeniedCacheException e) {
            return SelectionResult.permissionDenied(e.getMessage());
        } catch (JsonMappingException e) {
            int index = e.getMessage().indexOf('\n');
            String msg = index == -1 ? e.getMessage() : e.getMessage().substring(0, index);
            return SelectionResult.badSelector("Bad selector value: %s", msg);
        } catch (IOException e) {
            return SelectionResult.badSelector("Unable to process selector: " + e.getMessage());
        }
    }

    private synchronized SelectionResult select(String channelId,
          BiConsumer<String, JsonNode> receiver,
          InotifySelector selector) {
        try {
            PnfsId pnfsid = lookup(selector);
            WatchIdentity watchId = new WatchIdentity(channelId, pnfsid);
            InotifySelection selection = selectionByWatch.get(watchId);
            if (selection == null) {
                selection = new InotifySelection(receiver, selector, watchId);
                selectionByWatch.put(watchId, selection);
                selectionByPnfsId.put(pnfsid, selection);
                updateZK();
                return SelectionResult.created(selection);
            } else {
                selection.accept(selector);
                updateZK();
                return SelectionResult.merged(selection);
            }
        } catch (PermissionDeniedCacheException e) {
            return SelectionResult.permissionDenied("Not allowed.");
        } catch (FileNotFoundCacheException e) {
            return SelectionResult.resourceNotFound("Problem with path: " + e.getMessage());
        } catch (NotDirCacheException e) {
            return SelectionResult.conditionFailed(e.getMessage());
        } catch (CacheException | NoRouteToCellException e) {
            return SelectionResult.internalError(e.getMessage());
        } catch (InterruptedException e) {
            return SelectionResult.internalError("Service going down");
        }
    }

    private PnfsId lookup(InotifySelector selector) throws CacheException {
        FsPath path = selector.getFsPath();
        PnfsHandler handler = new PnfsHandler(pnfsHandler,
              RequestUser.getSubject(), RequestUser.getRestriction());

        FileAttributes attr = handler.getFileAttributes(path, EnumSet.of(PNFSID, TYPE));

        if (attr.getFileType() == FileType.DIR) {
            handler.getFileAttributes(path.toString(),
                  EnumSet.noneOf(FileAttribute.class),
                  EnumSet.of(AccessMask.LIST_DIRECTORY),
                  false);
        } else {
            if (selector.flags().contains(AddWatchFlag.IN_ONLYDIR)) {
                throw new NotDirCacheException("IN_ONLYDIR with " + attr.getFileType() + " target");
            }

            handler.getFileAttributes(path.toString(),
                  EnumSet.noneOf(FileAttribute.class),
                  EnumSet.of(AccessMask.READ_DATA),
                  false);
        }

        return attr.getPnfsId();
    }
}
