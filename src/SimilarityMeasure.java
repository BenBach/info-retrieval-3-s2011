import weka.core.DistanceFunction;
import weka.core.EuclideanDistance;
import weka.core.ManhattanDistance;

/**
* @author patrick
*/
public enum SimilarityMeasure {
    L1(new ManhattanDistance()),
    L2(new EuclideanDistance());

    private DistanceFunction distanceFunction;

    SimilarityMeasure(DistanceFunction distanceFunction) {
        this.distanceFunction = distanceFunction;
    }

    public DistanceFunction getDistanceFunction() {
        return distanceFunction;
    }
}
