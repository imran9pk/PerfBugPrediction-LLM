package org.elasticsearch.common.util.concurrent;


import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SizeBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

    private final BlockingQueue<E> queue;
    private final int capacity;

    private final AtomicInteger size = new AtomicInteger();

    public SizeBlockingQueue(BlockingQueue<E> queue, int capacity) {
        assert capacity >= 0;
        this.queue = queue;
        this.capacity = capacity;
    }

    @Override
    public int size() {
        return size.get();
    }

    public int capacity() {
        return this.capacity;
    }

    @Override
    public Iterator<E> iterator() {
        final Iterator<E> it = queue.iterator();
        return new Iterator<E>() {
            E current;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public E next() {
                current = it.next();
                return current;
            }

            @Override
            public void remove() {
                if (queue.remove(current)) {
                    size.decrementAndGet();
                }
            }
        };
    }

    @Override
    public E peek() {
        return queue.peek();
    }

    @Override
    public E poll() {
        E e = queue.poll();
        if (e != null) {
            size.decrementAndGet();
        }
        return e;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = queue.poll(timeout, unit);
        if (e != null) {
            size.decrementAndGet();
        }
        return e;
    }

    @Override
    public boolean remove(Object o) {
        boolean v = queue.remove(o);
        if (v) {
            size.decrementAndGet();
        }
        return v;
    }

    public void forcePut(E e) throws InterruptedException {
        size.incrementAndGet();
        try {
            queue.put(e);
        } catch (InterruptedException ie) {
            size.decrementAndGet();
            throw ie;
        }
    }


    @Override
    public boolean offer(E e) {
        while (true) {
            final int current = size.get();
            if (current >= capacity()) {
                return false;
            }
            if (size.compareAndSet(current, 1 + current)) {
                break;
            }
        }
        boolean offered = queue.offer(e);
        if (!offered) {
            size.decrementAndGet();
        }
        return offered;
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw new IllegalStateException("offer with timeout not allowed on size queue");
    }

    @Override
    public void put(E e) throws InterruptedException {
        throw new IllegalStateException("put not allowed on size queue");
    }

    @Override
    public E take() throws InterruptedException {
        E e;
        try {
            e = queue.take();
            size.decrementAndGet();
        } catch (InterruptedException ie) {
            throw ie;
        }
        return e;
    }

    @Override
    public int remainingCapacity() {
        return capacity() - size.get();
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        int v = queue.drainTo(c);
        size.addAndGet(-v);
        return v;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        int v = queue.drainTo(c, maxElements);
        size.addAndGet(-v);
        return v;
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return (T[]) queue.toArray(a);
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return queue.containsAll(c);
    }
}
