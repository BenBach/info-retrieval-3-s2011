/**
 * Created by IntelliJ IDEA.
 * User: Ben
 * Date: 09.05.11
 * Time: 03:36
 * To change this template use File | Settings | File Templates.
 */
public class DocumentOccurrences {
     private String documentName;
     private int occurrences;

    public DocumentOccurrences(String documentName, int occurrences) {
        this.documentName = documentName;
        this.occurrences = occurrences;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(int occurrences) {
        this.occurrences = occurrences;
    }

    public void increaseOccurrence()
    {
        this.occurrences++;
    }
}
