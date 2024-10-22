package io.crate.planner.optimizer.matcher;

import java.util.NoSuchElementException;

public class Captures {

    private static final Captures NIL = new Captures(null, null, null);

    private final Capture<?> capture;
    private final Object value;
    private final Captures tail;

    private Captures(Capture<?> capture, Object value, Captures tail) {
        this.capture = capture;
        this.value = value;
        this.tail = tail;
    }

    public static Captures empty() {
        return NIL;
    }

    public static <T> Captures of(Capture<T> capture, T value) {
        return new Captures(capture, value, NIL);
    }

    public <T> T get(Capture<T> capture) {
        if (this.equals(NIL)) {
            throw new NoSuchElementException("Requested value for unknown Capture");
        } else if (this.capture.equals(capture)) {
            return (T) value;
        } else {
            return tail.get(capture);
        }
    }

    public Captures add(Captures other) {
        if (this.equals(NIL)) {
            return other;
        } else {
            return new Captures(capture, value, tail.add(other));
        }
    }
}
