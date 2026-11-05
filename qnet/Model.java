package qnet;

import java.util.List;

public class Model {
    private final List<Element> elements;
    private double tcurr = 0.0;
    private boolean trace;

    public Model(List<Element> elements) {
        this.elements = elements;
    }

    public Model(Element... elements) {
        this(List.of(elements));
    }

    public void setTrace(boolean trace) { this.trace = trace; }

    public void simulate(double time) {
        while (true) {
            double tnext = Double.MAX_VALUE;
            for (Element e : elements) tnext = Math.min(tnext, e.getTnext());

            double t = Math.min(tnext, time);
            for (Element e : elements) e.doStatistics(t - tcurr);
            tcurr = t;
            for (Element e : elements) e.setTcurr(tcurr);
            if (tnext > time) break;

            for (Element e : elements) {
                if (e.getTnext() == tcurr) e.outAct();
            }

            boolean changed;
            do {
                changed = false;
                for (Element e : elements) changed |= e.tryUnblock();
            } while (changed);

            if (trace) printState();
        }
    }

    private void printState() {
        System.out.printf("t = %.4f%n", tcurr);
        for (Element e : elements) {
            String s = e instanceof Process p
                    ? String.format("busy=%d blocked=%d queue=%d", p.busy(), p.blocked(), p.getQueue())
                    : "";
            System.out.printf("   %-20s %s tnext=%.4f%n", e.getName(), s, e.getTnext());
        }
    }

    public void printResult() {
        System.out.printf("%n------------- RESULTS (time = %.2f) -------------%n", tcurr);
        for (Element e : elements) e.printResult(tcurr);
        System.out.println("-------------------------------------------------");
    }

    public double getTcurr() { return tcurr; }
}
