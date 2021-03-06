package uk.ac.ox.well.cortexjdk.commands.call.call;

import org.apache.commons.math3.util.Pair;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DirectedWeightedPseudograph;
import uk.ac.ox.well.cortexjdk.commands.Module;
import uk.ac.ox.well.cortexjdk.utils.alignment.kmer.KmerLookup;
import uk.ac.ox.well.cortexjdk.utils.arguments.Argument;
import uk.ac.ox.well.cortexjdk.utils.arguments.Output;
import uk.ac.ox.well.cortexjdk.utils.caller.Bubble;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexGraph;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexKmer;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexRecord;
import uk.ac.ox.well.cortexjdk.utils.io.table.TableReader;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeter;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeterFactory;
import uk.ac.ox.well.cortexjdk.utils.stoppingrules.BubbleClosingStopper;
import uk.ac.ox.well.cortexjdk.utils.traversal.*;

import java.io.File;
import java.io.PrintStream;
import java.util.*;

/**
 * Created by kiran on 23/06/2017.
 */
public class Call extends Module {
    @Argument(fullName="graph", shortName="g", doc="Graph")
    public CortexGraph GRAPH;

    @Argument(fullName="child", shortName="c", doc="Child")
    public String CHILD;

    @Argument(fullName="parents", shortName="p", doc="Parents")
    public ArrayList<String> PARENTS;

    @Argument(fullName="roi", shortName="r", doc="ROI")
    public CortexGraph ROI;

    @Argument(fullName="drafts", shortName="d", doc="Drafts")
    public HashMap<String, KmerLookup> LOOKUPS;

    @Argument(fullName="reference", shortName="R", doc="Reference")
    public KmerLookup REFERENCE;

    @Argument(fullName="annotations", shortName="a", doc="Annotated contigs")
    public File ANNOTATIONS;

    @Argument(fullName="window", shortName="w", doc="Window")
    public Integer WINDOW = 10;

    @Output
    public PrintStream out;

