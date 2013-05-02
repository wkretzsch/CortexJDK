package uk.ac.ox.well.indiana.analyses.reconstruction;

import net.sf.picard.reference.IndexedFastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import uk.ac.ox.well.indiana.tools.Tool;
import uk.ac.ox.well.indiana.utils.arguments.Argument;
import uk.ac.ox.well.indiana.utils.arguments.Output;
import uk.ac.ox.well.indiana.utils.io.cortex.CortexKmer;
import uk.ac.ox.well.indiana.utils.io.gff.GFF3;
import uk.ac.ox.well.indiana.utils.io.gff.GFF3Record;
import uk.ac.ox.well.indiana.utils.io.utils.TableWriter;
import uk.ac.ox.well.indiana.utils.io.utils.TableWriter2;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BuildKmerReferencePanel extends Tool {
    @Argument(fullName="reference", shortName="R", doc="Reference(s)")
    public ArrayList<IndexedFastaSequenceFile> REFERENCES;

    @Argument(fullName="gff", shortName="gff", doc="GFF file(s)")
    public ArrayList<GFF3> GFFS;

    @Argument(fullName="kmerSize", shortName="ks", doc="Kmer size")
    public Integer KMER_SIZE = 31;

    @Output
    public PrintStream out;

    private class KmerInfo {
        public String gene;
        public int count = 0;
    }

    @Override
    public void execute() {
        if (REFERENCES.size() != GFFS.size()) {
            throw new RuntimeException("Number of references and GFFs supplied must be identical");
        }

        Map<CortexKmer, KmerInfo> kmerInfoMap = new HashMap<CortexKmer, KmerInfo>();

        for (int i = 0; i < REFERENCES.size(); i++) {
            IndexedFastaSequenceFile reference = REFERENCES.get(i);
            GFF3 gff = GFFS.get(i);

            for (GFF3Record gr : gff) {
                if ("gene".equals(gr.getType())) {
                    ReferenceSequence subseq = reference.getSubsequenceAt(gr.getSeqid(), gr.getStart(), gr.getEnd());
                    byte[] seq = subseq.getBases();

                    for (int j = 0; j <= seq.length - KMER_SIZE; j++) {

                        byte[] bkmer = new byte[KMER_SIZE];
                        System.arraycopy(subseq.getBases(), j, bkmer, 0, KMER_SIZE);

                        CortexKmer ck = new CortexKmer(bkmer);

                        KmerInfo ki = (kmerInfoMap.containsKey(ck)) ? kmerInfoMap.get(ck) : new KmerInfo();
                        ki.gene = gr.getAttribute("ID");
                        ki.count++;

                        kmerInfoMap.put(ck, ki);
                    }
                }
            }
        }

        TableWriter2 tw = new TableWriter2(out);

        int i = 0;
        for (CortexKmer ck : kmerInfoMap.keySet()) {
            KmerInfo ki = kmerInfoMap.get(ck);

            if (ki.count == 1) {
                Map<String, String> entry = new HashMap<String, String>();
                entry.put("kmer", ck.getKmerAsString());
                entry.put("gene", ki.gene);

                tw.addEntry(entry);

                i++;
            }
        }

        log.info("Wrote {} records", i);
    }
}
