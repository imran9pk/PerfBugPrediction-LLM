package dmg.cells.services.login.user;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public interface TopDownUserRelationable {

    Enumeration<String> getContainers();

    void createContainer(String container)
          throws DatabaseException;

    Enumeration<String> getElementsOf(String container)
          throws NoSuchElementException;

    boolean isElementOf(String container, String element)
          throws NoSuchElementException;

    void addElement(String container, String element)
          throws NoSuchElementException;

    void removeElement(String container, String element)
          throws NoSuchElementException;

    void removeContainer(String container)
          throws NoSuchElementException,
          DatabaseException;
}
