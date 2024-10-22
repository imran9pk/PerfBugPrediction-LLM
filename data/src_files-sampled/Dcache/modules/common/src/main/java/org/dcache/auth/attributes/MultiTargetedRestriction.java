package org.dcache.auth.attributes;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import diskCacheV111.util.FsPath;
import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Collectors;

public class MultiTargetedRestriction implements Restriction {

    private static final EnumSet<Activity> ALLOWED_PARENT_ACTIVITIES
          = EnumSet.of(Activity.LIST, Activity.READ_METADATA);

    public static class Authorisation implements Serializable, Comparable<Authorisation> {

        private static final long serialVersionUID = 1L;

        private final EnumSet<Activity> activities;
        private final FsPath path;

        public Authorisation(Collection<Activity> activities, FsPath path) {
            this.activities = EnumSet.copyOf(activities);
            this.path = path;
        }

        public EnumSet<Activity> getActivity() {
            return activities;
        }

        public FsPath getPath() {
            return path;
        }

        @Override
        public int hashCode() {
            return activities.hashCode() ^ path.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof Authorisation)) {
                return false;
            }

            Authorisation otherAuthorisation = (Authorisation) other;
            return otherAuthorisation.activities.equals(activities)
                  && otherAuthorisation.path.equals(path);
        }

        @Override
        public int compareTo(Authorisation other) {
            return ComparisonChain.start()
                  .compare(this.path, other.path, Ordering.usingToString())
                  .compare(this.activities, other.activities, Ordering.natural().lexicographical())
                  .result();
        }

        @Override
        public String toString() {
            return "Authorisation{allowing " + activities + " on " + path + "}";
        }
    }

    private final Collection<Authorisation> authorisations;

    public MultiTargetedRestriction(Collection<Authorisation> authorisations) {
        this.authorisations = authorisations.stream().sorted().collect(toImmutableList());
    }

    @Override
    public boolean hasUnrestrictedChild(Activity activity, FsPath parent) {
        for (Authorisation authorisation : authorisations) {
            FsPath allowedPath = authorisation.getPath();
            EnumSet<Activity> allowedActivity = authorisation.getActivity();

            if (allowedActivity.contains(activity) &&
                  (allowedPath.hasPrefix(parent) || parent.hasPrefix(allowedPath))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath path) {
        for (Authorisation authorisation : authorisations) {
            FsPath allowedPath = authorisation.getPath();
            EnumSet<Activity> allowedActivity = authorisation.getActivity();
            if (allowedActivity.contains(activity) && path.hasPrefix(allowedPath)) {
                return false;
            }

            if (ALLOWED_PARENT_ACTIVITIES.contains(activity) && allowedPath.hasPrefix(path)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath directory, String child) {
        return isRestricted(activity, directory.child(child));
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof MultiTargetedRestriction)) {
            return false;
        }

        return ((MultiTargetedRestriction) other).authorisations.equals(authorisations);
    }

    @Override
    public int hashCode() {
        return authorisations.hashCode();
    }

    private boolean subsumes(MultiTargetedRestriction other) {
        return authorisations.stream()
              .allMatch(ap -> other.hasAuthorisationSubsumedBy(ap));
    }

    private boolean hasAuthorisationSubsumedBy(Authorisation other) {
        EnumSet<Activity> disallowedOtherActivities = EnumSet.complementOf(other.activities);
        return authorisations.stream()
              .anyMatch(
                    ap -> disallowedOtherActivities.containsAll(EnumSet.complementOf(ap.activities))
                          && other.getPath().hasPrefix(ap.getPath()));
    }

    @Override
    public boolean isSubsumedBy(Restriction other) {
        if (other instanceof MultiTargetedRestriction) {
            return ((MultiTargetedRestriction) other).subsumes(this);
        }

        return false;
    }

    @Override
    public String toString() {
        return authorisations.stream()
              .map(Object::toString)
              .collect(Collectors.joining(", ", "MultiTargetedRestriction[", "]"));
    }
}
