import com.google.common.collect.Lists;

import java.util.List;

/**
* @author patrick
*/
public class DocumentStatistics implements Comparable<DocumentStatistics> {
    private String document;
    private int numberOfOccurrences;
    private List<Integer> ranks;
    private List<Double> distances;
    private double averageRank = -1.0;
    private double averageDistance = -1.0;

    public DocumentStatistics(String document)
    {
        this.document = document;
        numberOfOccurrences = 0;
        ranks = Lists.newArrayList();
        distances = Lists.newArrayList();
    }

    public String getDocument() {
        return document;
    }

    public void addState(int rank, double distance) {
        numberOfOccurrences++;
        ranks.add(rank);
        distances.add(distance);
    }

    public double getAverageRank() {
        if (averageRank > -1) return averageRank;

        averageRank = 0;

        for (Integer rank : ranks)
            averageRank += rank;

        averageRank /= ranks.size();
        ranks = null;

        return averageRank;
    }

    public double getAverageDistance() {
        if (averageDistance > -1) return averageDistance;

        averageDistance = 0;

        for (Double distance : distances)
            averageDistance += distance;

        averageDistance /= distances.size();
        distances = null;

        return averageDistance;
    }

    public int getNumberOfOccurrences() {
        return numberOfOccurrences;
    }

    @Override
    public int compareTo(DocumentStatistics o) {
        int result;

        result = o.getNumberOfOccurrences() - getNumberOfOccurrences();
        if(result != 0.0) return result;

        result = Double.compare(getAverageRank(), o.getAverageRank());
        if(result != 0.0) return result;

        result = Double.compare(getAverageDistance(), o.getAverageDistance());
        return result;
    }
}
