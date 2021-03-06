package uk.ac.ox.well.cortexjdk.commands.index.alignedbam;

import htsjdk.samtools.*;
import uk.ac.ox.well.cortexjdk.commands.Module;
import uk.ac.ox.well.cortexjdk.utils.arguments.Argument;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexKmer;

import java.io.File;
import java.util.List;

public class QueryIndex extends Module {
    @Argument(fullName="bam", shortName="b", doc="BAM")
    public File SAM_FILE;

    @Argument(fullName="kmerSize", shortName="k", doc="Kmer size")
    public Integer KMER_SIZE = 47;

    @Argument(fullName="seq", shortName="s", doc="Kmer sequence")
    public CortexKmer KMER;

    @Override
    public void execute() {
        KmerIndex ki = new KmerIndex(SAM_FILE, KMER_SIZE, false);

        SamReader sr = SamReaderFactory.makeDefault().open(SAM_FILE);

        List<long[]> chunks = ki.find(KMER.getKmerAsBytes());

        for (long[] c : chunks) {
            SAMFileSpan sfs = new BAMFileSpan(new Chunk(c[0], c[1]));

            SAMRecordIterator recs = sr.indexing().iterator(sfs);

            while (recs.hasNext()) {
                SAMRecord rec = recs.next();

                log.info("    {}: {} {}", KMER, new Chunk(c[0], c[1]), rec.getSAMString());
            }

            recs.close();
        }
    }
}
