package uk.ac.ox.well.cortexjdk.commands.inheritance;

import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.writer.SortingVariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterFactory;
import htsjdk.variant.vcf.VCFEncoder;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.math3.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedWeightedPseudograph;
import uk.ac.ox.well.cortexjdk.commands.Module;
import uk.ac.ox.well.cortexjdk.utils.alignment.kmer.KmerLookup;
import uk.ac.ox.well.cortexjdk.utils.arguments.Argument;
import uk.ac.ox.well.cortexjdk.utils.arguments.Output;
import uk.ac.ox.well.cortexjdk.utils.caller.Bubble;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.*;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.links.CortexLinks;
import uk.ac.ox.well.cortexjdk.utils.io.table.TableWriter;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeter;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeterFactory;
import uk.ac.ox.well.cortexjdk.utils.sequence.SequenceUtils;
import uk.ac.ox.well.cortexjdk.utils.stoppingconditions.BubbleOpeningStopper;
import uk.ac.ox.well.cortexjdk.utils.stoppingconditions.ContigStopper;
import uk.ac.ox.well.cortexjdk.utils.stoppingconditions.DestinationStopper;
import uk.ac.ox.well.cortexjdk.utils.traversal.CortexEdge;
import uk.ac.ox.well.cortexjdk.utils.traversal.CortexVertex;
import uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngine;
import uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineFactory;

import java.io.File;
import java.io.PrintStream;
import java.util.*;

import static uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineConfiguration.GraphCombinationOperator.AND;
import static uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineConfiguration.TraversalDirection.BOTH;

/**
 * Created by kiran on 08/08/2017.
 */
public class CallVariants extends Module {
    @Argument(fullName="graph", shortName="g", doc="Graph")
    public CortexGraph GRAPH;

    @Argument(fullName="links", shortName="l", doc="Links")
    public ArrayList<CortexLinks> LINKS;

    @Argument(fullName="references", shortName="r", doc="References")
    public HashMap<String, KmerLookup> REFERENCES;

    @Argument(fullName="parent", shortName="p", doc="Parents")
    public HashMap<String, String> PARENTS;

    @Output
    public PrintStream out;

    @Override
    public void execute() {
        int refColor = GRAPH.getColorForSampleName("ref");
        Set<Integer> parentColors = new TreeSet<>(GRAPH.getColorsForSampleNames(new ArrayList<>(PARENTS.values())));
        Set<Integer> draftColors = new TreeSet<>(GRAPH.getColorsForSampleNames(new ArrayList<>(REFERENCES.keySet())));
        Set<Integer> childColors = getChildColors(parentColors, draftColors, refColor);

        log.info("Colors:");
        log.info("  - parents:  {}", parentColors);
        log.info("  - children: {}", childColors);
        log.info("  - refs:     {}", draftColors);

        Set<CortexKmer> seeds = getVariantSeeds(refColor, parentColors, draftColors);

        TraversalEngine e = new TraversalEngineFactory()
                .combinationOperator(AND)
                .traversalDirection(BOTH)
                .joiningColors(parentColors)
                .stopper(ContigStopper.class)
                .graph(GRAPH)
                //.links(LINKS)
                .make();

        ProgressMeter pm = new ProgressMeterFactory()
                .header("Building contigs")
                .message("seeds processed")
                .maxRecord(seeds.size())
                .make(log);

        Map<Interval, Map<String, String>> entries = new TreeMap<>();

        int numVariants = 0;
        for (CortexKmer ck : seeds) {
            Map<String, String> te = callVariant(parentColors, childColors, e, ck);

            if (te != null) {
                Interval it = new Interval(te.get("chrom"), Integer.valueOf(te.get("pos")), Integer.valueOf(te.get("pos")));
                entries.put(it, te);

                numVariants++;
            }

            pm.update("seeds processed, " + numVariants + " variants found");
        }

        TableWriter tw = new TableWriter(out);

        for (Interval it : entries.keySet()) {
            tw.addEntry(entries.get(it));
        }
    }

