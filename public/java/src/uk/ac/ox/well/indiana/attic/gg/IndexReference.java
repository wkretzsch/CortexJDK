package uk.ac.ox.well.indiana.attic.gg;

import uk.ac.ox.well.indiana.commands.Module;
import uk.ac.ox.well.indiana.utils.arguments.Argument;
import uk.ac.ox.well.indiana.utils.arguments.Output;

import java.io.File;
import java.io.PrintStream;

//import org.mapdb.BTreeKeySerializer;

public class IndexReference extends Module {
    @Argument(fullName="reference", shortName="r", doc="Reference sequence")
    public File REF_FILE;

    @Argument(fullName="kmerSize", shortName="k", doc="Kmer size")
    public Integer KMER_SIZE = 47;

    @Argument(fullName="cacheSize", shortName="c", doc="Cache size")
    public Integer CACHE_SIZE = 1000000;

    @Output
    public PrintStream out;

    @Override
    public void execute() {
        File dbFile = new File(REF_FILE.getAbsoluteFile() + ".kmerdb");

        /*
        DB db = DBMaker.fileDB(dbFile)
                .closeOnJvmShutdown()
                .transactionDisable()
                .fileMmapEnable()
                .cacheSize(CACHE_SIZE)
                .make();

        NavigableSet<Object[]> kmerIndex = db.treeSetCreate("index" + KMER_SIZE)
                .serializer(BTreeKeySerializer.ARRAY3)
                .make();

        FastaSequenceFile ref = new FastaSequenceFile(REF_FILE, true);
        ReferenceSequence rseq;
        while ((rseq = ref.nextSequence()) != null) {
            log.info("  {}", rseq.getName());

            String seq = new String(rseq.getBases());

            for (int i = 0; i <= seq.length() - KMER_SIZE; i++) {
                String sk = seq.substring(i, i + KMER_SIZE);

                //kmerIndex.add(new Object[]{sk, rseq.getContigIndex(), i});
                kmerIndex.add(new Object[]{sk, rseq.getName().split("\\s+")[0], i});
            }

            db.commit();
        }

        db.close();
        */
    }
}