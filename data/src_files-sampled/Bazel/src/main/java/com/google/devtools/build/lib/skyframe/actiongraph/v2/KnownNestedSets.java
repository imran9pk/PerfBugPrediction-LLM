package com.google.devtools.build.lib.skyframe.actiongraph.v2;

import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.AnalysisProtosV2.DepSetOfFiles;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import java.io.IOException;

public class KnownNestedSets extends BaseCache<Object, DepSetOfFiles> {
  private final KnownArtifacts knownArtifacts;

  KnownNestedSets(AqueryOutputHandler aqueryOutputHandler, KnownArtifacts knownArtifacts) {
    super(aqueryOutputHandler);
    this.knownArtifacts = knownArtifacts;
  }

  @Override
  protected Object transformToKey(Object nestedSet) {
    return ((NestedSet) nestedSet).toNode();
  }

  @Override
  DepSetOfFiles createProto(Object nestedSetObject, int id) throws IOException {
    NestedSet<?> nestedSet = (NestedSet) nestedSetObject;
    DepSetOfFiles.Builder depSetBuilder = DepSetOfFiles.newBuilder().setId(id);
    for (NestedSet<?> succ : nestedSet.getNonLeaves()) {
      depSetBuilder.addTransitiveDepSetIds(this.dataToIdAndStreamOutputProto(succ));
    }
    for (Object elem : nestedSet.getLeaves()) {
      depSetBuilder.addDirectArtifactIds(
          knownArtifacts.dataToIdAndStreamOutputProto((Artifact) elem));
    }
    return depSetBuilder.build();
  }

  @Override
  void toOutput(DepSetOfFiles depSetOfFilesProto) throws IOException {
    aqueryOutputHandler.outputDepSetOfFiles(depSetOfFilesProto);
  }
}
