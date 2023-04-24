package kr.ac.knu.isslab;

import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.dot.DotGraph;

import java.util.Map;

public class WjtpTransformer extends SceneTransformer {
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        System.out.println("WjtpTransformer: internalTransform: phaseName: " + phaseName);
        CHATransformer.v().transform();

        CallGraph callGraph = Scene.v().getCallGraph();
        int callGraphSize = callGraph.size();
        System.out.println("WjtpTransformer: internalTransform: callGraph.size(): " + callGraphSize);

        DotGraph dotGraph = new DotGraph("callgraph");

        for (Edge edge : callGraph) {
            SootMethod edgeSrc = (SootMethod) edge.getSrc();
            SootMethod edgeTgt = (SootMethod) edge.getTgt();
            if (!edgeSrc.getDeclaringClass().getName().contains("PACKAGENAME") && !edgeTgt.getDeclaringClass().getName().contains("PACKAGENAME")) {
                continue;
            }
            dotGraph.drawEdge(edgeSrc.toString(), edgeTgt.toString());
        }
        dotGraph.plot("./callgraph.dot");
    }
}
