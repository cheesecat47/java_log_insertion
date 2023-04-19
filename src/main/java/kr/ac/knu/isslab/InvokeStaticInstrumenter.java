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
            "java.util.HashMap handleInstrumentedLocals(java.lang.String,int,long,java.util.HashMap,java.util.ArrayList)");
    static SootMethod report = agentClass.getMethod("void report(java.util.HashMap)");
    static SootMethod getMethodStackInfo = agentClass.getMethod("java.util.HashMap getMethodStackInfo(java.lang.Thread)");
    static SootMethod isEdgeInCG = agentClass.getMethod("boolean isEdgeInCG(java.util.HashMap)");
    static SootMethod checkSystemAbnormality = agentClass.getMethod("boolean checkSystemAbnormality(int)");

    private final AtomicInteger myRefCounter = new AtomicInteger(0);
    private final HashMap<String, Integer> depthMap = new HashMap<>();
    private final List<String> javaPrimitives = new ArrayList<>(Arrays.asList("int", "long", "float", "double", "char", "boolean"));


    public InvokeStaticInstrumenter() {
        String userDirectory = System.getProperty("user.dir");
        try (BufferedReader br = new BufferedReader(new FileReader(userDirectory + "/output.depth.tsv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                depthMap.put(values[0], Integer.parseInt(values[1]));
            }
        } catch (Exception ignored) {}
    }

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

        // list 생성
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

        // parameter마다 개별로 map 생성해서 list에 추가
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

            // parameter type 추가
            Stmt mapPutTypeStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            mapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("type"), StringConstant.v(s.getRightOp().toString())));
            manipulatedStmtList.add(mapPutTypeStmt);

            // value 추가
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

            // map을 list에 추가
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

    private Stmt addMethodEntryLog(Body body, int methodLevel, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, List<IdentityStmt> parameterList){
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
            expr = Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                    StringConstant.v("Method Start"), IntConstant.v(methodLevel), startAtLocal, threadStrRef, NullConstant.v());
        } else {
            expr = Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                    StringConstant.v("Method Start"), IntConstant.v(methodLevel), startAtLocal, threadStrRef, attrListLocal);
        }
        Stmt methodEntryMapStmt = Jimple.v().newAssignStmt(methodEntryMap, expr);
        manipulatedStmtList.add(methodEntryMapStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        return printSootLogVirtual("after", body, insertPointStmt, methodEntryMap);
    }

    private void addAbnormalCallLog(Body body, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        Local edgeInCGBoolRef = createNewLocal(body, "bool", BooleanType.v());
        Stmt edgeInCGBoolStmt = Jimple.v().newAssignStmt(
                edgeInCGBoolRef,
                Jimple.v().newStaticInvokeExpr(isEdgeInCG.makeRef(), threadStrRef));
        manipulatedStmtList.add(edgeInCGBoolStmt);

        // if (!edgeInCGBool) { log.warn("This edge is not in CG"); }
        Stmt edgeIfStmt = Jimple.v().newIfStmt(
                Jimple.v().newEqExpr(edgeInCGBoolRef, IntConstant.v(1)),
                (Unit) units.getSuccOf(insertPointStmt));
        edgeIfStmt.addTag(new StringTag("MyAgent"));
        manipulatedStmtList.add(edgeIfStmt);

        Local edgeCheckMap = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        Stmt edgeCheckStmt = Jimple.v().newAssignStmt(
                edgeCheckMap,
                Jimple.v().newStaticInvokeExpr(handleInstrumentedLocals.makeRef(),
                        StringConstant.v("Unknown Call. Check the parent and this methods."), IntConstant.v(-1), startAtLocal, threadStrRef, NullConstant.v()));
        manipulatedStmtList.add(edgeCheckStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        printSootLogVirtual("after", body, insertPointStmt, edgeCheckMap);
    }

    private void redirectPrintStream(Body body, int methodLevel, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, InvokeExpr expr) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        // 함수 반환값 담을 list 생성
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
                        StringConstant.v("From PrintStream"), IntConstant.v(methodLevel), startAtLocal, threadStrRef, attrListLocal));
        manipulatedStmtList.add(printStreamMapStmt);

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        printSootLogVirtual("after", body, insertPointStmt, printStreamMap);
    }

    private void addLogicBranchLog(Body body, int methodLevel, Local startAtLocal, Local threadStrRef, Stmt branchStmt, String branch) {
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
                        StringConstant.v(msg), IntConstant.v(methodLevel), startAtLocal, threadStrRef, NullConstant.v())));

        if (branch.equals("true")) {
            // if문 조건, 현재 분기가 t일 때
            units.insertAfter(manipulatedStmtList, insertPointStmt);
            insertPointStmt = getLastElem(manipulatedStmtList);
            printSootLogVirtual("after", body, insertPointStmt, logicBranchMap);
        } else {
            // 현재 분기가 f일 때
            insertPointStmt = ((IfStmt) branchStmt).getTarget();
            units.insertBefore(manipulatedStmtList, insertPointStmt);
            printSootLogVirtual("before", body, insertPointStmt, logicBranchMap);
        }
    }

    private void addExceptionLog(Body body, int methodLevel, Local startAtLocal, Local threadStrRef, Trap t) {
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
                        StringConstant.v(msg), IntConstant.v(methodLevel), startAtLocal, threadStrRef, NullConstant.v())));

        Stmt insertPointStmt = (Stmt) t.getHandlerUnit();
        units.insertBefore(manipulatedStmtList, insertPointStmt);
        printSootLogVirtual("before", body, insertPointStmt, exceptionhMap);
    }

    private void addMethodExitLog(Body body, int methodLevel, Local startAtLocal, Local threadStrRef, Stmt insertPointStmt, Stmt stmt) {
        UnitPatchingChain units = body.getUnits();
        List<Stmt> manipulatedStmtList = new ArrayList<>();

        // 함수 반환값 담을 list 생성
        List<Object> ret = makeAttributesList(body, insertPointStmt);
        insertPointStmt = (Stmt) ret.get(0);
        Local attrListLocal = (Local) ret.get(1);

        if (stmt instanceof ReturnStmt) {
            ReturnStmt returnStmt = (ReturnStmt) stmt;

            // map 생성
            Local returnMapLocal = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
            Stmt newReturnMapStmt = Jimple.v().newAssignStmt(
                    returnMapLocal, Jimple.v().newNewExpr(RefType.v("java.util.HashMap")));
            manipulatedStmtList.add(newReturnMapStmt);

            Stmt initReturnMapStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            returnMapLocal, Scene.v().getMethod("<java.util.HashMap: void <init>()>").makeRef()));
            manipulatedStmtList.add(initReturnMapStmt);

            // return type 추가
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

            // value 추가
            List<Object> ret2 = handleSootValue(body, insertPointStmt, returnType, returnValue);
            insertPointStmt = (Stmt) ret2.get(0);
            Local returnValueSbLocal = (Local) ret2.get(1);

            Stmt returnMapPutValueStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            returnMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: java.lang.Object put(java.lang.Object,java.lang.Object)>").makeRef(),
                            StringConstant.v("value"), returnValueSbLocal));
            manipulatedStmtList.add(returnMapPutValueStmt);

            // map을 list에 추가
            Stmt listPutStmt = Jimple.v().newInvokeStmt(
                    Jimple.v().newInterfaceInvokeExpr(
                            attrListLocal,
                            Scene.v().getMethod("<java.util.List: boolean add(java.lang.Object)>").makeRef(),
                            returnMapLocal));
            manipulatedStmtList.add(listPutStmt);
        }

        if (body.getMethod().getName().equals("channelReadComplete")) {
            // map 생성
            Local elapsedTimeMapLocal = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    elapsedTimeMapLocal,
                    Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))));

            manipulatedStmtList.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            elapsedTimeMapLocal,
                            Scene.v().getMethod("<java.util.HashMap: void <init>()>").makeRef())));

            // 종료 시간 기록 long endAt = System.currentTimeMillis();
            Local endAtLocal = createNewLocal(body, "tiEndAt", LongType.v());
            manipulatedStmtList.add(Jimple.v().newAssignStmt(
                    endAtLocal,
                    Jimple.v().newStaticInvokeExpr(Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef())));

            // long elapsedTime = stopTime - startTime;
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

            // map을 list에 추가
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
                        StringConstant.v("Method End"), IntConstant.v(methodLevel), startAtLocal, threadStrRef, attrListLocal)));

        units.insertAfter(manipulatedStmtList, insertPointStmt);
        insertPointStmt = getLastElem(manipulatedStmtList);

        // 로그 출력 코드 return문 전에 추가
        printSootLogVirtual("after", body, insertPointStmt, methodExitMap);
    }

    protected void internalTransform(Body body, String phase, Map options) {
        UnitPatchingChain units = body.getUnits();
        SootMethod method = body.getMethod();

        // 외부 라이브러리 말고 프로그램 메소드 바이트코드만 변경
        if (!method.getSignature().contains("com.finedigital")) { return; }

        // call graph에 메소드 존재하는지 확인
        int methodLevel = depthMap.getOrDefault(method.getSignature(), 100);

        // 프로그램 메소드긴 하지만 너무 많이 반복되고, 타임 아웃을 위한 것일 뿐, 중요한 기능 아니어서 제외
        if (method.getName().equals("threadSleep")) { return; }

        if (method.getSignature().contains("lambda")) { return; }

        System.out.println("instrumenting method : " + method.getSignature() + " / level: " + methodLevel);
        // System.out.println("tags: " + method.getTags());
        // System.out.println("context: " + method.context());


        // ============================================================
        // 함수 시작 시 정보 수집
        // startAt 변수 생성 - 함수 시작, 종료 로그 매칭을 위해
        // map 변수 생성 - 기존에는 시작, 종료 로그 찍을 때 각각 정보를 모았음.
        //     시작 로그 찍을 때 해시맵 만들고 값 저장해두면 종료 로그 찍을 때 다시 호출 안해도 됨.
        // ============================================================

        // init 함수가 호출되기 전에 뭔가 하면 문제가 발생했음. 그래서 init 함수 호출 위치 찾고 그 다음에 추가하기 위한 과정
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

        // 시작 시간 기록 long startAt = System.currentTimeMillis();
        Local startAtLocal = createNewLocal(body, "tiStartAt", LongType.v());
        Stmt startAtStmt = Jimple.v().newAssignStmt(
                startAtLocal,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef()));
        units.insertAfter(startAtStmt, firstStmt);
        firstStmt = startAtStmt;

        // get thread and method stack info
        // Thread currentThread = Thread.currentThread();
        Local currentThreadRef = createNewLocal(body, "th", RefType.v("java.lang.Thread"));
        Stmt currentThreadStmt = Jimple.v().newAssignStmt(
                currentThreadRef,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().getMethod("<java.lang.Thread: java.lang.Thread currentThread()>").makeRef()));
        units.insertAfter(currentThreadStmt, firstStmt);
        firstStmt = currentThreadStmt;

        // HashMap threadStr = getMethodStackInfo(currentThread)
        Local threadStrRef = createNewLocal(body, "map", RefType.v("java.util.HashMap"));
        Stmt threadStrStmt = Jimple.v().newAssignStmt(
                threadStrRef,
                Jimple.v().newStaticInvokeExpr(getMethodStackInfo.makeRef(), currentThreadRef));
        units.insertAfter(threadStrStmt, firstStmt);
        firstStmt = threadStrStmt;


        // ============================================================
        // 엣지 확인 로그
        // 1. 이 함수가 CG에 없는 경우
        // 2. 이 함수는 CG에 있으나, 이 함수를 호출한 caller를 연결하는 엣지가 콜 그래프에 없을 경우
        // 이상 호출이라는 로그 출력
        // ============================================================
        addAbnormalCallLog(body, startAtLocal, threadStrRef, firstStmt);


        if (methodLevel == 100){ return; }

        // get current SystemAbnormality
        Local checkSALocal = createNewLocal(body, "b", BooleanType.v());
        Stmt checkSAStmt = Jimple.v().newAssignStmt(
                checkSALocal,
                Jimple.v().newStaticInvokeExpr(checkSystemAbnormality.makeRef(), IntConstant.v(methodLevel)));
        units.insertAfter(checkSAStmt, firstStmt);
        firstStmt = checkSAStmt;

        // ============================================================
        // 함수 시작 로그 추가
        // parameter 정보 삽입
        // 함수 시작 로그 다음 엣지 출력 로그 추가하기 위해 label을 엣지 확인 로그의 첫 stmt로 설정하는 과정 추가
        // ============================================================
        // print this log if methodLevel >= SystemAbnormality
        IfStmt ifStmt = Jimple.v().newIfStmt(
                Jimple.v().newEqExpr(checkSALocal, IntConstant.v(0)),
                (Stmt) units.getSuccOf(firstStmt));
        ifStmt.addTag(new StringTag("MyAgent"));
        units.insertAfter(ifStmt, firstStmt);

        firstStmt = addMethodEntryLog(body, methodLevel, startAtLocal, threadStrRef, ifStmt, parameterList);


        // ============================================================
        // System.out.Print<?> 바꾸기
        // ?에 들어가는 변수를 얻어서 우리 로그 형식으로 출력
        // ============================================================
        stmtIt = units.snapshotIterator();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (!stmt.containsInvokeExpr()) { continue; }

            InvokeExpr expr = stmt.getInvokeExpr();
            if (!(expr instanceof VirtualInvokeExpr)) { continue; }

            SootMethod thisMethod = expr.getMethod();
            if (!thisMethod.getDeclaringClass().toString().equals("java.io.PrintStream")) { continue; }
            if (expr.getArgs().size() == 0) { continue; }

            ifStmt = Jimple.v().newIfStmt(
                    Jimple.v().newEqExpr(checkSALocal, IntConstant.v(0)),
                    (Stmt) units.getSuccOf(stmt));
            ifStmt.addTag(new StringTag("MyAgent"));
            units.insertAfter(ifStmt, stmt);

            // virtualinvoke $r16.<java.io.PrintStream: void println(java.lang.String)>($r20);
            redirectPrintStream(body, methodLevel, startAtLocal, threadStrRef, ifStmt, expr);
        }

        // ============================================================
        // 논리 분기문 로깅
        // 여기서 삽입한 로그 외 if문, else문에 로깅 포인트 추가
        // ============================================================
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

            addLogicBranchLog(body, methodLevel, startAtLocal, threadStrRef, thisIfStmt, "true");
            addLogicBranchLog(body, methodLevel, startAtLocal, threadStrRef, thisIfStmt, "false");
        }

        // ============================================================
        // 예외 처리 로깅
        // ============================================================
        Chain<Trap> traps = body.getTraps();
        for (Trap t: traps) {
            addExceptionLog(body, methodLevel, startAtLocal, threadStrRef, t);
        }

        // ============================================================
        // 함수 종료 로그
        // 위에서 선언한 startAt, map 변수 활용해서 정보 재생성 안하도록.
        // return void 이어도 마지막 stmt는 return 이므로
        // return stmt 바로 앞에 로그 추가
        // ============================================================
        stmtIt = units.snapshotIterator();
        while (stmtIt.hasNext()) {
            Stmt stmt = (Stmt) stmtIt.next();
            if (!((stmt instanceof ReturnStmt) || (stmt instanceof ReturnVoidStmt))) { continue; }

            ifStmt = Jimple.v().newIfStmt(
                    Jimple.v().newEqExpr(checkSALocal, IntConstant.v(0)),
                    stmt);
            ifStmt.addTag(new StringTag("MyAgent"));
            units.insertBefore(ifStmt, stmt);

            addMethodExitLog(body, methodLevel, startAtLocal, threadStrRef, ifStmt, stmt);
            ifStmt.setTarget(stmt);
        }
    }
}
