package uk.ac.ox.well.indiana.analyses.reconstruction;

import com.google.common.base.Joiner;
import uk.ac.ox.well.indiana.tools.Tool;
import uk.ac.ox.well.indiana.utils.arguments.Argument;
import uk.ac.ox.well.indiana.utils.arguments.Output;
import uk.ac.ox.well.indiana.utils.assembly.CortexGraphWalker2;
import uk.ac.ox.well.indiana.utils.io.cortex.CortexKmer;
import uk.ac.ox.well.indiana.utils.io.cortex.CortexMap;
import uk.ac.ox.well.indiana.utils.io.utils.TableReader2;
import uk.ac.ox.well.indiana.utils.io.utils.TableWriter2;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BuildContigs extends Tool {
    @Argument(fullName="cortexGraph", shortName="cg", doc="Cortex graph")
    public CortexMap CORTEX_MAP;

    @Argument(fullName="kmerReferencePanel", shortName="krp", doc="Kmer reference panel")
    public File KMER_REFERENCE_PANEL;

    @Output
    public PrintStream out;

    private Map<CortexKmer, String> loadKmerReferencePanel() {
        TableReader2 tr = new TableReader2(KMER_REFERENCE_PANEL);

        Map<CortexKmer, String> kmerReferencePanel = new HashMap<CortexKmer, String>();
        for (Map<String, String> te : tr) {
            CortexKmer ck = new CortexKmer(te.get("kmer"));
            String gene = te.get("gene");

            kmerReferencePanel.put(ck, gene);
        }

        return kmerReferencePanel;
    }

    @Override
    public void execute() {
        CortexGraphWalker2 cgw = new CortexGraphWalker2(CORTEX_MAP);

        Map<CortexKmer, String> krp = loadKmerReferencePanel();

        TableWriter2 tw = new TableWriter2(out);

        for (int color = 0; color < CORTEX_MAP.getGraph().getNumColors(); color++) {
            log.info("Finding contigs for color {}...", color);

            Map<CortexKmer, Set<CortexKmer>> contigs = cgw.buildContigs(color, krp.keySet());

            for (CortexKmer contig : contigs.keySet()) {
                Map<String, String> entry = new HashMap<String, String>();

                entry.put("color", String.valueOf(color));
                entry.put("contig", contig.getKmerAsString());
                //entry.put("kmers", Joiner.on(",").join(contigs.get(contig)));

                tw.addEntry(entry);
            }

            log.info("Found {} contigs for color {}", contigs.size(), color);
        }
    }
}
