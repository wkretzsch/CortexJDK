package uk.ac.ox.well.cortexjdk.utils.io.cortex.graph;

import uk.ac.ox.well.cortexjdk.utils.sequence.SequenceUtils;

import java.util.Arrays;

public class CortexKmer implements CharSequence, Comparable<CortexKmer> {
    private byte[] kmer;
    private String sk;
    private boolean isFlippedFromSuppliedOrientation = false;

    public CortexKmer(String kmer) {
        this.kmer = SequenceUtils.alphanumericallyLowestOrientation(kmer.getBytes());

        isFlippedFromSuppliedOrientation = Arrays.hashCode(this.kmer) != Arrays.hashCode(kmer.getBytes());
        this.sk = new String(this.kmer);
    }

    public CortexKmer(byte[] kmer) {
        this.kmer = SequenceUtils.alphanumericallyLowestOrientation(kmer);

        isFlippedFromSuppliedOrientation = Arrays.hashCode(this.kmer) != Arrays.hashCode(kmer);
        this.sk = new String(this.kmer);
    }

    public CortexKmer(byte[] kmer, boolean kmerIsAlphanumericallyLowest) {
        this.kmer = kmer;

        if (!kmerIsAlphanumericallyLowest) {
            this.kmer = SequenceUtils.alphanumericallyLowestOrientation(kmer);

            isFlippedFromSuppliedOrientation = Arrays.hashCode(this.kmer) != Arrays.hashCode(kmer);
        }

        this.sk = new String(this.kmer);
    }

    public int length() {
        return kmer.length;
    }

    @Override
    public char charAt(int index) {
        return (char) this.kmer[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return getSubKmer(start, end - start);
    }

    public boolean isFlipped() { return isFlippedFromSuppliedOrientation; }

    public byte[] getKmerAsBytes() {
        return kmer;
    }

    public String getKmerAsString() {
        //return new String(kmer);
        return sk;
    }

    public CortexKmer getSubKmer(int start, int length) {
        byte[] subkmer = new byte[length];
        System.arraycopy(kmer, start, subkmer, 0, length);

        return new CortexKmer(subkmer);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(kmer);
    }

    @Override
    public boolean equals(java.lang.Object obj) {
        if (obj instanceof CortexKmer) {
            return Arrays.equals(kmer, ((CortexKmer) obj).getKmerAsBytes());
        } else if (obj instanceof String) {
            return Arrays.equals(kmer, ((String) obj).getBytes());
        } else if (obj instanceof byte[]) {
            return Arrays.equals(kmer, (byte[]) obj);
        }

        return false;
    }

    @Override
    public String toString() {
        return new String(kmer);
    }

    @Override
    public int compareTo(CortexKmer o) {
        return getKmerAsString().compareTo(o.getKmerAsString());
    }
}
