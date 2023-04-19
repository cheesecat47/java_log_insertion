package kr.ac.knu.isslab;

import soot.Pack;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.options.Options;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainDriver {
    public static void main(String[] args) {

        if (args.length == 0) {
            System.err.println("Usage: java MainDriver [options] classname");
            System.exit(0);
        }

        ArrayList<String> classPaths = new ArrayList<>(
                Arrays.asList(
                        System.getProperty("java.class.path").split(File.pathSeparator)
                )
        );

        List<String> jarFiles = Arrays.asList(
                System.getProperty("java.home") + "/lib/rt.jar",
                System.getProperty("java.home") + "/lib/jce.jar"
        );
        for (String jarFile : jarFiles) {
            if (!classPaths.contains(jarFile)) {
                classPaths.add(jarFile);
            }
        }
        System.out.println(classPaths);

        soot.G.reset();
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_soot_classpath(String.join(":", classPaths));

        Options.v().setPhaseOption("jb", "model-lambdametafactory:false");
        Options.v().setPhaseOption("jb", "use-original-names:true");

        Scene.v().loadClassAndSupport("java.lang.Object");
        Scene.v().loadClassAndSupport("java.lang.System");

        Options.v().setPhaseOption("cg", "enabled:true");
        Options.v().setPhaseOption("cg.spark", "enabled:true");    // must be enabled
        Options.v().setPhaseOption("cg.spark", "dump-pag:true");
        Options.v().setPhaseOption("cg.spark", "topo-sort:true");

        Pack wjtp = PackManager.v().getPack("wjtp");
        wjtp.add(new Transform("wjtp.transformer", new WjtpTransformer()));

        Pack jtp = PackManager.v().getPack("jtp");
        jtp.add(new Transform("jtp.instrumenter", new InvokeStaticInstrumenter()));

        soot.Main.main(args);
    }
}