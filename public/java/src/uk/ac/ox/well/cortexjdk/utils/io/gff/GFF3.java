package uk.ac.ox.well.cortexjdk.utils.io.gff;

import com.google.common.base.Joiner;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalTreeMap;
import uk.ac.ox.well.cortexjdk.utils.io.utils.LineReader;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GFF3 implements Iterable<GFF3Record>, Iterator<GFF3Record> {
    private class SequenceRegion {
        private int start;
        private int end;

        public SequenceRegion(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() { return start; }
        public int getEnd()   { return end;   }
    }

    private File gffFile;
    private TreeMap<String, String> headers = new TreeMap<>();
    private TreeMap<String, SequenceRegion> sequenceRegions = new TreeMap<>();

    private IntervalTreeMap<Set<GFF3Record>> intervals = new IntervalTreeMap<>();
    private TreeMap<String, GFF3Record> idrecords = new TreeMap<>();
    private ArrayList<GFF3Record> records = new ArrayList<>();

    private int index = 0;

    public GFF3(String gffFilePath) {
        this.gffFile = new File(gffFilePath);
        loadGFF3File(this.gffFile);
    }

    public GFF3(File gffFile) {
        this.gffFile = gffFile;
        loadGFF3File(this.gffFile);
    }

    private void loadGFF3File(File gffFile) {
        try {
            LineReader lr = new LineReader(gffFile);

            String version = lr.getNextRecord();
            if (!version.equalsIgnoreCase("##gff-version\t3") && !version.equalsIgnoreCase("##gff-version 3")) {
                throw new RuntimeException("Unable to parse GFF3 file '" + gffFile.getAbsolutePath() + "'. Expected first line of GFF3 file to say '##gff-version\t3', but found '" + version + "' instead.");
            }

            String line;
            while ((line = lr.getNextRecord()) != null) {
                if (line.startsWith("##") && !line.equals("###")) {
                    String[] fields = line.replaceFirst("##", "").split("\\s+");

                    if (fields[0].equalsIgnoreCase("sequence-region")) {
                        sequenceRegions.put(fields[1], new SequenceRegion(Integer.valueOf(fields[2]), Integer.valueOf(fields[3])));
                    } else if (line.equalsIgnoreCase("##FASTA")) {
                        lr.close();
                        break;
                    } else {
                        headers.put(fields[0], fields[1]);
                    }
                } else if (!line.startsWith("#") && line.contains("ID=")) {
                    GFF3Record record = new GFF3Record(line);
                    Interval interval = new Interval(record.getSeqid(), record.getStart(), record.getEnd());

                    if (!intervals.containsKey(interval)) {
                        intervals.put(interval, new HashSet<>());
                    }

                    intervals.get(interval).add(record);
                    idrecords.put(record.getAttributes().get("ID"), record);
                    records.add(record);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error when closing GFF3 file '" + gffFile.getAbsolutePath() + "': " + e);
        }
    }

    @Override
    public Iterator<GFF3Record> iterator() {
        index = 0;

        return this;
    }

    @Override
    public boolean hasNext() {
        return index < records.size();
    }

    @Override
    public GFF3Record next() {
        if (this.hasNext()) {
            GFF3Record record = records.get(index);

            index++;

            return record;
        }

        return null;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        ArrayList<String> headerLines = new ArrayList<>();

        headerLines.add("##gff-version\t3");

        for (String header : headers.keySet()) {
            headerLines.add("##" + header + "\t" + headers.get(header));
        }

        for (String sequenceRegion : sequenceRegions.keySet()) {
            headerLines.add("##sequence-region\t" + sequenceRegion + "\t" + sequenceRegions.get(sequenceRegion).getStart() + "\t" + sequenceRegions.get(sequenceRegion).getEnd());
        }

        return Joiner.on("\n").join(headerLines);
    }

    public Collection<GFF3Record> getOverlapping(Interval key) {
        Collection<Set<GFF3Record>> recordCollections = intervals.getOverlapping(key);

        HashSet<GFF3Record> records = new HashSet<>();

        for (Set<GFF3Record> recordSet : recordCollections) {
            records.addAll(recordSet);
        }

        return records;
    }

    public Collection<GFF3Record> getOverlapping(GFF3Record record) {
        Interval key = new Interval(record.getSeqid(), record.getStart(), record.getEnd());

        return getOverlapping(key);
    }

    public Collection<GFF3Record> getContained(Interval key) {
        Collection<Set<GFF3Record>> recordCollections = intervals.getContained(key);

        HashSet<GFF3Record> records = new HashSet<>();

        for (Set<GFF3Record> recordSet : recordCollections) {
            records.addAll(recordSet);
        }

        return records;
    }

    public Collection<GFF3Record> getChildren(GFF3Record record) {
        String id = record.getAttribute("ID");
        Collection<GFF3Record> exons = new ArrayList<>();

        for (GFF3Record exon : GFF3.getType("exon", this.getOverlapping(record))) {
            String exonId = exon.getAttribute("ID");

            if (exonId.contains(id)) {
                exons.add(exon);
            }
        }

        return exons;
    }

    public Collection<GFF3Record> getContained(GFF3Record record) {
        Interval key = new Interval(record.getSeqid(), record.getStart(), record.getEnd());

        return getContained(key);
    }

    public GFF3Record getRecord(String id) {
        return idrecords.get(id);
    }

    public static Collection<GFF3Record> getType(String type, Collection<GFF3Record> records) {
        ArrayList<GFF3Record> subsetOfRecords = new ArrayList<>();

        for (GFF3Record r : records) {
            if (type.equalsIgnoreCase(r.getType())) {
                subsetOfRecords.add(r);
            }
        }

        return subsetOfRecords;
    }
}