    @Override
    public void execute() {
        int childColor = GRAPH.getColorForSampleName(CHILD);
        List<Integer> parentColors = GRAPH.getColorsForSampleNames(PARENTS);

        Map<String, List<Map<String, String>>> allAnnotations = loadAnnotations();

        ProgressMeter pm = new ProgressMeterFactory()
                .header("Processing contigs")
                .message("contigs processed")
                .maxRecord(allAnnotations.size())
                .make(log);

        Map<CortexKmer, Boolean> rois = loadRois();

        TraversalEngine e = new TraversalEngineFactory()
                .traversalDirection(TraversalEngineConfiguration.TraversalDirection.BOTH)
                .combinationOperator(TraversalEngineConfiguration.GraphCombinationOperator.OR)
                .joiningColors(childColor)
                .stoppingRule(BubbleClosingStopper.class)
                .graph(GRAPH)
                .rois(ROI)
                .make();

        for (String contigName : allAnnotations.keySet()) {
            log.info("{}", contigName);

            DirectedWeightedPseudograph<CortexVertex, CortexEdge> gAlt = buildContigGraph(allAnnotations.get(contigName), 0, childColor);
            Map<String, Integer> vertexIndex = new HashMap<>();
            for (int i = 0; i < allAnnotations.get(contigName).size(); i++) {
                Map<String, String> m = allAnnotations.get(contigName).get(i);
                vertexIndex.put(m.get("kmer"), i);
            }

            for (int i = 0; i < allAnnotations.get(contigName).size(); i++) {
                Map<String, String> m = allAnnotations.get(contigName).get(i);

                if (m.get("code").equals(".")) {
                    CortexKmer novelKmer = new CortexKmer(m.get("kmer"));

                    Pair<Integer, Integer> novelStretchBoundaries = getNovelStretchBoundaries(allAnnotations.get(contigName), i);

                    log.info("  novel stretch: {} {}", novelStretchBoundaries.getFirst(), novelStretchBoundaries.getSecond());

                    if (novelStretchBoundaries.getFirst() > 0 && novelStretchBoundaries.getSecond() < allAnnotations.get(contigName).size() - 1) {
                        String backgroundStart = allAnnotations.get(contigName).get(novelStretchBoundaries.getFirst() - 1).get("background");
                        String backgroundStop = allAnnotations.get(contigName).get(novelStretchBoundaries.getSecond() + 1).get("background");
                        String regionStart = allAnnotations.get(contigName).get(novelStretchBoundaries.getFirst() - 1).get("code");
                        String regionStop = allAnnotations.get(contigName).get(novelStretchBoundaries.getSecond() + 1).get("code");

                        if (backgroundStart.equals(backgroundStop) && regionStart.equals(regionStop) && !backgroundStart.equals("NA")) {
                            int refColor = GRAPH.getColorForSampleName(backgroundStart);

                            e.getConfiguration().setTraversalColor(refColor);
                            e.getConfiguration().setPreviousTraversal(buildContigGraph(allAnnotations.get(contigName), novelStretchBoundaries.getSecond(), childColor));

                            int navBoundaryStart = novelStretchBoundaries.getFirst() - WINDOW >= 0 ? novelStretchBoundaries.getFirst() - WINDOW : 0;
                            int navBoundaryStop  = novelStretchBoundaries.getSecond() + WINDOW > allAnnotations.get(contigName).size() ? novelStretchBoundaries.getSecond() + WINDOW : allAnnotations.get(contigName).size();

                            String seed = allAnnotations.get(contigName).get(navBoundaryStart).get("kmer");

                            DirectedWeightedPseudograph<CortexVertex, CortexEdge> gRef = e.dfs(seed);

                            if (gRef != null) {
                                DirectedWeightedPseudograph<CortexVertex, CortexEdge> gSum = new DirectedWeightedPseudograph<>(CortexEdge.class);
                                Graphs.addGraph(gSum, gRef);
                                Graphs.addGraph(gSum, gAlt);

                                PathFinder dspRef = new PathFinder(gSum, refColor);
                                PathFinder dspAlt = new PathFinder(gSum, childColor);

                                List<CortexVertex> iss = new ArrayList<>();
                                List<CortexVertex> oss = new ArrayList<>();

                                for (int j = novelStretchBoundaries.getFirst() - 1; j > navBoundaryStart; j--) {
                                    String s = allAnnotations.get(contigName).get(j).get("kmer");
                                    //CortexVertex v = new CortexVertex(s, GRAPH.findRecord(new CortexKmer(s)));
                                    CortexVertex v = null; // todo fix

                                    if (TraversalEngine.outDegree(gSum, v) > 1) { oss.add(v); }
                                }

                                for (int j = novelStretchBoundaries.getSecond(); j < navBoundaryStop; j++) {
                                    String s = allAnnotations.get(contigName).get(j).get("kmer");
                                    //CortexVertex v = new CortexVertex(s, GRAPH.findRecord(new CortexKmer(s)));
                                    CortexVertex v = null; // todo fix

                                    if (TraversalEngine.inDegree(gSum, v) > 1) { iss.add(v); }
                                }

                                log.info("  {} {}", oss.size(), iss.size());

                                for (CortexVertex os : oss) {
                                    for (CortexVertex is : iss) {
                                        if (!os.equals(is)) {
                                            GraphPath<CortexVertex, CortexEdge> pRef = dspRef.getPathFinder(os, is);
                                            GraphPath<CortexVertex, CortexEdge> pAlt = dspAlt.getPathFinder(os, is, novelKmer, true);

                                            Bubble b = new Bubble(pRef, pAlt, null, null);

                                            if (b.getRefAllele().length() > 0 || b.getAltAllele().length() > 0) {
                                                log.info("  b: {} {} {}", b, os.getSk(), is.getSk());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    i = novelStretchBoundaries.getSecond();
                }
            }

            pm.update();
        }

        /*
        log.info("{}", rois.size());

        for (CortexKmer rk : rois.keySet()) {
            log.info("rk: {} {}", rk, rois.get(rk));
        }
        */
    }

    private DirectedWeightedPseudograph<CortexVertex, CortexEdge> buildContigGraph(List<Map<String, String>> annotations, int start, int color) {
        DirectedWeightedPseudograph<CortexVertex, CortexEdge> sg = new DirectedWeightedPseudograph<>(CortexEdge.class);

        String sk0 = annotations.get(start).get("kmer");
        CortexKmer ck0 = new CortexKmer(sk0);
        //CortexVertex cv0 = new CortexVertex(sk0, GRAPH.findRecord(ck0));
        CortexVertex cv0 = null; // todo fix
        sg.addVertex(cv0);

        for (int i = start + 1; i < annotations.size(); i++) {
            String sk1 = annotations.get(i).get("kmer");
            CortexKmer ck1 = new CortexKmer(sk1);
            //CortexVertex cv1 = new CortexVertex(sk1, GRAPH.findRecord(ck1));
            CortexVertex cv1 = null; // todo fix
            sg.addVertex(cv1);

            sg.addEdge(cv0, cv1, new CortexEdge(color, 1.0));

            cv0 = cv1;
        }

        return sg;
    }

    private Pair<Integer, Integer> getNovelStretchBoundaries(List<Map<String, String>> annotations, int i) {
        int novelStart;
        for (novelStart = i - 1;
             novelStart >= 0 && (annotations.get(novelStart).get("code").equals(".") || annotations.get(novelStart).get("code").equals("?"));
             novelStart--) {
        }
        novelStart++;

        int novelStop;
        for (novelStop = i + 1;
             novelStop < annotations.size() && (annotations.get(novelStop).get("code").equals(".") || annotations.get(novelStop).get("code").equals("?"));
             novelStop++) {
        }
        novelStop--;

        return new Pair<>(novelStart, novelStop);
    }

    private Map<CortexKmer, Boolean> loadRois() {
        Map<CortexKmer, Boolean> rois = new HashMap<>();

        for (CortexRecord cr : ROI) {
            rois.put(cr.getCortexKmer(), false);
        }

        return rois;
    }

    private Map<String, List<Map<String, String>>> loadAnnotations() {
        TableReader tr = new TableReader(ANNOTATIONS);

        Map<String, List<Map<String, String>>> contigs = new TreeMap<>();

        for (Map<String, String> m : tr) {
            if (!contigs.containsKey(m.get("contigName"))) {
                contigs.put(m.get("contigName"), new ArrayList<>());
            }
            contigs.get(m.get("contigName")).add(m);
        }

        return contigs;
    }
}
