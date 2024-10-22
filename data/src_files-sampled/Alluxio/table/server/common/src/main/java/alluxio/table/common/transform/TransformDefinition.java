package alluxio.table.common.transform;

import alluxio.table.common.transform.action.TransformAction;
import alluxio.table.common.transform.action.TransformActionRegistry;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class TransformDefinition {
  private final String mDefinition;
  private final List<TransformAction> mActions;
  private final Properties mProperties;

  private TransformDefinition(String definition, List<TransformAction> actions,
                              Properties properties) {
    mDefinition = normalize(definition);
    mActions = actions;
    mProperties = properties;
  }

  private String normalize(String definition) {
    definition = definition.trim();
    if (definition.endsWith(";")) {
      definition = definition.substring(0, definition.length() - 1);
    }
    return definition.toLowerCase();
  }

  public String getDefinition() {
    return mDefinition;
  }

  public List<TransformAction> getActions() {
    return mActions;
  }

  public Properties getProperties() {
    return mProperties;
  }

  public static TransformDefinition parse(String definition) {
    definition = definition.trim();

    if (definition.isEmpty()) {
      return new TransformDefinition(definition, Collections.emptyList(), new Properties());
    }

    definition = definition.replace(";", "\n");

    final Properties properties = new Properties();

    final StringReader reader = new StringReader(definition);

    try {
      properties.load(reader);
    } catch (IOException e) {
      return new TransformDefinition(definition, Collections.emptyList(), properties);
    }

    final List<TransformAction> actions = TransformActionRegistry.create(properties);

    return new TransformDefinition(definition, actions, properties);
  }
}
