package kr.ac.knu.isslab;

import soot.*;
import soot.jimple.*;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;
import soot.tagkit.StringTag;
import soot.util.Chain;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class InvokeStaticInstrumenter extends BodyTransformer {

    static SootClass agentClass = Scene.v().loadClassAndSupport("com.finedigital.MyAgent");
    static SootMethod handleInstrumentedLocals = agentClass.getMethod(
            "java.util.HashMap handleInstrumentedLocals(java.lang.String,long,java.util.HashMap,java.util.ArrayList)");
    static SootMethod report = agentClass.getMethod("void report(java.util.HashMap)");
    static SootMethod getMethodStackInfo = agentClass.getMethod("java.util.HashMap getMethodStackInfo(java.lang.Thread)");
    
    private final AtomicInteger myRefCounter = new AtomicInteger(0);
    private final List<String> javaPrimitives = new ArrayList<>(Arrays.asList("int", "long", "float", "double", "char", "boolean"));

    private <E> E getLastElem(List<E> l) {
        return l.get(l.size() - 1);
    }

    private Local createNewLocal(Body body, String refKey, Type t) {
        Local tmp = Jimple.v().newLocal(String.format("%s%d", refKey, myRefCounter.getAndAdd(1)), t);
        body.getLocals().add(tmp);
        return tmp;
    }

    private Stmt printSootLogVirtual(String beforeafter, Body body, Stmt insertPointStmt, Local mapLocal) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        Stmt reportStmt = Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(report.makeRef(), mapLocal));
        manipulatedStmtList.add(reportStmt);

        if (beforeafter.equals("before")) {
            units.insertBefore(manipulatedStmtList, insertPointStmt);
        } else if (beforeafter.equals("after")) {
            units.insertAfter(manipulatedStmtList, insertPointStmt);
            insertPointStmt = getLastElem(manipulatedStmtList);
        }
        return insertPointStmt;
    }

    private List<Object> makeAttributesList(Body body, Stmt insertPointStmt) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        Local attrListLocal = createNewLocal(body, "li", RefType.v("java.util.ArrayList"));
        Stmt attrListStmt = Jimple.v().newAssignStmt(
                attrListLocal,
                Jimple.v().newNewExpr(RefType.v("java.util.ArrayList")));
        manipulatedStmtList.add(attrListStmt);

        Stmt initAtrrListLocalStmt = Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        attrListLocal,
                        Scene.v().getMethod("<java.util.ArrayList: void <init>()>").makeRef()));
        manipulatedStmtList.add(initAtrrListLocalStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        return Arrays.asList(insertPointStmt, attrListLocal);
    }

    private List<Object> handleSootValue(Body body, Stmt insertPointStmt, String type, Value value) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        String appendArgType = "java.lang.Object";

        if (javaPrimitives.contains(type)) {
            appendArgType = type;
        } else if (type.equals("byte") || type.equals("short")) {
            appendArgType = "int";
            Local toInt = createNewLocal(body, "i", IntType.v());
            Stmt toIntCastStmt = Jimple.v().newAssignStmt(
                    toInt,
                    Jimple.v().newCastExpr(value, IntType.v()));
            value = toInt;
            manipulatedStmtList.add(toIntCastStmt);
        } else if (type.equals("java.lang.String") || type.equals("java/lang/String")) {
            appendArgType = "java.lang.String";
        }

        // String paramValue = new StringBuilder(paramValue).toString();
        Local paramValueSbLocal = createNewLocal(body, "sb", RefType.v("java.lang.StringBuilder"));
        Stmt newParamValueSbStmt = Jimple.v().newAssignStmt(
                paramValueSbLocal,
                Jimple.v().newNewExpr(RefType.v("java.lang.StringBuilder")));
        manipulatedStmtList.add(newParamValueSbStmt);

        Stmt initParamValueSbStmt = Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        paramValueSbLocal,
                        Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>").makeRef()));
        manipulatedStmtList.add(initParamValueSbStmt);

        Stmt paramValueSbAppendStmt = Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(
                        paramValueSbLocal,
                        Scene.v().getMethod(String.format("<java.lang.StringBuilder: java.lang.StringBuilder append(%s)>", appendArgType)).makeRef(),
                        value));
        manipulatedStmtList.add(paramValueSbAppendStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        return Arrays.asList(insertPointStmt, paramValueSbLocal);
    }

    private Stmt addParamInfoIntoAttrList(Body body, Local attrListLocal, Stmt insertPointStmt, List<IdentityStmt> parameterList) {
        UnitPatchingChain units = body.getUnits();
        SootMethod method = body.getMethod();

        int cnt = method.getParameterCount();
        if (cnt == 0) { return insertPointStmt; }

        List<Stmt> manipulatedStmtList = new ArrayList<>();

        for (IdentityStmt s: parameterList) {
            Local mapLocal = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
            Stmt newMapStmt = Jimple.v().newAssignStmt(
                    mapLocal,
                    Jimple.v().newNewExpr(RefType.v("java.util.HashMap")));
            manipulatedStmtList.add(newMapStmt);

            Stmt initMapStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            mapLocal, Scene.v().getMethod("<java.util.HashMap: void <init>()>").makeRef()));
            manipulatedStmtList.add(initMapStmt);

            Stmt mapPutTypeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            mapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("type"), StringConstant.v(s.getRightOp().toString())));
            manipulatedStmtList.add(mapPutTypeStmt);

            Value paramValue = s.getLeftOp();
            String paramType = paramValue.getType().toString();

            List<Object> ret = handleSootValue(body, insertPointStmt, paramType, paramValue);
            insertPointStmt = (Stmt) ret.get(0);
            Local paramValueSbLocal = (Local) ret.get(1);

            Stmt mapPutValueStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            mapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("value"), paramValueSbLocal));
            manipulatedStmtList.add(mapPutValueStmt);

            Stmt listPutStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newInterfaceInvokeExpr(
                            attrListLocal,
                            Scene.v().getMethod("<java.util.List: boolean add(java.lang.Object)>").makeRef(),
                            mapLocal));
            manipulatedStmtList.add(listPutStmt);
        }

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        return insertPointStmt;
    }

    private Stmt addMethodEntryLog(Body body, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, List<IdentityStmt> parameterList){
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        Local attrListLocal = null;
        if (parameterList.size() > 0){
            List<Object> ret = makeAttributesList(body, insertPointStmt);
            insertPointStmt = (Stmt) ret.get(0);
            attrListLocal = (Local) ret.get(1);
            insertPointStmt = addParamInfoIntoAttrList(body, attrListLocal, insertPointStmt, parameterList);
        }

        Local methodEntryMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        StaticInvokeExpr expr;
        if (attrListLocal == null) {
            expr = Jimple.v().newStaticInvokeExpr(
                handleInstrumentedLocals.makeRef(),
                StringConstant.v("Method Start"), 
                startAtLocal, 
                threadStrRef, 
                NullConstant.v()
            );
        } else {
            expr = Jimple.v().newStaticInvokeExpr(
                handleInstrumentedLocals.makeRef(),    
                StringConstant.v("Method Start"), 
                startAtLocal, 
                threadStrRef, 
                attrListLocal
            );
        }
        Stmt methodEntryMapStmt = Jimple.v().newAssignStmt(methodEntryMap, expr);
        manipulatedStmtList.add(methodEntryMapStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        return printSootLogVirtual("after", body, insertPointStmt, methodEntryMap);
    }

    private void redirectPrintStream(Body body, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, InvokeExpr expr) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        List<Object> ret = makeAttributesList(body, insertPointStmt);
        insertPointStmt = (Stmt) ret.get(0);
        Local attrListLocal = (Local) ret.get(1);

        for (Value val: expr.getArgs()) {
            Stmt listPutStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newInterfaceInvokeExpr(
                            attrListLocal,
                            Scene.v().getMethod("<java.util.List: boolean add(java.lang.Object)>").makeRef(),
                            val));
            manipulatedStmtList.add(listPutStmt);
        }

        Local printStreamMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        Stmt printStreamMapStmt = Jimple.v().newAssignStmt(
                printStreamMap,
                Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                        StringConstant.v("From PrintStream"), startAtLocal, threadStrRef, attrListLocal));
        manipulatedStmtList.add(printStreamMapStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        printSootLogVirtual("after", body, insertPointStmt, printStreamMap);
    }

    private void addLogicBranchLog(Body body, Local startAtLocal, Local threadStrRef, Stmt branchStmt, String branch) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();
        Stmt insertPointStmt = branchStmt;

        Local logicBranchMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));

        String msg = String.format("Ln [%d]: Branch: %s: %s",
                ((LineNumberTag) branchStmt.getTag("LineNumberTag")).getLineNumber(),
                ((IfStmt) branchStmt).getCondition().toString(),
                branch);
        manipulatedStmtList.add(Jimple.v().newAssignStmt(
                logicBranchMap,
                Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                        StringConstant.v(msg), startAtLocal, threadStrRef, NullConstant.v())));

        if (branch.equals("true")) {
            units.insertAfter(manipulatedStmtList, insertPointStmt);
            insertPointStmt = getLastElem(manipulatedStmtList);
            printSootLogVirtual("after", body, insertPointStmt, logicBranchMap);
        } else {
            insertPointStmt = ((IfStmt) branchStmt).getTarget();
            units.insertBefore(manipulatedStmtList, insertPointStmt);
            printSootLogVirtual("before", body, insertPointStmt, logicBranchMap);
        }
    }

    private void addExceptionLog(Body body, Local startAtLocal, Local threadStrRef, Trap t) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();
        Local exceptionhMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));

        String msg = "";
        LineNumberTag tag = (LineNumberTag) t.getBeginUnit().getTag("LineNumberTag");
        if (tag != null) { msg += String.format("Ln [%d]: ", tag.getLineNumber()); }
        msg += String.format("Catch %s", t.getException());

        manipulatedStmtList.add(Jimple.v().newAssignStmt(
                exceptionhMap,
                Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                        StringConstant.v(msg), startAtLocal, threadStrRef, NullConstant.v())));

        Stmt insertPointStmt = (Stmt) t.getHandlerUnit();
        units.insertBefore(manipulatedStmtList, insertPointStmt);
        printSootLogVirtual("before", body, insertPointStmt, exceptionhMap);
    }

    private void addMethodExitLog(Body body, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, Stmt stmt) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        List<Object> ret = makeAttributesList(body, insertPointStmt);
        insertPointStmt = (Stmt) ret.get(0);
        Local attrListLocal = (Local) ret.get(1);

        if (stmt instanceof ReturnStmt) {
            ReturnStmt returnStmt = (ReturnStmt) stmt;

            Local returnMapLocal = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
            Stmt newReturnMapStmt = Jimple.v().newAssignStmt(
                    returnMapLocal, Jimple.v().newNewExpr(RefType.v("java.util.HashMap")));
            manipulatedStmtList.add(newReturnMapStmt);

            Stmt initReturnMapStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            returnMapLocal, Scene.v().getMethod("<java.util.HashMap: void <init>()>").makeRef()));
            manipulatedStmtList.add(initReturnMapStmt);

            Value returnValue = returnStmt.getOp();
            String returnType = returnValue.getType().toString();

            Stmt returnMapPutTypeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            returnMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("type"), StringConstant.v(returnType)));
            manipulatedStmtList.add(returnMapPutTypeStmt);

            units.insertAfter(manipulatedStmtList, insertPointStmt);
            insertPointStmt = getLastElem(manipulatedStmtList);
            manipulatedStmtList.clear();

            List<Object> ret2 = handleSootValue(body, insertPointStmt, returnType, returnValue);
            insertPointStmt = (Stmt) ret2.get(0);
            Local returnValueSbLocal = (Local) ret2.get(1);

            Stmt returnMapPutValueStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            returnMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("value"), returnValueSbLocal));
            manipulatedStmtList.add(returnMapPutValueStmt);

            Stmt listPutStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newInterfaceInvokeExpr(
                            attrListLocal,
                            Scene.v().getMethod("<java.util.List: boolean add(java.lang.Object)>").makeRef(),
                            returnMapLocal));
            manipulatedStmtList.add(listPutStmt);
        }

        if (body.getMethod().getName().equals("channelReadComplete")) {
            Local elapsedTimeMapLocal = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    elapsedTimeMapLocal,
                    Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))));

            manipulatedStmtList.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            elapsedTimeMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: void <init>()>").makeRef())));

            Local endAtLocal = createNewLocal(body, "tiEndAt", LongType.v());
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    endAtLocal,
                    Jimple.v().newStaticInvokeExpr(Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef())));

            Local elapsedLocal = createNewLocal(body, "tiElapsed", LongType.v());
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    elapsedLocal,
                    Jimple.v().newSubExpr(endAtLocal, startAtLocal)));

            Local elapsedStrLocal = createNewLocal(body, "str", RefType.v("java.lang.String"));
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    elapsedStrLocal,
                    Jimple.v().newStaticInvokeExpr(Scene.v().getMethod("<java.lang.String: java.lang.String valueOf(long)>").makeRef(), elapsedLocal)));

            manipulatedStmtList.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            elapsedTimeMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("Elapsed Time (ms)"), elapsedStrLocal)));

            manipulatedStmtList.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newInterfaceInvokeExpr(
                            attrListLocal,
                            Scene.v().getMethod("<java.util.List: boolean add(java.lang.Object)>").makeRef(),
                            elapsedTimeMapLocal)));
        }

        Local methodExitMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        manipulatedStmtList.add(Jimple.v().newAssignStmt(
                methodExitMap,
                Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                        StringConstant.v("Method End"), startAtLocal, threadStrRef, attrListLocal)));

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        printSootLogVirtual("after", body, insertPointStmt, methodExitMap);
    }

    protected void internalTransform(Body body, String phase, Map options) {
        UnitPatchingChain units = body.getUnits();
        SootMethod method = body.getMethod();

        if (!method.getSignature().contains("com.finedigital")) { return; }
        if (method.getName().equals("threadSleep")) { return; }
        if (method.getSignature().contains("lambda")) { return; }

        System.out.println("instrumenting method : " + method.getSignature());
        
        boolean isInit = method.getName().contains("<init>");
        Stmt initInvokeStmt = null;
        Stmt firstStmt = (Stmt) units.getFirst();

        Iterator<Unit> stmtIt = units.snapshotIterator();
        List<IdentityStmt> parameterList = new ArrayList<>();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if ((stmt instanceof IdentityStmt) && (stmt.toString().contains("@parameter"))) {
                parameterList.add((IdentityStmt) stmt);
                firstStmt = stmt;
            }
            if (stmt.toString().contains("<init>")) { initInvokeStmt = stmt; }
        }
        if (isInit) { firstStmt = initInvokeStmt; }

        Local startAtLocal = createNewLocal(body, "tiStartAt", LongType.v());
        Stmt startAtStmt = Jimple.v().newAssignStmt(
                startAtLocal,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef()));
        units.insertAfter(startAtStmt, firstStmt);
        firstStmt = startAtStmt;

        Local currentThreadRef = createNewLocal(body, "th", RefType.v("java.lang.Thread"));
        Stmt currentThreadStmt = Jimple.v().newAssignStmt(
                currentThreadRef,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().getMethod("<java.lang.Thread: java.lang.Thread currentThread()>").makeRef()));
        units.insertAfter(currentThreadStmt, firstStmt);
        firstStmt = currentThreadStmt;

        Local threadStrRef = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        Stmt threadStrStmt = Jimple.v().newAssignStmt(
                threadStrRef,
                Jimple.v().newStaticInvokeExpr(getMethodStackInfo.makeRef(), currentThreadRef));
        units.insertAfter(threadStrStmt, firstStmt);
        firstStmt = threadStrStmt;

        firstStmt = addMethodEntryLog(body, startAtLocal, threadStrRef, firstStmt, parameterList);

        stmtIt = units.snapshotIterator();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (!stmt.containsInvokeExpr()) { continue; }

            InvokeExpr expr = stmt.getInvokeExpr();
            if (!(expr instanceof VirtualInvokeExpr)) { continue; }

            SootMethod thisMethod = expr.getMethod();
            if (!thisMethod.getDeclaringClass().toString().equals("java.io.PrintStream")) { continue; }
            if (expr.getArgs().size() == 0) { continue; }

            redirectPrintStream(body, startAtLocal, threadStrRef, stmt, expr);
        }

        stmtIt = units.snapshotIterator();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (!(stmt instanceof IfStmt)) { continue; }
            IfStmt thisIfStmt = (IfStmt) stmt;

            boolean isIfStmtFromOrigin = false;
            for (Tag t: thisIfStmt.getTags()) {
                if (!t.getName().equals("StringTag")) { continue; }
                isIfStmtFromOrigin |= ((StringTag) t).getInfo().equals("MyAgent");
            }
            if (isIfStmtFromOrigin) { continue; }

            addLogicBranchLog(body, startAtLocal, threadStrRef, thisIfStmt, "true");
            addLogicBranchLog(body, startAtLocal, threadStrRef, thisIfStmt, "false");
        }

        Chain<Trap> traps = body.getTraps();
        for (Trap t: traps) {
            addExceptionLog(body, startAtLocal, threadStrRef, t);
        }

        stmtIt = units.snapshotIterator();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (!((stmt instanceof ReturnStmt) || (stmt instanceof ReturnVoidStmt))) { continue; }

            addMethodExitLog(body, startAtLocal, threadStrRef, stmt, stmt);
        }
    }
}