    private Map<String, String> callVariant(Set<Integer> parentColors, Set<Integer> childColors, TraversalEngine e, CortexKmer ck) {
        for (int c : childColors) {
            CortexRecord cr = GRAPH.findRecord(ck);
            if (cr.getCoverage(c) > 0) {
                e.getConfiguration().setTraversalColor(c);

                int parentThatSharesChildAllele = -1;
                int parentThatDoesNotShareChildAllele = -1;
                for (int pc : parentColors) {
                    if (cr.getCoverage(pc) > 0) {
                        parentThatSharesChildAllele = pc;
                    } else if (cr.getCoverage(pc) == 0) {
                        parentThatDoesNotShareChildAllele = pc;
                    }
                }

                String sk = ck.getKmerAsString();
                List<CortexVertex> contigChild = new ArrayList<>();
                contigChild.add(new CortexVertex(new CortexByteKmer(sk), GRAPH.findRecord(sk)));

                CortexVertex source = null;
                e.seek(sk);
                while (e.hasPrevious()) {
                    CortexVertex cv = e.previous();
                    contigChild.add(0, cv);

                    if (cv.getCr().getCoverage(parentThatDoesNotShareChildAllele) > 0) {
                        source = cv;
                        break;
                    }
                }

                CortexVertex destination = null;
                e.seek(sk);
                while (e.hasNext()) {
                    CortexVertex cv = e.next();
                    contigChild.add(cv);

                    if (cv.getCr().getCoverage(parentThatDoesNotShareChildAllele) > 0) {
                        destination = cv;
                        break;
                    }
                }

                if (source != null && destination != null) {
                    e.getConfiguration().setTraversalColor(parentThatDoesNotShareChildAllele);

                    List<CortexVertex> contigParent = new ArrayList<>();
                    contigParent.add(source);

                    boolean destinationReached = false;

                    e.seek(source.getSk());
                    while (e.hasNext()) {
                        CortexVertex cv = e.next();
                        contigParent.add(cv);

                        if (cv.equals(destination)) {
                            destinationReached = true;
                            break;
                        }
                    }

                    if (destinationReached) {
                        Set<Interval> sourceIntervals = REFERENCES.get("ref").findKmer(source.getSk());
                        Set<Interval> destinationIntervals = REFERENCES.get("ref").findKmer(destination.getSk());

                        if (sourceIntervals.size() == 1 && destinationIntervals.size() == 1) {
                            Interval sourceInterval = sourceIntervals.iterator().next();
                            Interval destinationInterval = destinationIntervals.iterator().next();

                            if (sourceInterval.getContig().equals(destinationInterval.getContig())) {
                                Pair<String, String> alleles = trimToAlleles(TraversalEngine.toContig(contigChild), TraversalEngine.toContig(contigParent));

                                Map<String, String> te = new LinkedHashMap<>();
                                te.put("chrom", sourceInterval.getContig());
                                te.put("pos", String.valueOf(sourceInterval.getStart()));

                                if (alleles.getFirst().length() == 1 && alleles.getSecond().length() == 1) {
                                    te.put("type", "SNP");
                                } else if (alleles.getFirst().length() == alleles.getSecond().length()) {
                                    te.put("type", "MNP");
                                } else if (alleles.getFirst().length() < alleles.getSecond().length()) {
                                    te.put("type", "DEL");
                                } else if (alleles.getFirst().length() > alleles.getSecond().length()) {
                                    te.put("type", "INS");
                                } else {
                                    te.put("type", "UKN");
                                }

                                for (int cc : childColors) {
                                    if (cr.getCoverage(cc) == 0) {
                                        te.put(GRAPH.getSampleName(cc), GRAPH.getSampleName(parentThatDoesNotShareChildAllele));
                                    } else {
                                        te.put(GRAPH.getSampleName(cc), GRAPH.getSampleName(parentThatSharesChildAllele));
                                    }
                                }

                                te.put("alleles", String.format("%s/%s", alleles.getFirst(), alleles.getSecond()));

                                return te;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    @NotNull
    private Set<CortexKmer> getVariantSeeds(int refColor, Set<Integer> parentColors, Set<Integer> draftColors) {
        Set<CortexKmer> seeds = new HashSet<>();

        DirectedGraph<String, DefaultEdge> sg = new DefaultDirectedGraph<>(DefaultEdge.class);

        ProgressMeter pm = new ProgressMeterFactory()
                .header("Processing records")
                .message("records processed")
                .maxRecord(GRAPH.getNumRecords())
                .updateRecord(GRAPH.getNumRecords() / 10)
                .make(log);

        for (CortexRecord cr : GRAPH) {
            if (isSinglyConnected(cr) &&
                isSharedWithOnlyOneParent(cr, parentColors, draftColors) &&
                isSharedWithSomeChildren(cr, parentColors, draftColors, refColor) &&
                hasUniqueCoordinates(cr, draftColors)) {

                seeds.add(cr.getCortexKmer());

                String skFwd = cr.getKmerAsString();
                String skRev = SequenceUtils.reverseComplement(cr.getKmerAsString());
                sg.addVertex(skFwd);
                sg.addVertex(skRev);

                for (int c = 0; c < cr.getNumColors(); c++) {
                    if (cr.getCoverage(c) > 0) {
                        Collection<String> ies = cr.getInEdgesAsStrings(c, false);
                        for (String ie : ies) {
                            String inEdgeFwd = ie + skFwd.substring(0, skFwd.length() - 1);
                            String outEdgeRev = SequenceUtils.reverseComplement(inEdgeFwd);

                            sg.addVertex(inEdgeFwd);
                            sg.addEdge(inEdgeFwd, skFwd);

                            sg.addVertex(outEdgeRev);
                            sg.addEdge(skRev, outEdgeRev);
                        }

                        Collection<String> oes = cr.getOutEdgesAsStrings(c, false);
                        for (String oe : oes) {
                            String outEdgeFwd = skFwd.substring(1, skFwd.length()) + oe;
                            String inEdgeRev = SequenceUtils.reverseComplement(outEdgeFwd);

                            sg.addVertex(outEdgeFwd);
                            sg.addEdge(skFwd, outEdgeFwd);

                            sg.addVertex(inEdgeRev);
                            sg.addEdge(inEdgeRev, skRev);
                        }
                    }
                }
            }

            pm.update();
        }

        Set<String> uniqueSeeds = new HashSet<>();
        Set<CortexKmer> goodSeeds = new HashSet<>();
        for (String sk : sg.vertexSet()) {
            if (sg.inDegreeOf(sk) == 0 && sg.outDegreeOf(sk) == 1) {
                uniqueSeeds.add(sk);

                List<String> contig = new ArrayList<>();
                contig.add(sk);

                String v = sk;
                while (sg.outDegreeOf(v) == 1) {
                    List<String> out = Graphs.successorListOf(sg, v);
                    v = out.get(0);

                    contig.add(v);
                }

                if (contig.size() > 3) {
                    goodSeeds.add(new CortexKmer(contig.get(1)));
                }
            }
        }

        log.info("Found {} seed kmers for putative variants, {} unique, {} good", seeds.size(), uniqueSeeds.size(), goodSeeds.size());

        return goodSeeds;
    }

    private Set<Integer> getChildColors(Set<Integer> parentColors, Set<Integer> draftColors, int refColor) {
        Set<Integer> childColors = new TreeSet<>();

        for (int c = 0; c < GRAPH.getNumColors(); c++) {
            childColors.add(c);
        }

        childColors.removeAll(parentColors);
        childColors.removeAll(draftColors);
        childColors.remove(refColor);

        return childColors;
    }

    private boolean isSharedWithOnlyOneParent(CortexRecord cr, Set<Integer> parentColors, Set<Integer> draftColors) {
        int numParentsWithCoverage = 0;
        for (int pc : parentColors) {
            if (cr.getCoverage(pc) > 0) { numParentsWithCoverage++; }
        }

        int numDraftsWithCoverage = 0;
        for (int dc : draftColors) {
            if (cr.getCoverage(dc) > 0) { numDraftsWithCoverage++; }
        }

        return numParentsWithCoverage == 1 && numDraftsWithCoverage == 1;
    }

    private boolean isSharedWithSomeChildren(CortexRecord cr, Set<Integer> parentColors, Set<Integer> draftColors, int refColor) {
        Set<Integer> ignoreColors = new HashSet<>();
        ignoreColors.addAll(parentColors);
        ignoreColors.addAll(draftColors);
        ignoreColors.add(refColor);

        int numChildrenWithCoverage = 0;
        //int numChildren = 0;
        for (int c = 0; c < cr.getNumColors(); c++) {
            if (!ignoreColors.contains(c)) {
                if (cr.getCoverage(c) > 0) {
                    numChildrenWithCoverage++;
                }
                //numChildren++;
            }
        }

        return numChildrenWithCoverage >= 1; // && numChildrenWithCoverage < numChildren;
    }

    private boolean isSinglyConnected(CortexRecord cr) {
        boolean isSinglyConnected = true;
        for (int c = 0; c < cr.getNumColors(); c++) {
            if (cr.getCoverage(c) > 0) {
                if (!(cr.getInDegree(c) == 1 && cr.getOutDegree(c) == 1)) {
                    isSinglyConnected = false;
                }
            }
        }

        return isSinglyConnected;
    }

    private boolean hasUniqueCoordinates(CortexRecord cr, Set<Integer> draftColors) {
        int draftColor = -1;
        int numDraftsWithCoverage = 0;
        for (int dc : draftColors) {
            if (cr.getCoverage(dc) > 0) {
                draftColor = dc;
                numDraftsWithCoverage++;
            }
        }

        if (numDraftsWithCoverage == 1 && draftColor > -1) {
            KmerLookup kl = REFERENCES.get(GRAPH.getSampleName(draftColor));

            Set<Interval> its = kl.findKmer(cr.getKmerAsString());

            return its != null && its.size() == 1;
        }

        return false;
    }

    private Pair<String, String> trimToAlleles(String s0, String s1) {
        int s0start = 0, s0end = s0.length();
        int s1start = 0, s1end = s1.length();

        for (int i = 0, j = 0; i < s0.length() && j < s1.length(); i++, j++) {
            if (s0.charAt(i) != s1.charAt(j)) {
                s0start = i;
                s1start = j;
                break;
            }
        }

        for (int i = s0.length() - 1, j = s1.length() - 1; i >= 0 && j >= 0; i--, j--) {
            if (s0.charAt(i) != s1.charAt(j) || i == s0start - 1 || j == s1start - 1) {
                s0end = i + 1;
                s1end = j + 1;
                break;
            }
        }

        String[] pieces = new String[4];
        pieces[0] = s0.substring(0, s0start);
        pieces[1] = s0.substring(s0start, s0end);
        pieces[2] = s1.substring(s1start, s1end);
        pieces[3] = s0.substring(s0end, s0.length());

        return new Pair<>(pieces[1], pieces[2]);
    }
}
