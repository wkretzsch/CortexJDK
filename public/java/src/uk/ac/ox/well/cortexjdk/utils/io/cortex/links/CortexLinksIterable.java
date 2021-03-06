package uk.ac.ox.well.cortexjdk.utils.io.cortex.links;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.ox.well.cortexjdk.utils.exceptions.CortexJDKException;
import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexColor;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class CortexLinksIterable implements Iterable<CortexLinksRecord>, Iterator<CortexLinksRecord> {
    private File linksFile;

    private String headerStr;
    private String commentsStr;

    private int version;
    private int kmerSize;
    private int numColors;
    private long numKmersInGraph;
    private long numKmersWithLinks;
    private long numLinks;
    private long linkBytes;

    private List<CortexColor> colors = new ArrayList<>();
    private Map<String, Integer> sampleColorMap = new HashMap<>();

    private BufferedReader buffered;

    private CortexLinksRecord nextRecord = null;
    private int recordsSeen = 0;
    private long recordsStart = 0;

    public CortexLinksIterable(String linksString) {
        this.linksFile = new File(linksString);
        loadCortexLinks(this.linksFile);
    }

    public CortexLinksIterable(File linksFile) {
        this.linksFile = linksFile;
        loadCortexLinks(this.linksFile);
    }

    private void loadCortexLinks(File cortexLinks) {
        try {
            InputStream fileStream = new FileInputStream(cortexLinks);
            InputStream gzipStream = new GZIPInputStream(fileStream);
            Reader decoder = new InputStreamReader(gzipStream, Charset.forName("UTF-8"));
            buffered = new BufferedReader(decoder);

            StringBuilder headerBuilder = new StringBuilder();
            boolean inHeader = false;

            String line;
            while ((line = buffered.readLine()) != null) {
                recordsStart += line.length() + 1;

                if (line.equals("{")) { inHeader = true; }
                if (inHeader) { headerBuilder.append(line).append("\n"); }
                if (line.equals("}")) { break; }
            }

            headerStr = headerBuilder.toString();
            JSONObject header = new JSONObject(headerBuilder.toString());

            if (!header.has("formatVersion") && !header.has("format_version")) {
                throw new CortexJDKException("Cannot parse CortexLinks format version field");
            }
            version = header.has("formatVersion") ? header.getInt("formatVersion") : header.getInt("format_version");

            if (version != 2 && version != 3 && version != 4) {
                throw new CortexJDKException("Cannot parse CortexLinks format version '" + version + "'");
            }

            if (version == 2) {
                numColors = header.getInt("ncols");
                kmerSize = header.getInt("kmer_size");
                numKmersInGraph = header.getLong("num_kmers_in_graph");
                numKmersWithLinks = header.getLong("num_kmers_with_paths");
                numLinks = header.getLong("num_paths");
                linkBytes = header.getLong("path_bytes");

                JSONArray colors = header.getJSONArray("colours");
                for (int i = 0; i < colors.length(); i++) {
                    JSONObject jColor = colors.getJSONObject(i);

                    CortexColor color = new CortexColor();
                    color.setSampleName(jColor.getString("sample"));
                    color.setTotalSequence(jColor.getLong("total_sequence"));
                    color.setTipClippingApplied(true);
                    color.setLowCovgSupernodesRemoved(true);

                    this.colors.add(color);
                }
            } else {
                JSONObject graph = header.getJSONObject("graph");
                JSONObject paths = header.getJSONObject("paths");

                numColors = graph.getInt("num_colours");
                kmerSize = graph.getInt("kmer_size");
                numKmersInGraph = graph.getLong("num_kmers_in_graph");
                numKmersWithLinks = paths.getLong("num_kmers_with_paths");
                numLinks = paths.getLong("num_paths");
                linkBytes = paths.getLong("path_bytes");

                JSONArray colors = graph.getJSONArray("colours");
                for (int i = 0; i < colors.length(); i++) {
                    JSONObject jColor = colors.getJSONObject(i);

                    CortexColor color = new CortexColor();
                    color.setSampleName(jColor.getString("sample"));
                    color.setTotalSequence(jColor.getLong("total_sequence"));
                    color.setTipClippingApplied(jColor.getBoolean("cleaned_tips"));
                    color.setLowCovgSupernodesRemoved(true);

                    this.colors.add(color);
                }
            }

            sampleColorMap = new HashMap<>();
            for (int c = 0; c < colors.size(); c++) {
                CortexColor color = colors.get(c);
                sampleColorMap.put(color.getSampleName(), c);
            }

            StringBuilder commentsBuilder = new StringBuilder();
            while ((line = buffered.readLine()) != null) {
                if (line.startsWith("#")) {
                    commentsBuilder.append(line).append("\n");
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    break;
                }

                recordsStart += line.length() + 1;
                buffered.mark(100);
            }

            commentsStr = commentsBuilder.toString();
        } catch (IOException e) {
            throw new CortexJDKException("Unable to load Cortex links file '" + linksFile.getAbsolutePath() + "'", e);
        }
    }

    private void moveToBeginningOfRecordsSection() {
        try {
            buffered.reset();
            recordsSeen = 0;
        } catch (IOException e) {
            try {
                buffered.close();

                InputStream fileStream = new FileInputStream(this.linksFile);
                InputStream gzipStream = new GZIPInputStream(fileStream);
                Reader decoder = new InputStreamReader(gzipStream, Charset.forName("UTF-8"));
                buffered = new BufferedReader(decoder);

                buffered.skip(recordsStart);
                recordsSeen = 0;
            } catch (IOException e2) {
                throw new CortexJDKException("Unable to restart iteration over CortexLinks file", e2);
            }
        }

        nextRecord = getNextRecord();
    }

    private CortexLinksRecord getNextRecord() {
        if (recordsSeen < numKmersWithLinks) {
            try {
                String[] kmerLine = buffered.readLine().split("\\s+");

                String kmer = kmerLine[0];
                int numLinks = Integer.valueOf(kmerLine[1]);

                List<CortexJunctionsRecord> cjs = new ArrayList<>();

                for (int i = 0; i < numLinks; i++) {
                    String[] linkLine = buffered.readLine().split("[,\\s]+");

                    String orientation = linkLine[0];
                    int numKmers = version == 4 ? -1 : Integer.valueOf(linkLine[1]);
                    int numJunctions = version == 4 ? Integer.valueOf(linkLine[1]) : Integer.valueOf(linkLine[2]);
                    int[] coverages = new int[numColors];

                    int offset = version == 4 ? 2 : 3;

                    for (int c = 0; c < numColors; c++) {
                        coverages[c] = Integer.valueOf(linkLine[offset + c]);
                    }

                    String junctions = linkLine[offset + numColors];

                    CortexJunctionsRecord cj = new CortexJunctionsRecord(orientation.equals("F"), numKmers, numJunctions, coverages, junctions);
                    cjs.add(cj);
                }

                recordsSeen++;

                return new CortexLinksRecord(kmer, cjs);
            } catch (IOException e) {
                throw new CortexJDKException("Unable to parse CortexLinks record", e);
            }
        }

        return null;
    }

    @Override
    public Iterator<CortexLinksRecord> iterator() {
        moveToBeginningOfRecordsSection();

        return this;
    }

    @Override
    public boolean hasNext() {
        return nextRecord != null;
    }

    @Override
    public CortexLinksRecord next() {
        CortexLinksRecord currentRecord = nextRecord;

        nextRecord = getNextRecord();
        if (nextRecord == null) {
            close();
        }

        return currentRecord;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public void close() {
        try {
            this.buffered.close();
        } catch (IOException e) {
            throw new CortexJDKException("Unable to close CortexLinks file", e);
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder();

        out.append("file: ").append(linksFile.getAbsolutePath()).append("\n");
        out.append("----\n");
        out.append("binary version: ").append(version).append("\n");
        out.append("kmer size: ").append(kmerSize).append("\n");
        out.append("link bytes: ").append(linkBytes).append("\n");
        out.append("colors: ").append(numColors).append("\n");
        for (int i = 0; i < colors.size(); i++) {
            CortexColor color = colors.get(i);

            out.append("-- Color ").append(i).append(" --\n");
            out.append("  sample: '").append(color.getSampleName()).append("'\n");
            out.append("  total_sequence: ").append(color.getTotalSequence()).append("\n");
            out.append("  cleaned_tips: ").append(color.isTipClippingApplied()).append("\n");
            out.append("  cleaned_supernodes: ").append(color.isLowCovgSupernodesRemoved()).append("\n");
        }
        out.append("----\n");
        out.append("kmers in graph: ").append(numKmersInGraph).append("\n");
        out.append("kmers with links: ").append(numKmersWithLinks).append("\n");
        out.append("paths: ").append(numLinks).append("\n");
        out.append("----\n");

        return out.toString();
    }

    public String getJSONHeader() { return headerStr; }
    public String getComments() { return commentsStr; }
    public int getVersion() { return version; }
    public int getKmerSize() { return kmerSize; }
    public int getNumColors() { return numColors; }
    public long getNumKmersInGraph() { return numKmersInGraph; }
    public long getNumKmersWithLinks() { return numKmersWithLinks; }
    public long getNumLinks() { return numLinks; }
    public long getLinkBytes() { return linkBytes; }
    public File getCortexLinksFile() { return linksFile; }

    public boolean hasColor(int color) {
        return (color < colors.size());
    }

    public CortexColor getColor(int color) {
        return colors.get(color);
    }

    public int getColorForSampleName(String sampleName) {
        int sampleColor = -1;
        int sampleCopies = 0;

        for (int color = 0; color < colors.size(); color++) {
            if (colors.get(color).getSampleName().equalsIgnoreCase(sampleName)) {
                sampleColor = color;
                sampleCopies++;
            }
        }

        return (sampleCopies == 1) ? sampleColor : -1;
    }
}
