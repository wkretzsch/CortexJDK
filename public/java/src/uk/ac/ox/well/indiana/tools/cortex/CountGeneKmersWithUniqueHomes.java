package uk.ac.ox.well.indiana.tools.cortex;

import net.sf.picard.reference.FastaSequenceFile;
import net.sf.picard.reference.ReferenceSequence;
import uk.ac.ox.well.indiana.tools.Tool;
import uk.ac.ox.well.indiana.utils.arguments.Argument;
import uk.ac.ox.well.indiana.utils.arguments.Output;
import uk.ac.ox.well.indiana.utils.io.cortex.CortexGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.CortexRecord;
import uk.ac.ox.well.indiana.utils.sequence.SequenceUtils;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class CountGeneKmersWithUniqueHomes extends Tool {
    @Argument(fullName="cortexGraph", shortName="cg", doc="A binary Cortex graph")
    public CortexGraph CORTEX_GRAPH;

    @Argument(fullName="genesFasta", shortName="gf", doc="A FASTA-format genes file")
    public FastaSequenceFile GENES_FASTA;

    @Output
    public PrintStream out;

    private HashMap<String, String> loadGeneKmers(FastaSequenceFile genes, int kmerSize) {
        HashMap<String, String> kmerMap = new HashMap<String, String>();
        HashMap<String, Integer> kmerCoverageMap = new HashMap<String, Integer>();

        HashSet<String> geneNames = new HashSet<String>();

        ReferenceSequence seq;
        int totalSequenceLength = 0;
        int numGenes = 0;
        int tinyGenes = 0;
        while ((seq = genes.nextSequence()) != null) {
            totalSequenceLength += seq.length();

            String[] namePieces = seq.getName().split("\\s+");
            String geneName = namePieces[0];

            geneNames.add(geneName);

            if (seq.length() < kmerSize) {
                System.out.println("Gene '" + geneName + "' is smaller than the kmer size");
                tinyGenes++;
            }

            for (int i = 0; i < seq.length() - kmerSize; i++) {
                String kmer = new String(SequenceUtils.getCortexCompatibleOrientation(Arrays.copyOfRange(seq.getBases(), i, i + kmerSize)));

                kmer = kmer.toUpperCase();
                if (!kmer.contains("N") && !kmer.contains(".")) {
                    kmerMap.put(kmer, geneName);

                    if (!kmerCoverageMap.containsKey(kmer)) {
                        kmerCoverageMap.put(kmer, 1);
                    } else {
                        kmerCoverageMap.put(kmer, kmerCoverageMap.get(kmer) + 1);
                    }
                }
            }

            numGenes++;
        }

        HashSet<String> genesInMap = new HashSet<String>();
        for (String kmer : kmerMap.keySet()) {
            genesInMap.add(kmerMap.get(kmer));
        }

        System.out.println("Number of gene names: " + geneNames.size());
        System.out.println("Number of genes seen: " + numGenes);
        System.out.println("Number of genes smaller than kmer size: " + tinyGenes);
        System.out.println("Number of remaining genes: " + (numGenes - tinyGenes));
        System.out.println("Number of genes in map: " + genesInMap.size());

        //System.out.println("Total sequence length: " + totalSequenceLength);
        //System.out.println("Total kmers loaded (before pruning multi-copy kmers): " + kmerMap.size());

        for (String kmer : kmerCoverageMap.keySet()) {
            if (kmerCoverageMap.get(kmer) > 1) {
                kmerMap.remove(kmer);
            }
        }

        genesInMap.clear();
        for (String kmer : kmerMap.keySet()) {
            genesInMap.add(kmerMap.get(kmer));
        }

        System.out.println("Number of genes in map after pruning: " + genesInMap.size());

        for (String geneName : geneNames) {
            if (!genesInMap.contains(geneName)) {
                System.out.println("Gene '" + geneName + "' was removed from the gene list.");
            }
        }

        //System.out.println("Total kmers loaded (after pruning multi-copy kmers): " + kmerMap.size());

        return kmerMap;
    }

    public HashMap<String, Integer> getKmerCountsPerGene(HashMap<String, String> kmerMap) {
        HashMap<String, Integer> kmerCountsPerGene = new HashMap<String, Integer>();

        for (String kmer : kmerMap.keySet()) {
            String gene = kmerMap.get(kmer);
            if (!kmerCountsPerGene.containsKey(gene)) {
                kmerCountsPerGene.put(gene, 1);
            } else {
                kmerCountsPerGene.put(gene, kmerCountsPerGene.get(gene) + 1);
            }
        }

        return kmerCountsPerGene;
    }

    public int execute() {
        HashMap<String, String> kmerMap = loadGeneKmers(GENES_FASTA, CORTEX_GRAPH.getKmerSize());
        HashMap<String, Integer> totalKmerCountsPerGene = getKmerCountsPerGene(kmerMap);

        System.out.println("Number of genes in map: " + totalKmerCountsPerGene.size());

        HashMap<String, HashMap<String, Integer>> kmerCountsPerGenePerColor = new HashMap<String, HashMap<String, Integer>>();

        int genesColor = CORTEX_GRAPH.getColorForSampleName("genes");
        int p3D7Color = CORTEX_GRAPH.getColorForSampleName("3D7");

        if (genesColor >= 0) {
            for (CortexRecord cr : CORTEX_GRAPH) {
                if (cr.getCoverages()[genesColor] == 1 && cr.getCoverages()[p3D7Color] == 1) {
                    String kmer = cr.getKmerString();

                    if (kmer != null && kmerMap.containsKey(kmer)) {
                        String geneName = kmerMap.get(kmer);

                        for (int color = 0; color < CORTEX_GRAPH.getNumColors(); color++) {
                            if (cr.getCoverages()[color] == 1) {
                                String sampleName = CORTEX_GRAPH.getColor(color).getSampleName();

                                if (!kmerCountsPerGenePerColor.containsKey(sampleName)) {
                                    kmerCountsPerGenePerColor.put(sampleName, new HashMap<String, Integer>());
                                }

                                if (!kmerCountsPerGenePerColor.get(sampleName).containsKey(geneName)) {
                                    kmerCountsPerGenePerColor.get(sampleName).put(geneName, 1);
                                } else {
                                    kmerCountsPerGenePerColor.get(sampleName).put(geneName, kmerCountsPerGenePerColor.get(sampleName).get(geneName) + 1);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // yell at user
        }

        out.print("gene\ttotal\t");

        String[] sampleNames = kmerCountsPerGenePerColor.keySet().toArray(new String[0]);
        for (String sampleName : sampleNames) {
            out.print("\t" + sampleName);
        }
        out.println();

        for (String geneName : totalKmerCountsPerGene.keySet()) {
            out.print(geneName + "\t" + totalKmerCountsPerGene.get(geneName));

            for (String sampleName : sampleNames) {
                if (kmerCountsPerGenePerColor.containsKey(sampleName) && kmerCountsPerGenePerColor.get(sampleName).containsKey(geneName)) {
                    out.print("\t" + kmerCountsPerGenePerColor.get(sampleName).get(geneName));
                } else {
                    out.print("\t" + -1);
                }
            }

            out.println();
        }

        return 0;
    }
}
