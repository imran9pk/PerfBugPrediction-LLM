package com.google.javascript.jscomp.graph;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Set;

@GwtCompatible
public interface UnionFind<E> {

  public void add(E e);

  @CanIgnoreReturnValue
  public E union(E a, E b);

  public E find(E e);

  public boolean areEquivalent(E a, E b);

  public Set<E> elements();

  public ImmutableList<ImmutableSet<E>> allEquivalenceClasses();

  public Set<E> findAll(final E value);

}
