import com.google.common.base.Objects;

/**
* @author patrick
*/
public class DocumentSimilarity implements Comparable<DocumentSimilarity> {
    private double distance;
    private int rank;
    private String targetDocument;
    private String index;

    public DocumentSimilarity(double distance, String targetDocument, String index) {
        this.distance = distance;
        this.targetDocument = targetDocument;
        this.index = index;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getDistance() {
        return distance;
    }

    public String getTargetDocument() {
        return targetDocument;
    }

    public String getIndex() {
        return index;
    }

    @Override
    public int compareTo(DocumentSimilarity o) {
        return Double.compare(distance, o.distance);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(distance, targetDocument, index);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!obj.getClass().equals(getClass())) return false;
        DocumentSimilarity other = (DocumentSimilarity) obj;

        return Objects.equal(distance, other.distance) &&
                Objects.equal(rank, other.rank) &&
                Objects.equal(targetDocument, other.targetDocument) &&
                Objects.equal(index, other.index);
    }
}
