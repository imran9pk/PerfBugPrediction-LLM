package org.dcache.auth.attributes;

import java.io.Serializable;

public class Role implements LoginAttribute, Serializable {

    private static final long serialVersionUID = 1L;

    private final String _name;

    public Role(String name) {
        _name = name;
    }

    public String getRole() {
        return _name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Role)) {
            return false;
        }
        Role other = (Role) obj;
        return _name.equals(other._name);
    }

    @Override
    public int hashCode() {
        return _name.hashCode();
    }

    @Override
    public String toString() {
        return "Role[" + _name + ']';
    }
}
