/**
 * Created by IntelliJ IDEA.
 * User: Ben
 * Date: 09.05.11
 * Time: 01:26
 * To change this template use File | Settings | File Templates.
 */
public class DistanceStatistics {
    private double minDistance;
    private double maxDistance;
    private double avgDistance;

    public DistanceStatistics(double minDistance, double maxDistance, double avgDistance) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.avgDistance = avgDistance;
    }

    public double getMinDistance() {
        return minDistance;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public double getAvgDistance() {
        return avgDistance;
    }
}
