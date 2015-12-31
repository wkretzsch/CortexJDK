package uk.ac.ox.well.indiana.utils.alignment.pairwise;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.reference.ReferenceSequence;

import java.io.File;
import java.util.List;

public interface ExternalAligner {
    List<SAMRecord> align(String query, File targets);
    List<SAMRecord> align(List<ReferenceSequence> queries, File targets);
}