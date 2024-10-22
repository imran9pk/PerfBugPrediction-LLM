package org.jkiss.dbeaver.model.gis;


import org.locationtech.jts.geom.Geometry;

public class GisTransformRequest {

    private final Geometry sourceValue;
    private Geometry targetValue;
    private final int sourceSRID;
    private int targetSRID;

    private boolean showOnMap;

    public GisTransformRequest(Geometry sourceValue, int sourceSRID, int targetSRID) {
        this.sourceValue = sourceValue;
        this.sourceSRID = sourceSRID;
        this.targetSRID = targetSRID;
    }

    public Geometry getSourceValue() {
        return sourceValue;
    }

    public Geometry getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(Geometry targetValue) {
        this.targetValue = targetValue;
        if (this.targetValue != null) {
            this.targetSRID = this.targetValue.getSRID();
        } else {
            this.targetSRID = 0;
        }
    }

    public int getSourceSRID() {
        return sourceSRID;
    }

    public int getTargetSRID() {
        return targetSRID;
    }

    public void setTargetSRID(int targetSRID) {
        this.targetSRID = targetSRID;
    }

    public boolean isShowOnMap() {
        return showOnMap;
    }

    public void setShowOnMap(boolean showOnMap) {
        this.showOnMap = showOnMap;
    }
}
