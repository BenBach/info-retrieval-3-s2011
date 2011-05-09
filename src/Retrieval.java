import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import org.apache.tools.ant.DirectoryScanner;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class Retrieval {
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    @Option(name = "-i", aliases = {"--index"}, multiValued = true, required = false, usage = "the indices to be used")
    private List<String> indicesNames;
    private List<File> indices;

    @Option(name = "-k", required = false, usage = "the number k of to-be-retrieved documents")
    private int k = 7;
    @Option(name = "-m", aliases = {"--measure"}, required = false,
            usage = "the similarity function to be used for similarity retrieval")
    private SimilarityMeasure similarityMeasure = SimilarityMeasure.L1;
    private Attribute classAttribute = null;
    private Attribute documentAttribute = null;

    @SuppressWarnings({"ConstantConditions"})
    public void run() throws Exception {
        setupIndices();
        printProgramStatus();

        // build a table: query / index -> {similarity}
        Table<String, String, List<DocumentSimilarity>> table = HashBasedTable.create();

        for (File indexFile : indices) {
            ConverterUtils.DataSource source = new ConverterUtils.DataSource(indexFile.getAbsolutePath());
            Instances indexInstances = source.getDataSet();

            classAttribute = null;
            documentAttribute = null;

            Enumeration attributes = indexInstances.enumerateAttributes();
            while (attributes.hasMoreElements()) {
                Attribute attribute = (Attribute) attributes.nextElement();

                if (classAttribute == null && attribute.name().matches(".*[Cc]lass.*") &&
                        (attribute.type() == Attribute.STRING || attribute.type() == Attribute.NOMINAL))
                    classAttribute = attribute;

                if (documentAttribute == null && attribute.name().matches(".*[Dd]ocument.*") &&
                        attribute.type() == Attribute.STRING)
                    documentAttribute = attribute;

                if (documentAttribute != null && classAttribute != null) break;
            }

            if (classAttribute == null) {
                System.err.println("No class attribute found for index " + indexFile);
                System.err.println("Aborting");
                System.exit(1);
            }

            if (documentAttribute == null) {
                System.err.println("No document attribute found for index " + indexFile);
                System.err.println("Aborting");
                System.exit(1);
            }

            System.err.println("index " + indexFile.getName());
            System.err.println("    class: " + classAttribute.name());
            System.err.println("    document: " + documentAttribute.name());

            List<Instance> documentVectors = Lists.newLinkedList();

            Enumeration instances = indexInstances.enumerateInstances();
            while (instances.hasMoreElements()) {
                Instance instance = (Instance) instances.nextElement();

                // String document = getInstanceName(instance);
                //if (!queryDocuments.contains(document)) continue;

                documentVectors.add(instance);
            }

            similarityMeasure.getDistanceFunction().setInstances(indexInstances);

            // calculate distance to all other documents in the index file
            instances = indexInstances.enumerateInstances();
            while (instances.hasMoreElements()) {
                Instance instance = (Instance) instances.nextElement();
                String instanceName = getInstanceName(instance);
                String className = getClassName(instance);

                for (Instance queryInstance : documentVectors) {
                    String queryInstanceName = getInstanceName(queryInstance);

                    // skip same document
                    if (instanceName.equals(queryInstanceName))
                        continue;

                    double distance = similarityMeasure.getDistanceFunction().distance(queryInstance, instance);

                    List<DocumentSimilarity> similarities = table.get(queryInstanceName, indexFile.getName());

                    if (similarities == null) {
                        similarities = Lists.newArrayList();
                        table.put(queryInstanceName, indexFile.getName(), similarities);
                    }

                    DocumentSimilarity documentSimilarity =
                            new DocumentSimilarity(distance, instanceName, className, indexFile.getName());

                    similarities.add(documentSimilarity);
                }
            }

            for (Map.Entry<String, List<DocumentSimilarity>> stringListEntry : table.column(indexFile.getName())
                    .entrySet()) {
                List<DocumentSimilarity> documentSimilarities = stringListEntry.getValue();
                Collections.sort(documentSimilarities);

                for (int i = 0, documentSimilaritiesSize = documentSimilarities.size(); i < documentSimilaritiesSize;
                     i++) {
                    DocumentSimilarity documentSimilarity = documentSimilarities.get(i);
                    documentSimilarity.setRank(i + 1);
                }
            }
        }

        // document -> similarity PER CLASS
        Map<String, DocumentStatistics> documentToStatisticsPerClass = Maps.newHashMap();

        // document -> occurrence PER CLASS
        Map<String, DocumentOccurrences> docOccurrences = Maps.newHashMap();

        // document -> occurrence PER Feature List
        Map<String, Map<String, DocumentOccurrences>> docOccurrencesPerIndex = Maps.newHashMap();

        for (Map.Entry<String, Map<String, List<DocumentSimilarity>>> queryIndexMap : table.rowMap().entrySet()) {
            String query = queryIndexMap.getKey();
            Map<String, List<DocumentSimilarity>> indexResults = queryIndexMap.getValue();

            System.out.println("\n\nquery: " + query);

            System.out.print("rank ");
            for (File index : indices) {
                System.out.print(String.format("%-41.41s ", index.getName()));
            }

            System.out.println();

            System.out.print("-----+");

            for (int i = 0, indicesSize = indices.size(); i < indicesSize; i++) {
                for (int j = 0; j < 41; j++)
                    System.out.print('-');
                System.out.print('+');
            }

            System.out.println();

            // document -> similarity
            Multimap<String, DocumentSimilarity> documentToSimilarity = HashMultimap.create();

            // document -> similarity - Merged
            ArrayList<DocumentSimilarity> documentToSimilarityMerged = Lists.newArrayList();

            // index -> similarity
            Map<String, DocumentStatistics> indexStatistics = Maps.newHashMap();

            for (int i = 0; i < k; i++) {
                System.out.print(String.format("#%3d ", i));

                for (File index : indices) {
                    List<DocumentSimilarity> documentSimilarities = indexResults.get(index.getName());
                    DocumentStatistics statisticsPerIndex = indexStatistics.get(index.toString());

                    if (statisticsPerIndex == null)
                        statisticsPerIndex = new DocumentStatistics(index.toString());

                    if (documentSimilarities == null) {
                        System.err.println("No similarities for index " + index.getName());
                        for (int j = 0; j < 42; j++) System.out.print(' ');
                        continue;
                    }

                    for (DocumentSimilarity documentSimilarity : documentSimilarities)
                        documentToSimilarity.put(documentSimilarity.getTargetDocument(), documentSimilarity);

                    DocumentSimilarity similarity = documentSimilarities.get(i);

                    // Add to occurrence per index
                    Map<String, DocumentOccurrences> occurrencesPerFeature =
                            docOccurrencesPerIndex.get(index.toString());

                    if (occurrencesPerFeature == null)
                        occurrencesPerFeature = Maps.newHashMap();

                    DocumentOccurrences occurrence = occurrencesPerFeature.get(similarity.getTargetDocument());

                    if (occurrence == null)
                        occurrence = new DocumentOccurrences(similarity.getTargetDocument(), 0);

                    occurrence.increaseOccurrence();

                    occurrencesPerFeature.put(similarity.getTargetDocument(), occurrence);
                    docOccurrencesPerIndex.put(index.toString(), occurrencesPerFeature);

                    System.out.print(String.format("%-30.30s %10.6f ", similarity.getTargetDocument(),
                            similarity.getDistance()));

                    statisticsPerIndex.addState(0, similarity.getDistance());
                    indexStatistics.put(index.toString(), statisticsPerIndex);
                }

                System.out.println();
            }

            String queryClass = query.split("/", 2)[0];

            for (File index : indices) {
                List<DocumentSimilarity> documentSimilarities = indexResults.get(index.getName());

                for (DocumentSimilarity documentSimilarity : documentSimilarities) {
                    if (documentSimilarity.getClassName().equals(queryClass)) {
                        DocumentStatistics statisticsPerClass = documentToStatisticsPerClass.get(queryClass);

                        if (statisticsPerClass == null)
                            statisticsPerClass = new DocumentStatistics(queryClass);

                        statisticsPerClass.addState(0, documentSimilarity.getDistance());
                        documentToStatisticsPerClass.put(queryClass, statisticsPerClass);
                    }
                }
            }

            System.out.println("Distances:");

            double minDist = 0.0;
            double maxDist = 0.0;
            double avgDist = 0.0;
            int numberIndices = 0;

            HashMap<String, DistanceStatistics> distStats = Maps.newHashMap();

            for (DocumentStatistics statsForOneIndex : indexStatistics.values()) {
                numberIndices++;

                System.out.println("Index: " + statsForOneIndex.getDocument() +
                        " Min: " + statsForOneIndex.getMinDistance() +
                        " Max: " + statsForOneIndex.getMaxDistance() +
                        " Avg: " + statsForOneIndex.getAverageDistance());

                minDist += statsForOneIndex.getMinDistance();
                maxDist += statsForOneIndex.getMaxDistance();
                avgDist += statsForOneIndex.getAverageDistance();

                distStats.put(statsForOneIndex.getDocument(),
                        new DistanceStatistics(statsForOneIndex.getMinDistance(), statsForOneIndex.getMaxDistance(),
                                statsForOneIndex.getAverageDistance()));
            }

            minDist = minDist / numberIndices;
            maxDist = maxDist / numberIndices;
            avgDist = avgDist / numberIndices;

            System.out.println("Average - " +
                    " Min: " + minDist +
                    " Max: " + maxDist +
                    " Avg: " + avgDist);

            System.out.println();
            System.out.println();
            System.out.println();

            // Merged Result List

            for (File index : indices) {
                List<DocumentSimilarity> documentSimilarities = indexResults.get(index.getName());

                for (DocumentSimilarity documentSimilarity : documentSimilarities) {
                    if (documentSimilarity.getRank() <= k) {
                        documentSimilarity.setDistance(distanceNormalization(documentSimilarity.getDistance(),
                                distStats.get(index.toString()).getMinDistance(),
                                distStats.get(index.toString()).getMaxDistance()));

                        if (documentSimilarity.getDistance() <= 1.0 && documentSimilarity.getDistance() >= 0.0)
                            documentToSimilarityMerged.add(documentSimilarity);
                    }
                }
            }

            // remove duplicates
            List<Integer> toDelete = new ArrayList<Integer>(documentToSimilarityMerged.size());

            for (int c = 0; c < documentToSimilarityMerged.size(); c++) {
                DocumentSimilarity sim1 = documentToSimilarityMerged.get(c);

                for (int d = c + 1; d < documentToSimilarityMerged.size(); d++) {
                    DocumentSimilarity sim2 = documentToSimilarityMerged.get(d);

                    if (sim1.getTargetDocument().equals(sim2.getTargetDocument())) {
                        if (sim1.getDistance() < sim2.getDistance()) {
                            if (!toDelete.contains(d)) toDelete.add(d);
                        } else {
                            if (!toDelete.contains(c)) toDelete.add(c);
                        }
                    }
                }
            }

            Collections.sort(toDelete);

            for (int c = 0; c < toDelete.size(); c++)
                documentToSimilarityMerged.remove(toDelete.get(c) - c);

            // sort
            Collections.sort(documentToSimilarityMerged, new Comparator<DocumentSimilarity>() {
                @Override
                public int compare(DocumentSimilarity o1, DocumentSimilarity o2) {
                    return Double.compare(o1.getDistance(), o2.getDistance());
                }
            });

            for (DocumentSimilarity sim : documentToSimilarityMerged) {
                DocumentOccurrences value = docOccurrences.get(sim.getTargetDocument());

                if (value == null)
                    value = new DocumentOccurrences(sim.getTargetDocument(), 0);

                value.increaseOccurrence();
                docOccurrences.put(sim.getTargetDocument(), value);
            }

            // ------------------
            System.out.println("\nMerged Result List:");

            for (int c = 0; c < k; c++) {
                DocumentSimilarity sim = documentToSimilarityMerged.get(c);
                System.out.println(String.format("%d %-70.70s %10.6f ", c + 1, sim.getTargetDocument(),
                        sim.getDistance()));
            }

            System.out.println();

            System.out.println(String.format("\n%-70.70s %-7.7s %-15.15s %-15.15s",
                    "document", "#occur", "avg rank", "avg dist"));
            List<DocumentStatistics> statistics = Lists.newArrayList();

            for (String document : documentToSimilarity.keySet()) {
                Collection<DocumentSimilarity> documentSimilarities = documentToSimilarity.get(document);
                DocumentStatistics stats = new DocumentStatistics(document);

                for (DocumentSimilarity documentSimilarity : documentSimilarities)
                    stats.addState(documentSimilarity.getRank(), documentSimilarity.getDistance());

                statistics.add(stats);
            }

            Collections.sort(statistics, new Comparator<DocumentStatistics>() {
                @Override
                public int compare(DocumentStatistics o1, DocumentStatistics o2) {
                    int result = Double.compare(o1.getAverageRank(), o2.getAverageRank());

                    if (result != 0.0) return result;

                    result = Double.compare(o1.getAverageDistance(), o2.getAverageDistance());

                    if (result != 0.0) return result;

                    return o2.getNumberOfOccurrences() - o1.getNumberOfOccurrences();
                }
            });

            for (DocumentStatistics stats : statistics.subList(0, Math.min(k * 5, statistics.size())))
                System.out.println(String.format("%-70.70s %7d %15.3f %15.3f",
                        stats.getDocument(), stats.getNumberOfOccurrences(), stats.getAverageRank(),
                        stats.getAverageDistance()));

            for (DocumentStatistics stats : statistics.subList(Math.min(k * 5, statistics.size()), statistics.size()))
                if (stats.getNumberOfOccurrences() < indices.size())
                    System.out.println(String.format("%-70.70s %7d %15.3f %15.3f",
                            stats.getDocument(), stats.getNumberOfOccurrences(), stats.getAverageRank(),
                            stats.getAverageDistance()));
        }

        // Print average distances
        System.out.println("\n\n\n Distances per class:");
        for (DocumentStatistics stat : documentToStatisticsPerClass.values())
            System.out.println("Class: " + stat.getDocument() +
                    " Min: " + stat.getMinDistance() +
                    " Max: " + stat.getMaxDistance() +
                    " Avg: " + stat.getAverageDistance());

        // Print Occurrences
        System.out.println("\n\n\nOccurrences of documents in other result lists:");

        ArrayList<DocumentOccurrences> occurrences = new ArrayList<DocumentOccurrences>(docOccurrences.values());
        Collections.sort(occurrences, new Comparator<DocumentOccurrences>() {
            @Override
            public int compare(DocumentOccurrences o1, DocumentOccurrences o2) {
                return Double.compare(o1.getOccurrences(), o2.getOccurrences());
            }
        });

        for (DocumentOccurrences occurrence : occurrences)
            System.out.println("Document: " + occurrence.getDocumentName() +
                    "Occurrences: " + occurrence.getOccurrences());

        // Print Occurrences per index
        System.out.println("\n\n\nOccurrences of documents in feature lists:");

        for (File index : indices) {
            Map<String, DocumentOccurrences> occurrencesPerFeature = docOccurrencesPerIndex.get(index.toString());

            if (occurrencesPerFeature != null) {
                List<DocumentOccurrences> occurrencesForOneFeature =
                        new ArrayList<DocumentOccurrences>((occurrencesPerFeature.values()));

                Collections.sort(occurrencesForOneFeature, new Comparator<DocumentOccurrences>() {
                    @Override
                    public int compare(DocumentOccurrences o1, DocumentOccurrences o2) {
                        return Double.compare(o1.getOccurrences(), o2.getOccurrences());
                    }
                });

                System.out.println("Occurrence of Documents in feature " + index.toString() + ":");

                for (DocumentOccurrences occurrence : occurrences) {
                    System.out.println("Document: " + occurrence.getDocumentName() +
                            "Occurrences: " + occurrence.getOccurrences());
                }

                System.out.println();
            }
        }
    }

    private String getInstanceName(Instance instance) {
        return instance.toString(classAttribute) + "/" + instance.toString(documentAttribute);
    }

    private String getClassName(Instance instance) {
        return instance.toString(classAttribute);
    }

    private double distanceNormalization(double distance, double distanceMin, double distanceMax) {
        return ((distance - distanceMin) / (distanceMax - distanceMin));
    }

    private void setupIndices() {
        String workingDirectory = System.getProperty("user.dir");
        File file = new File(workingDirectory);

        if (indicesNames == null || indicesNames.size() == 0) {
            indices = Arrays.asList(file.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".arff") || name.endsWith(".arff.gz");
                }
            }));
        } else {
            indices = Lists.newLinkedList();

            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(file);
            directoryScanner.setCaseSensitive(true);

            for (String indexName : indicesNames) {
                Iterable<String> subIndicesNames =
                        Splitter.on(CharMatcher.is(',')).omitEmptyStrings().trimResults().split(
                                indexName);

                List<String> subIndices = Lists.newArrayList(subIndicesNames);

                for (String subIndice : subIndices) {
                    System.out.println("subindex " + subIndice);
                }

                directoryScanner.setIncludes(subIndices.toArray(new String[subIndices.size()]));
                directoryScanner.scan();
                String[] fileNames = directoryScanner.getIncludedFiles();

                for (String fileName : fileNames) {
                    if (!fileName.endsWith(".arff") && !fileName.endsWith(".arrf.gz")) continue;

                    indices.add(new File(fileName));
                }
            }
        }

        if (indices.size() == 0) {
            System.err.println("No .arff files found in current directory, or no .arff files specified");
            System.exit(1);
        }
    }

    private void printProgramStatus() {
        System.out.println(String.format("k                 : %d", k));
        System.out.println(String.format("Similarity Measure: %s", similarityMeasure));
        System.out.println("Used inidices:");

        for (File indexFile : indices)
            System.out.println("\t" + indexFile.getName());
    }

    public static void main(String[] args) throws Exception {
        Retrieval retrieval = new Retrieval();
        CmdLineParser parser = new CmdLineParser(retrieval);
        parser.setUsageWidth(80); // width of the error display area

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("java Retrieval [options...] arguments...");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        }

        retrieval.run();
    }

}
