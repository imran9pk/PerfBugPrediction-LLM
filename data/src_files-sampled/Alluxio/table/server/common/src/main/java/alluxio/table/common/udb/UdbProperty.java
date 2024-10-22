package alluxio.table.common.udb;

import alluxio.table.common.BaseProperty;
import alluxio.table.common.ConfigurationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdbProperty extends BaseProperty {
  private static final Logger LOG = LoggerFactory.getLogger(UdbProperty.class);

  public UdbProperty(String name, String description, String defaultValue) {
    super(name, description, defaultValue);
  }

  public String getFullName(String udbType) {
    return String.format("%s%s", ConfigurationUtils.getUdbPrefix(udbType), mName);
  }
}
