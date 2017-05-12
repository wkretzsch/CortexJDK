package uk.ac.ox.well.indiana.utils.traversal;

import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexRecord;

import java.util.Set;
import java.util.TreeSet;

/**
 * Created by kiran on 10/05/2017.
 */
public class CortexVertex {
    private String sk;
    private CortexRecord cr;
    private Set<Integer> colors = new TreeSet<>();

    public CortexVertex(String sk, CortexRecord cr, int color) {
        this.sk = sk;
        this.cr = cr;
        this.colors.add(color);
    }

    public CortexVertex(String sk, CortexRecord cr, Set<Integer> colors) {
        this.sk = sk;
        this.cr = cr;
        this.colors.addAll(colors);
    }

    public String getSk() { return sk; }

    public CortexRecord getCr() { return cr; }

    public Set<Integer> getColors() { return colors; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CortexVertex that = (CortexVertex) o;

        if (sk != null ? !sk.equals(that.sk) : that.sk != null) return false;
        if (cr != null ? !cr.equals(that.cr) : that.cr != null) return false;
        return colors != null ? colors.equals(that.colors) : that.colors == null;

    }

    @Override
    public int hashCode() {
        int result = sk != null ? sk.hashCode() : 0;
        result = 31 * result + (cr != null ? cr.hashCode() : 0);
        result = 31 * result + (colors != null ? colors.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CortexVertex{" +
                "sk='" + sk + '\'' +
                ", colors=" + colors +
                ", cr=" + cr +
                '}';
    }
}