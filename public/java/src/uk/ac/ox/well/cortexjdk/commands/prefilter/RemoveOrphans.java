package uk.ac.ox.well.cortexjdk.commands.prefilter;

import org.jgrapht.Graph;
import uk.ac.ox.well.cortexjdk.commands.Module;
import uk.ac.ox.well.cortexjdk.utils.arguments.Argument;
import uk.ac.ox.well.cortexjdk.utils.arguments.Description;
import uk.ac.ox.well.cortexjdk.utils.arguments.Output;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexGraph;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexGraphWriter;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexKmer;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexRecord;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeter;
import uk.ac.ox.well.cortexjdk.utils.progress.ProgressMeterFactory;
import uk.ac.ox.well.cortexjdk.utils.stoppingrules.OrphanStopper;
import uk.ac.ox.well.cortexjdk.utils.traversal.CortexEdge;
import uk.ac.ox.well.cortexjdk.utils.traversal.CortexVertex;
import uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngine;
import uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineConfiguration.GraphCombinationOperator.AND;
import static uk.ac.ox.well.cortexjdk.utils.traversal.TraversalEngineConfiguration.TraversalDirection.BOTH;

@Description(text="Remove chains of orphaned kmers (those that don't ever connect to parents)")
public class RemoveOrphans extends Module {
    @Argument(fullName="graph", shortName="g", doc="Graph")
    public CortexGraph GRAPH;

    @Argument(fullName="parents", shortName="p", doc="Parents")
    public ArrayList<String> PARENTS;

    @Argument(fullName="child", shortName="c", doc="Child")
    public String CHILD;

    @Argument(fullName="roi", shortName="r", doc="ROI")
    public CortexGraph ROI;

    @Output
    public File out;

    @Output(fullName="orphans_out", shortName="oo", doc="Orphans output file")
    public File orphans_out;

    @Override
    public void execute() {
        int childColor = GRAPH.getColorForSampleName(CHILD);
        Set<Integer> parentColors = new HashSet<>(GRAPH.getColorsForSampleNames(PARENTS));

        log.info("Colors:");
        log.info(" -   child: {}", GRAPH.getColorForSampleName(CHILD));
        log.info(" - parents: {}", GRAPH.getColorsForSampleNames(PARENTS));

        ProgressMeter pm = new ProgressMeterFactory()
                .header("Finding orphans")
                .message("records processed")
                .maxRecord(ROI.getNumRecords())
                .make(log);

        Set<CortexKmer> orphans = new HashSet<>();
        int numOrphanChains = 0;

        TraversalEngine e = new TraversalEngineFactory()
                .traversalDirection(BOTH)
                .combinationOperator(AND)
                .traversalColor(childColor)
                .joiningColors(parentColors)
                .stoppingRule(OrphanStopper.class)
                .rois(ROI)
                .graph(GRAPH)
                .make();

        for (CortexRecord rr : ROI) {
            if (!orphans.contains(rr.getCortexKmer())) {
                Graph<CortexVertex, CortexEdge> dfs = e.dfs(rr.getKmerAsString());

                if (dfs != null && dfs.vertexSet().size() > 0) {
                    numOrphanChains++;

                    for (CortexVertex av : dfs.vertexSet()) {
                        orphans.add(av.getCk());
                    }
                }
            }

            pm.update();
        }

        log.info("Found {} orphaned kmer chains ({} kmers total)", numOrphanChains, orphans.size());

        log.info("Writing...");

        CortexGraphWriter cgw = new CortexGraphWriter(out);
        cgw.setHeader(ROI.getHeader());

        CortexGraphWriter cgo = new CortexGraphWriter(orphans_out);
        cgo.setHeader(ROI.getHeader());

        int numKept = 0, numExcluded = 0;
        for (CortexRecord rr : ROI) {
            if (!orphans.contains(rr.getCortexKmer())) {
                cgw.addRecord(rr);
                numKept++;
            } else {
                cgo.addRecord(rr);
                numExcluded++;
            }
        }

        cgw.close();
        cgo.close();

        log.info("  {}/{} ({}%) kept, {}/{} ({}%) excluded",
                numKept,     ROI.getNumRecords(), 100.0f * (float) numKept / (float) ROI.getNumRecords(),
                numExcluded, ROI.getNumRecords(), 100.0f * (float) numExcluded / (float) ROI.getNumRecords()
        );
    }
}
